package uk.nothingsuite.dotwidgets.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import uk.nothingsuite.dotwidgets.R
import kotlin.math.ceil
import kotlin.math.min
import uk.nothingsuite.design.R as DesignR

/**
 * Draws dot-matrix text as bitmaps in OUR process and hands the launcher
 * finished pictures. Launchers are unreliable at loading another app's font
 * for widget text (the Nothing launcher stopped doing it after a reboot), but
 * they always show an image. Same trick as Now Playing's DotMatrixBitmap.
 */
object DotInk {

    enum class Kind(val sp: Float, val light: Boolean, val spacing: Float) {
        HERO(56f, false, 0.12f),
        TITLE(32f, false, 0.12f),
        BODY(20f, false, 0.12f),
        LABEL(14f, true, 0.2f),
    }

    private var bold: Typeface? = null
    private var light: Typeface? = null

    private fun face(context: Context, isLight: Boolean): Typeface {
        if (bold == null) bold = ResourcesCompat.getFont(context, DesignR.font.doto_round) ?: Typeface.MONOSPACE
        if (light == null) light = ResourcesCompat.getFont(context, DesignR.font.doto_round_light) ?: bold
        return if (isLight) light!! else bold!!
    }

    /** Render [text] (upper-cased) into a bitmap. Wraps at [maxWidthDp] when given. */
    fun text(context: Context, text: String, kind: Kind, color: Int, maxWidthDp: Int? = null): Bitmap {
        val dm = context.resources.displayMetrics
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face(context, kind.light)
            textSize = kind.sp * dm.scaledDensity
            letterSpacing = kind.spacing
            this.color = color
        }
        val s = text.uppercase()
        val natural = ceil(paint.measureText(s)).toInt().coerceAtLeast(1)
        val maxW = maxWidthDp?.let { (it * dm.density).toInt() } ?: Int.MAX_VALUE
        val width = min(natural, maxW)
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(3)
            .build()
        // letterSpacing adds trailing space; measure the widest real line
        val w = (0 until layout.lineCount).maxOf { ceil(layout.getLineWidth(it)).toInt() }.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w + 2, layout.height + 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(((w + 2 - layout.width) / 2f), 1f)
        layout.draw(c)
        return bmp
    }

    /** A row of [count] round dots, the first fraction filled — the suite's progress bar. */
    fun dots(context: Context, fraction: Double, count: Int, color: Int): Bitmap {
        val d = context.resources.displayMetrics.density
        val r = 3.5f * d; val gap = 6f * d; val stroke = 1.2f * d
        val w = (count * (2 * r) + (count - 1) * gap + stroke * 2).toInt()
        val h = (2 * r + stroke * 2).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val on = (fraction.coerceIn(0.0, 1.0) * count).toInt()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = stroke }
        for (i in 0 until count) {
            val cx = stroke + r + i * (2 * r + gap); val cy = h / 2f
            c.drawCircle(cx, cy, r - (if (i < on) 0f else stroke / 2), if (i < on) fill else ring)
        }
        return bmp
    }
}

// Convenience for widget render() code -------------------------------------

fun RemoteViews.ink(context: Context, id: Int, text: String, kind: DotInk.Kind, colorRes: Int = R.color.dw_white, maxWidthDp: Int? = null) {
    setImageViewBitmap(id, DotInk.text(context, text, kind, context.getColor(colorRes), maxWidthDp))
}

fun RemoteViews.inkLabel(context: Context, id: Int, text: String, colorRes: Int = R.color.dw_grey) =
    ink(context, id, text, DotInk.Kind.LABEL, colorRes)

fun RemoteViews.inkDots(context: Context, id: Int, fraction: Double, count: Int = 10, colorRes: Int = R.color.dw_white) {
    setImageViewBitmap(id, DotInk.dots(context, fraction, count, context.getColor(colorRes)))
}
