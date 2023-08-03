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

package com.android.adservices.service.adselection;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;

/** Utility class to fetch the data version from trusted bidding/scoring signals. */
public class DataVersionFetcher {
    public static final String DATA_VERSION_HEADER_KEY = "x-fledge-bidding-signals-format-version";

    @VisibleForTesting static final int MAX_UNSIGNED_8_BIT = 255;

    /**
     * Extracts the data version and returns it if it exists.
     *
     * @param trustedBiddingData contains the {@link Uri} which is used as a key to fetch the
     *     headers for this ad.
     * @param trustedBiddingResponsesByBaseUri contains the trusted bidding headers for this ad
     *     where we may find the data version header.
     * @throws IllegalStateException if the data version does not exist for this combination of
     *     {@link DBTrustedBiddingData#getUri()} and {@code Map<Uri, TrustedBiddingResponse>
     *     trustedBiddingResponsesByBaseUri}, or if {@link TrustedBiddingResponse#getHeaders()} does
     *     not conform to the expected requirements.
     */
    public static int getDataVersion(
            @NonNull DBTrustedBiddingData trustedBiddingData,
            @NonNull Map<Uri, TrustedBiddingResponse> trustedBiddingResponsesByBaseUri)
            throws IllegalStateException {
        if (Objects.isNull(trustedBiddingData)
                || Objects.isNull(
                        trustedBiddingResponsesByBaseUri.get(trustedBiddingData.getUri()))) {
            throw new IllegalStateException("Data Version Header does not exist");
        }
        try {
            JSONObject headers =
                    trustedBiddingResponsesByBaseUri.get(trustedBiddingData.getUri()).getHeaders();
            int dataVersion = headers.getJSONArray(DATA_VERSION_HEADER_KEY).getInt(0);
            if (dataVersion < 0 || dataVersion > MAX_UNSIGNED_8_BIT) {
                LogUtil.v("Header object: " + headers + " does not conform to 8 bit requirements.");
                throw new IllegalStateException(
                        "Data version header does not conform to unsigned 8 bit requirements,");
            }
            return dataVersion;
        } catch (JSONException e) {
            LogUtil.v("Data version header does not conform required header formatting.");
            throw new IllegalStateException(
                    "Data version header does not conform required header formatting.");
        }
    }
}
