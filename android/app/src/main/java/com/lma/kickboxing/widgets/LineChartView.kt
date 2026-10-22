package com.lma.kickboxing.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class LineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var values: List<Float> = emptyList()
    private var lineColorStr: String = "#E30613"

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B6B73")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7E7EA")
        strokeWidth = 2f
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A9A")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    fun setData(labels: List<String>, values: List<Float>, colorHex: String = "#E30613") {
        this.labels = labels
        this.values = values
        this.lineColorStr = colorHex
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (values.isEmpty()) {
            canvas.drawText("No data yet", w / 2, h / 2, emptyPaint)
            return
        }

        val color = Color.parseColor(lineColorStr)
        linePaint.color = color
        dotPaint.color = color
        fillPaint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
            android.graphics.Shader.TileMode.CLAMP
        )

        val bottomPad = 44f
        val topPad = 20f
        val chartH = h - bottomPad - topPad
        val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val n = values.size

        canvas.drawLine(0f, h - bottomPad, w, h - bottomPad, axisPaint)

        if (n == 1) {
            val x = w / 2
            val y = h - bottomPad - (values[0] / maxVal) * chartH
            canvas.drawCircle(x, y, 6f, dotPaint)
            canvas.drawText(labels[0], x, h - bottomPad + 28f, labelPaint)
            return
        }

        val stepX = w / (n - 1)
        val path = Path()
        val fillPath = Path()

        for (i in 0 until n) {
            val x = i * stepX
            val y = h - bottomPad - (values[i] / maxVal) * chartH
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h - bottomPad)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((n - 1) * stepX, h - bottomPad)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        for (i in 0 until n) {
            val x = i * stepX
            val y = h - bottomPad - (values[i] / maxVal) * chartH
            canvas.drawCircle(x, y, 6f, dotPaint)
            if (n <= 8 || i % (n / 8 + 1) == 0) {
                canvas.drawText(labels.getOrElse(i) { "" }, x, h - bottomPad + 28f, labelPaint)
            }
        }
    }
}
