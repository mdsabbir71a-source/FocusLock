package com.focuslock.app;

import com.android.billingclient.api.ProductDetails;

/** Select a three-day monthly trial only when Play returns it as eligible. */
final class SubscriptionOffers {
    private SubscriptionOffers() {}

    static int score(String wantedPlan, ProductDetails.SubscriptionOfferDetails offer) {
        if (!wantedPlan.equals(offer.getBasePlanId())) return 0;
        if ("monthly-5".equals(wantedPlan) && offer.getPricingPhases().getPricingPhaseList().size() == 2) {
            boolean freeWeek = false;
            boolean recurringYear = false;
            for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList()) {
                if (phase.getPriceAmountMicros() == 0 && phase.getBillingCycleCount() == 1 &&
                        "P3D".equals(phase.getBillingPeriod())) {
                    freeWeek = true;
                }
                if (phase.getPriceAmountMicros() > 0 &&
                        phase.getRecurrenceMode() == ProductDetails.RecurrenceMode.INFINITE_RECURRING &&
                        "P1M".equals(phase.getBillingPeriod())) {
                    recurringYear = true;
                }
            }
            if (freeWeek && recurringYear) return 2;
        }
        return offer.getOfferId() == null ? 1 : 0;
    }
}
