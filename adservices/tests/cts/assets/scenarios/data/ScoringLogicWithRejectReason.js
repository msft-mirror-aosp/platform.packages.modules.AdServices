function scoreAd(ad, bid, auction_config, seller_signals, trusted_scoring_signals,
  contextual_signal, custom_audience_signal) {
  forDebuggingOnly.reportAdAuctionWin('<seller-win-debug-reporting-uri>'
      + "?ad_render_uri=" + ad.render_uri
      + "&wb=${winningBid}&madeWb=${madeWinningBid}"
      + "&hob=${highestScoringOtherBid}&madeHob=${madeHighestScoringOtherBid}");
  forDebuggingOnly.reportAdAuctionLoss('<seller-loss-debug-reporting-uri>'
      + "?ad_render_uri=" +ad.render_uri
      + "&wb=${winningBid}&madeWb=${madeWinningBid}"
      + "&hob=${highestScoringOtherBid}&madeHob=${madeHighestScoringOtherBid}"
      + "&rejectReason=${rejectReason}");
  if ('bid_floor' in seller_signals && bid < seller_signals.bid_floor) {
      bid = -1;
  }
  return {
    'status': 0,
    'score' : bid,
    'rejectReason': 'blocked-by-publisher'
  }
}


function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {
  let reporting_address = '<seller-reporting-uri>';
  return {'status': 0, 'results': {'signals_for_buyer': '{"signals_for_buyer" : 1}'
          , 'reporting_uri': reporting_address + '?render_uri='
              + render_uri + '?bid=' + bid } };
}
