/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function generateBid(ad, auction_signals, per_buyer_signals,
  trusted_bidding_signals, contextual_signals, custom_audience_bidding_signals) {
  var bid = 5;
  if (custom_audience_bidding_signals.name === "shoes") {
    bid = 10;
  }
  return {'status': 0, 'ad': ad, 'bid': bid };
}
function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,
 contextual_signals, custom_audience_reporting_signals) {
  // Add the address of your reporting server here
  let reporting_address = '<buyer-reporting-uri>';
  // Register beacons
  let clickUri = reporting_address + '/buyerInteraction?click';
  const beacons = {'click': clickUri}
  registerAdBeacon(beacons)
  return {'status': 0, 'results': {'reporting_uri':
         reporting_address + '/reportWin?ca=' + custom_audience_reporting_signals.name} };
}