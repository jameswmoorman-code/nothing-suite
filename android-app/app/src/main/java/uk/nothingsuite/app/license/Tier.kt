package uk.nothingsuite.app.license

/**
 * App-wide entitlement state. Everything that can be gated reads this.
 *
 * Pricing (one-time, GBP):
 *   PLUS  £2.99 — advanced Glyph visualiser animations
 *   PRO   £4.99 — Plus + future premium modules (custom screener greetings,
 *                 transcript export, etc.)
 */
enum class Sku(val price: String) { PLUS("£2.99"), PRO("£4.99") }

data class Tier(
    val sku: Sku? = null,
    val preferredAnimationId: String = "plain",
) {
    val isPremium: Boolean get() = sku != null
    val isPro: Boolean get() = sku == Sku.PRO

    companion object {
        val Free = Tier()
    }
}
