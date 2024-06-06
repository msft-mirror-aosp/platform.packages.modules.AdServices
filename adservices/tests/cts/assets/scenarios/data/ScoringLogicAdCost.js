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

function scoreAd(ad, bid, auction_config, seller_signals, trusted_scoring_signals,
  contextual_signal, custom_audience_scoring_signals) {
  return {'status': 0, 'score': bid };
}
function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {
  // Add the address of your reporting server here
  let reporting_address = '<seller-reporting-uri>';
  return {'status': 0, 'results': {'signals_for_buyer': '{"signals_for_buyer" : 1}'
          , 'reporting_uri': reporting_address + '/reportResult?render_uri='
            + render_uri + '?bid=' + bid } };
}