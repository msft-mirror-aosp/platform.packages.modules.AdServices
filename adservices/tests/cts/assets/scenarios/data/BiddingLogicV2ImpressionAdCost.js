/**
 * Returns the bid passed in the user_bidding_signals, if empty then generates
 * a bid of 10 for the shoes CA or
 * a bid of 5 otherwise
 */
function generateBid(ad, auction_signals, per_buyer_signals, trusted_bidding_signals, contextual_signals, custom_audience_signals) {
  var bid = 5;
  if ('user_bidding_signals' in custom_audience_signals && 'bid' in custom_audience_signals.user_bidding_signals) {
      bid = custom_audience_signals.user_bidding_signals.bid;
  }
  if (custom_audience_signals.name === "shoes") {
      bid = 10;
  }
  return {'status': 0, 'ad': ad, 'bid': bid, 'adCost': 1.0 };
}

function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,
 contextual_signals, custom_audience_signals) {
  let reporting_address = '<buyer-reporting-uri>';
  return {'status': 0, 'results': {'reporting_uri':
         reporting_address + '/reportWin?ca=' + custom_audience_signals.name + '?adCost=' + contextual_signals.adCost} };
}
