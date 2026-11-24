package app.nightfall

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/** Schlichter, ruhiger Schalter – ohne Material-Standardlook. */
class Toggle @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val bg = ContextCompat.getColor(context, R.color.bg)
    private val trackOff = ContextCompat.getColor(context, R.color.track_off)
    private val knobOff = ContextCompat.getColor(context, R.color.knob_off)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.25f * d
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var progress = 0f
    private var animator: ValueAnimator? = null

    var checked = false
        private set

    fun setChecked(value: Boolean, animate: Boolean) {
        checked = value
        val target = if (value) 1f else 0f
        animator?.cancel()
        if (!animate) {
            progress = target; invalidate(); return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 320
            interpolator = PathInterpolator(0.3f, 0f, 0f, 1f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((54 * d).toInt(), (30 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        val inset = stroke.strokeWidth / 2f

        rect.set(inset, inset, w - inset, h - inset)
        // Umriss (aus) blendet in Füllung (an) über
        stroke.color = ColorUtils.setAlphaComponent(trackOff, ((1f - progress) * 255).toInt())
        canvas.drawRoundRect(rect, r, r, stroke)
        fill.color = ColorUtils.setAlphaComponent(ink, (progress * 255).toInt())
        canvas.drawRoundRect(rect, r, r, fill)

        val kr = 11f * d
        val left = 3f * d + kr
        val right = w - 4f * d - kr
        knob.color = ColorUtils.blendARGB(knobOff, bg, progress)
        canvas.drawCircle(left + (right - left) * progress, h / 2f, kr, knob)
    }
}
