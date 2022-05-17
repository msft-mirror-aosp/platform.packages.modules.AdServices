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

package android.adservices.customaudience;

import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.net.Uri;

/**
  * Custom audience service.
  *
  * @hide
  */
interface ICustomAudienceService {
    /**
     * Adds the user to the given {@link CustomAudience}.
     *
     * An attempt to register the user for a custom audience with the same combination of owner,
     * buyer, and name will cause the existing custom audience's information to be overwritten,
     * including the list of ads data.
     *
     * Note that the ads list will also be completely overwritten by the daily background fetch job.
     *
     * This call fails with a status of
     * {@link FledgeErrorResponse#STATUS_INVALID_ARGUMENT} if the call comes from an
     * unauthorized party, if the storage limit has been exceeded by the calling party, or if any
     * URL parameters in the {@code customAudience} given are not authenticated with the
     * {@code customAudience} buyer.
     *
     * This call fails with a status of
     * {@link FledgeErrorResponse#STATUS_INTERNAL_ERROR} if an internal service error
     * is encountered.
     */
    void joinCustomAudience(in CustomAudience customAudience, in ICustomAudienceCallback callback);

    /**
     * Attempts to remove a user from a given custom audience, identified by {@code owner},
     * {@code buyer}, and {@code name}.
     *
     * This call does not communicate errors back to the caller, regardless of whether the custom
     * audience specified existed in on-device storage or not, and regardless of whether the
     * caller was authorized to leave the custom audience.
     */
    void leaveCustomAudience(in String owner, in String buyer, in String name,
            in ICustomAudienceCallback callback);

    /**
     * Configures PP api to avoid fetching the biddingLogicJS and trustedBiddingData from a server and instead
     * use the content provided in {@code biddingLogicJS} and {@code trustedBiddingData} for the CA
     * identified by {@code owner}, {@code buyer}, {@code name}
     *
     * The call will fail with status
     * {@link FledgeErrorResponse#STATUS_INTERNAL_ERROR} if:
     * the API hasn't been enabled by developer options or by an adb command
     * or if the calling application manifest is not setting Android:debuggable to true.
     * or if the CA hasn't been created by the same app doing invoking this API.
     *
     * The call will fail silently if the CustomAudience has been created by a different app.
     */
    void overrideCustomAudienceRemoteInfo(
        in String owner,
        in String buyer,
        in String name,
        in String biddingLogicJS,
        in String trustedBiddingData,
        in CustomAudienceOverrideCallback callback);

    /**
     * Deletes any override created by calling
     * {@code overrideCustomAudienceRemoteInfo} for the CA identified by
     * {@code owner} {@code buyer}, {@code name}.
     *
     * The call will fail with status
     * {@link FledgeErrorResponse#STATUS_INTERNAL_ERROR} if:
     * the API hasn't been enabled by developer options or by an adb command
     * or if the calling application manifest is not setting Android:debuggable to true.
     *
     * The call will fail silently if the CustomAudience has been created by a different app.
     */
    void removeCustomAudienceRemoteInfoOverride(
        in String owner,
        in String buyer,
        in String name,
        in CustomAudienceOverrideCallback callback);

    /**
     * Deletes any override created by calling
     * {@code overrideCustomAudienceRemoteInfo} from this application.
     *
     * The call will fail with status
     * {@link FledgeErrorResponse#STATUS_INTERNAL_ERROR} if the API hasn't been enabled
     * by developer options or by an adb command and if the calling
     * application manifest is not setting Android:debuggable to true.
     */
    void resetAllCustomAudienceOverrides(
        in CustomAudienceOverrideCallback callback);
}
