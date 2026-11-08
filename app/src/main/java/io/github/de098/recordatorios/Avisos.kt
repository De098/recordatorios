package io.github.de098.recordatorios

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/**
 * Avisos con el nombre y el logo de la app. Los dispara una alarma local (ver [Programador]) con estos extras:
 * titulo, texto, tipo (tarea | cita | cumple | resumen | otro), dia (AAAA-MM-DD, opcional) e id.
 * Las tareas llevan el botón «✔ Hecho».
 */
object Avisos {
    const val ACCION = "io.github.de098.recordatorios.AVISO"
    const val ACCION_HECHO = "io.github.de098.recordatorios.HECHO"
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

    fun mostrar(c: Context, i: Intent) {
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

        val b = Notification.Builder(c, tipo)
            .setSmallIcon(R.drawable.ic_aviso)
            .setColor(c.getColor(R.color.acento))
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(if (tipo == "cita") Notification.CATEGORY_EVENT else Notification.CATEGORY_REMINDER)

        // tareas: botón «✔ Hecho» (el id de la tarea es su clave; los avisos previos llevan «clave@aviso»)
        val clave = idTxt.substringBefore("@")
        if (tipo == "tarea" && CLAVE_TAREA.matches(clave)) {
            val hecho = Intent(c, HechoReceiver::class.java).setAction(ACCION_HECHO)
                .putExtra("k", clave).putExtra("nid", id)
            val ph = PendingIntent.getBroadcast(c, id xor 0x4845, hecho, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_aviso), "✔ Hecho", ph).build())
        }
        c.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}

/** Salta la alarma: enseña el aviso. */
class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Avisos.ACCION) Avisos.mostrar(c, i)
    }
}

/** Botón «✔ Hecho» del aviso: marca la tarea, quita el aviso y pone al día los demás. */
class HechoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Avisos.ACCION_HECHO) return
        Nucleo.iniciar(c)
        Calendario.marcar(i.getStringExtra("k") ?: return)
        c.getSystemService(android.app.NotificationManager::class.java).cancel(i.getIntExtra("nid", 0))
        Programador.ahora(c)   // los avisos repetidos de esa tarea ya no se programan
    }
}
