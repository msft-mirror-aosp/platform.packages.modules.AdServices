/*
  * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.adselection;

import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.AdSelectionConfig;

/**
  * This is the Ad Selection Service, which defines the interface used for both reporting impressions and adselection.
  *
  * {@hide}
  *
  */
interface AdSelectionService {
/**
  * Notifies PPAPI that there is a new impression to report for the
  * ad selected by the ad-selection run identified by {@code adSelectionId}.
  * There is no guarantee about when the event will be reported. The event
  * reporting could be delayed and events could be batched.
  *
  * The call will fail with a status of
  * {@link ReportImpressionResponse#STATUS_INVALID_ARGUMENT} if there is no
  * auction matching the provided {@link ReportImpressionRequest#getAdSelectionId()} or if
  * the supplied {@link ReportImpressionRequest#getAdSelectionConfig()} is invalid.
  * The call will fail with status
  * {@link ReportImpressionResponse#STATUS_INTERNAL_ERROR} if an
  * internal server error is encountered.
  *
  * The reporting guarantee is at-most-once, any error during the connection to
  * the seller and/or buyer reporting URLs might be retried but we won't
  * guarantee the completion.
  *
  * {@hide}
  *
  */
    void reportImpression(
            in ReportImpressionRequest request,
            in ReportImpressionCallback callback);
}
