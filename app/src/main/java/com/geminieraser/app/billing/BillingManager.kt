package com.geminieraser.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    companion object {
        // ── Google Play Console Subscription Product IDs ──────────────────────
        const val SUB_YEARLY = "pro_yearly_trial"   // Base plan: yearly-base | Offer: 3-day-free-trial
        const val SUB_WEEKLY = "pro_weekly"          // Base plan: weekly-base

        /** All pro product IDs — used when checking if a user owns any active subscription. */
        val ALL_PRO_PRODUCTS = listOf(SUB_YEARLY, SUB_WEEKLY)
    }

    init {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
        connectToPlayBilling()
    }

    private fun connectToPlayBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkActiveSubscriptions()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        })
    }

    /**
     * Checks if the user already owns any active subscription (Yearly or Weekly).
     * Updates [isPremium] accordingly.
     */
    fun checkActiveSubscriptions() {
        if (!billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPremium = purchases.any { purchase ->
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            purchase.products.any { it in ALL_PRO_PRODUCTS }
                }
                _isPremium.value = hasPremium
            }
        }
    }

    /**
     * Launches the Google Play billing flow for the given [productId].
     *
     * @param activity   The calling [Activity] needed by the Play Billing Library.
     * @param productId  One of [SUB_YEARLY] or [SUB_WEEKLY].
     */
    fun launchBillingFlow(activity: Activity, productId: String = SUB_YEARLY) {
        if (!billingClient.isReady) {
            showToast(activity, "Billing service not ready yet. Please try again.")
            return
        }

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()

        billingClient.queryProductDetailsAsync(queryParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList.isNotEmpty()
            ) {
                val productDetails = productDetailsList[0]

                // For subscriptions, pick the offer that includes a free trial if available,
                // otherwise fall back to the first available offer.
                val offerDetails = productDetails.subscriptionOfferDetails
                val selectedOffer = offerDetails?.firstOrNull { offer ->
                    offer.pricingPhases.pricingPhaseList.any { phase ->
                        phase.priceAmountMicros == 0L // Free trial phase
                    }
                } ?: offerDetails?.firstOrNull()

                val offerToken = selectedOffer?.offerToken
                if (offerToken == null) {
                    showToast(activity, "No offers available for this product.")
                    return@queryProductDetailsAsync
                }

                val productDetailsParams = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParams)
                    .build()

                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                val msg = if (productDetailsList.isEmpty()) {
                    "Subscription product '$productId' not found. Ensure it is active in Google Play Console."
                } else {
                    "Billing error: ${billingResult.debugMessage}"
                }
                showToast(activity, msg)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                CoroutineScope(Dispatchers.IO).launch {
                    billingClient.acknowledgePurchase(acknowledgeParams) {
                        checkActiveSubscriptions()
                    }
                }
            } else {
                checkActiveSubscriptions()
            }
        }
    }

    private fun showToast(activity: Activity, message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
