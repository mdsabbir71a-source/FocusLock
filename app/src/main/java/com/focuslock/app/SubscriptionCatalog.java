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
import java.util.WeakHashMap;

/** Loads eligible localized pricing from Google Play. Never initiates a purchase. */
final class SubscriptionCatalog {
    private static final WeakHashMap<WebView, Long> REQUESTS = new WeakHashMap<>();
    private static long nextRequest;
    static void load(Activity activity, WebView page) {
        final long requestId;
        synchronized (REQUESTS) { requestId = ++nextRequest; REQUESTS.put(page, requestId); }
        BillingClient client = BillingClient.newBuilder(activity)
                .setListener((result, purchases) -> { })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts().build()).build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    show("Subscription prices are unavailable. Please try again later.", null, null, false, 0); return;
                }
                QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("focuslock_premium")
                        .setProductType(BillingClient.ProductType.SUBS).build();
                QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product)).build();
                client.queryProductDetailsAsync(params, (status, details) -> {
                    String yearly = null, monthly = null;
                    long yearlyMicros = -1, monthlyMicros = -1;
                    String yearlyCurrency = null, monthlyCurrency = null;
                    boolean trial = false;
                    if (status.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (ProductDetails item : details.getProductDetailsList()) {
                            if (!"focuslock_premium".equals(item.getProductId()) || item.getSubscriptionOfferDetails() == null) continue;
                            ProductDetails.SubscriptionOfferDetails selectedYear = null;
                            ProductDetails.SubscriptionOfferDetails selectedMonth = null;
                            int yearlyScore = 0, monthlyScore = 0;
                            for (ProductDetails.SubscriptionOfferDetails offer : item.getSubscriptionOfferDetails()) {
                                int ys = SubscriptionOfferPolicy.score("yearly", offer.getBasePlanId(), offer.getOfferId());
                                int ms = SubscriptionOfferPolicy.score("monthly", offer.getBasePlanId(), offer.getOfferId());
                                if (ys > yearlyScore) { yearlyScore = ys; selectedYear = offer; }
                                if (ms > monthlyScore) { monthlyScore = ms; selectedMonth = offer; }
                            }
                            if (selectedYear != null) {
                                for (ProductDetails.PricingPhase phase : selectedYear.getPricingPhases().getPricingPhaseList()) {
                                    if (phase.getPriceAmountMicros() == 0 && ("P7D".equals(phase.getBillingPeriod()) || "P1W".equals(phase.getBillingPeriod()))) trial = true;
                                    if (phase.getRecurrenceMode() == ProductDetails.RecurrenceMode.INFINITE_RECURRING && "P1Y".equals(phase.getBillingPeriod())) {
                                        yearly = phase.getFormattedPrice() + " / year";
                                        yearlyMicros = phase.getPriceAmountMicros();
                                        yearlyCurrency = phase.getPriceCurrencyCode();
                                    }
                                }
                            }
                            if (selectedMonth != null) {
                                for (ProductDetails.PricingPhase phase : selectedMonth.getPricingPhases().getPricingPhaseList()) {
                                    if (phase.getRecurrenceMode() == ProductDetails.RecurrenceMode.INFINITE_RECURRING && "P1M".equals(phase.getBillingPeriod())) {
                                        monthly = phase.getFormattedPrice() + " / month";
                                        monthlyMicros = phase.getPriceAmountMicros();
                                        monthlyCurrency = phase.getPriceCurrencyCode();
                                    }
                                }
                            }
                        }
                    }
                    int savings = 0;
                    if (monthlyMicros > 0 && yearlyMicros >= 0 && monthlyCurrency != null && monthlyCurrency.equals(yearlyCurrency)) {
                        double fullYear = (double) monthlyMicros * 12.0;
                        savings = Math.max(0, Math.min(100, (int) Math.round(100.0 * (1.0 - yearlyMicros / fullYear))));
                    }
                    show(yearly == null && monthly == null
                            ? "Subscription plans are not available yet." : "", yearly, monthly, trial, savings);
                });
            }
            @Override public void onBillingServiceDisconnected() { show("Google Play is unavailable.", null, null, false, 0); }
            private void show(String status, String yearly, String monthly, boolean trial, int savings) {
                client.endConnection();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    synchronized (REQUESTS) {
                        Long latest = REQUESTS.get(page);
                        if (latest == null || latest.longValue() != requestId) return;
                    }
                    page.evaluateJavascript("window.updatePlayPrices(" + JSONObject.quote(yearly == null ? "Unavailable" : yearly)
                            + "," + JSONObject.quote(monthly == null ? "Unavailable" : monthly)
                            + "," + JSONObject.quote(status) + "," + trial + "," + savings + ")", null);
                });
            }
        });
    }
}
