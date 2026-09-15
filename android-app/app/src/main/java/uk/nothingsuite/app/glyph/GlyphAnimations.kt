package uk.nothingsuite.app.glyph

import kotlinx.coroutines.delay
import uk.nothingsuite.billing.Tier

/**
 * How progress is *drawn* on the strip. Free tier gets a plain fill;
 * premium unlocks richer visualisers. This is the seam the £2.99 / £4.99
 * unlock sits on — nothing else in the app is gated.
 */
interface GlyphAnimation {
    val id: String
    val premium: Boolean
    suspend fun render(glyph: GlyphController, percent: Int)
}

object GlyphAnimations {

    /** Free: static fill to the current percentage. */
    object PlainFill : GlyphAnimation {
        override val id = "plain"
        override val premium = false
        override suspend fun render(glyph: GlyphController, percent: Int) = glyph.showProgress(percent)
    }

    /** Premium: sweeps up from 0 to the value, then holds. */
    object Sweep : GlyphAnimation {
        override val id = "sweep"
        override val premium = true
        override suspend fun render(glyph: GlyphController, percent: Int) {
            for (p in 0..percent step 5) {
                glyph.showProgress(p)
                delay(18)
            }
            glyph.showProgress(percent)
        }
    }

    /** Premium: fill, then "breathe" the strip on milestones (80 %, 100 %). */
    object Milestone : GlyphAnimation {
        override val id = "milestone"
        override val premium = true
        override suspend fun render(glyph: GlyphController, percent: Int) {
            glyph.showProgress(percent)
            if (percent == 100 || percent == 80) {
                glyph.pulse(cycles = if (percent == 100) 3 else 1)
                delay(600)
                glyph.showProgress(percent)
            }
        }
    }

    val all: List<GlyphAnimation> = listOf(PlainFill, Sweep, Milestone)

    /** The selected animation, downgraded to PlainFill if the tier doesn't cover it. */
    fun forTier(tier: Tier, preferredId: String = tier.preferredAnimationId): GlyphAnimation {
        val wanted = all.firstOrNull { it.id == preferredId } ?: PlainFill
        return if (wanted.premium && !tier.isPremium) PlainFill else wanted
    }
}
