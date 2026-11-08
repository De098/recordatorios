package io.github.de098.recordatorios

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Lo que la web de la app (calendario y notas) pide con fetch("/cal/…" o "/notas/…"): aquí lo contesta la propia app,
 * sin servidor ni internet. local.js lo llama a través del puente «Recordatorios».
 */
object Rutas {
    const val ORIGEN = "https://appassets.androidplatform.net"
    private const val JSON = "application/json; charset=utf-8"
    private const val TEXTO = "text/plain; charset=utf-8"

    /** Contesta una petición y devuelve {estado, tipo, cuerpo} como texto JSON. */
    @Synchronized
    fun pedir(c: Context, metodo: String, ruta: String, cuerpo: String): String {
        Nucleo.iniciar(c)
        val u = Uri.parse(ruta)
        val path = u.path ?: ""
        val q = u.queryParameterNames.associateWith { u.getQueryParameter(it) ?: "" }
        var estado = 200
        var tipo = JSON
        var texto: String
        var cambia = false
        try {
            when {
                path.startsWith("/notas/") -> {
                    val r = Notas.responder(metodo, path, cuerpo)
                    if (r == null) { estado = 404; tipo = TEXTO; texto = "no" } else { estado = r.first; tipo = r.second; texto = r.third }
                }
                metodo == "GET" && path == "/cal/datos" -> texto = Calendario.datosJson(q)
                metodo == "GET" && path == "/cal/buscar" -> texto = Calendario.buscar(q["q"] ?: "").toString()
                metodo == "POST" && path == "/cal/hecho" -> {
                    texto = Calendario.marcar(cuerpo); tipo = TEXTO; cambia = true
                    Avisos.cancelar(c, cuerpo.trim())
                }
                metodo == "POST" && path == "/cal/anadir" -> { texto = Calendario.guardar(cuerpo, nuevo = true); tipo = TEXTO; cambia = true }
                metodo == "POST" && path == "/cal/editar" -> { texto = Calendario.guardar(cuerpo); tipo = TEXTO; cambia = true }
                metodo == "POST" && path == "/cal/borrar" -> { texto = Calendario.borrar(cuerpo); tipo = TEXTO; cambia = true }
                else -> { estado = 404; tipo = TEXTO; texto = "no" }
            }
        } catch (e: Exception) {
            estado = 500; tipo = TEXTO; texto = "Error (${e.javaClass.simpleName})"
        }
        if (cambia) Programador.ahora(c)   // avisos y widgets al día en cuanto cambia algo
        return JSONObject().put("estado", estado).put("tipo", tipo).put("cuerpo", texto).toString()
    }
}
