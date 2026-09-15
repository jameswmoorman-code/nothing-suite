package uk.nothingsuite.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Google Play one-time purchases ("in-app products", non-consumable).
 *
 * Play Console setup (once per app):
 *   Monetise → Products → In-app products → Create, using the product IDs
 *   from that app's [Catalogue].
 *
 * Google handles payment, VAT, refunds and restores across devices. We only
 * ever ask "does this Google account own PLUS or PRO?" — no user data is
 * sent anywhere by us.
 */
class PlayBillingLicenseSource(
    context: Context,
    private val catalogue: Catalogue,
    private val onEntitlementChanged: () -> Unit,
) : LicenseSource, PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    @Volatile private var cachedSku: Sku? = null
    @Volatile private var connected = false

    init { connect() }

    private fun connect() {
        if (connected) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) refreshOwned()
            }
            override fun onBillingServiceDisconnected() { connected = false }
        })
    }

    /** Ask Play which products this account owns. Runs async; result cached. */
    private fun refreshOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .onEach { acknowledgeIfNeeded(it) }
                .flatMap { it.products }
            val ownedSkus = owned.mapNotNull { catalogue.byId(it)?.sku }
            cachedSku = when {
                Sku.PRO in ownedSkus -> Sku.PRO
                Sku.PLUS in ownedSkus -> Sku.PLUS
                else -> null
            }
            onEntitlementChanged()
        }
    }

    override fun currentSku(): Sku? {
        if (!connected) connect()
        return cachedSku
    }

    /** Launch the Google Play purchase sheet for a SKU. */
    fun buy(activity: Activity, sku: Sku) {
        val productId = catalogue.idFor(sku) ?: run {
            Log.w(TAG, "no product configured for $sku"); return
        }
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            ).build()

        client.queryProductDetailsAsync(query) { result, details ->
            val product: ProductDetails = details.firstOrNull() ?: run {
                Log.w(TAG, "product $productId not found: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .build()
                    )
                ).build()
            client.launchBillingFlow(activity, flow)
        }
    }

    /** Google calls this after the purchase sheet closes. */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { acknowledgeIfNeeded(it) }
            refreshOwned()
        } else {
            Log.i(TAG, "purchase flow ended: ${result.responseCode} ${result.debugMessage}")
        }
    }

    /** Unacknowledged purchases are auto-refunded by Google after 3 days — always acknowledge. */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { r ->
            Log.i(TAG, "acknowledge → ${r.responseCode}")
        }
    }

    /** "Restore purchases" button: re-query and wait briefly for the answer. */
    fun restore(): Sku? {
        val latch = CountDownLatch(1)
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { _, _ -> refreshOwned(); latch.countDown() }
        latch.await(5, TimeUnit.SECONDS)
        return cachedSku
    }

    private companion object {
        const val TAG = "PlayBilling"
    }
}
