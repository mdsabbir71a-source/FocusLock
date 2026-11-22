package com.focuslock.app;
import android.app.Activity;
import android.webkit.WebView;
import org.json.JSONObject;
import com.android.billingclient.api.*;
import java.util.Collections;
import java.util.WeakHashMap;

final class SubscriptionCatalog {
    private static final WeakHashMap<WebView, Long> REQUESTS = new WeakHashMap<>();
    private static long nextRequest;
    static void load(Activity activity, WebView page) {
        final long requestId;
        synchronized (REQUESTS) { requestId = ++nextRequest; REQUESTS.put(page, requestId); }
        BillingClient client = BillingClient.newBuilder(activity).setListener((r,p)->{})
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build();
        client.startConnection(new BillingClientStateListener() {
            String monthly = null, lifetime = null;
            boolean trial = false, ended = false;
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) { show("Google Play is unavailable."); return; }
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder().setProductId("focuslock_premium").setProductType(BillingClient.ProductType.SUBS).build();
                client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(product)).build(), (status, response) -> {
                    if (status.getResponseCode() == BillingClient.BillingResponseCode.OK) for (ProductDetails detail : response.getProductDetailsList()) {
                        if (!"focuslock_premium".equals(detail.getProductId()) || detail.getSubscriptionOfferDetails() == null) continue;
                        ProductDetails.SubscriptionOfferDetails selected = null; int best = 0;
                        for (ProductDetails.SubscriptionOfferDetails offer : detail.getSubscriptionOfferDetails()) {
                            int score = SubscriptionOffers.score("monthly-5", offer);
                            if (score > best) { best = score; selected = offer; }
                        }
                        if (selected != null) for (ProductDetails.PricingPhase phase : selected.getPricingPhases().getPricingPhaseList()) {
                            if (phase.getPriceAmountMicros() == 0 && "P3D".equals(phase.getBillingPeriod())) trial = true;
                            if (phase.getPriceAmountMicros() > 0 && phase.getRecurrenceMode() == ProductDetails.RecurrenceMode.INFINITE_RECURRING && "P1M".equals(phase.getBillingPeriod())) monthly = phase.getFormattedPrice();
                        }
                    }
                    QueryProductDetailsParams.Product oneTime = QueryProductDetailsParams.Product.newBuilder().setProductId("focuslock_lifetime").setProductType(BillingClient.ProductType.INAPP).build();
                    client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(oneTime)).build(), (status2, response2) -> {
                        if (status2.getResponseCode() == BillingClient.BillingResponseCode.OK) for (ProductDetails detail : response2.getProductDetailsList()) {
                            if (!"focuslock_lifetime".equals(detail.getProductId())) continue;
                            ProductDetails.OneTimePurchaseOfferDetails offer = detail.getOneTimePurchaseOfferDetails();
                            if (offer != null) lifetime = offer.getFormattedPrice();
                        }
                        show(monthly == null && lifetime == null ? "Prices unavailable. Please try again." : "");
                    });
                });
            }
            @Override public void onBillingServiceDisconnected() { show("Google Play disconnected. Please try again."); }
            private synchronized void show(String status) {
                if (ended) return;
                ended = true; client.endConnection();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    synchronized (REQUESTS) { Long latest = REQUESTS.get(page); if (latest == null || latest.longValue() != requestId) return; }
                    page.evaluateJavascript("window.updatePlayPrices(" + JSONObject.quote(lifetime == null ? "" : lifetime)
                        + "," + JSONObject.quote(monthly == null ? "" : monthly)
                        + "," + JSONObject.quote(status) + "," + trial + ")", null);
                });
            }
        });
    }
}
