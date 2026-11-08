package io.github.de098.recordatorios

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Copia de seguridad en un solo archivo .json: lo apuntado (items), los «hecho», las notas y la contraseña del candado
 * (solo su sal y su prueba: las notas con candado siguen cifradas dentro de la copia).
 * Importar AÑADE: no borra nada; lo que tenga el mismo id se sustituye (las notas, solo si la copia es más nueva).
 */
object Copia {
    private const val APP = "io.github.de098.recordatorios"
    private val ID = Regex("[0-9a-f]{6,32}")
    private val HECHO = Regex("""[0-9a-f]{6,32}__\d{8}T\d{4}""")

    private val items get() = File(Nucleo.base, "items")
    private val hechos get() = File(Nucleo.base, "hechos")
    private val notas get() = File(Nucleo.base, "notas")
    private val candado get() = File(notas, "candado.json")

    fun nombreArchivo(): String = "recordatorios-copia-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}.json"

    private fun leerCarpeta(d: File, saltar: String? = null): JSONArray {
        val a = JSONArray()
        d.listFiles()?.sortedBy { it.name }?.forEach { f ->
            if (f.isFile && f.name.endsWith(".json") && !f.name.startsWith(".") && f.name != saltar)
                try { a.put(JSONObject(f.readText())) } catch (_: Exception) {}
        }
        return a
    }

    @Synchronized
    fun exportar(): String = JSONObject().apply {
        put("app", APP); put("formato", 1)
        put("fecha", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
        put("items", leerCarpeta(items))
        put("hechos", JSONArray(hechos.list()?.filter { HECHO.matches(it) }?.sorted() ?: emptyList<String>()))
        put("notas", leerCarpeta(notas, "candado.json"))
        put("candado", try { JSONObject(candado.readText()) } catch (_: Exception) { JSONObject.NULL })
    }.toString(1)

    /** Devuelve el mensaje para enseñar («Recuperado: …» o por qué no). */
    @Synchronized
    fun importar(texto: String): String {
        val j = try { JSONObject(texto) } catch (_: Exception) { return "Ese archivo no es una copia de Recordatorios" }
        if (j.optString("app") != APP) return "Ese archivo no es una copia de Recordatorios"
        if (j.optInt("formato") > 1) return "Esa copia es de una versión más nueva de la app: actualízala primero"

        var nItems = 0; var nHechos = 0; var nNotas = 0; var sinClave = 0
        j.optJSONArray("items")?.let { a ->
            for (i in 0 until a.length()) {
                val it = a.optJSONObject(i) ?: continue
                val id = it.optString("id")
                if (!ID.matches(id) || it.optString("inicio").isEmpty()) continue
                Nucleo.escribir(File(items, "$id.json"), it.toString(1)); nItems++
            }
        }
        j.optJSONArray("hechos")?.let { a ->
            for (i in 0 until a.length()) {
                val n = a.optString(i)
                if (HECHO.matches(n) && !File(hechos, n).exists()) { Nucleo.escribir(File(hechos, n), ""); nHechos++ }
            }
        }
        // contraseña del candado: si aquí no hay, se trae la de la copia; si hay otra distinta, las notas con candado
        // de la copia no se podrían abrir con la de aquí y no se traen
        val suya = j.optJSONObject("candado")
        val mia = try { JSONObject(candado.readText()) } catch (_: Exception) { null }
        val mismaClave = when {
            suya == null -> true
            mia == null -> { Nucleo.escribir(candado, suya.toString(1)); true }
            else -> mia.optString("sal") == suya.optString("sal")
        }
        j.optJSONArray("notas")?.let { a ->
            for (i in 0 until a.length()) {
                val n = a.optJSONObject(i) ?: continue
                val id = n.optString("id")
                if (!ID.matches(id)) continue
                if (!n.isNull("candado") && n.optJSONObject("candado") != null && !mismaClave) { sinClave++; continue }
                val f = File(notas, "$id.json")
                val local = try { JSONObject(f.readText()) } catch (_: Exception) { null }
                if (local != null && local.optString("editada") >= n.optString("editada")) continue   // la de aquí es igual o más nueva
                Nucleo.escribir(f, n.toString(1)); nNotas++
            }
        }
        val partes = mutableListOf<String>()
        if (nItems > 0) partes += "$nItems cosa${if (nItems > 1) "s" else ""} apuntada${if (nItems > 1) "s" else ""}"
        if (nNotas > 0) partes += "$nNotas nota${if (nNotas > 1) "s" else ""}"
        if (nHechos > 0 && partes.isEmpty()) partes += "$nHechos «hecho»"
        var msj = if (partes.isEmpty()) "La copia no tenía nada nuevo" else "Recuperado: " + partes.joinToString(" y ")
        if (sinClave > 0) msj += ". $sinClave nota${if (sinClave > 1) "s" else ""} con candado no se han traído: " +
            "la contraseña de la copia es distinta de la de aquí"
        return msj
    }
}
