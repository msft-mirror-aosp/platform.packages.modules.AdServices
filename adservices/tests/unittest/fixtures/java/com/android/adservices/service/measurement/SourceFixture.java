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
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SourceFixture {
    private SourceFixture() { }

    // Assume the field values in this Source.Builder have no relation to the field values in
    // {@link ValidSourceParams}
    public static Source.Builder getValidSourceBuilder() {
        return new Source.Builder()
                .setPublisher(ValidSourceParams.PUBLISHER)
                .setAppDestination(ValidSourceParams.ATTRIBUTION_DESTINATION)
                .setEnrollmentId(ValidSourceParams.ENROLLMENT_ID)
                .setRegistrant(ValidSourceParams.REGISTRANT);
    }

    // Assume the field values in this Source have no relation to the field values in
    // {@link ValidSourceParams}
    public static Source getValidSource() {
        return new Source.Builder()
                .setEventId(ValidSourceParams.SOURCE_EVENT_ID)
                .setPublisher(ValidSourceParams.PUBLISHER)
                .setAppDestination(ValidSourceParams.ATTRIBUTION_DESTINATION)
                .setWebDestination(ValidSourceParams.WEB_DESTINATION)
                .setEnrollmentId(ValidSourceParams.ENROLLMENT_ID)
                .setRegistrant(ValidSourceParams.REGISTRANT)
                .setEventTime(ValidSourceParams.SOURCE_EVENT_TIME)
                .setExpiryTime(ValidSourceParams.EXPIRY_TIME)
                .setPriority(ValidSourceParams.PRIORITY)
                .setSourceType(ValidSourceParams.SOURCE_TYPE)
                .setInstallAttributionWindow(ValidSourceParams.INSTALL_ATTRIBUTION_WINDOW)
                .setInstallCooldownWindow(ValidSourceParams.INSTALL_COOLDOWN_WINDOW)
                .setAttributionMode(ValidSourceParams.ATTRIBUTION_MODE)
                .setAggregateSource(ValidSourceParams.buildAggregateSource())
                .setFilterData(ValidSourceParams.buildFilterData())
                .setIsDebugReporting(true)
                .build();
    }

    public static class ValidSourceParams {
        public static final Long EXPIRY_TIME = 8640000010L;
        public static final Long PRIORITY = 100L;
        public static final UnsignedLong SOURCE_EVENT_ID = new UnsignedLong(1L);
        public static final Long SOURCE_EVENT_TIME = 8640000000L;
        public static final Uri ATTRIBUTION_DESTINATION =
                Uri.parse("android-app://com.destination");
        public static Uri WEB_DESTINATION = Uri.parse("https://destination.com");
        public static final Uri PUBLISHER = Uri.parse("android-app://com.publisher");
        public static final Uri REGISTRANT = Uri.parse("android-app://com.registrant");
        public static final String ENROLLMENT_ID = "enrollment-id";
        public static final Source.SourceType SOURCE_TYPE = Source.SourceType.EVENT;
        public static final Long INSTALL_ATTRIBUTION_WINDOW = 841839879274L;
        public static final Long INSTALL_COOLDOWN_WINDOW = 8418398274L;
        public static final UnsignedLong DEBUG_KEY = new UnsignedLong(7834690L);
        public static final @Source.AttributionMode int ATTRIBUTION_MODE =
                Source.AttributionMode.TRUTHFULLY;
        public static final int AGGREGATE_CONTRIBUTIONS = 0;

        public static final String buildAggregateSource() {
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

        public static final String buildFilterData() {
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

        public static final AggregatableAttributionSource buildAggregatableAttributionSource() {
            return new AggregatableAttributionSource.Builder()
                    .setAggregatableSource(Map.of("5", new BigInteger("345")))
                    .setFilterData(
                            new FilterData.Builder()
                                    .setAttributionFilterMap(
                                            Map.of(
                                                    "product", List.of("1234", "4321"),
                                                    "conversion_subdomain",
                                                            List.of("electronics.megastore")))
                                    .build())
                    .build();
        }
    }
}
