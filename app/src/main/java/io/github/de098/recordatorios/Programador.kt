package io.github.de098.recordatorios

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.ZoneId

/**
 * Avisos programados en el propio móvil, sin red ni servidor.
 * Los avisos de las próximas 48 h ([Calendario.avisos]) se dejan como alarmas exactas. Se ponen al día al abrir la app,
 * cada 15 min (lo mínimo que deja Android sin gastar batería), al cambiar algo (apuntar, hecho, borrar…) y al encender
 * el móvil o actualizar la app.
 */
object Programador {
    private const val PREFS = "programador"
    private const val JOB = 1823

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Recalcula los avisos, reprograma las alarmas y repinta los widgets. */
    @Synchronized
    fun ahora(c: Context) {
        val ctx = c.applicationContext
        Nucleo.iniciar(ctx)
        try { programar(ctx, Calendario.avisos()) } catch (_: Exception) { /* siguen valiendo las alarmas que ya había */ }
        try { Widgets.actualizarTodos(ctx) } catch (_: Exception) {}
    }

    private fun programar(c: Context, lista: List<Calendario.Aviso>) {
        val am = c.getSystemService(AlarmManager::class.java)
        // quitar las de antes
        for (rc in prefs(c).getString("codigos", "")!!.split(",").mapNotNull { it.toIntOrNull() }) {
            PendingIntent.getBroadcast(c, rc, Intent(c, AvisoReceiver::class.java).setAction(Avisos.ACCION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        }
        val zona = ZoneId.systemDefault()
        val ahora = System.currentTimeMillis()
        val nuevas = mutableListOf<Int>()
        for (a in lista) {
            val t = a.cuando.atZone(zona).toInstant().toEpochMilli()
            if (t <= ahora) continue
            val rc = (a.id + "@" + a.cuando).hashCode()
            val i = Intent(c, AvisoReceiver::class.java).setAction(Avisos.ACCION)
                .putExtra("titulo", a.titulo).putExtra("texto", a.texto).putExtra("tipo", a.tipo)
                .putExtra("dia", a.dia.toString()).putExtra("id", a.id)
            val pi = PendingIntent.getBroadcast(c, rc, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            // Android 10-11: las alarmas exactas no necesitan permiso
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            nuevas += rc
        }
        prefs(c).edit().putString("codigos", nuevas.joinToString(",")).apply()
    }

    /** La puesta al día cada 15 min (sobrevive a reinicios). */
    fun programarTrabajo(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB) != null) return
        js.schedule(JobInfo.Builder(JOB, ComponentName(c, ProgramadorJob::class.java))
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .build())
    }
}

class ProgramadorJob : JobService() {
    override fun onStartJob(p: JobParameters): Boolean {
        Programador.ahora(this)
        return false
    }
    override fun onStopJob(p: JobParameters) = false
}

/** Al encender el móvil o actualizar la app las alarmas se borran: volver a ponerlas. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Programador.programarTrabajo(c)
        Programador.ahora(c)
    }
}
