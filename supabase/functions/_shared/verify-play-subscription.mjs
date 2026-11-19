import { getPlaySubscription } from "./google-play-api.mjs";
import { evaluateSubscription } from "./play-subscription-evaluator.mjs";

/**
 * Evaluate a token using data fetched directly from Google. This is only a
 * verification primitive: caller authentication, token ownership, entitlement
 * persistence, purchase acknowledgement, and lifecycle notifications must be
 * implemented before connecting it to customer checkout.
 */
export async function verifyPlaySubscription(credentials, purchaseToken, options = {}) {
  const resource = await getPlaySubscription(credentials, purchaseToken, options.fetchFn ?? fetch);
  const result = evaluateSubscription(resource, options.nowMs ?? Date.now());
  return { entitled: result.entitled, expiresAt: result.expiresAt ?? null };
}
