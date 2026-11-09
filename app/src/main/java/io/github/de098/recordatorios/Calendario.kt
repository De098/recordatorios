package io.github.de098.recordatorios

import io.github.de098.recordatorios.Nucleo as N
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Lo que pide la web del calendario (/cal/…), contestado en el propio móvil. */
object Calendario {
    private val NORMAL = mapOf("tarea" to "#29b6f6", "cita" to "#29b6f6")   // sin color elegido: azul del logo de la app (antes tareas gris y citas naranja)
    private val HEX = N.COLOR_ITEM.filter { it.third != null }.associate { it.first to it.third!! }
    private val D = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

    private fun color(it: Item) = HEX[it.color] ?: NORMAL[it.tipo]!!
    private fun ahoraMin() = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

    private fun base(it: Item) = JSONObject().apply {
        put("id", it.id); put("ini", it.inicio.format(N.ISO_MIN)); put("col", it.color)
        put("t", it.texto); put("tipo", it.tipo); put("c", color(it)); put("imp", it.imp)
        put("rep", if (it.repetir != "no") N.describirRepeticion(it) else "")
        put("r", it.repetir)
        put("ciclo", it.ciclo?.let { JSONArray(listOf(it.first, it.second)) } ?: JSONObject.NULL)
        put("avisos", JSONArray(it.avisos.map { N.avisoJson(it) }))
        put("cada", it.cada); put("cumple", it.cumple); put("nac", it.nacido ?: JSONObject.NULL)
        put("notas", it.notas)
    }

    private fun copia(o: JSONObject) = JSONObject(o.toString())

    /** Todo lo que toca entre desde y hasta (fechas), para pintar el calendario. */
    fun datos(desde: LocalDate, hasta: LocalDate, ahora: LocalDateTime = ahoraMin()): JSONObject {
        val d0 = desde.atStartOfDay(); val d1 = hasta.atStartOfDay()
        val cosas = mutableListOf<JSONObject>()
        val atrasadas = mutableListOf<JSONObject>()
        for (it in N.leerItems()) {
            val b = base(it)
            for (o in N.ocurrencias(it, d0, d1)) {
                val e = copia(b)
                e.put("d", o.format(D)); e.put("h", if (it.cumple) "" else o.format(N.HM)); e.put("tt", N.titulo(it, o))
                if (it.tipo == "tarea") {
                    e.put("k", N.clave(it, o)); e.put("hecho", N.estaHecho(it, o)); e.put("pasada", o <= ahora)
                }
                cosas += e
            }
            N.pendiente(it, ahora)?.let { p ->
                val e = copia(b)
                e.put("d", p.format(D)); e.put("h", p.format(N.HM)); e.put("k", N.clave(it, p))
                e.put("cuando", N.fechaCorta(p, ahora))
                atrasadas += e
            }
        }
        cosas.sortWith(compareBy({ it.getString("d") }, { !it.getBoolean("imp") }, { it.getString("h") }))
        atrasadas.sortWith(compareBy({ it.getString("d") }, { it.getString("h") }))
        return JSONObject().apply {
            put("hoy", ahora.format(D)); put("ahora", ahora.format(N.HM))
            put("cosas", JSONArray(cosas)); put("pendientes", JSONArray(atrasadas)); put("opciones", opciones())
        }
    }

    private fun opciones() = JSONObject().apply {
        put("repetir", JSONArray(N.REPETIR.map { JSONArray(listOf(it.first, it.second)) }))
        put("avisos", JSONArray(N.AVISOS.map { JSONArray(listOf(N.avisoJson(it.first), it.second)) }))
        put("cada", JSONArray(N.CADA.map { JSONArray(listOf(it.first, it.second)) }))
        put("cada_def", N.CADA_HORAS * 60)
        put("colores", JSONArray(N.COLOR_ITEM.map { JSONArray(listOf(it.first, it.second)) }))
    }

    /** consulta ?desde=AAAA-MM-DD&hasta=AAAA-MM-DD (como mucho ~2 meses). */
    fun datosJson(q: Map<String, String>): String {
        var (desde, hasta) = try { LocalDate.parse(q["desde"]) to LocalDate.parse(q["hasta"]) } catch (_: Exception) {
            val hoy = LocalDate.now(); hoy.withDayOfMonth(1).minusDays(7) to hoy.plusDays(45)
        }
        if (hasta > desde.plusDays(70)) hasta = desde.plusDays(70)
        return datos(desde, hasta).toString()
    }

    /** Buscador: en todo lo apuntado; la próxima vez que toca (o la última si ya pasó). */
    fun buscar(q: String, ahora: LocalDateTime = LocalDateTime.now()): JSONArray {
        val hoy0 = ahora.toLocalDate().atStartOfDay()
        if (N.normal(q).trim().length < 2) return JSONArray()
        val futuras = mutableListOf<JSONObject>(); val pasadas = mutableListOf<JSONObject>()
        for (it in N.leerItems()) {
            if (!N.coincide(it, q)) continue
            val s = N.siguiente(it, if (it.cumple) hoy0 else ahora)
            val pasada = s == null
            val o = s ?: N.ultima(it, ahora) ?: it.inicio
            val cuando = (if (pasada) "Fue " else "") + (if (it.cumple || o.year == ahora.year) N.cuandoTxt(it, o, ahora)
                else N.fechaCorta(o, ahora).replace(" a las", " ${o.year} a las"))
            val e = JSONObject().apply {
                put("id", it.id); put("t", N.titulo(it, o)); put("notas", it.notas); put("tipo", it.tipo)
                put("cumple", it.cumple); put("imp", it.imp); put("c", color(it))
                put("rep", if (it.repetir != "no") N.describirRepeticion(it) else "")
                put("d", o.format(D)); put("pasada", pasada); put("cuando", cuando)
            }
            if (pasada) pasadas += e else futuras += e
        }
        futuras.sortBy { it.getString("d") }
        pasadas.sortByDescending { it.getString("d") }
        return JSONArray(futuras + pasadas)
    }

    /**
     * Apunta (nuevo) o cambia algo desde el formulario de la web.
     * JSON {id, texto, fecha, hora, tipo, color, repetir, ciclo:[sí,no], avisos:[…], cada, nacido, notas}.
     * tipo «cumple» = cumpleaños (cita anual a las 9:00). Al cambiar, lo que no venga se conserva.
     */
    fun guardar(cuerpo: String, nuevo: Boolean = false): String {
        val e: JSONObject; val viejo: Item?; val texto: String; val cumple: Boolean; val inicio: LocalDateTime
        try {
            e = JSONObject(cuerpo)
            viejo = if (nuevo) null else N.leerItems().first { it.id == e.getString("id") }
            texto = e.getString("texto").trim()
            cumple = e.optString("tipo") == "cumple"
            inicio = LocalDateTime.parse("${e.getString("fecha")}T${if (cumple) "09:00" else e.getString("hora")}")
        } catch (_: Exception) {
            return "No guardado: datos raros"
        }
        if (texto.isEmpty()) return "No guardado: falta el texto"
        var tipo = e.optString("tipo").takeIf { it == "tarea" || it == "cita" } ?: viejo?.tipo ?: "tarea"
        val color = e.optString("color").takeIf { c -> e.has("color") && N.COLOR_ITEM.any { it.first == c } } ?: viejo?.color ?: ""
        var repetir = e.optString("repetir").takeIf { r -> N.REPETIR.any { it.first == r } } ?: viejo?.repetir ?: "no"
        var nacido: Int? = null
        if (cumple) {
            tipo = "cita"; repetir = "anual"
            nacido = e.optInt("nacido", 0).takeIf { it in 1900..LocalDate.now().year }
        }
        var ciclo = viejo?.ciclo
        if (repetir == "ciclo") {
            try {
                val c = e.optJSONArray("ciclo")
                val si = c?.getInt(0) ?: ciclo?.first ?: 1
                val no = c?.getInt(1) ?: ciclo?.second ?: 1
                ciclo = si.coerceIn(1, 30) to no.coerceIn(1, 30)
            } catch (_: Exception) {
                return "No guardado: días salteados raros"
            }
        }
        val validos = N.AVISOS.map { it.first }
        val pedidos = e.optJSONArray("avisos")?.let { a -> (0 until a.length()).mapNotNull {
            when (val v = a.get(it)) { "dia" -> DIA; is Number -> v.toInt().takeIf { n -> n >= 0 }; else -> null } } }
            ?: viejo?.avisos ?: listOf(1440)
        val avisos = validos.filter { it in pedidos } + pedidos.filter { it !in validos && it >= 0 }
        val cada = e.optInt("cada", -1).takeIf { c -> N.CADA.any { it.first == c } } ?: viejo?.cada ?: (N.CADA_HORAS * 60)
        val antes = avisos.filter { it >= 0 }.minOrNull() ?: -1
        val it = N.guardarItem(texto, inicio, repetir, tipo, antes, ciclo, avisos, cada, color, id = viejo?.id,
            cumple = cumple, nacido = nacido, notas0 = if (e.has("notas")) N.txt(e, "notas").take(500) else null)
        // tarea repetida con inicio ya pasado → no avisar ya mismo de la vez vieja (al cambiar, solo si cambian fecha o repetición)
        val ahora = LocalDateTime.now()
        val cambia = viejo == null || viejo.inicio != inicio || viejo.repetir != repetir ||
            (repetir == "ciclo" && viejo.ciclo != ciclo)
        val u = N.ultima(it, ahora)
        if (cambia && tipo == "tarea" && repetir != "no" && u != null && u < ahora.minusMinutes(1)) N.marcarHecho(it, u)
        return "${if (nuevo) "Apuntado" else "Guardado"}: ${if (cumple) N.titulo(it) else texto}"
    }

    /**
     * cuerpo = el id (borra todo, como siempre) o JSON {id, modo: "esta" | "siguientes", fecha: "AAAA-MM-DD"}
     * para quitar solo una vez de algo que se repite, o esa y las siguientes.
     */
    fun borrar(cuerpo0: String): String {
        val cuerpo = cuerpo0.trim()
        var id = cuerpo
        var modo = "todo"
        var dia: LocalDate? = null
        if (cuerpo.startsWith("{")) {
            try {
                val j = JSONObject(cuerpo)
                id = j.optString("id").trim()
                modo = j.optString("modo", "todo")
                val f = j.optString("fecha")
                if (f.isNotEmpty()) dia = LocalDate.parse(f)
            } catch (_: Exception) { return "No borrado: petición rara" }
        }
        val item = N.leerItems().firstOrNull { x -> x.id == id } ?: return "No borrado: ya no está"
        if ((modo == "esta" || modo == "siguientes") && dia != null && item.repetir != "no" && !item.cumple) {
            if (!N.quitarVez(item, dia, modo)) return "Borrado: ${item.texto}"
            return if (modo == "esta") "Borrada solo esa vez: ${item.texto}" else "Borradas esa vez y las siguientes: ${item.texto}"
        }
        N.borrarItem(item)
        return "Borrado: ${item.texto}"
    }

    /** Marca como hecha la tarea «id__AAAAMMDDTHHMM». */
    fun marcar(clave: String): String {
        val m = Regex("""([0-9a-f]{6,32})__(\d{8}T\d{4})""").matchEntire(clave.trim()) ?: return "No se ha podido marcar"
        val it = N.leerItems().firstOrNull { it.id == m.groupValues[1] } ?: return "Esa tarea ya no existe"
        N.marcarHecho(it, LocalDateTime.parse(m.groupValues[2], java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")))
        return "✔ Hecho: ${it.texto}"
    }

    /** Deshace «✔ Hecho» (botón «Deshacer» del aviso, por si se pulsó sin querer). */
    fun desmarcar(clave: String): String {
        val m = Regex("""([0-9a-f]{6,32})__(\d{8}T\d{4})""").matchEntire(clave.trim()) ?: return "No se ha podido deshacer"
        val it = N.leerItems().firstOrNull { it.id == m.groupValues[1] } ?: return "Esa tarea ya no existe"
        N.desmarcarHecho(it, LocalDateTime.parse(m.groupValues[2], java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")))
        return "Deshecho: ${it.texto}"
    }

    // ---------------------------------------------------------------- avisos del móvil

    data class Aviso(val cuando: LocalDateTime, val titulo: String, val texto: String, val tipo: String,
                     val dia: LocalDate, val id: String)

    private const val MAX_AVISOS = 200

    /**
     * Avisos de las próximas horas, para dejarlos como alarmas: tareas a su hora y luego cada «cada» minutos hasta
     * «Hecho»; citas (y tareas IMPORTANTES) sus avisos previos; cumpleaños con su texto de fiesta; resumen de las 8:00.
     */
    fun avisos(horas: Long = 48, ahora0: LocalDateTime = LocalDateTime.now()): List<Aviso> {
        val ahora = ahora0.truncatedTo(ChronoUnit.MINUTES)
        val fin = ahora.plusHours(horas)
        val salida = mutableListOf<Aviso>()
        fun poner(cuando: LocalDateTime, titulo: String, texto: String, tipo: String, dia: LocalDateTime, id: String) {
            if (cuando > ahora && cuando <= fin) salida += Aviso(cuando, titulo, texto, tipo, dia.toLocalDate(), id)
        }
        for (it in N.leerItems()) {
            if (it.tipo == "tarea") {
                var veces = N.ocurrencias(it, ahora, fin)
                val p = N.pendiente(it, ahora)
                if (p != null && p !in veces) veces = listOf(p) + veces
                for ((n, o) in veces.withIndex()) {
                    if (N.estaHecho(it, o)) continue
                    val hasta = veces.getOrNull(n + 1) ?: N.siguiente(it, o) ?: fin
                    val tope = minOf(hasta, fin.plusMinutes(1))
                    var t = o; var k = 0
                    while (t < tope && k < 60) {
                        poner(t, (if (it.imp) "‼ " else "🔔 ") + it.texto,
                            N.conNotas("Tocaba " + N.fechaCorta(o, t), it, " · ", 120), "tarea", o, N.clave(it, o))
                        t = t.plusMinutes(it.cada.toLong()); k++
                    }
                }
            }
            if (N.avisosDe(it).isNotEmpty()) {
                for (o in N.ocurrencias(it, ahora.minusDays(1), fin.plusDays(8))) {
                    for ((a, desde, _) in N.ventanasAviso(it, o)) {
                        val id = "${N.clave(it, o)}@${N.avisoJson(a)}"
                        if (it.cumple) {
                            val (tit, cuerpo) = N.avisoCumple(it, o, desde)
                            poner(desde, (if (it.imp) "‼ " else "") + tit, N.conNotas(cuerpo, it, " · ", 120), "cumple", o, id)
                        } else {
                            poner(desde, (if (it.imp) "‼ " else "📅 ") + it.texto,
                                N.conNotas(N.fechaCorta(o, desde).replaceFirstChar { c -> c.uppercase() }, it, " · ", 120),
                                it.tipo, o, id)
                        }
                    }
                }
            }
        }
        for (d in listOf(ahora.toLocalDate(), ahora.toLocalDate().plusDays(1))) {
            val cuando = d.atTime(N.HORA_RESUMEN)
            if (cuando > ahora && cuando <= fin) {
                val texto = N.resumenDia(cuando) ?: continue
                val lineas = texto.split("\n")
                poner(cuando, lineas[0], lineas.drop(1).joinToString("\n"), "resumen", cuando, "resumen-$d")
            }
        }
        return salida.sortedBy { it.cuando }.take(MAX_AVISOS)
    }
}
