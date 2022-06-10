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

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.AdSelectionOverrideCallback;

/**
  * This is the Ad Selection Service, which defines the interface used for the Ad selection workflow
  * to orchestrate the on-device execution of
  * 1. Ad selection.
  * 2. Impression reporting.
  *
  * @hide
  */
interface AdSelectionService {
    /**
    * This method orchestrates the buyer and seller side logic to pick the winning ad amongst all
    * the on-device remarketing ad candidates and seller provided contextual ad candidates. It will
    * execuate the ad tech-provided Javascript code based on the following sequence:
    * 1. Buy-side bidding logic execution
    * 2. Buy-side ad filtering and processing(i.e. brand safety check, frequency capping, generated
    *    bid validation etc.)
    * 3. Sell-side decision logic execution to determine a winning ad based on bidding loigic
    * outputs and business logic.
    *
    * The {@link AdSelectionConfig} is provided by the SDK and contains the required information
    * to execute the on-device ad selection and impression reporting.
    *
    * The (@link AdSelectionCallback} returns {@link AdSelectionResponse} if the asynchronous call
    * succeeds.
    * The (@link AdSelectionCallback} returns {@link FledgeErrorResponse} if the asynchronous call
    * fails.
    * If the ad selection is successful, the {@link AdSelectionResponse} contains
    * {@link AdSelectionId} and {@link AdData}
    * If the ad selection fails, the response contains only
    * {@link FledgeErrorResponse#RESULT_INTERNAL_ERROR} if an internal server error is encountered,
    * or {@link FledgeErrorResponse#RESULT_INVALID_ARGUMENT} if invalid
    * argument is provided.
    *
    * Otherwise, this call fails to send the response to the callback and throws a RemoteException.
    *
    * {@hide}
    */
    void runAdSelection(in AdSelectionConfig adSelectionConfig, in AdSelectionCallback callback);

    /**
    * Notifies PPAPI that there is a new impression to report for the
    * ad selected by the ad-selection run identified by {@code adSelectionId}.
    * There is no guarantee about when the event will be reported. The event
    * reporting could be delayed and events could be batched.
    *
    * The call will fail with a status of
    * {@link FledgeErrorResponse#STATUS_INVALID_ARGUMENT} if there is no
    * auction matching the provided {@link ReportImpressionInput#getAdSelectionId()} or if
    * the supplied {@link ReportImpressionInput#getAdSelectionConfig()} is invalid.
    * The call will fail with status
    * {@link FledgeErrorResponse#STATUS_INTERNAL_ERROR} if an
    * internal server error is encountered.
    *
    * The reporting guarantee is at-most-once, any error during the connection to
    * the seller and/or buyer reporting URLs might be retried but we won't
    * guarantee the completion.
    *
    * {@hide}
    */
    void reportImpression(
        in ReportImpressionInput request,
        in ReportImpressionCallback callback);

   /**
    * This method is intended to be called before {@code runAdSelection}
    * and {@code reportImpression} using the same
    * {@link AdSelectionConfig} in order to configure
    * PPAPI to avoid to fetch info from remote servers and use the
    * data provided.
    *
    * The call will throw an IllegalStateException if the API hasn't been enabled
    * by developer options or by an adb command or if the calling
    * application manifest is not setting Android:debuggable to true.
    */
    void overrideAdSelectionConfigRemoteInfo(
        in AdSelectionConfig adSelectionConfig,
        in String decisionLogicJS,
        in AdSelectionOverrideCallback callback);

   /**
    * Deletes any override created by calling
    * {@code overrideAdSelectionConfigRemoteInfo} for the given
    * AdSelectionConfig
    *
    * The call will throw an IllegalStateException if:
    * the API hasn't been enabled by developer options or by an adb command
    * or if the calling application manifest is not setting Android:debuggable to true.
    */
    void removeAdSelectionConfigRemoteInfoOverride(
        in AdSelectionConfig adSelectionConfig,
        in AdSelectionOverrideCallback callback);

   /**
    * Deletes any override created by calling
    * {@code overrideAdSelectionConfigRemoteInfo} from this application
    *
    * The call will throw an IllegalStateException if:
    * the API hasn't been enabled by developer options or by an adb command
    * or if the calling application manifest is not setting Android:debuggable to true.
    */
    void resetAllAdSelectionConfigRemoteOverrides(
        in AdSelectionOverrideCallback callback);
}
