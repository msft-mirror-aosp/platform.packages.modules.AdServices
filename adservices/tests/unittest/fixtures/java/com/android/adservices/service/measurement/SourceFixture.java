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

package com.android.adservices.service.measurement;

import android.net.Uri;

import com.android.adservices.LogUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;

public final class SourceFixture {
    private SourceFixture() { }

    // Assume the field values in this Source.Builder have no relation to the field values in
    // {@link ValidSourceParams}
    public static Source.Builder getValidSourceBuilder() {
        return new Source.Builder()
            .setPublisher(ValidSourceParams.sPublisher)
            .setAttributionDestination(ValidSourceParams.sAttributionDestination)
            .setAdTechDomain(ValidSourceParams.sAdTechDomain)
            .setRegistrant(ValidSourceParams.sRegistrant);
    }

    // Assume the field values in this Source have no relation to the field values in
    // {@link ValidSourceParams}
    public static Source getValidSource() {
        return new Source.Builder()
                .setEventId(ValidSourceParams.sSourceEventId)
                .setPublisher(ValidSourceParams.sPublisher)
                .setAttributionDestination(ValidSourceParams.sAttributionDestination)
                .setAdTechDomain(ValidSourceParams.sAdTechDomain)
                .setRegistrant(ValidSourceParams.sRegistrant)
                .setEventTime(ValidSourceParams.sSourceEventTime)
                .setExpiryTime(ValidSourceParams.sExpiryTime)
                .setPriority(ValidSourceParams.sPriority)
                .setSourceType(ValidSourceParams.sSourceType)
                .setInstallAttributionWindow(ValidSourceParams.sInstallAttributionWindow)
                .setInstallCooldownWindow(ValidSourceParams.sInstallCooldownWindow)
                .setAttributionMode(ValidSourceParams.sAttributionMode)
                .setAggregateSource(ValidSourceParams.buildAggregateSource())
                .setAggregateFilterData(ValidSourceParams.buildAggregateFilterData())
                .build();
    }

    public static class ValidSourceParams {
        public static Long sExpiryTime = 8640000010L;
        public static Long sPriority = 100L;
        public static Long sSourceEventId = 1L;
        public static Long sSourceEventTime = 8640000000L;
        public static Uri sAttributionDestination = Uri.parse("android-app://com.destination");
        public static Uri sPublisher = Uri.parse("android-app://com.publisher");
        public static Uri sRegistrant = Uri.parse("android-app://com.registrant");
        public static Uri sAdTechDomain = Uri.parse("https://com.example");
        public static Source.SourceType sSourceType = Source.SourceType.EVENT;
        public static Long sInstallAttributionWindow = 841839879274L;
        public static Long sInstallCooldownWindow = 8418398274L;
        public static @Source.AttributionMode int sAttributionMode =
                Source.AttributionMode.TRUTHFULLY;
        public static int sAggregateContributions = 0;

        public static String buildAggregateSource() {
            try {
                JSONArray aggregatableSource = new JSONArray();
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", "campaignCounts");
                jsonObject.put("key_piece", "0x159");
                aggregatableSource.put(jsonObject);
                return aggregatableSource.toString();
            } catch (JSONException e) {
                LogUtil.e("JSONException when building aggregate source.");
            }
            return null;
        }

        public static String buildAggregateFilterData() {
            try {
                JSONObject filterData = new JSONObject();
                filterData.put("conversion_subdomain",
                        new JSONArray(Collections.singletonList("electronics.megastore")));
                filterData.put("product", new JSONArray(Arrays.asList("1234", "2345")));
                return filterData.toString();
            } catch (JSONException e) {
                LogUtil.e("JSONException when building aggregate filter data.");
            }
            return null;
        }
    }
}
