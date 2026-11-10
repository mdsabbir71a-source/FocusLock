package com.focuslock.app;

import android.app.Activity;
import android.webkit.WebView;
import org.json.JSONObject;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.QueryProductDetailsParams;
import java.util.Collections;

/** Loads eligible localized pricing from Google Play. Never initiates a purchase. */
final class SubscriptionCatalog {
    static void load(Activity activity, WebView page) {
        BillingClient client = BillingClient.newBuilder(activity)
                .setListener((result, purchases) -> { })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts().build()).build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    show("Subscription prices are unavailable. Please try again later.", null, null); return;
                }
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("focuslock_premium")
                        .setProductType(BillingClient.ProductType.SUBS).build();
                QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product)).build();
                client.queryProductDetailsAsync(params, (status, details) -> {
                    String yearly = null, monthly = null;
                    if (status.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (ProductDetails item : details.getProductDetailsList()) {
                            if (item.getSubscriptionOfferDetails() == null) continue;
                            for (ProductDetails.SubscriptionOfferDetails offer : item.getSubscriptionOfferDetails()) {
                                if (offer.getOfferId() != null) continue;
                                for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList()) {
                                    if ("yearly".equals(offer.getBasePlanId())) yearly = phase.getFormattedPrice() + " / year";
                                    if ("monthly".equals(offer.getBasePlanId())) monthly = phase.getFormattedPrice() + " / month";
                                }
                            }
                        }
                    }
                    show(yearly == null && monthly == null
                            ? "Subscription plans are not available yet." : "Prices provided by Google Play.", yearly, monthly);
                });
            }
            @Override public void onBillingServiceDisconnected() { show("Google Play is unavailable.", null, null); }
            private void show(String status, String yearly, String monthly) {
                client.endConnection();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    page.evaluateJavascript("window.updatePlayPrices(" + JSONObject.quote(yearly == null ? "Unavailable" : yearly)
                            + "," + JSONObject.quote(monthly == null ? "Unavailable" : monthly)
                            + "," + JSONObject.quote(status) + ")", null);
                });
            }
        });
    }
}
