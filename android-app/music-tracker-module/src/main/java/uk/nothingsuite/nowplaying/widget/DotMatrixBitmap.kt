package uk.nothingsuite.nowplaying.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import uk.nothingsuite.design.R as DesignR

/**
 * App widgets (RemoteViews / Glance) cannot use custom fonts directly — only
 * the system families. To keep the dot-matrix look on the widget we render
 * each line of text to a bitmap with the design system's font and show the
 * bitmap. Cheap: two small bitmaps per update, every few minutes.
 */
object DotMatrixBitmap {

    fun render(
        context: Context,
        text: String,
        sizeSp: Float,
        color: Int = Color.WHITE,
        maxWidthPx: Int,
        letterSpacing: Float = 0.08f,
    ): Bitmap {
        val density = context.resources.displayMetrics.scaledDensity
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, DesignR.font.ndot) ?: android.graphics.Typeface.MONOSPACE
            textSize = sizeSp * density
            this.color = color
            this.letterSpacing = letterSpacing
        }
        val upper = text.uppercase()
        val fitted = ellipsise(paint, upper, maxWidthPx)
        val width = paint.measureText(fitted).toInt().coerceAtLeast(1)
        val fm = paint.fontMetricsInt
        val height = (fm.descent - fm.ascent).coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(fitted, 0f, -fm.ascent.toFloat(), paint)
        return bmp
    }

    private fun ellipsise(paint: Paint, text: String, maxWidth: Int): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
