/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.measurement.countunique;

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.CountUniqueReport;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregatePayloadGenerator;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CountUniqueRegistrar implements ICountUniqueRegistrar {

    private final DatastoreManager mDatastoreManager;

    public CountUniqueRegistrar(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    @Override
    public void registerCountUniqueEvent(
            AsyncRegistration asyncRegistration, List<String> eventHeader) {

        try {
            if (asyncRegistration == null || eventHeader == null) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "CountUniqueRegistrar: Count Unique Event registration failed."
                                        + " Found null async registration or event header.");
                return;
            }
            Optional<CountUniqueReport> report =
                    createCountUniqueReport(eventHeader, asyncRegistration);

            if (report.isPresent()) {
                boolean transactionResult =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    dao.insertCountUniqueReport(report.get());
                                });
                if (!transactionResult) {
                    LoggerFactory.getMeasurementLogger()
                            .d(
                                    "CountUniqueRegistrar: Count Unique Event registration failed."
                                            + " Unable to store report.");
                }
            }
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Json exception when parsing count unique event"
                                    + " header",
                            e);
        } catch (IllegalArgumentException e) {
            LoggerFactory.getMeasurementLogger()
                    .d("CountUniqueRegistrar: Invalid count unique event header", e);
        }
    }

    private Optional<CountUniqueReport> createCountUniqueReport(
            List<String> eventHeader, AsyncRegistration asyncRegistration) throws JSONException {

        int headerSize = eventHeader.size();
        if (headerSize != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Exactly one event header is expected. Found : "
                                    + headerSize);
            return Optional.empty();
        }

        String eventHeaderStr = eventHeader.get(0);
        if (eventHeaderStr.isEmpty()) {
            LoggerFactory.getMeasurementLogger().d("CountUniqueRegistrar: Event header is empty");
            return Optional.empty();
        }
        JSONObject eventHeaderJson = new JSONObject(eventHeaderStr);

        CountUniqueReport.Builder builder = new CountUniqueReport.Builder();

        BigInteger key = getKey(eventHeaderJson);
        int value = getValue(eventHeaderJson);
        builder.setPayload(
                createHistogramContribution(key, value, getFilteringId(eventHeaderJson))
                        .toJSONObject()
                        .toString());
        builder.setReportId(UUID.randomUUID().toString());
        Optional<Uri> registrationUriOrigin =
                WebAddresses.originAndScheme(asyncRegistration.getRegistrationUri());
        if (registrationUriOrigin.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: "
                                    + "Invalid or empty registration uri - "
                                    + asyncRegistration.getRegistrationUri());
            return Optional.empty();
        }

        builder.setReportingOrigin(registrationUriOrigin.get());

        if (!eventHeaderJson.isNull(CountUniqueHeaderContract.CONTEXT_ID)) {
            builder.setContextId(eventHeaderJson.getString(CountUniqueHeaderContract.CONTEXT_ID));
        }

        builder.setStatus(CountUniqueReport.Status.PENDING);
        builder.setScheduledReportTime(asyncRegistration.getRequestTime());
        builder.setApiVersion(AggregatePayloadGenerator.getApiVersion(FlagsFactory.getFlags()));
        if (asyncRegistration.hasAdIdPermission()
                && !eventHeaderJson.isNull(CountUniqueHeaderContract.DEBUG_KEY)) {
            builder.setDebugKey(eventHeaderJson.getString(CountUniqueHeaderContract.DEBUG_KEY));
        }
        return Optional.of(builder.build());
    }

    private AggregateHistogramContribution createHistogramContribution(
            BigInteger key, int value, Optional<UnsignedLong> filteringId) {
        AggregateHistogramContribution.Builder builder =
                new AggregateHistogramContribution.Builder();
        builder.setKey(key);
        builder.setValue(value);
        filteringId.ifPresent(builder::setId);
        return builder.build();
    }

    private BigInteger getKey(JSONObject eventHeader) throws JSONException {
        if (eventHeader.isNull(CountUniqueHeaderContract.KEY)) {
            LoggerFactory.getMeasurementLogger()
                    .d("CountUniqueRegistrar: " + "Key not present in event header");
            throw new IllegalArgumentException("Key not present in event header");
        }
        String keyInHeader = eventHeader.getString(CountUniqueHeaderContract.KEY);
        // read key from metadata once implemented
        return new BigInteger(keyInHeader);
    }

    private int getValue(JSONObject eventHeader) throws JSONException {
        if (eventHeader.isNull(CountUniqueHeaderContract.VALUE)) {
            LoggerFactory.getMeasurementLogger()
                    .d("CountUniqueRegistrar: " + "Value not present in event header");
            throw new IllegalArgumentException("Value not present in event header");
        }
        return eventHeader.getInt(CountUniqueHeaderContract.VALUE);
    }

    private Optional<UnsignedLong> getFilteringId(JSONObject eventHeader) throws JSONException {
        if (eventHeader.isNull(CountUniqueHeaderContract.FILTERING_ID)) {
            return Optional.empty();
        }
        // compute filteringId using offset once metadata is implemented
        return Optional.of(
                new UnsignedLong(eventHeader.getLong(CountUniqueHeaderContract.FILTERING_ID)));
    }

    public interface CountUniqueHeaderContract {
        String KEY = "key";
        String VALUE = "value";
        String FILTERING_ID = "filteringId";
        String OFFSET_FROM_KEY = "offsetFromKey";
        String SCALE = "scale";
        String OFFSET_KEY = "offsetKey";
        String CONTEXT_ID = "contextId";
        String FILTERING_ID_MAX_BYTES = "filteringIdMaxBytes";
        String DEBUG_KEY = "debug_key";
    }
}
