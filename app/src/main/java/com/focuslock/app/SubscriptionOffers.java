package com.focuslock.app;

import com.android.billingclient.api.ProductDetails;

/** Select a seven-day free yearly offer only when Play returns it as eligible. */
final class SubscriptionOffers {
    private SubscriptionOffers() {}

    static int score(String wantedPlan, ProductDetails.SubscriptionOfferDetails offer) {
        if (!wantedPlan.equals(offer.getBasePlanId())) return 0;
        if ("yearly".equals(wantedPlan) && offer.getPricingPhases().getPricingPhaseList().size() == 2) {
            boolean freeWeek = false;
            boolean recurringYear = false;
            for (ProductDetails.PricingPhase phase : offer.getPricingPhases().getPricingPhaseList()) {
                if (phase.getPriceAmountMicros() == 0 && phase.getBillingCycleCount() == 1 &&
                        ("P7D".equals(phase.getBillingPeriod()) || "P1W".equals(phase.getBillingPeriod()))) {
                    freeWeek = true;
                }
                if (phase.getPriceAmountMicros() > 0 &&
                        phase.getRecurrenceMode() == ProductDetails.RecurrenceMode.INFINITE_RECURRING &&
                        "P1Y".equals(phase.getBillingPeriod())) {
                    recurringYear = true;
                }
            }
            if (freeWeek && recurringYear) return 2;
        }
        return offer.getOfferId() == null ? 1 : 0;
    }
}
