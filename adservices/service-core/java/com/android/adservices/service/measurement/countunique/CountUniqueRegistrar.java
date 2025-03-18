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
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.measurement.CountUniqueMetadata;
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
import java.util.concurrent.TimeUnit;

public class CountUniqueRegistrar implements ICountUniqueRegistrar {

    private static final long METADATA_EXPIRY_WINDOW_MILLS = TimeUnit.DAYS.toMillis(30);
    private final DatastoreManager mDatastoreManager;

    public CountUniqueRegistrar(DatastoreManager datastoreManager) {
        mDatastoreManager = datastoreManager;
    }

    @Override
    public void registerCountUniqueEvent(
            AsyncRegistration asyncRegistration, List<String> eventHeader, String enrollmentId) {
        if (asyncRegistration == null || eventHeader == null) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Count Unique Event registration failed."
                                    + " Found null async registration or event header.");
            return;
        }

        int headerSize = eventHeader.size();
        if (headerSize != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Exactly one event header is expected. Found : "
                                    + headerSize);
            return;
        }

        final String eventHeaderStr = eventHeader.get(0);
        if (eventHeaderStr.isEmpty()) {
            LoggerFactory.getMeasurementLogger().d("CountUniqueRegistrar: Event header is empty");
            return;
        }

        boolean transactionResult =
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            try {
                                Optional<CountUniqueReport> report =
                                        createCountUniqueReport(
                                                dao,
                                                eventHeaderStr,
                                                asyncRegistration,
                                                enrollmentId);
                                if (report.isPresent()) {
                                    dao.insertCountUniqueReport(report.get());
                                }
                            } catch (JSONException e) {
                                LoggerFactory.getMeasurementLogger()
                                        .d(
                                                "CountUniqueRegistrar: Json exception when "
                                                        + "parsing count unique event"
                                                        + " header",
                                                e);
                            } catch (IllegalArgumentException e) {
                                LoggerFactory.getMeasurementLogger()
                                        .d(
                                                "CountUniqueRegistrar: Invalid count unique event "
                                                        + "header",
                                                e);
                            }
                        });
        if (!transactionResult) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Count Unique Event registration failed."
                                    + " Unable to store report in db");
        }
    }

    @Override
    public void registerCountUniqueMetadata(
            AsyncRegistration asyncRegistration, List<String> metadataHeader) {
        if (asyncRegistration == null || metadataHeader == null) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Count Unique Metadata registration failed."
                                    + " Found null async registration or metadata header.");
            return;
        }
        int headerSize = metadataHeader.size();
        if (headerSize != 1) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "CountUniqueRegistrar: Exactly one metadata header is expected. Found"
                                    + " : "
                                    + headerSize);
            return;
        }
        String metadataHeaderStr = metadataHeader.get(0);
        if (metadataHeaderStr.isEmpty()) {
            LoggerFactory.getMeasurementLogger()
                    .d("CountUniqueRegistrar: Metadata header is empty");
            return;
        }

        try {
            List<MetadataOperation> operations =
                    MetadataOperation.getOperationsFromHeader(metadataHeaderStr);

            for (MetadataOperation operation : operations) {
                Optional<CountUniqueMetadata> m =
                        createCountUniqueMetadata(operation, asyncRegistration);
                if (m.isPresent()) {
                    CountUniqueMetadata metadata = m.get();
                    boolean transactionResult =
                            mDatastoreManager.runInTransaction(
                                    (dao) -> {
                                        if (operation.getType()
                                                == MetadataOperation.OperationType.set) {
                                            dao.insertCountUniqueMetadata(
                                                    metadata, operation.isIgnoreIfPresent());
                                        } else if (operation.getType()
                                                == MetadataOperation.OperationType.delete) {
                                            dao.deleteCountUniqueMetadata(
                                                    metadata.getKey(),
                                                    metadata.getReportingOrigin());
                                        }
                                    });
                    if (!transactionResult) {
                        LoggerFactory.getMeasurementLogger()
                                .d(
                                        "CountUniqueRegistrar: Count Unique Metadata registration"
                                                + " failed. Unable to store metadata in db.");
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            LoggerFactory.getMeasurementLogger()
                    .d("CountUniqueRegistrar: Failure when parsing metadata header", e);
        }
    }

    private Optional<CountUniqueMetadata> createCountUniqueMetadata(
            MetadataOperation operation, AsyncRegistration asyncRegistration) {
        CountUniqueMetadata.Builder builder = new CountUniqueMetadata.Builder();
        builder.setKey(operation.getKey());

        String value = operation.getValue();
        if (value != null && !value.isEmpty()) {
            builder.setValue(Integer.parseInt(operation.getValue()));
        }

        builder.setExpirationTime(
                asyncRegistration.getRequestTime() + METADATA_EXPIRY_WINDOW_MILLS);

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
        return Optional.of(builder.build());
    }

    private Optional<CountUniqueReport> createCountUniqueReport(
            IMeasurementDao dao,
            String eventHeader,
            AsyncRegistration asyncRegistration,
            String enrollmentId)
            throws JSONException, DatastoreException {

        JSONObject eventHeaderJson = new JSONObject(eventHeader);
        CountUniqueReport.Builder builder = new CountUniqueReport.Builder();
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

        BigInteger key = getKey(dao, eventHeaderJson, registrationUriOrigin.get());
        int value = getValue(eventHeaderJson);
        builder.setContributionValue(value);
        builder.setContributionTime(asyncRegistration.getRequestTime());
        builder.setPayload(
                createHistogramContribution(key, value, getFilteringId(eventHeaderJson))
                        .toJSONObject()
                        .toString());
        builder.setReportId(UUID.randomUUID().toString());
        builder.setReportingOrigin(registrationUriOrigin.get());

        if (!eventHeaderJson.isNull(CountUniqueHeaderContract.CONTEXT_ID)) {
            builder.setContextId(eventHeaderJson.getString(CountUniqueHeaderContract.CONTEXT_ID));
        }

        builder.setStatus(CountUniqueReport.ReportDeliveryStatus.PENDING);
        builder.setScheduledReportTime(asyncRegistration.getRequestTime());
        builder.setApiVersion(AggregatePayloadGenerator.getApiVersion(FlagsFactory.getFlags()));
        if (asyncRegistration.hasAdIdPermission()
                && !eventHeaderJson.isNull(CountUniqueHeaderContract.DEBUG_KEY)) {
            builder.setDebugKey(eventHeaderJson.getString(CountUniqueHeaderContract.DEBUG_KEY));
            builder.setDebugReportStatus(CountUniqueReport.ReportDeliveryStatus.PENDING);
        } else {
            builder.setDebugReportStatus(CountUniqueReport.ReportDeliveryStatus.NONE);
        }
        builder.setEnrollmentId(enrollmentId);
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

    private BigInteger getKey(IMeasurementDao dao, JSONObject eventHeader, Uri registrationOrigin)
            throws DatastoreException, JSONException {
        if (eventHeader.isNull(CountUniqueHeaderContract.KEY)) {
            LoggerFactory.getMeasurementLogger()
                    .d("CountUniqueRegistrar: " + "Key not present in event header");
            throw new IllegalArgumentException("Key not present in event header");
        }
        String keyInHeader = eventHeader.getString(CountUniqueHeaderContract.KEY);
        CountUniqueMetadata metadata = dao.getCountUniqueMetadata(keyInHeader, registrationOrigin);
        return BigInteger.valueOf(metadata.getValue());
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
        // TODO(b/398412235): compute filteringId using offset once metadata is implemented
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
