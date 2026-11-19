package com.focuslock.app;
import java.util.Arrays;
import com.android.billingclient.api.ProductDetails.*;
public class TrialTest {
 static SubscriptionOfferDetails offer(String base,String id,PricingPhase...p){return new SubscriptionOfferDetails(base,id,new PricingPhases(Arrays.asList(p)));}
 static void check(int actual,int expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){
  PricingPhase week=new PricingPhase(0,1,2,"P7D"), year=new PricingPhase(39990000,0,1,"P1Y"), extra=new PricingPhase(5000000,1,2,"P1M");
  check(SubscriptionOffers.score("yearly",offer("yearly","custom-id",week,year)),2);
  check(SubscriptionOffers.score("yearly",offer("yearly",null,year)),1);
  check(SubscriptionOffers.score("yearly",offer("yearly","custom-id",week,extra,year)),0);
  check(SubscriptionOffers.score("monthly",offer("yearly","custom-id",week,year)),0);
  check(SubscriptionOffers.score("monthly",offer("monthly",null,extra)),1);
  System.out.println("Offer selection tests passed");
 }
}
