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

import static com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution.BUCKET;
import static com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution.ID;
import static com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution.VALUE;

import static java.util.Map.entry;

import android.adservices.measurement.RegistrationRequest;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.registration.AsyncFetchStatus;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.util.Enrollment;
import com.android.adservices.service.measurement.util.UnsignedLong;

import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 *
 * <p>Tests in assets/msmt_interop_tests/ directory were copied from Chromium
 * src/content/test/data/attribution_reporting/interop GitHub commit
 * 2c05d700df45bcad83d3ed8fae8b9cb15fc24716.
 */
@RunWith(Parameterized.class)
public class E2EInteropMockTest extends E2EAbstractMockTest {
    static {
        sTestsToSkip =
                ImmutableSet.of(
                        "aggregatable_debug_reports_trigger.json",
                        "aggregatable_report_window.json",
                        "aggregatable_reports_fake_source.json",
                        "aggregation_coordinator_origin.json",
                        "aggregation_key_identifier_length.json",
                        "attribution_scopes_navigation_limit_no_scopes.json",
                        "attribution_scopes_navigation_limit_with_scopes.json",
                        "attribution_scopes_null_scopes_removes_data.json",
                        "attribution_scopes_parsing_failures.json",
                        "custom_trigger_data.json",
                        "destination_limit.json",
                        "destination_rate_limit.json",
                        "destination_validation.json",
                        "event_level_report_time.json",
                        "event_level_trigger_filter_data.json",
                        "event_report_window.json",
                        "expired_source.json",
                        "fenced.json",
                        "filter_data_validation.json",
                        "header_presence.json",
                        "lookback_window_precision.json",
                        "max_event_level_reports_per_source.json",
                        "os_debug_reports.json",
                        "preferred_platform.json",
                        "prio_dup.json",
                        "rate_limit_max_attributions.json",
                        "rate_limit_max_reporting_origins_per_source_reporting_site.json",
                        "redirect_source_trigger.json",
                        "source_destination_limit_fifo.json",
                        "source_destination_limit_fifo_rate_limits.json",
                        "source_registration_limits.json",
                        "source_storage_limit_expiry.json",
                        "unsuitable_response_url.json",
                        "verbose_debug_report_multiple_data.json");
    }

    // The following keys are to JSON fields that should be interpreted in milliseconds.
    public static final Set<String> TIMESTAMP_KEYS_IN_MILLIS =
            new HashSet<>(
                    Arrays.asList(
                            TestFormatJsonMapping.TIMESTAMP_KEY,
                            TestFormatJsonMapping.REPORT_TIME_KEY));
    // The following keys are to JSON fields that should be interpreted in seconds.
    public static final Set<String> TIMESTAMP_KEYS_IN_SECONDS =
            new HashSet<>(
                    Arrays.asList(
                            TestFormatJsonMapping.SCHEDULED_REPORT_TIME,
                            TestFormatJsonMapping.SOURCE_REGISTRATION_TIME));
    // All interop tests are specified with timestamps that are offsets, so we establish a start
    // time for those offsets to add to. There are two IMPORTANT conditions for this start time:
    // 1.) The time must be recent so that the timestamp is large enough to work with the temporary
    //    "inline migration" we have for event_report_window.
    // 2.) The time must be at the start of a day because there are operations that round timestamps
    //     down to the day, e.g. in populating the source_registration_time for aggregatable reports
    private static final long START_TIME = 1674000000000L;
    private static final String TEST_DIR_NAME = "msmt_interop_tests";
    private static final String ANDROID_APP_SCHEME = "android-app";
    private static final String UNUSED = "";
    private static final List<AsyncFetchStatus.EntityStatus> sParsingErrors =
            List.of(
                    AsyncFetchStatus.EntityStatus.HEADER_ERROR,
                    AsyncFetchStatus.EntityStatus.PARSING_ERROR,
                    AsyncFetchStatus.EntityStatus.VALIDATION_ERROR);
    private static final Map<String, String> sApiConfigPhFlags =
            Map.ofEntries(
                    entry("rate_limit_max_attributions", UNUSED),
                    entry("aggregation_coordinator_origins", UNUSED),
                    entry(
                            "rate_limit_max_attribution_reporting_origins",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_ATTRIBUTION),
                    entry(
                            "rate_limit_max_source_registration_reporting_origins",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_DISTINCT_REPORTING_ORIGINS_IN_SOURCE),
                    entry(
                            "max_destinations_per_source_site_reporting_site",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_DISTINCT_DESTINATIONS_IN_ACTIVE_SOURCE),
                    entry(
                            "max_event_level_channel_capacity_event",
                            FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_EVENT),
                    entry(
                            "max_event_level_channel_capacity_navigation",
                            FlagsConstants
                                    .KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION),
                    entry(
                            "max_event_level_channel_capacity_scopes_event",
                            FlagsConstants.KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_EVENT),
                    entry(
                            "max_event_level_channel_capacity_scopes_navigation",
                            FlagsConstants
                                    .KEY_MEASUREMENT_ATTRIBUTION_SCOPE_MAX_INFO_GAIN_NAVIGATION),
                    entry(
                            "rate_limit_max_reporting_origins_per_source_reporting_site",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_REPORTING_ORIGINS_PER_SOURCE_REPORTING_SITE_PER_WINDOW),
                    entry(
                            "max_destinations_per_rate_limit_window",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_DESTINATIONS_PER_PUBLISHER_PER_RATE_LIMIT_WINDOW),
                    entry(
                            "max_destinations_per_reporting_site_per_day",
                            FlagsConstants.KEY_MEASUREMENT_DESTINATION_PER_DAY_RATE_LIMIT),
                    entry(
                            "max_destinations_per_rate_limit_window_reporting_site",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_DEST_PER_PUBLISHER_X_ENROLLMENT_PER_RATE_LIMIT_WINDOW),
                    entry(
                            "max_sources_per_origin",
                            FlagsConstants.KEY_MEASUREMENT_MAX_SOURCES_PER_PUBLISHER),
                    entry(
                            "max_event_level_reports_per_destination",
                            FlagsConstants.KEY_MEASUREMENT_MAX_EVENT_REPORTS_PER_DESTINATION),
                    entry(
                            "max_aggregatable_reports_per_destination",
                            FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_DESTINATION),
                    entry(
                            "max_trigger_state_cardinality",
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION),
                    entry(
                            "max_aggregatable_debug_reports_per_source",
                            FlagsConstants.KEY_MEASUREMENT_MAX_ADR_COUNT_PER_SOURCE),
                    entry(
                            "max_aggregatable_debug_budget_per_context_site",
                            FlagsConstants.KEY_MEASUREMENT_ADR_BUDGET_PER_PUBLISHER_WINDOW),
                    entry(
                            "max_aggregatable_reports_per_source",
                            FlagsConstants.KEY_MEASUREMENT_MAX_AGGREGATE_REPORTS_PER_SOURCE));

    private static String preprocessor(String json) {
        // In a header response provided in string format, .test could also be surrounded by escaped
        // quotes.
        return json.replaceAll("\\.test(?=[\"\\/\\\\])", ".com")
                // Remove comments
                .replaceAll("// .*", "")
                .replaceAll("\"destination\":", "\"web_destination\":")
                // In a header response provided in string format, destination may be surronded by
                // escaped quotes.
                .replaceAll("\\\\\"destination\\\\\":", "\\\\\"web_destination\\\\\":");
    }

    private static final Map<String, String> sPhFlagsForInterop =
            Map.ofEntries(
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_FLEXIBLE_EVENT_REPORTING_API_ENABLED,
                            "true"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENABLE_SOURCE_DEACTIVATION_AFTER_FILTERING,
                            "true"),
                    entry(FlagsConstants.KEY_MEASUREMENT_ENABLE_V1_SOURCE_TRIGGER_DATA, "true"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENABLE_SEPARATE_DEBUG_REPORT_TYPES_FOR_ATTRIBUTION_RATE_LIMIT,
                            "true"),
                    entry(FlagsConstants.KEY_MEASUREMENT_ENABLE_ATTRIBUTION_SCOPE, "true"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENABLE_DESTINATION_PER_DAY_RATE_LIMIT_WINDOW,
                            "true"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENABLE_FIFO_DESTINATIONS_DELETE_AGGREGATE_REPORTS,
                            "true"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_ENABLE_SOURCE_DESTINATION_LIMIT_PRIORITY,
                            "true"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_FLEX_API_MAX_INFORMATION_GAIN_NAVIGATION,
                            "11.46173"),
                    entry(FlagsConstants.KEY_MEASUREMENT_DEFAULT_DESTINATION_LIMIT_ALGORITHM, "1"),
                    entry(FlagsConstants.KEY_MEASUREMENT_ENABLE_LOOKBACK_WINDOW_FILTER, "true"),
                    entry(FlagsConstants.KEY_MEASUREMENT_ENABLE_HEADER_ERROR_DEBUG_REPORT, "true"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_ENABLE_EVENT_LEVEL_EPSILON_IN_SOURCE,
                            "true"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_ENABLE_UPDATE_TRIGGER_REGISTRATION_HEADER_LIMIT,
                            "true"),
                    entry(FlagsConstants.KEY_MEASUREMENT_ENABLE_AGGREGATE_VALUE_FILTERS, "true"),
                    entry(FlagsConstants.KEY_MEASUREMENT_ENABLE_AGGREGATE_DEBUG_REPORTING, "true"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_ENABLE_BOTH_SIDE_DEBUG_KEYS_IN_REPORTS,
                            "true"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_ENABLE_FLEXIBLE_CONTRIBUTION_FILTERING,
                            "true"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_ENABLE_AGGREGATABLE_NAMED_BUDGETS,
                            "true"));

    @Parameterized.Parameters(name = "{3}")
    public static Collection<Object[]> getData() throws IOException, JSONException {
        return data(TEST_DIR_NAME, E2EInteropMockTest::preprocessor, sApiConfigPhFlags);
    }

    public E2EInteropMockTest(
            Collection<Action> actions,
            ReportObjects expectedOutput,
            ParamsProvider paramsProvider,
            String name,
            Map<String, String> phFlagsMap)
            throws RemoteException {
        super(
                actions,
                expectedOutput,
                paramsProvider,
                name,
                ((Supplier<Map<String, String>>)
                        () -> {
                            for (String key : sPhFlagsForInterop.keySet()) {
                                phFlagsMap.putIfAbsent(key, sPhFlagsForInterop.get(key));
                            }
                            return phFlagsMap;
                        })
                        .get());
        mAttributionHelper = TestObjectProvider.getAttributionJobHandler(mDatastoreManager, mFlags);
        mMeasurementImpl =
                TestObjectProvider.getMeasurementImpl(
                        mDatastoreManager,
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        mMockContentResolver);
        mAsyncRegistrationQueueRunner =
                TestObjectProvider.getAsyncRegistrationQueueRunner(
                        mSourceNoiseHandler,
                        mDatastoreManager,
                        mAsyncSourceFetcher,
                        mAsyncTriggerFetcher,
                        mDebugReportApi,
                        mAggregateDebugReportApi,
                        mFlags);
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws JSONException, IOException {
        RegistrationRequest request = sourceRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            prepareEventReportNoising(getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri)));
            updateEnrollment(uri);
            insertSourceOrAssertUnparsable(
                    sourceRegistration.getPublisher(),
                    sourceRegistration.mTimestamp,
                    uri,
                    sourceRegistration.mArDebugPermission,
                    request,
                    getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri));
        }
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        processActualDebugReportApiJob(sourceRegistration.mTimestamp);
        processActualDebugReportJob(sourceRegistration.mTimestamp, 0L);
    }

    @Override
    void processAction(RegisterTrigger triggerRegistration) throws IOException, JSONException {
        RegistrationRequest request = triggerRegistration.mRegistrationRequest;
        // For interop tests, we currently expect only one HTTPS response per registration with no
        // redirects, partly due to differences in redirect handling across attribution APIs.
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            prepareAggregateReportNoising(
                    getNextUriConfig(triggerRegistration.mUriConfigsMap.get(uri)));
            updateEnrollment(uri);
            insertTriggerOrAssertUnparsable(
                    triggerRegistration.getDestination(),
                    triggerRegistration.mTimestamp,
                    uri,
                    triggerRegistration.mArDebugPermission,
                    request,
                    getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri));
        }
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
        // Attribution can happen up to an hour after registration call, due to AsyncRegistration
        processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
        processActualDebugReportApiJob(triggerRegistration.mTimestamp);
    }

    @Override
    JSONArray getActualAggregateHistograms(byte[] encodedCborPayload) throws JSONException {
        List<JSONObject> result = new ArrayList<>();

        try {
            final List<DataItem> dataItems =
                    new CborDecoder(new ByteArrayInputStream(encodedCborPayload)).decode();
            final co.nstant.in.cbor.model.Map payload =
                    (co.nstant.in.cbor.model.Map) dataItems.get(0);
            final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
            List<DataItem> contributions = payloadArray.getDataItems();
            for (int i = 0; i < contributions.size(); i++) {
                co.nstant.in.cbor.model.Map m = (co.nstant.in.cbor.model.Map) contributions.get(i);
                JSONObject histogram = new JSONObject();
                String value =
                        "0x"
                                + new BigInteger(
                                                1,
                                                ((ByteString) m.get(new UnicodeString(BUCKET)))
                                                        .getBytes())
                                        .toString(16);
                // Skip contribution padding
                if (i > 0 && value.equals("0x0")) {
                    continue;
                }
                histogram.put(AggregateHistogramKeys.BUCKET, value);
                histogram.put(
                        AggregateHistogramKeys.VALUE,
                        new BigInteger(1, ((ByteString) m.get(new UnicodeString(VALUE))).getBytes())
                                .intValue());
                if (m.get(new UnicodeString(AggregateHistogramKeys.ID)) != null) {
                    UnsignedLong id =
                            new UnsignedLong(
                                    new BigInteger(
                                                    1,
                                                    ((ByteString) m.get(new UnicodeString(ID)))
                                                            .getBytes())
                                            .longValue());
                    histogram.put(ID, id);
                }
                result.add(histogram);
            }
        } catch (CborException e) {
            throw new JSONException(e);
        }

        return new JSONArray(result);
    }

    private void insertSourceOrAssertUnparsable(
            String publisher,
            long timestamp,
            String uri,
            boolean arDebugPermission,
            RegistrationRequest request,
            Map<String, List<String>> headers)
            throws JSONException {
        String enrollmentId =
                Enrollment.getValidEnrollmentId(
                                Uri.parse(uri),
                                request.getAppPackageName(),
                                mEnrollmentDao,
                                sContext,
                                mFlags)
                        .get();
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setTopOrigin(Uri.parse(publisher))
                        .setOsDestination(null)
                        .setWebDestination(null)
                        .setRegistrant(getRegistrant(request.getAppPackageName()))
                        .setRequestTime(timestamp)
                        .setSourceType(getSourceType(request))
                        .setType(AsyncRegistration.RegistrationType.WEB_SOURCE)
                        .setAdIdPermission(true)
                        .setDebugKeyAllowed(arDebugPermission)
                        .setRegistrationUri(Uri.parse(uri))
                        .build();

        AsyncFetchStatus status = new AsyncFetchStatus();
        Optional<Source> maybeSource =
                mAsyncSourceFetcher.parseSource(asyncRegistration, enrollmentId, headers, status);

        if (maybeSource.isPresent()) {
            Assert.assertTrue(
                    "mAsyncRegistrationQueueRunner.storeSource failed",
                    mDatastoreManager.runInTransaction(
                            measurementDao ->
                                    mAsyncRegistrationQueueRunner.storeSource(
                                            maybeSource.get(),
                                            asyncRegistration,
                                            measurementDao,
                                            status)));
        } else {
            Assert.assertTrue(sParsingErrors.contains(status.getEntityStatus()));
        }
    }

    private void insertTriggerOrAssertUnparsable(
            String destination,
            long timestamp,
            String uri,
            boolean arDebugPermission,
            RegistrationRequest request,
            Map<String, List<String>> headers)
            throws JSONException {
        String enrollmentId =
                Enrollment.getValidEnrollmentId(
                                Uri.parse(uri),
                                request.getAppPackageName(),
                                mEnrollmentDao,
                                sContext,
                                mFlags)
                        .get();
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId(UUID.randomUUID().toString())
                        .setTopOrigin(Uri.parse(destination))
                        .setRegistrant(getRegistrant(request.getAppPackageName()))
                        .setRequestTime(timestamp)
                        .setType(AsyncRegistration.RegistrationType.WEB_TRIGGER)
                        .setAdIdPermission(true)
                        .setDebugKeyAllowed(arDebugPermission)
                        .setRegistrationUri(Uri.parse(uri))
                        .build();

        AsyncFetchStatus status = new AsyncFetchStatus();
        Optional<Trigger> maybeTrigger =
                mAsyncTriggerFetcher.parseTrigger(asyncRegistration, enrollmentId, headers, status);

        if (maybeTrigger.isPresent()) {
            Assert.assertTrue(
                    "mAsyncRegistrationQueueRunner.storeTrigger failed",
                    mDatastoreManager.runInTransaction(
                            measurementDao ->
                                    mAsyncRegistrationQueueRunner.storeTrigger(
                                            maybeTrigger.get(), measurementDao)));
        } else {
            Assert.assertTrue(sParsingErrors.contains(status.getEntityStatus()));
        }
    }

    private static Source.SourceType getSourceType(RegistrationRequest request) {
        return request.getInputEvent() == null
                ? Source.SourceType.EVENT
                : Source.SourceType.NAVIGATION;
    }

    private static Uri getRegistrant(String packageName) {
        return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
    }
}