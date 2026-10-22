package com.lma.kickboxing.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var values: List<Float> = emptyList()
    private val barColors = listOf(
        "#E30613", "#1565C0", "#2E9E4B", "#E6B800", "#7B3FA0", "#E8791A", "#6B4226", "#111113", "#9A9A9A"
    ).map { Color.parseColor(it) }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B6B73")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1F")
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7E7EA")
        strokeWidth = 2f
    }

    fun setData(labels: List<String>, values: List<Float>) {
        this.labels = labels
        this.values = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val bottomPad = 50f
        val topPad = 36f
        val chartH = h - bottomPad - topPad
        val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)

        canvas.drawLine(0f, h - bottomPad, w, h - bottomPad, axisPaint)

        val n = values.size
        val slot = w / n
        val barWidth = slot * 0.5f

        for (i in 0 until n) {
            val v = values[i]
            val barH = if (maxVal > 0) (v / maxVal) * chartH else 0f
            val left = i * slot + (slot - barWidth) / 2
            val top = h - bottomPad - barH
            val right = left + barWidth
            val bottom = h - bottomPad
            barPaint.color = barColors[i % barColors.size]
            canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, barPaint)

            if (v > 0) {
                canvas.drawText(v.toInt().toString(), left + barWidth / 2, top - 10f, valuePaint)
            }
            canvas.drawText(labels.getOrElse(i) { "" }, left + barWidth / 2, h - bottomPad + 34f, labelPaint)
        }
    }
}
