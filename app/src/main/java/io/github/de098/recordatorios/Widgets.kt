package io.github.de098.recordatorios

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

/**
 * Widgets (8-nov-2026, pedido por D): Próximos (con ✔ Hecho), Mes (puntos de colores) y Botones (Apuntar / Nota nueva).
 * Leen los datos locales de la app (Calendario) cada vez que se repintan; tema claro/oscuro el de la app.
 */
object Widgets {
    const val ACC_MES = "io.github.de098.recordatorios.WIDGET_MES"

    private class Paleta(val oscuro: Boolean) {
        val texto = if (oscuro) 0xFFF0EFF3.toInt() else 0xFF241F31.toInt()
        val suave = if (oscuro) 0xFFA6A2AD.toInt() else 0xFF6F6A78.toInt()
        val hoyTexto = if (oscuro) 0xFF10131A.toInt() else 0xFFFFFFFF.toInt()
        val rojo = if (oscuro) 0xFFF66151.toInt() else 0xFFC01C28.toInt()
        val borde = if (oscuro) 0xFF90A4AE.toInt() else 0xFF78858F.toInt()
        val fondo = if (oscuro) R.drawable.w_fondo_oscuro else R.drawable.w_fondo_claro
        val boton = if (oscuro) R.drawable.w_boton_oscuro else R.drawable.w_boton_claro
        val hoy = if (oscuro) R.drawable.w_hoy_oscuro else R.drawable.w_hoy_claro
    }

    private fun paleta(c: Context): Paleta {
        val t = c.getSharedPreferences("sincro", Context.MODE_PRIVATE).getString("tema", null)
        val sistema = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return Paleta(if (t != null) t == "oscuro" else sistema)
    }

    /** Lo apuntado desde una semana antes del mes (desplazado) hasta ~2 meses después, como lo pide la web. */
    private fun datos(c: Context, desplaza: Int = 0): JSONObject? = try {
        Nucleo.iniciar(c)
        val desde = LocalDate.now().withDayOfMonth(1).plusMonths(desplaza.toLong()).minusDays(7)
        Calendario.datos(desde, desde.plusDays(60))
    } catch (_: Exception) { null }

    fun actualizarTodos(c: Context) {
        val am = AppWidgetManager.getInstance(c)
        for (id in am.getAppWidgetIds(ComponentName(c, WProximos::class.java))) proximos(c, am, id)
        for (id in am.getAppWidgetIds(ComponentName(c, WMes::class.java))) mes(c, am, id)
        for (id in am.getAppWidgetIds(ComponentName(c, WBotones::class.java))) botones(c, am, id)
    }

    private fun abrir(c: Context, rc: Int, dia: String? = null, pestana: String? = null, accion: String? = null): PendingIntent {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        dia?.let { i.putExtra(MainActivity.EXTRA_DIA, it) }
        pestana?.let { i.putExtra(MainActivity.EXTRA_PESTANA, it) }
        accion?.let { i.putExtra(MainActivity.EXTRA_ACCION, it) }
        return PendingIntent.getActivity(c, rc, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val DIAS = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
    private val DIAS_L = arrayOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")
    private val MESES = arrayOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre",
        "octubre", "noviembre", "diciembre")

    private fun nombreDia(d: String, hoy: String): String {
        val f = Calendar.getInstance().apply { set(d.substring(0, 4).toInt(), d.substring(5, 7).toInt() - 1, d.substring(8, 10).toInt()) }
        val h = Calendar.getInstance().apply { set(hoy.substring(0, 4).toInt(), hoy.substring(5, 7).toInt() - 1, hoy.substring(8, 10).toInt()) }
        val dif = ((f.timeInMillis - h.timeInMillis) / 86_400_000.0).let { Math.round(it).toInt() }
        return when (dif) {
            0 -> "Hoy"; 1 -> "Mañana"
            else -> "${DIAS[f.get(Calendar.DAY_OF_WEEK) - 1].replaceFirstChar { it.uppercase() }} ${f.get(Calendar.DAY_OF_MONTH)} ${MESES[f.get(Calendar.MONTH)].take(3)}"
        }
    }

    // ---------------------------------------------------------------- PRÓXIMOS (un día; ‹ › saltan a días con cosas)
    const val ACC_PROX = "io.github.de098.recordatorios.WIDGET_PROX"

    private fun hoyIso(): String = String.format(Locale.ROOT, "%tF", Calendar.getInstance())

    /** Días (AAAA-MM-DD) que tienen algo, desde hoy, ordenados. */
    private fun diasConCosas(d: JSONObject?): List<String> {
        val cs = d?.optJSONArray("cosas") ?: return emptyList()
        val hoy = hoyIso()
        return (0 until cs.length()).map { cs.getJSONObject(it).optString("d") }.filter { it >= hoy }.distinct().sorted()
    }

    /** Día que enseña el widget (hoy si no se ha movido o si el elegido ya pasó). */
    fun diaProximos(c: Context, wid: Int): String {
        val g = c.getSharedPreferences("sincro", Context.MODE_PRIVATE).getString("prox_$wid", null)
        val hoy = hoyIso()
        return if (g == null || g < hoy) hoy else g
    }

    /** ‹ (-1) o › (+1): al día anterior/siguiente que tenga algo (hacia atrás, como mucho hasta hoy). */
    fun moverProximos(c: Context, wid: Int, delta: Int) {
        val dia = diaProximos(c, wid); val hoy = hoyIso()
        val dias = diasConCosas(datos(c))
        val nuevo = if (delta > 0) dias.firstOrNull { it > dia } ?: dia
                    else dias.lastOrNull { it < dia && it >= hoy } ?: hoy
        c.getSharedPreferences("sincro", Context.MODE_PRIVATE).edit().putString("prox_$wid", nuevo).apply()
    }

    private fun proximos(c: Context, am: AppWidgetManager, wid: Int) {
        val p = paleta(c)
        val v = RemoteViews(c.packageName, R.layout.w_proximos)
        v.setInt(R.id.raiz, "setBackgroundResource", p.fondo)
        v.setTextColor(R.id.titulo, p.texto); v.setTextColor(R.id.vacio, p.suave)
        for (b in intArrayOf(R.id.ant, R.id.sig, R.id.actualizar)) { v.setInt(b, "setBackgroundResource", p.boton); v.setTextColor(b, p.texto) }
        v.setOnClickPendingIntent(R.id.actualizar, PendingIntent.getActivity(c, 9100 + wid,
            Intent(c, AccionWidget::class.java).putExtra("accion", "actualizar"), PendingIntent.FLAG_IMMUTABLE))

        val d = datos(c)
        val hoy = hoyIso()
        val dia = diaProximos(c, wid)
        val dias = diasConCosas(d)
        // título: «Hoy, domingo 8» / «Mañana, lunes 9» / «Jueves 12 nov»
        val f = Calendar.getInstance().apply { set(dia.substring(0, 4).toInt(), dia.substring(5, 7).toInt() - 1, dia.substring(8, 10).toInt()) }
        val nombre = nombreDia(dia, hoy)
        v.setTextViewText(R.id.titulo, if (nombre == "Hoy" || nombre == "Mañana")
            "$nombre, ${DIAS_L[f.get(Calendar.DAY_OF_WEEK) - 1]} ${f.get(Calendar.DAY_OF_MONTH)}" else nombre)
        v.setOnClickPendingIntent(R.id.titulo, abrir(c, 9000 + wid, dia = dia, pestana = "cal"))
        // flechas: apagadas si no hay a dónde ir
        val hayAnt = dia > hoy; val haySig = dias.any { it > dia }
        v.setTextColor(R.id.ant, if (hayAnt) p.texto else (p.texto and 0x00FFFFFF) or 0x40000000)
        v.setTextColor(R.id.sig, if (haySig) p.texto else (p.texto and 0x00FFFFFF) or 0x40000000)
        for ((b, delta) in listOf(R.id.ant to -1, R.id.sig to 1))
            v.setOnClickPendingIntent(b, PendingIntent.getBroadcast(c, 9800 + wid * 2 + (delta + 1) / 2,
                Intent(c, WProximos::class.java).setAction(ACC_PROX).putExtra("wid", wid).putExtra("delta", delta),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))

        val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(2).setHasStableIds(true)
        var n = 0L
        if (d != null) {
            fun fila(o: JSONObject, sub: String, atrasada: Boolean) {
                val r = RemoteViews(c.packageName, R.layout.w_proximos_fila)
                val imp = o.optBoolean("imp"); val hecho = o.optBoolean("hecho")
                r.setTextViewText(R.id.t, o.optString("tt", o.optString("t")))   // importante: en rojo (sin ❗, como pidió D)
                r.setTextColor(R.id.t, when { hecho -> p.suave; imp -> p.rojo; else -> p.texto })
                r.setTextViewText(R.id.s, sub); r.setTextColor(R.id.s, if (atrasada) p.rojo else p.suave)
                r.setInt(R.id.barra, "setColorFilter", try { Color.parseColor(o.optString("c")) } catch (_: Exception) { p.suave })
                r.setOnClickFillInIntent(R.id.fila, Intent().putExtra("accion", "abrir").putExtra("dia", o.optString("d")))
                val k = o.optString("k")
                if (o.optString("tipo") == "tarea" && k.isNotEmpty() && !hecho) {
                    r.setViewVisibility(R.id.hecho, View.VISIBLE)
                    r.setOnClickFillInIntent(R.id.hecho, Intent().putExtra("accion", "hecho").putExtra("k", k).putExtra("t", o.optString("t")))
                }
                items.addItem(n++, r)
            }
            fun cab(t: String) {
                val h = RemoteViews(c.packageName, R.layout.w_proximos_cab)
                h.setTextViewText(R.id.cab, t); h.setTextColor(R.id.cab, p.suave)
                items.addItem(n++, h)
            }
            // hoy: primero lo atrasado de otros días
            val pend = d.optJSONArray("pendientes") ?: JSONArray()
            val pendK = (0 until pend.length()).map { pend.getJSONObject(it).optString("k") }.toSet()
            val atrasadas = (0 until pend.length()).map { pend.getJSONObject(it) }.filter { it.optString("d") < hoy }
            if (dia == hoy && atrasadas.isNotEmpty()) {
                cab("Atrasadas")
                for (o in atrasadas) fila(o, "Tocaba " + o.optString("cuando"), true)
                cab("Hoy")
            }
            val cosas = d.optJSONArray("cosas") ?: JSONArray()
            for (i in 0 until cosas.length()) {
                val o = cosas.getJSONObject(i)
                if (o.optString("d") != dia) continue
                if (dia == hoy && o.optString("k") in pendK && o.optString("d") < hoy) continue
                val sub = listOfNotNull(o.optString("h").ifEmpty { null }, if (o.optBoolean("hecho")) "hecha" else null,
                    if (o.optString("tipo") == "cita" && !o.optBoolean("cumple")) "cita" else null,
                    o.optString("notas").ifEmpty { null }).joinToString(" · ")
                fila(o, sub, false)
            }
        }
        val vacio = n == 0L
        if (vacio) v.setTextViewText(R.id.vacio, if (dias.any { it > dia }) "Nada este día  ·  › para lo siguiente" else "Nada este día")
        v.setViewVisibility(R.id.vacio, if (vacio) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.lista, if (vacio) View.GONE else View.VISIBLE)
        v.setRemoteAdapter(R.id.lista, items.build())
        v.setPendingIntentTemplate(R.id.lista, PendingIntent.getActivity(c, 9200 + wid,
            Intent(c, AccionWidget::class.java), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        am.updateAppWidget(wid, v)
    }

    // ---------------------------------------------------------------- MES
    private fun mes(c: Context, am: AppWidgetManager, wid: Int) {
        val p = paleta(c)
        val prefs = c.getSharedPreferences("sincro", Context.MODE_PRIVATE)
        val desplaza = prefs.getInt("mes_$wid", 0)
        val v = RemoteViews(c.packageName, R.layout.w_mes)
        v.setInt(R.id.raiz, "setBackgroundResource", p.fondo)
        v.setTextColor(R.id.mes, p.texto)
        for (b in intArrayOf(R.id.ant, R.id.sig)) { v.setInt(b, "setBackgroundResource", p.boton); v.setTextColor(b, p.texto) }
        val semana = intArrayOf(R.id.d0, R.id.d1, R.id.d2, R.id.d3, R.id.d4, R.id.d5, R.id.d6)
        for (s in semana) v.setTextColor(s, p.suave)

        val hoy = Calendar.getInstance()
        val m = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, desplaza) }
        v.setTextViewText(R.id.mes, MESES[m.get(Calendar.MONTH)].replaceFirstChar { it.uppercase() } + " " + m.get(Calendar.YEAR))
        // puntos por día (colores de lo apuntado; hechos más claros)
        val puntos = HashMap<String, MutableList<Int>>(); val imps = HashSet<String>()
        for (cs in listOfNotNull(datos(c, desplaza)?.optJSONArray("cosas"))) {
            for (i in 0 until cs.length()) {
                val o = cs.getJSONObject(i); val dd = o.optString("d")
                val col = try { Color.parseColor(o.optString("c")) } catch (_: Exception) { p.suave }
                val l = puntos.getOrPut(dd) { mutableListOf() }
                if (l.size < 3 && col !in l) l += if (o.optBoolean("hecho")) (col and 0x00FFFFFF) or 0x55000000 else col
                if (o.optBoolean("imp")) imps += dd
            }
        }
        val dia = m.clone() as Calendar
        dia.add(Calendar.DAY_OF_MONTH, -((m.get(Calendar.DAY_OF_WEEK) + 5) % 7))   // empezar en lunes
        for (n in 0 until 42) {
            val idC = c.resources.getIdentifier("c$n", "id", c.packageName)
            val idN = c.resources.getIdentifier("n$n", "id", c.packageName)
            val iso = String.format(Locale.ROOT, "%04d-%02d-%02d", dia.get(Calendar.YEAR), dia.get(Calendar.MONTH) + 1, dia.get(Calendar.DAY_OF_MONTH))
            val delMes = dia.get(Calendar.MONTH) == m.get(Calendar.MONTH)
            val esHoy = dia.get(Calendar.YEAR) == hoy.get(Calendar.YEAR) && dia.get(Calendar.DAY_OF_YEAR) == hoy.get(Calendar.DAY_OF_YEAR)
            v.setTextViewText(idN, dia.get(Calendar.DAY_OF_MONTH).toString())
            v.setTextColor(idN, when { esHoy -> p.hoyTexto; iso in imps && delMes -> p.rojo; delMes -> p.texto; else -> (p.texto and 0x00FFFFFF) or 0x55000000 })
            v.setInt(idN, "setBackgroundResource", if (esHoy) p.hoy else 0)
            val cols = puntos[iso].orEmpty()
            for (j in 0 until 3) {
                val idQ = c.resources.getIdentifier("q${n}_$j", "id", c.packageName)
                val col = cols.getOrNull(j)
                v.setViewVisibility(idQ, if (col == null) View.GONE else View.VISIBLE)
                if (col != null) v.setInt(idQ, "setColorFilter", if (delMes) col else (col and 0x00FFFFFF) or 0x55000000)
            }
            v.setOnClickPendingIntent(idC, abrir(c, wid * 100 + n, dia = iso, pestana = "cal"))
            dia.add(Calendar.DAY_OF_MONTH, 1)
        }
        v.setOnClickPendingIntent(R.id.mes, abrir(c, 9300 + wid))
        for ((b, delta) in listOf(R.id.ant to -1, R.id.sig to 1))
            v.setOnClickPendingIntent(b, PendingIntent.getBroadcast(c, wid * 10 + delta + 5,
                Intent(c, WMes::class.java).setAction(ACC_MES).putExtra("wid", wid).putExtra("delta", delta),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        am.updateAppWidget(wid, v)
    }

    // ---------------------------------------------------------------- BOTONES
    private fun botones(c: Context, am: AppWidgetManager, wid: Int) {
        val p = paleta(c)
        val v = RemoteViews(c.packageName, R.layout.w_botones)
        v.setInt(R.id.raiz, "setBackgroundResource", p.fondo)
        for (t in intArrayOf(R.id.bapuntar_t, R.id.bnota_t)) v.setTextColor(t, p.texto)
        v.setInt(R.id.raya, "setColorFilter", p.borde)
        v.setOnClickPendingIntent(R.id.bapuntar, abrir(c, 9500 + wid, pestana = "cal", accion = "nuevo"))
        v.setOnClickPendingIntent(R.id.bnota, abrir(c, 9600 + wid, pestana = "notas", accion = "nota"))
        am.updateAppWidget(wid, v)
    }
}

class WProximos : AppWidgetProvider() {
    override fun onUpdate(c: Context, am: AppWidgetManager, ids: IntArray) = Widgets.actualizarTodos(c)
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Widgets.ACC_PROX) {
            Widgets.moverProximos(c, i.getIntExtra("wid", 0), i.getIntExtra("delta", 0))
            Widgets.actualizarTodos(c)
        } else super.onReceive(c, i)
    }
}

class WBotones : AppWidgetProvider() {
    override fun onUpdate(c: Context, am: AppWidgetManager, ids: IntArray) = Widgets.actualizarTodos(c)
}

class WMes : AppWidgetProvider() {
    override fun onUpdate(c: Context, am: AppWidgetManager, ids: IntArray) = Widgets.actualizarTodos(c)
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Widgets.ACC_MES) {
            val wid = i.getIntExtra("wid", 0); val delta = i.getIntExtra("delta", 0)
            val pr = c.getSharedPreferences("sincro", Context.MODE_PRIVATE)
            val nuevo = pr.getInt("mes_$wid", 0) + delta
            pr.edit().putInt("mes_$wid", nuevo).apply()
            Widgets.actualizarTodos(c)
        } else super.onReceive(c, i)
    }
}

/** Pulsaciones dentro de los widgets: sin pantalla (se cierra al momento). Hecho = como «✔ Hecho» en la web. */
class AccionWidget : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val app = applicationContext
        Nucleo.iniciar(app)
        when (intent.getStringExtra("accion")) {
            "hecho" -> {
                val k = intent.getStringExtra("k") ?: ""
                val t = intent.getStringExtra("t") ?: ""
                Calendario.marcar(k)
                Avisos.cancelar(app, k)
                Programador.ahora(app)
                Toast.makeText(app, "Hecho: $t", Toast.LENGTH_SHORT).show()
            }
            "actualizar" -> { Programador.ahora(app); Toast.makeText(app, "Actualizado", Toast.LENGTH_SHORT).show() }
            "abrir" -> startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_DIA, intent.getStringExtra("dia")).putExtra(MainActivity.EXTRA_PESTANA, "cal"))
        }
        finish()
    }
}
