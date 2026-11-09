package io.github.de098.recordatorios

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Avisos con el nombre y el logo de la app. Los dispara una alarma local (ver [Programador]) con estos extras:
 * titulo, texto, tipo (tarea | cita | cumple | resumen | otro), dia (AAAA-MM-DD, opcional) e id.
 * Las tareas llevan el botón «✔ Hecho»; al pulsarlo el aviso pasa a «Hecha» con «Deshacer» (por si fue sin querer).
 * 10-nov (D): el aviso usa un diseño propio (letra de la app y botón verde como el de la app), con el botón
 * siempre a la vista aunque el aviso esté plegado.
 */
object Avisos {
    const val ACCION = "io.github.de098.recordatorios.AVISO"
    const val ACCION_HECHO = "io.github.de098.recordatorios.HECHO"
    const val ACCION_DESHACER = "io.github.de098.recordatorios.DESHACER"
    private val CLAVE_TAREA = Regex("""[0-9a-f]{6,32}__\d{8}T\d{4}""")

    // canal → nombre visible en Ajustes (se puede elegir sonido/vibración de cada uno)
    private val CANALES = linkedMapOf(
        "tarea" to "Tareas",
        "cita" to "Citas",
        "cumple" to "Cumpleaños",
        "resumen" to "Resumen del día",
        "otro" to "Otros avisos",
    )

    fun crearCanales(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        for ((id, nombre) in CANALES)
            nm.createNotificationChannel(NotificationChannel(id, nombre, NotificationManager.IMPORTANCE_HIGH))
    }

    private fun idNotificacion(id: String) = id.hashCode()

    /** Quita de la barra el aviso de esa tarea (clave «id__AAAAMMDDTHHMM»), p. ej. al marcarla hecha. */
    fun cancelar(c: Context, clave: String) {
        c.getSystemService(NotificationManager::class.java).cancel(idNotificacion(clave))
    }

    /** Los datos del aviso, tal como llegan en el intent (sirven para volver a pintarlo al pulsar «Hecho» o «Deshacer»). */
    private fun datos(i: Intent, destino: Intent) = destino.apply {
        for (k in listOf("titulo", "texto", "tipo", "dia", "id")) putExtra(k, i.getStringExtra(k))
    }

    /** hecha = true: la tarea ya está marcada → «✔ Hecha» con el botón «Deshacer» (se quita sola al minuto). */
    fun mostrar(c: Context, i: Intent, hecha: Boolean = false) {
        crearCanales(c)
        val titulo = i.getStringExtra("titulo")?.takeIf { it.isNotBlank() } ?: "Recordatorio"
        val texto = i.getStringExtra("texto") ?: ""
        val tipo = i.getStringExtra("tipo")?.lowercase()?.takeIf { it in CANALES } ?: "otro"
        val dia = i.getStringExtra("dia")?.takeIf { it.isNotEmpty() }
        val idTxt = i.getStringExtra("id")?.takeIf { it.isNotEmpty() } ?: "$titulo|$texto|${System.currentTimeMillis()}"
        val id = idNotificacion(idTxt)

        val abrir = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (dia != null) putExtra(MainActivity.EXTRA_DIA, dia)
            putExtra(MainActivity.EXTRA_PESTANA, "cal")
        }
        val pi = PendingIntent.getActivity(c, id, abrir, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        // tareas: botón «✔ Hecho» (el id de la tarea es su clave; los avisos previos llevan «clave@aviso»)
        val clave = idTxt.substringBefore("@")
        val esTarea = tipo == "tarea" && CLAVE_TAREA.matches(clave)
        val boton: PendingIntent? = if (!esTarea) null else {
            val accion = if (hecha) ACCION_DESHACER else ACCION_HECHO
            val q = datos(i, Intent(c, HechoReceiver::class.java).setAction(accion)).putExtra("id", idTxt).putExtra("k", clave)
            PendingIntent.getBroadcast(c, id xor (if (hecha) 0x4448 else 0x4845), q,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val tit = if (hecha) "✔ Hecha: $titulo" else titulo
        val txt = if (hecha) "¿Ha sido sin querer? Pulsa «Deshacer»." else texto
        fun vista(diseno: Int) = RemoteViews(c.packageName, diseno).apply {
            setTextViewText(R.id.nt, tit)
            setTextViewText(R.id.nx, txt)
            setViewVisibility(R.id.nx, if (txt.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
            if (boton == null) setViewVisibility(R.id.nb, android.view.View.GONE)
            else {
                setTextViewText(R.id.nb, if (hecha) "Deshacer" else "✓ Hecho")
                setInt(R.id.nb, "setBackgroundResource", if (hecha) R.drawable.n_boton_borde else R.drawable.w_hecho)
                setTextColor(R.id.nb, if (hecha) 0xFF26A269.toInt() else 0xFFFFFFFF.toInt())
                setOnClickPendingIntent(R.id.nb, boton)
            }
        }

        val b = Notification.Builder(c, tipo)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(c.getColor(R.color.acento))
            .setContentTitle(tit)
            .setContentText(txt)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(vista(R.layout.n_aviso))
            .setCustomBigContentView(vista(R.layout.n_aviso_grande))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)   // al cambiar a «Hecha» o volver con «Deshacer», sin sonar otra vez
            .setCategory(if (tipo == "cita") Notification.CATEGORY_EVENT else Notification.CATEGORY_REMINDER)
        if (hecha) b.setTimeoutAfter(60_000)
        c.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}

/** Salta la alarma: enseña el aviso. */
class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Avisos.ACCION) return
        Avisos.mostrar(c, i)
        // ya ha llegado la hora de esa tarea: repintar los widgets (botón «Hecho»)
        val pend = goAsync()
        kotlin.concurrent.thread(name = "repintar") { try { Widgets.actualizarTodos(c.applicationContext) } finally { pend.finish() } }
    }
}

/** Botones del aviso: «✔ Hecho» marca la tarea y deja el aviso en «Hecha» con «Deshacer»; «Deshacer» la desmarca y lo devuelve. */
class HechoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val hecho = i.action == Avisos.ACCION_HECHO
        if (!hecho && i.action != Avisos.ACCION_DESHACER) return
        val k = i.getStringExtra("k") ?: return
        Nucleo.iniciar(c)
        if (hecho) Calendario.marcar(k) else Calendario.desmarcar(k)
        Avisos.mostrar(c, i, hecha = hecho)
        Programador.ahora(c)   // con «Hecho» los avisos repetidos de esa tarea ya no se programan; con «Deshacer» vuelven
    }
}
