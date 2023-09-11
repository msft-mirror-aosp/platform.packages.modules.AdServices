function generateBid(ad, auction_signals, per_buyer_signals,
  trusted_bidding_signals, contextual_signals, custom_audience_bidding_signals) {
  var bid = 5;
  if (custom_audience_bidding_signals.name === "shoes") {
    bid = 10;
  }
  return {'status': 0, 'ad': ad, 'bid': bid, 'adCost': 1.0 };
}
function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,
 contextual_signals, custom_audience_reporting_signals) {
  // Add the address of your reporting server here
  let reporting_address = '<buyer-reporting-uri>';
  // Register beacons
  let clickUri = reporting_address + '/buyerInteraction?click?adCost=' + contextual_signals.adCost;
  let viewUri = reporting_address + '/buyerInteraction?view';
  const beacons = {'click': clickUri, 'view': viewUri}
  registerAdBeacon(beacons)
  return {'status': 0, 'results': {'reporting_uri':
         reporting_address + '/reportWin?ca=' + custom_audience_reporting_signals.name} };
}