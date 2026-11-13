// Pure interpretation of Google Play SubscriptionPurchaseV2 responses.
// This does not contact Google or grant access by itself.
export function evaluateSubscription(subscription, nowMs = Date.now()) {
  const states = new Set(["SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED"]);
  if (!subscription || !states.has(subscription.subscriptionState)) return { entitled: false };
  const lines = Array.isArray(subscription.lineItems) ? subscription.lineItems : [];
  const eligible = lines.filter((item) =>
    item.productId === "focuslock_premium" &&
    ["monthly", "yearly"].includes(item.offerDetails?.basePlanId) &&
    Number.isFinite(Date.parse(item.expiryTime)) &&
    Date.parse(item.expiryTime) > nowMs
  );
  if (!eligible.length) return { entitled: false };
  const expiry = Math.max(...eligible.map((item) => Date.parse(item.expiryTime)));
  return { entitled: true, expiresAt: new Date(expiry).toISOString() };
}
