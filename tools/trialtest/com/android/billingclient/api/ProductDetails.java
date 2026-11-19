package com.android.billingclient.api;
import java.util.List;
public class ProductDetails {
 public static class RecurrenceMode { public static final int INFINITE_RECURRING=1; }
 public static class PricingPhase { long price; int count, mode; String period; public PricingPhase(long p,int c,int m,String d){price=p;count=c;mode=m;period=d;} public long getPriceAmountMicros(){return price;} public int getBillingCycleCount(){return count;} public int getRecurrenceMode(){return mode;} public String getBillingPeriod(){return period;} }
 public static class PricingPhases { List<PricingPhase> phases; public PricingPhases(List<PricingPhase> p){phases=p;} public List<PricingPhase> getPricingPhaseList(){return phases;} }
 public static class SubscriptionOfferDetails { String base,id; PricingPhases phases; public SubscriptionOfferDetails(String b,String i,PricingPhases p){base=b;id=i;phases=p;} public String getBasePlanId(){return base;} public String getOfferId(){return id;} public PricingPhases getPricingPhases(){return phases;} }
}
