package uk.nothingsuite.billing

/** The two one-time tiers every app in the suite uses. Price strings are per-app. */
enum class Sku { PLUS, PRO }

/** A Play in-app product: which tier it grants, its Play Console product ID, and the display price. */
data class Product(val sku: Sku, val productId: String, val price: String)

/**
 * Per-app product catalogue. Each app passes its own; the IDs must match
 * the products created in Play Console for that app's package name.
 */
data class Catalogue(val products: List<Product>) {
    val plus: Product? get() = products.firstOrNull { it.sku == Sku.PLUS }
    val pro: Product? get() = products.firstOrNull { it.sku == Sku.PRO }
    fun byId(id: String): Product? = products.firstOrNull { it.productId == id }
    fun idFor(sku: Sku): String? = products.firstOrNull { it.sku == sku }?.productId

    companion object {
        /** One unlock only — the common case for the small apps. */
        fun single(productId: String, price: String) = Catalogue(listOf(Product(Sku.PLUS, productId, price)))
    }
}

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
