package com.igor.rota

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val orange = Color.parseColor("#FC4C02")
    private lateinit var tvTime: TextView
    private lateinit var tvDist: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvMax: TextView
    private lateinit var tvMsg: TextView
    private lateinit var route: RouteView
    private lateinit var btns: LinearLayout
    private lateinit var rec: LinearLayout
    private lateinit var histScroll: ScrollView
    private lateinit var histList: LinearLayout
    private var shown: St? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun f(v: Double, d: Int = 1) = String.format(Locale("pt", "BR"), "%.${d}f", v)
    private fun hms(ms: Long): String { val s = ms / 1000; return String.format("%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) }
    private fun prefs() = getSharedPreferences("rota", Context.MODE_PRIVATE)

    private fun tv(size: Float, bold: Boolean = false, color: Int = Color.DKGRAY) = TextView(this).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun btn(txt: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); setBackgroundColor(bg); setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        buildUi()
        if (!hasPerm()) perms()
        h.post(ticker)
    }

    override fun onDestroy() { h.removeCallbacks(ticker); super.onDestroy() }

    private val ticker = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 500) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(36), dp(16), dp(16))
        }
        root.addView(tv(20f, true, orange).apply { text = "● Rota"; gravity = Gravity.START })
        val tabs = LinearLayout(this)
        tabs.addView(btn("Gravar", orange) { showTab(true) })
        tabs.addView(btn("Atividades", Color.GRAY) { showTab(false) })
        root.addView(tabs)

        rec = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        route = RouteView(this)
        rec.addView(route, LinearLayout.LayoutParams(MATCH_PARENT, dp(260)))
        tvTime = tv(52f, true, Color.BLACK); rec.addView(tvTime)
        rec.addView(tv(12f).apply { text = "tempo em movimento" })
        val stats = LinearLayout(this).apply { setPadding(0, dp(12), 0, dp(12)) }
        fun stat(label: String): TextView {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F0F0F0")); setPadding(0, dp(8), 0, dp(8)) }
            val v = tv(22f, true, Color.BLACK)
            col.addView(v); col.addView(tv(11f).apply { text = label })
            stats.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            return v
        }
        tvDist = stat("km"); tvAvg = stat("média km/h"); tvMax = stat("máx km/h")
        rec.addView(stats)
        btns = LinearLayout(this); rec.addView(btns)
        tvMsg = tv(13f); rec.addView(tvMsg)
        root.addView(rec)

        histList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        histScroll = ScrollView(this).apply { visibility = View.GONE; addView(histList) }
        root.addView(histScroll)
        setContentView(root)
    }

    private fun showTab(gravar: Boolean) {
        rec.visibility = if (gravar) View.VISIBLE else View.GONE
        histScroll.visibility = if (gravar) View.GONE else View.VISIBLE
        if (!gravar) renderHist()
    }

    private fun refresh() {
        val ms = Tracker.elapsed()
        tvTime.text = hms(ms)
        tvDist.text = f(Tracker.dist / 1000, 2)
        tvAvg.text = f(if (ms > 0) Tracker.dist / (ms / 1000.0) * 3.6 else 0.0)
        tvMax.text = f(Tracker.maxKmh)
        val p = Tracker.points()
        route.pts = p; route.invalidate()
        if (Tracker.st == St.RUNNING && p.isEmpty()) tvMsg.text = "Aguardando sinal de GPS…"
        else if (tvMsg.text == "Aguardando sinal de GPS…") tvMsg.text = ""
        if (shown != Tracker.st) { shown = Tracker.st; renderButtons() }
    }

    private fun renderButtons() {
        btns.removeAllViews()
        val red = Color.parseColor("#C62828")
        when (Tracker.st) {
            St.IDLE -> btns.addView(btn("Iniciar", orange) { start() })
            St.RUNNING -> {
                btns.addView(btn("Pausar", Color.DKGRAY) { Tracker.pause() })
                btns.addView(btn("Finalizar", red) { finalizar() })
            }
            St.PAUSED -> {
                btns.addView(btn("Retomar", orange) { start() })
                btns.addView(btn("Finalizar", red) { finalizar() })
            }
        }
    }

    private fun hasPerm() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun perms() {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(l.toTypedArray(), 1)
    }

    private fun start() {
        if (!hasPerm()) { perms(); tvMsg.text = "Permita a localização e toque em Iniciar."; return }
        val was = Tracker.st
        if (was == St.IDLE) Tracker.reset()
        Tracker.resume()
        tvMsg.text = ""
        if (was == St.IDLE) startForegroundService(Intent(this, TrackingService::class.java).setAction("START"))
    }

    private fun finalizar() {
        Tracker.finish()
        val p = Tracker.points()
        if (p.size > 1 && Tracker.dist > 0) {
            saveAct(p, Tracker.dist, Tracker.elapsed(), Tracker.maxKmh)
            tvMsg.text = "Atividade salva em \"Atividades\"."
        } else tvMsg.text = "Atividade muito curta, não foi salva."
        startService(Intent(this, TrackingService::class.java).setAction("STOP"))
    }

    private fun acts() = JSONArray(prefs().getString("acts", "[]"))

    private fun saveAct(pts: List<Pt>, dist: Double, ms: Long, mx: Double) {
        val step = Math.ceil(pts.size / 400.0).toInt().coerceAtLeast(1)
        val sb = StringBuilder()
        pts.forEachIndexed { i, p -> if (i % step == 0 || i == pts.lastIndex) sb.append(p.lat).append(',').append(p.lon).append(';') }
        val o = JSONObject().put("id", System.currentTimeMillis()).put("dist", dist).put("ms", ms).put("max", mx).put("pts", sb.toString())
        val old = acts(); val n = JSONArray().put(o)
        for (i in 0 until old.length()) n.put(old.get(i))
        prefs().edit().putString("acts", n.toString()).apply()
    }

    private fun renderHist() {
        histList.removeAllViews()
        val a = acts()
        if (a.length() == 0) { histList.addView(tv(14f).apply { text = "Nenhuma atividade ainda."; setPadding(0, dp(24), 0, 0) }); return }
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val ms = o.getLong("ms"); val d = o.getDouble("dist")
            val avg = if (ms > 0) d / (ms / 1000.0) * 3.6 else 0.0
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F0F0F0")); setPadding(dp(12), dp(12), dp(12), dp(12)) }
            card.addView(tv(15f, true, Color.BLACK).apply { text = df.format(Date(o.getLong("id"))); gravity = Gravity.START })
            card.addView(tv(13f).apply { text = "${f(d / 1000, 2)} km · ${hms(ms)} · méd ${f(avg)} km/h · máx ${f(o.getDouble("max"))} km/h"; gravity = Gravity.START })
            val rv = RouteView(this)
            rv.pts = o.getString("pts").split(";").filter { it.isNotEmpty() }.map { s -> val q = s.split(","); Pt(q[0].toDouble(), q[1].toDouble()) }
            card.addView(rv, LinearLayout.LayoutParams(MATCH_PARENT, dp(150)).apply { topMargin = dp(8) })
            val id = o.getLong("id")
            card.addView(Button(this).apply { text = "Excluir"; setOnClickListener { delAct(id) } })
            histList.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
        }
    }

    private fun delAct(id: Long) {
        val old = acts(); val n = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getLong("id") != id) n.put(old.get(i))
        prefs().edit().putString("acts", n.toString()).apply()
        renderHist()
    }
}
