package com.focuslock.app;

import android.content.Context;
import com.android.billingclient.api.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Refreshes existing verified access without interrupting the user. */
final class SubscriptionRefresh {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    static void refresh(Context context) {
        Context app = context.getApplicationContext();
        if (!PremiumEntitlementStore.hasStoredAccess(app) || !IN_FLIGHT.compareAndSet(false, true)) return;
        final BillingClient client = BillingClient.newBuilder(app)
                .setListener((result, purchases) -> { })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult setup) {
                if (setup.getResponseCode() != BillingClient.BillingResponseCode.OK) { done(); return; }
                QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build();
                client.queryPurchasesAsync(params, (result, purchases) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) { done(); return; }
                    Purchase candidate = null;
                    for (Purchase purchase : purchases) {
                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED
                                && purchase.getProducts().contains("focuslock_premium")) {
                            candidate = purchase; break;
                        }
                    }
                    // A successful Play query without an active purchase revokes cached access.
                    if (candidate == null) { PremiumEntitlementStore.clear(app); done(); return; }
                    Purchase current = candidate;
                    PlayPurchaseVerifier.verify(app, current.getPurchaseToken(), (verified, expiry, error) -> {
                        if (!verified) {
                            // A definite negative result revokes access; a network
                            // failure preserves the limited offline grace period.
                            if ("No active subscription was verified.".equals(error))
                                PremiumEntitlementStore.clear(app);
                            done();
                            return;
                        }
                        PremiumEntitlementStore.recordVerified(app, expiry);
                        if (current.isAcknowledged()) { done(); return; }
                        AcknowledgePurchaseParams ack = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(current.getPurchaseToken()).build();
                        client.acknowledgePurchase(ack, response -> {
                            if (response.getResponseCode() != BillingClient.BillingResponseCode.OK)
                                PremiumEntitlementStore.clear(app);
                            done();
                        });
                    });
                });
            }
            @Override public void onBillingServiceDisconnected() { done(); }
            private void done() { client.endConnection(); IN_FLIGHT.set(false); }
        });
    }
}
