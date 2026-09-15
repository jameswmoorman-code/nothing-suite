package uk.nothingsuite.billing

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Premium lock switch.
 *
 * Sources are checked in order; the first that reports a purchase wins.
 *
 *   1. [PlayBillingLicenseSource] — the production path. Google Play handles
 *      payment, VAT and refunds; we just ask what this account owns.
 *   2. [FileLicenseSource] — a signed `license.json` in app-private storage.
 *      Kept for (a) your own testing without a Play purchase and (b) any
 *      future GitHub/F-Droid build. Never used for Play sales — Play policy
 *      requires Play Billing for in-app unlocks.
 *
 * Licence file shape: { "payload": "<base64 json {sku, issuedTo, issuedAt}>", "sig": "<base64>" }
 */
class LicenseManager(
    private val context: Context,
    val catalogue: Catalogue,
    sources: List<LicenseSource>? = null,
) {
    private val _tier = MutableStateFlow(Tier.Free)
    val tier: StateFlow<Tier> = _tier.asStateFlow()

    val play: PlayBillingLicenseSource = PlayBillingLicenseSource(context, catalogue) { refresh() }
    private val sources: List<LicenseSource> = sources ?: listOf(play, FileLicenseSource(context))

    init { refresh() }

    fun refresh() {
        val sku = sources.firstNotNullOfOrNull { runCatching { it.currentSku() }.getOrNull() }
        _tier.value = _tier.value.copy(sku = sku)
        Log.i(TAG, "tier=${_tier.value}")
    }

    fun setPreferredAnimation(id: String) {
        _tier.value = _tier.value.copy(preferredAnimationId = id)
    }

    /** Called from a "Restore / import licence" screen after the user pastes a licence string. */
    fun importLicense(json: String): Boolean {
        val ok = FileLicenseSource(context).write(json)
        if (ok) refresh()
        return ok
    }

    private companion object { const val TAG = "License" }
}

interface LicenseSource {
    /** Return the purchased SKU, or null when unlicensed. Throwing == unlicensed. */
    fun currentSku(): Sku?
}

class FileLicenseSource(private val context: Context) : LicenseSource {

    @Serializable
    private data class Envelope(val payload: String, val sig: String)

    @Serializable
    private data class Payload(val sku: String, val issuedTo: String = "", val issuedAt: Long = 0)

    private val file get() = File(context.filesDir, "license.json")
    private val json = Json { ignoreUnknownKeys = true }

    override fun currentSku(): Sku? {
        if (!file.exists()) return null
        val env = json.decodeFromString<Envelope>(file.readText())
        val payloadBytes = Base64.decode(env.payload, Base64.DEFAULT)
        if (!verify(payloadBytes, Base64.decode(env.sig, Base64.DEFAULT))) return null
        val payload = json.decodeFromString<Payload>(String(payloadBytes))
        return Sku.entries.firstOrNull { it.name.equals(payload.sku, ignoreCase = true) }
    }

    fun write(raw: String): Boolean = runCatching {
        json.decodeFromString<Envelope>(raw) // validate shape first
        file.writeText(raw)
        currentSku() != null
    }.getOrDefault(false)

    private fun verify(data: ByteArray, sig: ByteArray): Boolean = runCatching {
        val keyBytes = Base64.decode(PUBLIC_KEY_B64, Base64.DEFAULT)
        val pub = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(keyBytes))
        Signature.getInstance("Ed25519").apply { initVerify(pub); update(data) }.verify(sig)
    }.getOrDefault(false)

    private companion object {
        /**
         * Your Ed25519 public key, X.509/SPKI DER, base64.
         * Generate a pair:  openssl genpkey -algorithm ed25519 -out lic.key
         *                   openssl pkey -in lic.key -pubout -outform DER | base64
         * Sign a payload:   openssl pkeyutl -sign -inkey lic.key -rawin -in payload.json | base64
         */
        const val PUBLIC_KEY_B64 = "REPLACE_WITH_YOUR_BASE64_SPKI_PUBLIC_KEY"
    }
}
