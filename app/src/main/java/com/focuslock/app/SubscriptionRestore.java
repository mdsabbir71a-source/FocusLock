package com.focuslock.app;

import android.app.Activity;
import android.app.AlertDialog;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryPurchasesParams;

/** Looks up existing Play subscriptions; entitlement verification is not configured yet. */
final class SubscriptionRestore {
    private static final String PRODUCT_ID = "focuslock_premium";

    static void start(Activity activity) {
        BillingClient client = BillingClient.newBuilder(activity)
                .setListener((result, purchases) -> { })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts().build())
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    finish("Google Play could not be reached. Check your connection and try again.");
                    return;
                }
                QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build();
                client.queryPurchasesAsync(params, (queryResult, purchases) -> {
                    if (queryResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        finish("Google Play could not check your subscriptions. Please try again.");
                        return;
                    }
                    boolean found = false;
                    boolean pending = false;
                    for (Purchase purchase : purchases) {
                        if (!purchase.getProducts().contains(PRODUCT_ID)) continue;
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) found = true;
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) pending = true;
                    }
                    if (found) finish("A FocusLock subscription was found, but restoring premium access is not available yet. Please contact support.");
                    else if (pending) finish("Your subscription purchase is still pending. Please try again after payment is complete.");
                    else finish("No active FocusLock subscription was found on this Google Play account.");
                });
            }
            @Override public void onBillingServiceDisconnected() {
                finish("Google Play disconnected. Check your connection and try again.");
            }
            private void finish(String message) {
                client.endConnection();
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed())
                        new AlertDialog.Builder(activity).setTitle("Restore purchases")
                                .setMessage(message).setPositiveButton("OK", null).show();
                });
            }
        });
    }
}
