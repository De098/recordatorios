package io.github.de098.recordatorios

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.OnBackInvokedDispatcher
import java.io.ByteArrayInputStream
import kotlin.concurrent.thread

/**
 * Recordatorios. Calendario y Notas son dos páginas web empaquetadas en la app (assets/web), cada una en su WebView
 * (cambiar de una a otra es instantáneo). Todo se queda en el móvil: la app no tiene permiso de internet y las
 * peticiones de las páginas (/cal/…, /notas/…) las contesta [Rutas] a través del puente «Recordatorios».
 * Se cambia de página con el menú del logo (menu.js).
 */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_DIA = "dia"           // AAAA-MM-DD: abrir el calendario en ese día
        const val EXTRA_PESTANA = "pestana"   // "cal" o "notas"
        const val EXTRA_ACCION = "accion"     // widgets: "nuevo" (Añadir en el calendario) o "nota" (Nota nueva)
        private val DIA_OK = Regex("""\d{4}-\d{2}-\d{2}""")
        private val HOST = Uri.parse(Rutas.ORIGEN).host
        private const val PEDIR_EXPORTAR = 41
        private const val PEDIR_IMPORTAR = 42
        private const val MAX_COPIA = 50 * 1024 * 1024   // 50 MB
    }

    private class Pestana(val id: String, val ruta: String) {
        lateinit var web: WebView
        var cargada = false
        var jsPendiente: String? = null   // JS a ejecutar cuando la página esté lista (botones del widget)
        var lista = false                 // la página terminó de cargar
    }

    private val pestanas = listOf(Pestana("cal", "/cal"), Pestana("notas", "/notas"))
    private var actual = pestanas[0]
    private var diaPendiente: String? = null
    private lateinit var raiz: LinearLayout

    /** Lo que las páginas pueden pedirle a la app. */
    private inner class Puente {
        /** Contesta /cal/… y /notas/…: devuelve {estado, tipo, cuerpo}. */
        @JavascriptInterface
        fun pedir(metodo: String, ruta: String, cuerpo: String): String =
            Rutas.pedir(this@MainActivity, metodo, ruta, cuerpo)

        /** Menú del logo: guardar una copia de seguridad (el sistema pregunta dónde). */
        @JavascriptInterface
        fun exportar() = runOnUiThread {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, Copia.nombreArchivo())
            try { startActivityForResult(i, PEDIR_EXPORTAR) } catch (_: Exception) { aviso("No se puede guardar en este móvil") }
        }

        /** Menú del logo: recuperar una copia (añade, no borra nada). */
        @JavascriptInterface
        fun importar() = runOnUiThread {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "application/octet-stream", "text/plain"))
            try { startActivityForResult(i, PEDIR_IMPORTAR) } catch (_: Exception) { aviso("No se puede abrir archivos en este móvil") }
        }

        /** Menú del logo: versión de la app, la que pone el build (versionName); cambia sola al compilar. */
        @JavascriptInterface
        fun version(): String =
            try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

        /** Tamaño de letra de Android (1 = normal); menu.js agranda toda la página en proporción. */
        @JavascriptInterface
        fun escala(): Float = resources.configuration.fontScale

        /** La web avisa del tema elegido (menu.js) para pintar a juego la barra de estado y la de gestos. */
        @JavascriptInterface
        fun tema(oscuro: Boolean, fondo: String) {
            val c = try { Color.parseColor(fondo) } catch (_: Exception) { return }
            getSharedPreferences("sincro", MODE_PRIVATE).edit().putString("tema", if (oscuro) "oscuro" else "claro").apply()
            runOnUiThread { Widgets.actualizarTodos(this@MainActivity) }
            runOnUiThread {
                raiz.setBackgroundColor(c)
                val claras = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (oscuro) 0 else claras, claras)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(estado: Bundle?) {
        super.onCreate(estado)
        Nucleo.iniciar(this)

        raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.fondo))
        }
        val zona = FrameLayout(this)
        raiz.addView(zona, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        for (p in pestanas) {
            p.web = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(false)
                // letra de Android más pequeña: la letra la sigue (como antes, no descuadra);
                // más grande: solo letra descuadraría, así que la deja en 100 y menu.js agranda todo en proporción con escala()
                settings.textZoom = minOf(100, Math.round(resources.configuration.fontScale * 100))
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                setBackgroundColor(getColor(R.color.fondo))
                webViewClient = Cliente(p)
                addJavascriptInterface(Puente(), "Recordatorios")
                visibility = View.GONE
            }
            zona.addView(p.web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        // pantalla completa (de borde a borde): se deja hueco para la barra de estado y la de gestos
        raiz.setOnApplyWindowInsetsListener { v, ins ->
            val s = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(s.left, s.top, s.right, s.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(raiz)

        // gesto atrás: primero lo de la web (paneles, vistas), luego volver al calendario, luego salir
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            when {
                actual.web.canGoBack() -> actual.web.goBack()
                actual !== pestanas[0] -> elegir(pestanas[0])
                else -> moveTaskToBack(true)
            }
        }

        val inicial = estado?.getString("pestana")
        elegir(pestanas.firstOrNull { it.id == inicial } ?: pestanas[0])
        abrirDesde(intent)
        pedirPermisoAvisos()
        Programador.programarTrabajo(this)
    }

    override fun onSaveInstanceState(s: Bundle) {
        super.onSaveInstanceState(s)
        s.putString("pestana", actual.id)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abrirDesde(intent)
    }

    override fun onResume() {
        super.onResume()
        thread(name = "programador") { Programador.ahora(this) }   // al abrir la app: avisos y widgets al día
    }

    /** Aviso pulsado o atajo de un widget: página y día. */
    private fun abrirDesde(i: Intent?) {
        i ?: return
        val dia = i.getStringExtra(EXTRA_DIA)?.takeIf { DIA_OK.matches(it) }
        val pest = pestanas.firstOrNull { it.id == i.getStringExtra(EXTRA_PESTANA) }
        when (i.getStringExtra(EXTRA_ACCION)) {
            "nuevo" -> { elegir(pestanas[0]); ejecutar(pestanas[0], "document.getElementById('bnuevo') && document.getElementById('bnuevo').click()") }
            "nota" -> { elegir(pestanas[1]); ejecutar(pestanas[1], "document.getElementById('bnueva') && document.getElementById('bnueva').click()") }
        }
        i.removeExtra(EXTRA_ACCION)
        if (dia != null) {
            elegir(pestanas[0])
            irADia(dia)
        } else if (pest != null) elegir(pest)
        i.removeExtra(EXTRA_DIA)
        i.removeExtra(EXTRA_PESTANA)
    }

    private fun ejecutar(p: Pestana, js: String) {
        if (p.lista) p.web.evaluateJavascript(js, null) else p.jsPendiente = js
    }

    private fun irADia(dia: String) {
        val cal = pestanas[0]
        if (cal.lista) cal.web.evaluateJavascript("window.irADia && irADia('$dia')", null)
        else diaPendiente = dia   // se pone al terminar de cargar (onPageFinished)
    }

    private fun elegir(p: Pestana) {
        actual = p
        for (q in pestanas) q.web.visibility = if (q === p) View.VISIBLE else View.GONE
        if (!p.cargada) {
            p.cargada = true
            p.lista = false
            p.web.loadUrl(Rutas.ORIGEN + p.ruta)
        } else p.web.evaluateJavascript("window.temaMenu && temaMenu.aplicar()", null)   // por si se cambió en la otra
    }

    /** Sirve las páginas y sus archivos desde assets/web: /cal → cal.html, /notas → notas.html, /cal/x.js → x.js… */
    private fun servir(path: String): WebResourceResponse {
        val p = path.trimEnd('/')
        val nombre = when {
            p == "/cal" -> "cal.html"
            p == "/notas" -> "notas.html"
            p.startsWith("/cal/") || p.startsWith("/notas/") -> p.substringAfterLast('/')
            else -> ""
        }
        if (nombre.isNotEmpty() && ".." !in nombre) try {
            val mime = when (nombre.substringAfterLast('.', "")) {
                "html" -> "text/html"; "js" -> "text/javascript"; "svg" -> "image/svg+xml"
                "png" -> "image/png"; "woff2" -> "font/woff2"; "txt" -> "text/plain"; else -> "application/octet-stream"
            }
            val texto = mime.startsWith("text/") || mime == "image/svg+xml"
            return WebResourceResponse(mime, if (texto) "utf-8" else null, assets.open("web/$nombre"))
        } catch (_: Exception) {}
        return WebResourceResponse("text/plain", "utf-8", 404, "No encontrado", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private inner class Cliente(val p: Pestana) : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
            if (r.url.host == HOST) servir(r.url.path ?: "")
            else WebResourceResponse("text/plain", "utf-8", 404, "Sin red", emptyMap(), ByteArrayInputStream(ByteArray(0)))

        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url
            if (u.host == HOST) {
                // menú de la web: ir a la otra página = enseñar su WebView (ya cargada)
                val otra = pestanas.firstOrNull { it !== p && (u.path == it.ruta || u.path == it.ruta + "/") }
                if (otra != null) { elegir(otra); return true }
                return false
            }
            // enlaces de fuera (p. ej. en una nota): al navegador
            try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
            return true
        }

        override fun onPageFinished(v: WebView, url: String) {
            p.lista = true
            if (p.id == "cal") diaPendiente?.let { diaPendiente = null; irADia(it) }
            p.jsPendiente?.let { p.jsPendiente = null; v.evaluateJavascript(it, null) }
        }
    }

    private fun aviso(t: String) = android.widget.Toast.makeText(this, t, android.widget.Toast.LENGTH_LONG).show()

    @Deprecated("Activity sin AndroidX: es la forma de recibir el archivo elegido")
    override fun onActivityResult(pedido: Int, resultado: Int, datos: Intent?) {
        super.onActivityResult(pedido, resultado, datos)
        val uri = datos?.data
        if (resultado != RESULT_OK || uri == null) return
        thread(name = "copia") {
            val msj = try {
                when (pedido) {
                    PEDIR_EXPORTAR -> {
                        val texto = Copia.exportar()
                        contentResolver.openOutputStream(uri, "wt")!!.use { it.write(texto.toByteArray()) }
                        "Copia guardada"
                    }
                    PEDIR_IMPORTAR -> {
                        val texto = contentResolver.openInputStream(uri)!!.use { s ->
                            val b = s.readNBytes(MAX_COPIA + 1)
                            if (b.size > MAX_COPIA) throw IllegalStateException("grande")
                            String(b)
                        }
                        Copia.importar(texto).also {
                            Programador.ahora(this)
                            runOnUiThread { for (p in pestanas) if (p.cargada) p.web.reload() }
                        }
                    }
                    else -> return@thread
                }
            } catch (_: Exception) {
                if (pedido == PEDIR_EXPORTAR) "No se ha podido guardar la copia" else "No se ha podido leer ese archivo"
            }
            runOnUiThread { aviso(msj) }
        }
    }

    private fun pedirPermisoAvisos() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Avisos.crearCanales(this)
    }
}
