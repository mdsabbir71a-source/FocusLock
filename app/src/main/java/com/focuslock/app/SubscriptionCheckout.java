package com.focuslock.app;

import android.app.Activity;
import android.app.AlertDialog;
import com.android.billingclient.api.*;
import java.util.Collections;

/** Checkout remains disabled until verified entitlements are configured and tested. */
final class SubscriptionCheckout {
    static void start(Activity activity, String planId) {
        if (!verifiedEntitlementsReady()) {
            new AlertDialog.Builder(activity).setTitle("Subscriptions are not ready")
                    .setMessage("Purchases are not available yet. Please try again later.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        openPlayCheckout(activity, planId);
    }

    private static boolean verifiedEntitlementsReady() {
        return false;
    }

    private static void openPlayCheckout(Activity activity, String planId) {
        BillingClient client = BillingClient.newBuilder(activity)
                .setListener((result, purchases) -> { /* Verification and acknowledgement required. */ })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult setup) {
                if (setup.getResponseCode() != BillingClient.BillingResponseCode.OK) { client.endConnection(); return; }
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("focuslock_premium").setProductType(BillingClient.ProductType.SUBS).build();
                QueryProductDetailsParams query = QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product)).build();
                client.queryProductDetailsAsync(query, (result, response) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) { client.endConnection(); return; }
                    for (ProductDetails details : response.getProductDetailsList()) {
                        if (details.getSubscriptionOfferDetails() == null) continue;
                        ProductDetails.SubscriptionOfferDetails chosen = null;
                        for (ProductDetails.SubscriptionOfferDetails offer : details.getSubscriptionOfferDetails()) {
                            if (!planId.equals(offer.getBasePlanId())) continue;
                            if (chosen == null || ("yearly-7-day-trial".equals(offer.getOfferId()))) chosen = offer;
                        }
                        if (chosen == null) continue;
                        BillingFlowParams.ProductDetailsParams item = BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details).setOfferToken(chosen.getOfferToken()).build();
                        BillingFlowParams flow = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(Collections.singletonList(item)).build();
                        activity.runOnUiThread(() -> client.launchBillingFlow(activity, flow));
                        return;
                    }
                    client.endConnection();
                });
            }
            @Override public void onBillingServiceDisconnected() { client.endConnection(); }
        });
    }
}
