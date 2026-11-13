package com.focuslock.app;
public class SubscriptionOfferPolicyTest {
    private static void check(int actual, int expected) {
        if (actual != expected) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) {
        check(SubscriptionOfferPolicy.score("monthly", "monthly", null), 1);
        check(SubscriptionOfferPolicy.score("yearly", "yearly", null), 1);
        check(SubscriptionOfferPolicy.score("yearly", "yearly", "yearly-7-day-trial"), 2);
        check(SubscriptionOfferPolicy.score("monthly", "monthly", "unknown"), 0);
        check(SubscriptionOfferPolicy.score("yearly", "yearly", "unknown"), 0);
        check(SubscriptionOfferPolicy.score("yearly", "monthly", null), 0);
        check(SubscriptionOfferPolicy.score("weekly", "weekly", null), 0);
        System.out.println("Subscription offer policy tests passed.");
    }
}
