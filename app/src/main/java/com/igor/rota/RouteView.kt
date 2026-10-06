package com.igor.rota

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Desenha a rota (sem mapa de fundo), com início em verde e posição atual em azul. */
class RouteView(c: Context) : View(c) {
    var pts: List<Pt> = emptyList()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FC4C02"); style = Paint.Style.STROKE
        strokeWidth = 12f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 42f; textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#F0F0F0"))
        val w = width.toFloat(); val h = height.toFloat()
        if (pts.isEmpty()) { c.drawText("A rota aparece aqui", w / 2, h / 2, txt); return }
        val mnA = pts.minOf { it.lat }; val mxA = pts.maxOf { it.lat }
        val mnO = pts.minOf { it.lon }; val mxO = pts.maxOf { it.lon }
        val k = cos(Math.toRadians((mnA + mxA) / 2))
        val gw = max((mxO - mnO) * k, 1e-5); val gh = max(mxA - mnA, 1e-5)
        val pad = 40f
        val sc = min((w - 2 * pad) / gw, (h - 2 * pad) / gh)
        val ox = (w - gw * sc) / 2; val oy = (h - gh * sc) / 2
        fun x(p: Pt) = (ox + (p.lon - mnO) * k * sc).toFloat()
        fun y(p: Pt) = (h - (oy + (p.lat - mnA) * sc)).toFloat()
        val path = Path()
        pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p)) else path.lineTo(x(p), y(p)) }
        c.drawPath(path, line)
        dot.color = Color.parseColor("#2E7D32"); c.drawCircle(x(pts.first()), y(pts.first()), 16f, dot)
        if (pts.size > 1) { dot.color = Color.parseColor("#1565C0"); c.drawCircle(x(pts.last()), y(pts.last()), 16f, dot) }
    }
}
