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
import android.net.Uri;

/**
  * Custom audience management interface.
  *
  * @hide
  */
interface ICustomAudienceManagementService {
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
}
