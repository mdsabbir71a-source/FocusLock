package com.focuslock.app;

/** Chooses only the configured base plans and the intended yearly trial. */
final class SubscriptionOfferPolicy {
    private SubscriptionOfferPolicy() {}
    static int score(String wantedPlan, String basePlan, String offerId) {
        if (!"monthly".equals(wantedPlan) && !"yearly".equals(wantedPlan)) return 0;
        if (!wantedPlan.equals(basePlan)) return 0;
        if ("yearly".equals(wantedPlan) && "yearly-7-day-trial".equals(offerId)) return 2;
        return offerId == null ? 1 : 0;
    }
}
