package com.focuslock.app;

import android.content.Context;
import com.android.billingclient.api.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import android.os.Handler;
import android.os.Looper;

/** Refreshes existing verified access without interrupting the user. */
final class SubscriptionRefresh {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final Object CALLBACK_LOCK = new Object();
    private static final ArrayList<Runnable> CALLBACKS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void refresh(Context context) { refresh(context, null); }

    static void refresh(Context context, Runnable whenFinished) {
        Context app = context.getApplicationContext();
        synchronized (CALLBACK_LOCK) {
            if (!PremiumEntitlementStore.hasStoredAccess(app) && !IN_FLIGHT.get()) {
                if (whenFinished != null) MAIN.post(whenFinished);
                return;
            }
            if (whenFinished != null) CALLBACKS.add(whenFinished);
            if (!IN_FLIGHT.compareAndSet(false, true)) return;
        }
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
            private void done() {
                synchronized (CALLBACK_LOCK) {
                    if (!IN_FLIGHT.getAndSet(false)) return;
                    client.endConnection();
                    ArrayList<Runnable> pending = new ArrayList<>(CALLBACKS);
                    CALLBACKS.clear();
                    for (Runnable callback : pending) MAIN.post(callback);
                }
            }
        });
    }
}
