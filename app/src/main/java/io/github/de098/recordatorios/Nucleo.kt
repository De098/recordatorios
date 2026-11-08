package io.github.de098.recordatorios

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Aviso «al empezar el día» dentro de la lista de avisos (en los archivos se guarda como "dia"). */
const val DIA = -100

/** Algo apuntado: una tarea (avisa hasta «Hecho») o una cita (avisa antes). Los cumpleaños son citas anuales. */
data class Item(
    val id: String,
    val texto: String,
    val tipo: String,            // "tarea" | "cita"
    val inicio: LocalDateTime,   // en los cumpleaños, con el año 2000 (cuenta todos los años)
    val repetir: String,         // no | diario | semanal | mensual | anual | ciclo
    val ciclo: Pair<Int, Int>?,  // días sí, días no
    val color: String,
    val cada: Int,               // tareas: minutos entre avisos hasta «Hecho»
    val imp: Boolean,            // texto TODO EN MAYÚSCULAS = importante
    val notas: String,
    val avisos: List<Int>,       // minutos antes, o DIA
    val cumple: Boolean,
    val nacido: Int?,
    val antes: Int,
)

/**
 * Datos y reglas de fechas. Cada cosa es un archivo .json en items/ y cada «hecho» un archivo vacío en hechos/
 * (dentro de la carpeta privada de la app).
 */
object Nucleo {
    const val CADA_HORAS = 6
    val CADA = listOf(15 to "15 minutos", 30 to "30 minutos", 60 to "1 hora", 120 to "2 horas", 180 to "3 horas",
        360 to "6 horas", 720 to "12 horas", 1440 to "1 día")
    val GRACIA_CITA: Duration = Duration.ofHours(2)   // aviso «a la hora»: aún vale un rato después
    val REPETIR = listOf("no" to "Una vez", "diario" to "Cada día", "semanal" to "Cada semana", "mensual" to "Cada mes",
        "ciclo" to "Días salteados (x sí, y no)", "anual" to "Cada año")
    val HORA_CUMPLE: LocalTime = LocalTime.of(9, 0)
    val AVISOS_CUMPLE = listOf(1440, DIA)
    val AVISOS = listOf(0 to "A la hora", 30 to "30 min antes", 60 to "1 hora antes", 120 to "2 horas antes",
        180 to "3 horas antes", 360 to "6 horas antes", DIA to "Al empezar el día (desde las 7:00)",
        1440 to "1 día antes", 2880 to "2 días antes", 10080 to "1 semana antes")
    val COLOR_ITEM = listOf(Triple("", "⚪ Normal", null), Triple("rojo", "🔴 Rojo", "#e01b24"),
        Triple("naranja", "🟠 Naranja", "#ff7800"), Triple("amarillo", "🟡 Amarillo", "#f6d32d"),
        Triple("verde", "🟢 Verde", "#2ec27e"), Triple("azul", "🔵 Azul", "#3584e4"),
        Triple("morado", "🟣 Morado", "#9141ac"), Triple("marron", "🟤 Marrón", "#986a44"),
        Triple("negro", "⚫ Negro", "#000000"))
    val HORA_DIA: LocalTime = LocalTime.of(7, 0)
    val HORA_DIA_IMPORTANTE: LocalTime = LocalTime.of(6, 0)
    val HORA_RESUMEN: LocalTime = LocalTime.of(8, 0)
    val DIAS = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    val DIAS_C = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
    val MESES_C = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
        "octubre", "noviembre", "diciembre")

    private val CLAVE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
    val ISO_MIN: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    lateinit var base: File
    private val items get() = File(base, "items")
    private val hechos get() = File(base, "hechos")

    fun iniciar(c: Context) {
        if (!::base.isInitialized) base = c.applicationContext.filesDir
    }

    // ---------------------------------------------------------------- datos

    /** True si el texto está todo en mayúsculas y tiene al menos 3 letras («DNI», «DENTISTA 10:00»). */
    fun importante(texto: String): Boolean {
        val letras = texto.filter { it.isLetter() }
        return letras.length >= 3 && letras.all { it.isUpperCase() }
    }

    /** Texto de un campo JSON (null y ausente = ""). */
    fun txt(j: JSONObject, k: String): String = if (j.isNull(k)) "" else j.opt(k).toString()

    fun leerItems(): List<Item> {
        val lista = mutableListOf<Item>()
        val nombres = items.list() ?: return lista
        val validos = AVISOS.map { it.first }
        for (n in nombres) {
            if (!n.endsWith(".json") || n.startsWith(".")) continue
            try {
                val j = JSONObject(File(items, n).readText())
                var inicio = LocalDateTime.parse(j.getString("inicio"))
                var tipo = j.optString("tipo", "tarea")
                var repetir = j.optString("repetir", "no")
                val antes = j.optInt("antes", 1440)
                val color = txt(j, "color").takeIf { c -> COLOR_ITEM.any { it.first == c } } ?: ""
                val cada = maxOf(5, j.optInt("cada", CADA_HORAS * 60))
                val texto = txt(j, "texto")
                val av = j.optJSONArray("avisos")
                var avisos = if (av != null) (0 until av.length()).mapNotNull { leerAviso(av.get(it)) }
                             else if (antes >= 0) listOf(antes) else emptyList()
                avisos = avisos.filter { it in validos || it >= 0 }
                val cumple = j.optBoolean("cumple")
                var nacido: Int? = null
                if (cumple) {
                    tipo = "cita"; repetir = "anual"
                    inicio = inicio.withYear(2000)   // bisiesto: el 29 de febrero también vale
                    nacido = j.optInt("nacido", 0).takeIf { it != 0 }
                }
                var ciclo: Pair<Int, Int>? = null
                if (repetir == "ciclo") {
                    val c = j.getJSONArray("ciclo")
                    val si = c.getInt(0); val no = c.getInt(1)
                    if (si < 1 || no < 0) continue
                    ciclo = si to no
                }
                lista += Item(j.getString("id"), texto, tipo, inicio, repetir, ciclo, color, cada, importante(texto),
                    txt(j, "notas").trim(), avisos, cumple, nacido, antes)
            } catch (_: Exception) {
                // archivo roto: se ignora
            }
        }
        return lista
    }

    private fun leerAviso(v: Any?): Int? = when (v) {
        "dia" -> DIA
        is Number -> v.toInt().takeIf { it >= 0 }
        else -> null
    }

    fun avisoJson(a: Int): Any = if (a == DIA) "dia" else a

    /** Escribe de golpe (archivo temporal y renombrar), para no dejar nunca uno a medias. */
    fun escribir(f: File, contenido: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, ".${f.name}.${UUID.randomUUID().toString().take(6)}.tmp")
        tmp.writeText(contenido)
        if (!tmp.renameTo(f)) { f.writeText(contenido); tmp.delete() }
    }

    /** Apunta algo nuevo; con id reescribe uno ya apuntado, conservando «creado» y campos desconocidos. */
    fun guardarItem(texto: String, inicio: LocalDateTime, repetir: String, tipo: String = "tarea", antes0: Int = 1440,
                    ciclo: Pair<Int, Int>? = null, avisos0: List<Int>? = null, cada: Int? = null, color: String = "",
                    id: String? = null, cumple: Boolean = false, nacido: Int? = null, notas0: String? = null): Item {
        val previo = id?.let { try { JSONObject(File(items, "$it.json").readText()) } catch (_: Exception) { null } } ?: JSONObject()
        val ahoraTxt = LocalDateTime.now().format(ISO_MIN)
        val nuevoId = id ?: UUID.randomUUID().toString().replace("-", "").take(12)
        val j = JSONObject()
        j.put("id", nuevoId); j.put("texto", texto.trim()); j.put("tipo", tipo)
        j.put("inicio", inicio.format(ISO_MIN)); j.put("repetir", repetir)
        j.put("creado", txt(previo, "creado").ifEmpty { ahoraTxt })
        if (id != null) {
            j.put("editado", ahoraTxt)
            val conocidos = setOf("id", "texto", "tipo", "inicio", "repetir", "creado", "editado", "ciclo", "color",
                "cada", "avisos", "antes", "cumple", "nacido", "notas")
            for (k in previo.keys()) if (k !in conocidos) j.put(k, previo.get(k))
        }
        if (repetir == "ciclo" && ciclo != null) j.put("ciclo", JSONArray(listOf(ciclo.first, ciclo.second)))
        val notas = (notas0 ?: txt(previo, "notas")).trim()
        if (notas.isNotEmpty()) j.put("notas", notas)
        if (cumple) {
            j.put("cumple", true)
            if (nacido != null) j.put("nacido", nacido)
        }
        if (color.isNotEmpty()) j.put("color", color)
        val avisos = avisos0 ?: if (antes0 >= 0) listOf(antes0) else emptyList()
        var antes = antes0
        if (tipo == "tarea") j.put("cada", cada ?: (CADA_HORAS * 60))
        if (tipo == "cita") {
            j.put("avisos", JSONArray(avisos.map { avisoJson(it) }))
            antes = avisos.filter { it >= 0 }.minOrNull() ?: -1
            j.put("antes", antes)
        }
        escribir(File(items, "$nuevoId.json"), j.toString(1))
        return Item(nuevoId, texto.trim(), tipo, inicio, repetir, ciclo, color, cada ?: (CADA_HORAS * 60),
            importante(texto), notas, avisos, cumple, nacido, antes)
    }

    fun borrarItem(it: Item) {
        File(items, "${it.id}.json").delete()
        hechos.listFiles()?.forEach { f -> if (f.name.startsWith(it.id + "__")) f.delete() }
    }

    fun clave(it: Item, cuando: LocalDateTime) = "${it.id}__${cuando.format(CLAVE)}"
    fun estaHecho(it: Item, cuando: LocalDateTime) = File(hechos, clave(it, cuando)).exists()
    fun marcarHecho(it: Item, cuando: LocalDateTime) = escribir(File(hechos, clave(it, cuando)), "")

    // ---------------------------------------------------------------- fechas

    private fun dias(a: LocalDateTime, b: LocalDateTime) = Duration.between(a, b).toDays()

    /** La última vez que tocaba (<= ahora), o null si aún no ha tocado nunca. */
    fun ultima(it: Item, ahora: LocalDateTime): LocalDateTime? {
        val ini = it.inicio
        if (ini > ahora) return null
        return when (it.repetir) {
            "diario" -> ini.plusDays(dias(ini, ahora))
            "semanal" -> ini.plusWeeks(dias(ini, ahora) / 7)
            "mensual" -> {
                var k = (ahora.year - ini.year) * 12L + ahora.monthValue - ini.monthValue
                while (k > 0 && ini.plusMonths(k) > ahora) k--
                ini.plusMonths(k)
            }
            "anual" -> {
                var k = (ahora.year - ini.year).toLong()
                while (k > 0 && ini.plusMonths(12 * k) > ahora) k--
                ini.plusMonths(12 * k)
            }
            "ciclo" -> {
                val (si, no) = it.ciclo!!
                var d = dias(ini, ahora)
                if (d % (si + no) >= si) d = d - d % (si + no) + si - 1   // hoy es día «no»: el último día «sí»
                ini.plusDays(d)
            }
            else -> ini
        }
    }

    /** La próxima vez que toca (> ahora), o null si era de una vez y ya pasó. */
    fun siguiente(it: Item, ahora: LocalDateTime): LocalDateTime? {
        val ini = it.inicio
        if (ini > ahora) return ini
        val u = ultima(it, ahora)!!
        return when (it.repetir) {
            "diario" -> u.plusDays(1)
            "semanal" -> u.plusWeeks(1)
            "mensual" -> ini.plusMonths((u.year - ini.year) * 12L + u.monthValue - ini.monthValue + 1)
            "anual" -> ini.plusMonths(12L * (u.year - ini.year + 1))
            "ciclo" -> {
                val (si, no) = it.ciclo!!
                var d = dias(ini, u) + 1
                if (d % (si + no) >= si) d = d - d % (si + no) + si + no   // cae en día «no»: al siguiente ciclo
                ini.plusDays(d)
            }
            else -> null
        }
    }

    /** Todas las veces que toca entre desde (incluido) y hasta (excluido). */
    fun ocurrencias(it: Item, desde: LocalDateTime, hasta: LocalDateTime): List<LocalDateTime> {
        var o: LocalDateTime? = ultima(it, desde) ?: it.inicio
        val salida = mutableListOf<LocalDateTime>()
        while (o != null && o < hasta && salida.size < 400) {
            if (o >= desde) salida += o
            o = siguiente(it, o)
        }
        return salida
    }

    /** Tareas: la vez que está sin hacer, o null. */
    fun pendiente(it: Item, ahora: LocalDateTime): LocalDateTime? {
        if (it.tipo != "tarea") return null
        val u = ultima(it, ahora) ?: return null
        return if (estaHecho(it, u)) null else u
    }

    /** Avisos previos: los de la cita y, si es importante (cita o tarea), además 1 día antes y «el día». */
    fun avisosDe(it: Item): List<Int> {
        val avs = if (it.tipo == "cita") it.avisos.toMutableList() else mutableListOf()
        if (it.imp) for (extra in listOf(1440, DIA)) if (extra !in avs) avs += extra
        return avs
    }

    /** [(aviso, desde, hasta)]: cada aviso puede salir entre desde y hasta. */
    fun ventanasAviso(it: Item, o: LocalDateTime): List<Triple<Int, LocalDateTime, LocalDateTime>> =
        avisosDe(it).map { a ->
            if (a == DIA) {
                var desde = o.toLocalDate().atTime(if (it.imp) HORA_DIA_IMPORTANTE else HORA_DIA)
                if (desde > o) desde = o.toLocalDate().atStartOfDay()
                val hasta = if (it.cumple) o.toLocalDate().atTime(23, 59) else o
                Triple(a, desde, hasta)
            } else Triple(a, o.minusMinutes(a.toLong()), o.plus(if (a == 0) GRACIA_CITA else Duration.ZERO))
        }

    // ---------------------------------------------------------------- textos

    fun fechaCorta(d: LocalDateTime, ahora: LocalDateTime = LocalDateTime.now()): String {
        val hora = d.format(HM)
        val hoy = ahora.toLocalDate()
        return when (d.toLocalDate()) {
            hoy -> "hoy a las $hora"
            hoy.plusDays(1) -> "mañana a las $hora"
            hoy.minusDays(1) -> "ayer a las $hora"
            else -> "${DIAS_C[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MESES_C[d.monthValue - 1]} a las $hora"
        }
    }

    /** Como fechaCorta pero sin hora (cumpleaños). */
    fun diaCorto(d: LocalDateTime, ahora: LocalDateTime = LocalDateTime.now()): String =
        when (java.time.temporal.ChronoUnit.DAYS.between(ahora.toLocalDate(), d.toLocalDate())) {
            0L -> "hoy"
            1L -> "mañana"
            -1L -> "ayer"
            else -> "${DIAS_C[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MESES_C[d.monthValue - 1]}" +
                (if (d.year != ahora.year) " ${d.year}" else "")
        }

    /** Texto para enseñar: en los cumpleaños «🎂 Cumple de Ana · 30 años». */
    fun titulo(it: Item, o: LocalDateTime? = null): String {
        if (!it.cumple) return it.texto
        var t = "🎂 Cumple de ${it.texto}"
        if (it.nacido != null && o != null && o.year - it.nacido > 0) t += " · ${o.year - it.nacido} años"
        return t
    }

    /** (título, texto) del aviso de cumpleaños, con fiesta. */
    fun avisoCumple(it: Item, o: LocalDateTime, ahora: LocalDateTime = LocalDateTime.now()): Pair<String, String> {
        val n = java.time.temporal.ChronoUnit.DAYS.between(ahora.toLocalDate(), o.toLocalDate())
        val nombre = it.texto
        val anos = it.nacido?.let { o.year - it }?.takeIf { it > 0 }
        return when {
            n <= 0 -> "🎂 ¡Hoy es el cumple de $nombre! 🎉" to
                ((if (anos != null) "Cumple $anos años 🥳 " else "🥳 ") + "¡No te olvides de felicitarle! 🎈")
            n == 1L -> "🎁 Mañana es el cumple de $nombre 🎈" to
                ((if (anos != null) "Cumple $anos años · " else "") + "¿Tienes ya el regalo? 🎉")
            else -> "🎁 El cumple de $nombre es el ${diaCorto(o, ahora)} 🎈" to
                ("Faltan $n días" + (if (anos != null) " · cumple $anos años" else "") + " 🎉")
        }
    }

    /** Añade «📝 notas» al texto si las hay (largo = recortar). */
    fun conNotas(texto: String, it: Item, sep: String = "\n", largo: Int? = null): String {
        if (it.notas.isEmpty()) return texto
        var n = it.notas.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (largo != null && n.length > largo) n = n.take(largo - 1).trimEnd() + "…"
        return "$texto$sep📝 $n"
    }

    /** Para buscar: minúsculas y sin tildes. */
    fun normal(t: String): String =
        Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun coincide(it: Item, busca: String): Boolean {
        val b = normal(busca).trim()
        return b.isEmpty() || b in normal(listOf(it.texto, it.notas, titulo(it)).joinToString(" "))
    }

    fun cuandoTxt(it: Item, d: LocalDateTime, ahora: LocalDateTime = LocalDateTime.now()) =
        if (it.cumple) diaCorto(d, ahora) else fechaCorta(d, ahora)

    fun describirRepeticion(it: Item): String {
        val ini = it.inicio
        val hora = ini.format(HM)
        return when (it.repetir) {
            "anual" -> "Cada año el ${ini.dayOfMonth} de ${MESES[ini.monthValue - 1]}" + (if (it.cumple) "" else " a las $hora")
            "diario" -> "Todos los días a las $hora"
            "semanal" -> {
                val dia = DIAS[ini.dayOfWeek.value - 1]
                "Todos los $dia${if (dia.endsWith("s")) "" else "s"} a las $hora"
            }
            "mensual" -> "El día ${ini.dayOfMonth} de cada mes a las $hora"
            "ciclo" -> {
                val (si, no) = it.ciclo!!
                val t = if (si == 1 && no == 1) "Un día sí y otro no" else "$si día${if (si > 1) "s" else ""} sí, $no no"
                "$t a las $hora (desde el ${ini.dayOfMonth} ${MESES_C[ini.monthValue - 1]})"
            }
            else -> "Una vez"
        }
    }

    /** Resumen de buenos días: lo de hoy y las tareas atrasadas, o null si no hay nada. */
    fun resumenDia(ahora: LocalDateTime): String? {
        val d0 = ahora.toLocalDate().atStartOfDay()
        val d1 = d0.plusDays(1)
        val cumples = mutableListOf<String>()
        val cosas = mutableListOf<Pair<LocalDateTime, String>>()
        var atrasadas = 0
        for (it in leerItems()) {
            val p = pendiente(it, ahora)
            if (p != null && p < d0) atrasadas++
            for (o in ocurrencias(it, d0, d1)) {
                val marca = if (it.imp) "‼ " else ""
                when {
                    it.cumple -> cumples += conNotas(marca + titulo(it, o), it, " · ", 60)
                    it.tipo == "tarea" && estaHecho(it, o) -> {}
                    else -> cosas += o to conNotas("$marca${if (it.tipo == "cita") "📅" else "⏰"} ${o.format(HM)} ${it.texto}", it, " · ", 60)
                }
            }
        }
        if (cumples.isEmpty() && cosas.isEmpty() && atrasadas == 0) return null
        val lineas = mutableListOf("☀ Hoy ${DIAS[ahora.dayOfWeek.value - 1]} ${ahora.dayOfMonth}")
        lineas += cumples
        lineas += cosas.sortedWith(compareBy({ it.first }, { it.second })).map { it.second }
        if (atrasadas > 0) {
            val s = if (atrasadas > 1) "s" else ""
            lineas += "🔔 $atrasadas tarea$s atrasada$s"
        }
        return lineas.joinToString("\n")
    }

    fun hoy(): LocalDate = LocalDate.now()
}
