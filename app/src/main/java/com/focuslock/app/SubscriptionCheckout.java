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

    private static void message(Activity activity, String text) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed())
                new AlertDialog.Builder(activity).setTitle("FocusLock Premium")
                        .setMessage(text).setPositiveButton("OK", null).show();
        });
    }

    private static void openPlayCheckout(Activity activity, String planId) {
        final BillingClient[] holder = new BillingClient[1];
        BillingClient client = BillingClient.newBuilder(activity)
                .setListener((result, purchases) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) return;
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) {
                        message(activity, "Google Play could not complete the purchase. Please try again.");
                        return;
                    }
                    for (Purchase purchase : purchases) {
                        if (!purchase.getProducts().contains("focuslock_premium")) continue;
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                            message(activity, "Your payment is pending. Premium will be available once verified.");
                            continue;
                        }
                        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                        PlayPurchaseVerifier.verify(activity, purchase.getPurchaseToken(), (verified, expires, error) -> {
                            if (!verified) { message(activity, error == null ? "Could not verify your subscription." : error); return; }
                            PremiumEntitlementStore.recordVerified(activity, expires);
                            if (purchase.isAcknowledged()) { message(activity, "Premium access is active."); return; }
                            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.getPurchaseToken()).build();
                            holder[0].acknowledgePurchase(params, ack -> {
                                if (ack.getResponseCode() == BillingClient.BillingResponseCode.OK)
                                    message(activity, "Premium access is active.");
                                else {
                                    PremiumEntitlementStore.clear(activity);
                                    message(activity, "Could not confirm your purchase. Please try Restore purchases.");
                                }
                            });
                        });
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        holder[0] = client;
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult setup) {
                if (setup.getResponseCode() != BillingClient.BillingResponseCode.OK) { client.endConnection(); message(activity, "Google Play is unavailable. Please try again."); return; }
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("focuslock_premium").setProductType(BillingClient.ProductType.SUBS).build();
                QueryProductDetailsParams query = QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product)).build();
                client.queryProductDetailsAsync(query, (result, response) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) { client.endConnection(); message(activity, "Could not load subscription plans. Please try again."); return; }
                    for (ProductDetails details : response.getProductDetailsList()) {
                        if (details.getSubscriptionOfferDetails() == null) continue;
                        ProductDetails.SubscriptionOfferDetails chosen = null;
                        int bestScore = 0;
                        for (ProductDetails.SubscriptionOfferDetails offer : details.getSubscriptionOfferDetails()) {
                            int score = SubscriptionOfferPolicy.score(planId, offer.getBasePlanId(), offer.getOfferId());
                            if (score > bestScore) { bestScore = score; chosen = offer; }
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
                    message(activity, "This subscription plan is not available on your Google Play account.");
                });
            }
            @Override public void onBillingServiceDisconnected() { client.endConnection(); }
        });
    }
}
