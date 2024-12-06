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

import static com.android.adservices.ResultCode.RESULT_OK;
import static com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution.BUCKET;
import static com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution.ID;
import static com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution.VALUE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import static java.util.Map.entry;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.HpkeJni;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.measurement.actions.Action;
import com.android.adservices.service.measurement.actions.AggregateReportingJob;
import com.android.adservices.service.measurement.actions.EventReportingJob;
import com.android.adservices.service.measurement.actions.InstallApp;
import com.android.adservices.service.measurement.actions.RegisterListSources;
import com.android.adservices.service.measurement.actions.RegisterSource;
import com.android.adservices.service.measurement.actions.RegisterTrigger;
import com.android.adservices.service.measurement.actions.RegisterWebSource;
import com.android.adservices.service.measurement.actions.RegisterWebTrigger;
import com.android.adservices.service.measurement.actions.ReportObjects;
import com.android.adservices.service.measurement.actions.UninstallApp;
import com.android.adservices.service.measurement.actions.UriConfig;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.attribution.AttributionJobHandlerWrapper;
import com.android.adservices.service.measurement.attribution.TriggerContentProvider;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.noising.ImpressionNoiseUtil;
import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.ondevicepersonalization.NoOdpDelegationWrapper;
import com.android.adservices.service.measurement.registration.AsyncRegistrationContentProvider;
import com.android.adservices.service.measurement.registration.AsyncRegistrationQueueRunner;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.reporting.AggregateDebugReportApi;
import com.android.adservices.service.measurement.reporting.AggregateReportingJobHandlerWrapper;
import com.android.adservices.service.measurement.reporting.DebugReportApi;
import com.android.adservices.service.measurement.reporting.DebugReportingJobHandlerWrapper;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.reporting.EventReportingJobHandlerWrapper;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.service.stats.NoOpLoggerImpl;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.HttpsURLConnection;

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
 * <p>Consider @RunWith(Parameterized.class)
 */
public abstract class E2EAbstractMockTest extends E2EAbstractTest {

    // Class extensions may choose to disable or enable added noise.
    AttributionJobHandlerWrapper mAttributionHelper;
    MeasurementImpl mMeasurementImpl;
    ClickVerifier mClickVerifier;
    MeasurementDataDeleter mMeasurementDataDeleter;
    Flags mFlags;
    AsyncRegistrationQueueRunner mAsyncRegistrationQueueRunner;
    SourceNoiseHandler mSourceNoiseHandler;
    ImpressionNoiseUtil mImpressionNoiseUtil;
    AsyncSourceFetcher mAsyncSourceFetcher;
    AsyncTriggerFetcher mAsyncTriggerFetcher;
    AdServicesErrorLogger mErrorLogger;

    EnrollmentDao mEnrollmentDao;
    DatastoreManager mDatastoreManager;

    ContentResolver mMockContentResolver;
    ContentProviderClient mMockContentProviderClient;
    private final Set<String> mSeenUris = new HashSet<>();
    private final Map<String, String> mUriToEnrollmentId = new HashMap<>();
    protected DebugReportApi mDebugReportApi;
    protected AggregateDebugReportApi mAggregateDebugReportApi;

    @Rule(order = 11)
    public final AdServicesExtendedMockitoRule extendedMockito;

    private static Map<String, String> sPhFlags =
            Map.ofEntries(
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_AGGREGATE_REPORT_DELAY_CONFIG,
                            AGGREGATE_REPORT_DELAY + ",0"),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_DEFAULT_AGGREGATION_COORDINATOR_ORIGIN,
                            WebUtil.validUrl("https://coordinator.test")),
                    entry(
                            FlagsConstants.KEY_MEASUREMENT_AGGREGATION_COORDINATOR_ORIGIN_LIST,
                            WebUtil.validUrl("https://coordinator.test")),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_EXCL_SOURCE_REGISTRATION_TIME,
                            "0"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_NULL_AGG_REPORT_RATE_INCL_SOURCE_REGISTRATION_TIME,
                            "0"),
                    entry(
                            FlagsConstants
                                    .KEY_MEASUREMENT_MAX_AGGREGATE_KEYS_PER_SOURCE_REGISTRATION,
                            "5"));

    E2EAbstractMockTest(
            Collection<Action> actions,
            ReportObjects expectedOutput,
            ParamsProvider paramsProvider,
            String name,
            Map<String, String> phFlagsMap) throws RemoteException {
        super(
                actions,
                expectedOutput,
                name,
                ((Supplier<Map<String, String>>)
                                () -> {
                                    for (String key : sPhFlags.keySet()) {
                                        phFlagsMap.putIfAbsent(key, sPhFlags.get(key));
                                    }
                                    return phFlagsMap;
                                })
                        .get());
        mClickVerifier = mock(ClickVerifier.class);
        mFlags = spy(FlagsFactory.getFlags());
        doReturn(false).when(mFlags).getEnrollmentEnableLimitedLogging();
        mErrorLogger = mock(AdServicesErrorLogger.class);
        mDatastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);
        extendedMockito = E2EMockStatic.newE2EMockStaticRule(paramsProvider);
        mMeasurementDataDeleter = spy(new MeasurementDataDeleter(mDatastoreManager, mFlags));

        mEnrollmentDao =
                new EnrollmentDao(
                        ApplicationProvider.getApplicationContext(),
                        DbTestUtil.getSharedDbHelperForTest(),
                        mFlags,
                        /* enable seed */ true,
                        new NoOpLoggerImpl(),
                        EnrollmentUtil.getInstance());
        mDebugReportApi =
                new DebugReportApi(
                        ApplicationProvider.getApplicationContext(),
                        mFlags,
                        new EventReportWindowCalcDelegate(mFlags),
                        new SourceNoiseHandler(mFlags));
        mAggregateDebugReportApi = new AggregateDebugReportApi(mFlags);

        mImpressionNoiseUtil = spy(new ImpressionNoiseUtil());

        mSourceNoiseHandler =
                spy(new SourceNoiseHandler(
                        mFlags,
                        new EventReportWindowCalcDelegate(mFlags),
                        mImpressionNoiseUtil));

        mAsyncSourceFetcher =
                spy(
                        new AsyncSourceFetcher(
                                sContext,
                                mEnrollmentDao,
                                mFlags,
                                mDatastoreManager,
                                mDebugReportApi));
        mAsyncTriggerFetcher =
                spy(
                        new AsyncTriggerFetcher(
                                sContext,
                                mEnrollmentDao,
                                mFlags,
                                new NoOdpDelegationWrapper(),
                                mDatastoreManager,
                                mDebugReportApi));
        mMockContentResolver = mock(ContentResolver.class);
        mMockContentProviderClient = mock(ContentProviderClient.class);

        Uri triggerUri = TriggerContentProvider.getTriggerUri();
        Uri asyncRegistrationTriggerUri = AsyncRegistrationContentProvider.getTriggerUri();
        when(mMockContentResolver.acquireContentProviderClient(triggerUri))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentResolver.acquireContentProviderClient(asyncRegistrationTriggerUri))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(eq(triggerUri), any())).thenReturn(triggerUri);
        when(mMockContentProviderClient.insert(eq(asyncRegistrationTriggerUri), any()))
                .thenReturn(asyncRegistrationTriggerUri);
        when(mClickVerifier.isInputEventVerifiable(any(), anyLong(), anyString())).thenReturn(true);
    }

    void prepareEventReportNoising(UriConfig uriConfig) {
        List<int[]> fakeReportConfigs = uriConfig.getFakeReportConfigs();
        Mockito.doReturn(fakeReportConfigs)
                .when(mImpressionNoiseUtil).getReportConfigsForSequenceIndex(any(), anyLong());
        Mockito.doReturn(fakeReportConfigs)
                .when(mImpressionNoiseUtil)
                .selectFlexEventReportRandomStateAndGenerateReportConfigs(any(), anyInt(), any());
        Mockito.doReturn(fakeReportConfigs == null ? 2.0D : 0.0D).when(mSourceNoiseHandler)
                .getRandomDouble(any());
    }

    void prepareAggregateReportNoising(UriConfig uriConfig) {
        mAttributionHelper.prepareAggregateReportNoising(uriConfig);
    }


    @Override
    void prepareRegistrationServer(RegisterSource sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            UriConfig uriConfig = getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri));
            if (uriConfig.shouldEnroll()) {
                updateEnrollment(uri);
            }
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            when(urlConnection.getURL()).thenReturn(new URL(uri.toString()));
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mAsyncSourceFetcher).openUrl(new URL(uri));
            prepareEventReportNoising(uriConfig);
        }
    }

    @Override
    void prepareRegistrationServer(RegisterListSources sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            UriConfig uriConfig = getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri));
            if (uriConfig.shouldEnroll()) {
                updateEnrollment(uri);
            }
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            when(urlConnection.getURL()).thenReturn(new URL(uri.toString()));
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mAsyncSourceFetcher).openUrl(new URL(uri));
            prepareEventReportNoising(uriConfig);
        }
    }

    @Override
    void prepareRegistrationServer(RegisterTrigger triggerRegistration) throws IOException {
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            UriConfig uriConfig = getNextUriConfig(triggerRegistration.mUriConfigsMap.get(uri));
            if (uriConfig.shouldEnroll()) {
                updateEnrollment(uri);
            }
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            when(urlConnection.getURL()).thenReturn(new URL(uri.toString()));
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation ->
                            getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mAsyncTriggerFetcher).openUrl(new URL(uri));
            prepareAggregateReportNoising(uriConfig);
        }
    }

    @Override
    void prepareRegistrationServer(RegisterWebSource sourceRegistration) throws IOException {
        for (String uri : sourceRegistration.mUriToResponseHeadersMap.keySet()) {
            UriConfig uriConfig = getNextUriConfig(sourceRegistration.mUriConfigsMap.get(uri));
            if (uriConfig.shouldEnroll()) {
                updateEnrollment(uri);
            }
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            when(urlConnection.getURL()).thenReturn(new URL(uri.toString()));
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation -> getNextResponse(sourceRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mAsyncSourceFetcher).openUrl(new URL(uri));
            prepareEventReportNoising(uriConfig);
        }
    }

    @Override
    void prepareRegistrationServer(RegisterWebTrigger triggerRegistration) throws IOException {
        for (String uri : triggerRegistration.mUriToResponseHeadersMap.keySet()) {
            UriConfig uriConfig = getNextUriConfig(triggerRegistration.mUriConfigsMap.get(uri));
            if (uriConfig.shouldEnroll()) {
                updateEnrollment(uri);
            }
            HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
            when(urlConnection.getResponseCode()).thenReturn(200);
            when(urlConnection.getURL()).thenReturn(new URL(uri.toString()));
            Answer<Map<String, List<String>>> headerFieldsMockAnswer =
                    invocation ->
                            getNextResponse(triggerRegistration.mUriToResponseHeadersMap, uri);
            Mockito.doAnswer(headerFieldsMockAnswer).when(urlConnection).getHeaderFields();
            Mockito.doReturn(urlConnection).when(mAsyncTriggerFetcher).openUrl(new URL(uri));
            prepareAggregateReportNoising(uriConfig);
        }
    }

    @Override
    void processAction(RegisterSource sourceRegistration) throws IOException, JSONException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.register source failed",
                RESULT_OK,
                mMeasurementImpl.register(
                        sourceRegistration.mRegistrationRequest,
                        sourceRegistration.mAdIdPermission,
                        sourceRegistration.mTimestamp));
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        processActualDebugReportApiJob(sourceRegistration.mTimestamp);
        processActualDebugReportJob(sourceRegistration.mTimestamp, 0L);
    }

    @Override
    void processAction(RegisterWebSource sourceRegistration) throws IOException, JSONException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebSource failed",
                RESULT_OK,
                mMeasurementImpl.registerWebSource(
                        sourceRegistration.mRegistrationRequest,
                        sourceRegistration.mAdIdPermission,
                        sourceRegistration.mTimestamp));
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        processActualDebugReportApiJob(sourceRegistration.mTimestamp);
        processActualDebugReportJob(sourceRegistration.mTimestamp, 0L);
    }

    @Override
    void processAction(RegisterListSources sourceRegistration) throws IOException, JSONException {
        prepareRegistrationServer(sourceRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebSource failed",
                RESULT_OK,
                mMeasurementImpl.registerSources(
                        sourceRegistration.mRegistrationRequest, sourceRegistration.mTimestamp));
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        processActualDebugReportApiJob(sourceRegistration.mTimestamp);
    }

    @Override
    void processAction(RegisterTrigger triggerRegistration) throws IOException, JSONException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertEquals(
                "MeasurementImpl.register trigger failed",
                RESULT_OK,
                mMeasurementImpl.register(
                        triggerRegistration.mRegistrationRequest,
                        triggerRegistration.mAdIdPermission,
                        triggerRegistration.mTimestamp));
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();

        // To test interactions with deletion of expired records, run event reporting and deletion
        // before performing attribution.
        processAction(new EventReportingJob(
                triggerRegistration.mTimestamp - TimeUnit.MINUTES.toMillis(1)));
        long earliestValidInsertion =
                triggerRegistration.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
        runDeleteExpiredRecordsJob(earliestValidInsertion);

        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
        // Attribution can happen up to an hour after registration call, due to AsyncRegistration
        processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
        processActualDebugReportApiJob(triggerRegistration.mTimestamp);
    }

    @Override
    void processAction(RegisterWebTrigger triggerRegistration) throws IOException, JSONException {
        prepareRegistrationServer(triggerRegistration);
        Assert.assertEquals(
                "MeasurementImpl.registerWebTrigger failed",
                RESULT_OK,
                mMeasurementImpl.registerWebTrigger(
                        triggerRegistration.mRegistrationRequest,
                        triggerRegistration.mAdIdPermission,
                        triggerRegistration.mTimestamp));
        mAsyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker();
        Assert.assertTrue(
                "AttributionJobHandler.performPendingAttributions returned false",
                mAttributionHelper.performPendingAttributions());
        // Attribution can happen up to an hour after registration call, due to AsyncRegistration
        processActualDebugReportJob(triggerRegistration.mTimestamp, TimeUnit.MINUTES.toMillis(30));
        processActualDebugReportApiJob(triggerRegistration.mTimestamp);
    }

    // Triggers debug reports to be sent
    void processActualDebugReportJob(long timestamp, long delay) throws IOException, JSONException {
        long maxAggregateReportUploadRetryWindowMs =
                Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;
        long reportTime = timestamp + delay;
        Object[] eventCaptures =
                EventReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
                        mDatastoreManager,
                        reportTime
                                - Flags.DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                        reportTime,
                        true,
                        mFlags,
                        ApplicationProvider.getApplicationContext());

        processActualDebugEventReports(
                timestamp,
                (List<EventReport>) eventCaptures[0],
                (List<Uri>) eventCaptures[1],
                (List<JSONObject>) eventCaptures[2]);

        Object[] aggregateCaptures =
                AggregateReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
                        mDatastoreManager,
                        reportTime - maxAggregateReportUploadRetryWindowMs,
                        reportTime,
                        true,
                        mFlags);

        processActualDebugAggregateReports(
                timestamp,
                (List<AggregateReport>) aggregateCaptures[0],
                (List<Uri>) aggregateCaptures[1],
                (List<JSONObject>) aggregateCaptures[2]);
    }

    // Process actual additional debug reports.
    protected void processActualDebugReportApiJob(long timestamp)
            throws IOException, JSONException {
        Object[] reportCaptures =
                DebugReportingJobHandlerWrapper.spyPerformScheduledPendingReports(
                        mDatastoreManager, sContext);

        processActualDebugReports(
                timestamp,
                (List<Uri>) reportCaptures[1],
                (List<JSONObject>) reportCaptures[2]);
    }

    @Override
    void processAction(InstallApp installApp) {
        Assert.assertTrue(
                "measurementDao.doInstallAttribution failed",
                mDatastoreManager.runInTransaction(
                        measurementDao ->
                                measurementDao.doInstallAttribution(
                                        installApp.mUri, installApp.mTimestamp)));
    }

    @Override
    void processAction(UninstallApp uninstallApp) {
        Assert.assertTrue(
                "measurementDao.undoInstallAttribution failed",
                mMeasurementImpl.deletePackageRecords(uninstallApp.mUri, uninstallApp.mTimestamp));
    }

    @Override
    void processAction(EventReportingJob reportingJob) throws IOException, JSONException {
        long earliestValidInsertion =
                reportingJob.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
        runDeleteExpiredRecordsJob(earliestValidInsertion);

        Object[] eventCaptures =
                EventReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
                        mDatastoreManager,
                        reportingJob.mTimestamp
                                - Flags.DEFAULT_MEASUREMENT_MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS,
                        reportingJob.mTimestamp,
                        false,
                        mFlags,
                        ApplicationProvider.getApplicationContext());

        processActualEventReports(
                (List<EventReport>) eventCaptures[0],
                (List<Uri>) eventCaptures[1],
                (List<JSONObject>) eventCaptures[2]);
    }

    @Override
    void processAction(AggregateReportingJob reportingJob) throws IOException, JSONException {
        long maxAggregateReportUploadRetryWindowMs =
                Flags.DEFAULT_MEASUREMENT_MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS;
        long earliestValidInsertion =
                reportingJob.mTimestamp - Flags.MEASUREMENT_DATA_EXPIRY_WINDOW_MS;
        runDeleteExpiredRecordsJob(earliestValidInsertion);

        Object[] aggregateCaptures =
                AggregateReportingJobHandlerWrapper.spyPerformScheduledPendingReportsInWindow(
                        mDatastoreManager,
                        reportingJob.mTimestamp - maxAggregateReportUploadRetryWindowMs,
                        reportingJob.mTimestamp,
                        false,
                        mFlags);

        processActualAggregateReports(
                (List<AggregateReport>) aggregateCaptures[0],
                (List<Uri>) aggregateCaptures[1],
                (List<JSONObject>) aggregateCaptures[2]);
    }

    // Class extensions may need different processing to prepare for result evaluation.
    void processActualEventReports(
            List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads)
            throws JSONException {
        List<JSONObject> eventReportObjects =
                getActualEventReportObjects(eventReports, destinations, payloads);
        mActualOutput.mEventReportObjects.addAll(eventReportObjects);
    }

    void processActualDebugEventReports(
            long triggerTime,
            List<EventReport> eventReports,
            List<Uri> destinations,
            List<JSONObject> payloads)
            throws JSONException {
        List<JSONObject> eventReportObjects =
                getActualEventReportObjects(eventReports, destinations, payloads);
        for (JSONObject obj : eventReportObjects) {
            obj.put(TestFormatJsonMapping.REPORT_TIME_KEY, String.valueOf(triggerTime));
        }
        mActualOutput.mDebugEventReportObjects.addAll(eventReportObjects);
    }

    void processActualDebugReports(
            long timestamp,
            List<Uri> destinations,
            List<JSONObject> payloads) {
        List<JSONObject> debugReportObjects = getActualDebugReportObjects(
                timestamp, destinations, payloads);
        mActualOutput.mDebugReportObjects.addAll(debugReportObjects);
    }

    private List<JSONObject> getActualEventReportObjects(
            List<EventReport> eventReports, List<Uri> destinations, List<JSONObject> payloads) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            Map<String, Object> map = new HashMap<>();
            map.put(TestFormatJsonMapping.REPORT_TIME_KEY, eventReports.get(i).getReportTime());
            map.put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString());
            map.put(TestFormatJsonMapping.PAYLOAD_KEY, payloads.get(i));
            result.add(new JSONObject(map));
        }
        return result;
    }

    private List<JSONObject> getActualDebugReportObjects(
            long timestamp,
            List<Uri> destinations,
            List<JSONObject> payloads) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            Map<String, Object> map = new HashMap<>();
            map.put(TestFormatJsonMapping.REPORT_TIME_KEY, String.valueOf(timestamp));
            map.put(TestFormatJsonMapping.REPORT_TO_KEY, destinations.get(i).toString());
            map.put(TestFormatJsonMapping.PAYLOAD_KEY, payloads.get(i));
            result.add(new JSONObject(map));
        }
        return result;
    }

    // Class extensions may need different processing to prepare for result evaluation.
    void processActualAggregateReports(
            List<AggregateReport> aggregateReports,
            List<Uri> destinations,
            List<JSONObject> payloads)
            throws JSONException {
        List<JSONObject> aggregateReportObjects =
                getActualAggregateReportObjects(aggregateReports, destinations, payloads,
                        /* timestamp= */ null);
        mActualOutput.mAggregateReportObjects.addAll(aggregateReportObjects);
    }

    void processActualDebugAggregateReports(
            long triggerOrSourceTime,
            List<AggregateReport> aggregateReports,
            List<Uri> destinations,
            List<JSONObject> payloads)
            throws JSONException {
        List<JSONObject> aggregateReportObjects =
                getActualAggregateReportObjects(aggregateReports, destinations, payloads,
                        Long.valueOf(triggerOrSourceTime));
        mActualOutput.mDebugAggregateReportObjects.addAll(aggregateReportObjects);
    }

    /**
     * Collects aggregate reports as JSON objects. The timestamp parameter allows callers with
     * non-debug reports to pass null to associate report time with scheduled report time in the
     * record. Callers with debug reports can provide a non-null timestamp to have report time
     * associated with the relevant source or trigger time at which the report would be sent.
     */
    private List<JSONObject> getActualAggregateReportObjects(
            List<AggregateReport> aggregateReports,
            List<Uri> destinations,
            List<JSONObject> payloads,
            @Nullable Long timestamp)
            throws JSONException {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < destinations.size(); i++) {
            JSONObject sharedInfo = new JSONObject(payloads.get(i).getString("shared_info"));
            result.add(
                    new JSONObject()
                            .put(
                                    TestFormatJsonMapping.REPORT_TIME_KEY,
                                    String.valueOf(
                                            timestamp == null
                                                    ? aggregateReports
                                                            .get(i)
                                                            .getScheduledReportTime()
                                                    : timestamp.longValue()))
                            .put(
                                    TestFormatJsonMapping.REPORT_TO_KEY,
                                    destinations.get(i).toString())
                            .put(
                                    TestFormatJsonMapping.PAYLOAD_KEY,
                                    getActualAggregatablePayloadForTest(
                                            sharedInfo, payloads.get(i))));
        }
        return result;
    }

    private JSONObject getActualAggregatablePayloadForTest(JSONObject sharedInfo, JSONObject data)
            throws JSONException {
        String payload =
                data.getJSONArray("aggregation_service_payloads")
                        .getJSONObject(0)
                        .getString("payload");

        final byte[] decryptedPayload =
                HpkeJni.decrypt(
                        decode(AggregateCryptoFixture.getPrivateKeyBase64()),
                        decode(payload),
                        (AggregateCryptoFixture.getSharedInfoPrefix() + sharedInfo.toString())
                                .getBytes());

        String sourceDebugKey = data.optString(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY);
        String triggerDebugKey = data.optString(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY);

        JSONObject sharedInfoJson = new JSONObject();
        for (String key : sAggregateReportSharedInfoKeys) {
            if (sharedInfo.has(key)) {
                sharedInfoJson.put(key, sharedInfo.getString(key));
            }
        }

        JSONObject aggregateJson =
                new JSONObject()
                        .put(
                                AggregateReportPayloadKeys.SHARED_INFO,
                                sharedInfoJson)
                        .put(
                                AggregateReportPayloadKeys.AGGREGATION_COORDINATOR_ORIGIN,
                                data.optString("aggregation_coordinator_origin", ""))
                        .put(
                                AggregateReportPayloadKeys.HISTOGRAMS,
                                getActualAggregateHistograms(decryptedPayload));
        if (!sourceDebugKey.isEmpty()) {
            aggregateJson.put(AggregateReportPayloadKeys.SOURCE_DEBUG_KEY, sourceDebugKey);
        }
        if (!triggerDebugKey.isEmpty()) {
            aggregateJson.put(AggregateReportPayloadKeys.TRIGGER_DEBUG_KEY, triggerDebugKey);
        }
        if (!data.isNull(AggregateReportPayloadKeys.TRIGGER_CONTEXT_ID)) {
            aggregateJson.put(
                    AggregateReportPayloadKeys.TRIGGER_CONTEXT_ID,
                    data.optString(AggregateReportPayloadKeys.TRIGGER_CONTEXT_ID));
        }

        return aggregateJson;
    }

    JSONArray getActualAggregateHistograms(byte[] encodedCborPayload) throws JSONException {
        List<JSONObject> result = new ArrayList<>();

        try {
            final List<DataItem> dataItems =
                    new CborDecoder(new ByteArrayInputStream(encodedCborPayload)).decode();
            final co.nstant.in.cbor.model.Map payload =
                    (co.nstant.in.cbor.model.Map) dataItems.get(0);
            final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
            for (DataItem i : payloadArray.getDataItems()) {
                co.nstant.in.cbor.model.Map m = (co.nstant.in.cbor.model.Map) i;
                JSONObject histogram = new JSONObject();
                Object value =
                        "0x"
                                + new BigInteger(
                                                1,
                                                ((ByteString) m.get(new UnicodeString(BUCKET)))
                                                        .getBytes())
                                        .toString(16);
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

    protected static Map<String, List<String>> getNextResponse(
            Map<String, List<Map<String, List<String>>>> uriToResponseHeadersMap, String uri) {
        List<Map<String, List<String>>> responseList = uriToResponseHeadersMap.get(uri);
        return responseList.remove(0);
    }

    protected static UriConfig getNextUriConfig(List<UriConfig> uriConfigs) {
        return uriConfigs.remove(0);
    }

    private void runDeleteExpiredRecordsJob(long earliestValidInsertion) {
        int retryLimit = Flags.MEASUREMENT_MAX_RETRIES_PER_REGISTRATION_REQUEST;
        mDatastoreManager.runInTransaction(
                dao -> dao.deleteExpiredRecords(earliestValidInsertion, retryLimit, null, 0));
    }

    void updateEnrollment(String uri) {
        if (mSeenUris.contains(uri)) {
            return;
        }
        mSeenUris.add(uri);
        String enrollmentId = getEnrollmentId(uri);
        Set<String> attributionRegistrationUrls;
        EnrollmentData enrollmentData = mEnrollmentDao.getEnrollmentData(enrollmentId);
        if (enrollmentData != null) {
            mEnrollmentDao.delete(enrollmentId);
            attributionRegistrationUrls =
                    new HashSet<>(enrollmentData.getAttributionSourceRegistrationUrl());
            attributionRegistrationUrls.addAll(
                    enrollmentData.getAttributionTriggerRegistrationUrl());
            attributionRegistrationUrls.add(uri);
        } else {
            attributionRegistrationUrls = Set.of(uri);
        }
        Uri registrationUri = Uri.parse(uri);
        String reportingUrl = registrationUri.getScheme() + "://" + registrationUri.getAuthority();
        insertEnrollment(enrollmentId, reportingUrl, new ArrayList<>(attributionRegistrationUrls));
    }

    private void insertEnrollment(String enrollmentId, String reportingUrl,
            List<String> attributionRegistrationUrls) {
        EnrollmentData enrollmentData = new EnrollmentData.Builder()
                .setEnrollmentId(enrollmentId)
                .setAttributionSourceRegistrationUrl(attributionRegistrationUrls)
                .setAttributionTriggerRegistrationUrl(attributionRegistrationUrls)
                .setAttributionReportingUrl(List.of(reportingUrl))
                .build();
        Assert.assertTrue(mEnrollmentDao.insert(enrollmentData));
    }

    private String getEnrollmentId(String uri) {
        Optional<Uri> domainAndScheme = WebAddresses.topPrivateDomainAndScheme(Uri.parse(uri));
        String authority = domainAndScheme.get().getAuthority();
        return mUriToEnrollmentId.computeIfAbsent(authority, k -> "enrollment-id-" + authority);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value.getBytes());
    }
}
