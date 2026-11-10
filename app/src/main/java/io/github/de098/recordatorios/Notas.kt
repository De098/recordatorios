package io.github.de098.recordatorios

import io.github.de098.recordatorios.Nucleo as N
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Notas privadas. Cada nota es un .json en notas/ (dentro de la carpeta privada de la app):
 *   {"id", "titulo", "texto", "color", "fijada", "creada", "editada", "candado"}
 * "candado": null, o {"nonce", "datos"} = título y texto cifrados (TweetNaCl secretbox) EN LA PÁGINA, con la clave
 * sacada de la contraseña (PBKDF2-HMAC-SHA512, sal y vueltas en notas/candado.json). Aquí nunca se descifra.
 * Borrar = mover a notas/papelera/.
 */
object Notas {
    private val COLORES = listOf("", "rojo", "naranja", "amarillo", "verde", "azul", "morado", "marron", "negro")
    private const val MAX_NOTA = 300_000   // bytes por nota
    private val FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    private val carpeta get() = File(N.base, "notas")
    private val papelera get() = File(carpeta, "papelera")
    private val candado get() = File(carpeta, "candado.json")

    private fun ahora(): String = LocalDateTime.now().format(FECHA)

    private fun esId(s: String) = s.isNotEmpty() && s.all { it in "0123456789abcdef" }

    private fun leerNotas(): List<JSONObject> {
        val nombres = carpeta.list() ?: return emptyList()
        val lista = mutableListOf<JSONObject>()
        for (n in nombres) {
            if (!n.endsWith(".json") || n.startsWith(".") || n == "candado.json") continue
            try {
                lista += JSONObject(File(carpeta, n).readText())
            } catch (_: Exception) {
                // archivo roto o a medias: se ignora
            }
        }
        return lista
    }

    private fun leerCandado(): JSONObject? = try { JSONObject(candado.readText()) } catch (_: Exception) { null }

    fun datosJson(): String = JSONObject().apply {
        put("notas", org.json.JSONArray(leerNotas()))
        put("candado", leerCandado() ?: JSONObject.NULL)
        put("hora", ahora())
    }.toString()

    /**
     * JSON de la página. Si la nota cambió en otro sitio desde que se abrió («base» ≠ «editada»), no se pisa:
     * se guarda la tuya como nota nueva «(copia)». Devuelve JSON {ok, nota, aviso}.
     */
    fun guardar(cuerpo: String): String {
        val e = try { JSONObject(cuerpo) } catch (_: JSONException) {
            return JSONObject().put("ok", false).put("aviso", "datos raros").toString()
        }
        val cand = e.optJSONObject("candado")
        val color = N.txt(e, "color").takeIf { it in COLORES } ?: ""
        var titulo = N.txt(e, "titulo").take(300)
        var texto = N.txt(e, "texto")
        if (cand != null) { titulo = ""; texto = "" }   // cifrada: nada en claro
        var aviso = ""
        var id = N.txt(e, "id")
        var viejo: JSONObject? = null
        if (id.isNotEmpty()) viejo = try { JSONObject(File(carpeta, "$id.json").readText()) } catch (_: Exception) { null }
        val base = N.txt(e, "base")
        if (viejo != null && base.isNotEmpty() && N.txt(viejo, "editada") != base) {
            id = ""   // cambiada en otro sitio mientras tanto: la tuya como copia
            if (cand == null) titulo = titulo.ifEmpty { "Nota" } + " (copia)"
            aviso = "Esta nota se cambió en otro sitio a la vez: la tuya se ha guardado como copia"
        }
        if (!esId(id)) {
            id = UUID.randomUUID().toString().replace("-", "").take(12)
            viejo = null
        }
        val nota = JSONObject().apply {
            put("titulo", titulo); put("texto", texto); put("color", color)
            put("fijada", e.optBoolean("fijada"))
            put("candado", cand ?: JSONObject.NULL)
            put("id", id)
            put("creada", viejo?.let { N.txt(it, "creada") }?.ifEmpty { null } ?: ahora())
            put("editada", ahora())
        }
        val datos = nota.toString(1)
        if (datos.toByteArray().size > MAX_NOTA) return JSONObject().put("ok", false).put("aviso", "Nota demasiado larga").toString()
        N.escribir(File(carpeta, "$id.json"), datos)
        return JSONObject().put("ok", true).put("nota", nota).put("aviso", aviso).toString()
    }

    fun borrar(id0: String): String {
        val id = id0.trim()
        if (!esId(id)) return "No borrada: id raro"
        val ruta = File(carpeta, "$id.json")
        if (!ruta.exists()) return "No borrada: ya no está"
        papelera.mkdirs()
        val destino = File(papelera, "$id.json")
        if (!ruta.renameTo(destino)) { destino.writeText(ruta.readText()); ruta.delete() }
        // la carpeta papelera está dentro de la app: la persona no puede entrar, así que no se le promete nada
        return "Borrada"
    }

    /** Crea la contraseña (sal, vueltas y una «prueba» cifrada para comprobarla). Solo si aún no hay. */
    fun ponerCandado(cuerpo: String): String {
        if (leerCandado() != null) return "Ya hay contraseña"
        val c = try {
            val j = JSONObject(cuerpo)
            val p = j.getJSONObject("prueba")
            JSONObject().put("v", 1).put("sal", j.get("sal").toString()).put("vueltas", j.getInt("vueltas"))
                .put("prueba", JSONObject().put("nonce", p.get("nonce").toString()).put("datos", p.get("datos").toString()))
        } catch (_: JSONException) {
            return "Datos raros"
        }
        N.escribir(candado, c.toString(1))
        return "Contraseña creada"
    }

    /**
     * Contraseña nueva (11-nov, D). La página ya ha vuelto a cifrar TODAS las notas con candado con la clave nueva
     * (aquí nunca se descifra): se comprueba que no falta ninguna y que ninguna ha cambiado mientras tanto.
     * Cuerpo: {"prueba_vieja", "candado": {sal, vueltas, prueba}, "notas": [{id, base, candado}]}. Devuelve JSON {ok, aviso}.
     */
    fun cambiarCandado(cuerpo: String): String {
        fun mal(t: String) = JSONObject().put("ok", false).put("aviso", t).toString()
        val actual = leerCandado() ?: return mal("Aún no hay contraseña")
        val nuevo: JSONObject
        val cambios = HashMap<String, Pair<String, JSONObject>>()
        val vieja: JSONObject?
        try {
            val e = JSONObject(cuerpo)
            val c = e.getJSONObject("candado"); val p = c.getJSONObject("prueba")
            nuevo = JSONObject().put("v", 1).put("sal", c.get("sal").toString()).put("vueltas", c.getInt("vueltas"))
                .put("prueba", JSONObject().put("nonce", p.get("nonce").toString()).put("datos", p.get("datos").toString()))
            vieja = e.optJSONObject("prueba_vieja")
            val ns = e.optJSONArray("notas") ?: org.json.JSONArray()
            for (i in 0 until ns.length()) {
                val n = ns.getJSONObject(i); val cc = n.getJSONObject("candado")
                cambios[n.get("id").toString()] = N.txt(n, "base") to
                    JSONObject().put("nonce", cc.get("nonce").toString()).put("datos", cc.get("datos").toString())
            }
        } catch (_: JSONException) {
            return mal("Datos raros")
        }
        val pa = actual.optJSONObject("prueba")
        if (vieja == null || pa == null || N.txt(vieja, "nonce") != N.txt(pa, "nonce") || N.txt(vieja, "datos") != N.txt(pa, "datos"))
            return mal("La contraseña se ha cambiado en otro sitio. Vuelve a abrir Notas y prueba otra vez.")
        val cerradas = leerNotas().filter { it.optJSONObject("candado") != null }.associateBy { N.txt(it, "id") }
        if (cerradas.keys != cambios.keys || cerradas.any { (i, n) -> N.txt(n, "editada") != cambios[i]!!.first })
            return mal("Alguna nota con candado ha cambiado mientras tanto. No he tocado nada: prueba otra vez.")
        for ((i, n) in cerradas) {
            n.put("candado", cambios[i]!!.second); n.put("editada", ahora())
            N.escribir(File(carpeta, "$i.json"), n.toString(1))
        }
        N.escribir(candado, nuevo.toString(1))
        return JSONObject().put("ok", true).put("notas", cerradas.size).toString()
    }

    /**
     * Contraseña olvidada (11-nov, D): sin ella no hay forma de abrir las notas con candado, así que se BORRAN
     * (también las de la papelera) y se quita la contraseña. Las notas sin candado no se tocan.
     */
    fun olvidarCandado(): String {
        var n = 0
        for (nota in leerNotas()) if (nota.optJSONObject("candado") != null && File(carpeta, N.txt(nota, "id") + ".json").delete()) n++
        papelera.listFiles()?.forEach { f ->
            try { if (f.name.endsWith(".json") && JSONObject(f.readText()).optJSONObject("candado") != null) f.delete() } catch (_: Exception) {}
        }
        candado.delete()
        return if (n == 1) "Borrada 1 nota con candado y la contraseña" else "Borradas $n notas con candado y la contraseña"
    }

    /** Lo que piden /notas/datos, /notas/guardar, /notas/borrar y /notas/candado: (estado, tipo, cuerpo) o null si no es de notas. */
    fun responder(metodo: String, ruta: String, cuerpo: String): Triple<Int, String, String>? {
        val json = "application/json; charset=utf-8"
        val texto = "text/plain; charset=utf-8"
        return when {
            metodo == "GET" && ruta == "/notas/datos" -> Triple(200, json, datosJson())
            metodo == "POST" && ruta == "/notas/guardar" -> Triple(200, json, guardar(cuerpo))
            metodo == "POST" && ruta == "/notas/borrar" -> Triple(200, texto, borrar(cuerpo))
            metodo == "POST" && ruta == "/notas/candado" -> Triple(200, texto, ponerCandado(cuerpo))
            metodo == "POST" && ruta == "/notas/candado-cambiar" -> Triple(200, json, cambiarCandado(cuerpo))
            metodo == "POST" && ruta == "/notas/candado-olvidar" -> Triple(200, texto, olvidarCandado())
            else -> null
        }
    }
}
