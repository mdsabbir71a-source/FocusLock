import { strict as assert } from "node:assert";
import { evaluateSubscription } from "../supabase/functions/_shared/play-subscription-evaluator.mjs";
const now = Date.parse("2026-11-10T00:00:00Z");
const line = { productId:"focuslock_premium", offerDetails:{ basePlanId:"yearly" }, expiryTime:"2026-11-11T00:00:00Z" };
for (const state of ["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"])
  assert.equal(evaluateSubscription({ subscriptionState:state, lineItems:[line] }, now).entitled, true);
for (const state of ["SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED", "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING"])
  assert.equal(evaluateSubscription({ subscriptionState:state, lineItems:[line] }, now).entitled, false);
assert.equal(evaluateSubscription({ subscriptionState:"SUBSCRIPTION_STATE_ACTIVE", lineItems:[{...line, expiryTime:"2026-11-09T00:00:00Z"}] }, now).entitled, false);
assert.equal(evaluateSubscription({ subscriptionState:"SUBSCRIPTION_STATE_ACTIVE", lineItems:[{...line, productId:"other"}] }, now).entitled, false);
assert.equal(evaluateSubscription({ subscriptionState:"SUBSCRIPTION_STATE_ACTIVE", lineItems:[{...line, offerDetails:{basePlanId:"weekly"}}] }, now).entitled, false);
console.log("Subscription entitlement state tests passed.");
