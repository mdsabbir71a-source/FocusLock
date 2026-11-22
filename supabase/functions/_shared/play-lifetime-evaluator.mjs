/** Accept only a completed, non-rental purchase of FocusLock lifetime access. */
export function evaluateLifetimePurchase(resource) {
  if (resource?.purchaseStateContext?.purchaseState !== "PURCHASED" || !Array.isArray(resource.productLineItem)) return false;
  return resource.productLineItem.some(item => {
    const offer = item?.productOfferDetails;
    return item?.productId === "focuslock_lifetime"
      && offer?.purchaseOptionId === "lifetime"
      && offer?.consumptionState === "CONSUMPTION_STATE_YET_TO_BE_CONSUMED"
      && !offer?.rentOfferDetails
      && Number.isInteger(offer?.refundableQuantity)
      && offer.refundableQuantity > 0;
  });
}
