function scoreAd(ad, bid, auction_config, seller_signals, trusted_scoring_signals,
  contextual_signal, user_signal, custom_audience_scoring_signals) {
  return {'status': 0, 'score': bid };
}
function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {
  // Add the address of your reporting server here
  let reporting_address = '<seller-reporting-uri>';
  return {'status': 0, 'results': {'signals_for_buyer': '{"signals_for_buyer" : 1}'
          , 'reporting_uri': reporting_address + '/reportResult?render_uri='
            + render_uri + '?bid=' + bid } };
}