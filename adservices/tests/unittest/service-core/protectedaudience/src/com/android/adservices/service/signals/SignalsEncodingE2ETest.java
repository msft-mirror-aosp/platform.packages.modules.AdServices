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

package com.android.adservices.service.signals;

import static com.android.adservices.service.signals.SignalsFixture.assertSignalsUnorderedListEqualsExceptIdAndTime;
import static com.android.adservices.service.signals.SignalsFixture.intToBase64;
import static com.android.adservices.service.signals.SignalsFixture.intToBytes;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsInput;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MockStatic(PeriodicEncodingJobService.class)
@RequiresSdkLevelAtLeastT
public final class SignalsEncodingE2ETest extends AdServicesExtendedMockitoTestCase {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final long WAIT_TIME_SECONDS = 10L;

    private static final String SIGNALS_PATH = "/signals";
    private static final String ENCODER_PATH = "/encoder";
    private static final boolean ISOLATE_CONSOLE_MESSAGE_IN_LOGS_ENABLED = true;
    private static final IsolateSettings ISOLATE_SETTINGS_WITH_MAX_HEAP_ENFORCEMENT_ENABLED =
            IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                    ISOLATE_CONSOLE_MESSAGE_IN_LOGS_ENABLED);
    private static final int PAS_ENCODING_SOURCE_TYPE =
            AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE;

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Rule(order = 12)
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImplMock;

    @Mock
    private UpdateSignalsProcessReportedLoggerImpl mUpdateSignalsProcessReportedLoggerImplMock;

    private FlagsWithEnabledPeriodicEncoding mFlagsWithProtectedSignalsAndEncodingEnabled =
            new FlagsWithEnabledPeriodicEncoding();

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(
                    mFlagsWithProtectedSignalsAndEncodingEnabled, mAdServicesLoggerMock);

    private ProtectedSignalsDao mSignalsDao;
    private EncoderEndpointsDao mEncoderEndpointsDao;
    private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private ProtectedSignalsServiceImpl mService;
    private UpdateSignalsOrchestrator mUpdateSignalsOrchestrator;
    private UpdatesDownloader mUpdatesDownloader;
    private UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;
    private UpdateProcessorSelector mUpdateProcessorSelector;
    private UpdateEncoderEventHandler mUpdateEncoderEventHandler;
    private SignalEvictionController mSignalEvictionController;
    private EncodedPayloadDao mEncodedPayloadDao;
    private PeriodicEncodingJobWorker mPeriodicEncodingJobWorker;
    private SignalsScriptEngine mScriptEngine;

    private AdTechUriValidator mAdtechUriValidator;
    private FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilter;
    private EncoderLogicHandler mEncoderLogicHandler;
    private EncoderPersistenceDao mEncoderPersistenceDao;
    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private Flags mFakeFlags;
    private EnrollmentDao mEnrollmentDao;
    private Clock mClock;
    private ForcedEncoder mForcedEncoder;

    @Before
    public void setup() {
        mSignalsDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();
        mEncoderEndpointsDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderEndpointsDao();
        mEncoderLogicMetadataDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderLogicMetadataDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mEnrollmentDao =
                new EnrollmentDao(
                        mSpyContext,
                        DbTestUtil.getSharedDbHelperForTest(),
                        mFlagsWithProtectedSignalsAndEncodingEnabled);

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mUpdateProcessorSelector = new UpdateProcessorSelector();
        mEncoderPersistenceDao = EncoderPersistenceDao.getInstance();

        mAdServicesHttpsClient =
                new AdServicesHttpsClient(mBackgroundExecutorService, 2000, 2000, 10000);
        mFakeFlags = FakeFlagsFactory.getFlagsForTest();
        mForcedEncoder =
                new ForcedEncoderFactory(
                                mFakeFlags.getFledgeEnableForcedEncodingAfterSignalsUpdate(),
                                mFakeFlags
                                        .getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds(),
                                mSpyContext)
                        .createInstance();
        mEncoderLogicHandler =
                new EncoderLogicHandler(
                        mEncoderPersistenceDao,
                        mEncoderEndpointsDao,
                        mEncoderLogicMetadataDao,
                        mSignalsDao,
                        mAdServicesHttpsClient,
                        mBackgroundExecutorService,
                        mAdServicesLoggerMock,
                        mFakeFlags);
        mUpdateEncoderEventHandler =
                new UpdateEncoderEventHandler(
                        mEncoderEndpointsDao,
                        mEncoderLogicHandler,
                        mSpyContext,
                        AdServicesExecutors.getBackgroundExecutor(),
                        /* isCompletionBroadcastEnabled= */ false,
                        mForcedEncoder,
                        false);
        mSignalEvictionController = new SignalEvictionController(ImmutableList.of(), 0, 0);
        mUpdateProcessingOrchestrator =
                new UpdateProcessingOrchestrator(
                        mSignalsDao,
                        mUpdateProcessorSelector,
                        mUpdateEncoderEventHandler,
                        mSignalEvictionController,
                        mForcedEncoder);
        mAdtechUriValidator = new AdTechUriValidator("", "", "", "");
        mFledgeAuthorizationFilter =
                ExtendedMockito.spy(
                        new FledgeAuthorizationFilter(
                                mSpyContext.getPackageManager(),
                                mEnrollmentDao,
                                mAdServicesLoggerMock));
        mProtectedSignalsServiceFilter =
                new ProtectedSignalsServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        mFlagsWithProtectedSignalsAndEncodingEnabled,
                        mAppImportanceFilterMock,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);

        doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();

        mUpdatesDownloader =
                new UpdatesDownloader(mLightweightExecutorService, mAdServicesHttpsClient);

        mUpdateSignalsOrchestrator =
                new UpdateSignalsOrchestrator(
                        mBackgroundExecutorService,
                        mUpdatesDownloader,
                        mUpdateProcessingOrchestrator,
                        mAdtechUriValidator,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);
        mService =
                new ProtectedSignalsServiceImpl(
                        mSpyContext,
                        mUpdateSignalsOrchestrator,
                        mFledgeAuthorizationFilter,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        AdServicesExecutors.getBackgroundExecutor(),
                        mAdServicesLoggerImplMock,
                        mFlagsWithProtectedSignalsAndEncodingEnabled,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mProtectedSignalsServiceFilter,
                        mEnrollmentDao,
                        mUpdateSignalsProcessReportedLoggerImplMock);

        RetryStrategy retryStrategy = new NoOpRetryStrategyImpl();
        mScriptEngine =
                new SignalsScriptEngine(
                        ISOLATE_SETTINGS_WITH_MAX_HEAP_ENFORCEMENT_ENABLED::getMaxHeapSizeBytes,
                        retryStrategy,
                        ISOLATE_SETTINGS_WITH_MAX_HEAP_ENFORCEMENT_ENABLED
                                ::getIsolateConsoleMessageInLogsEnabled);
        mClock = Clock.getInstance();
        mPeriodicEncodingJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalsDao,
                        mScriptEngine,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mFlagsWithProtectedSignalsAndEncodingEnabled,
                        mEnrollmentDao,
                        mClock,
                        mAdServicesLoggerMock);

        doNothing()
                .when(
                        () ->
                                PeriodicEncodingJobService.scheduleIfNeeded(
                                        any(), any(), anyBoolean()));
    }

    @After
    public void teardown() {
        if (mEncoderPersistenceDao != null) {
            mEncoderPersistenceDao.deleteAllEncoders();
        }
    }

    @Test
    public void testSignalsEncoding_Success() throws Exception {
        String encodeSignalsJS =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "   return {'status': 0, 'results': new Uint8Array([signals.size])};\n"
                        + "}\n";
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String json =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(2)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Append one signal
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(103)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "},"
                        // Put two more signals using put_if_not_present
                        + "\"put_if_not_present\":{\""
                        + intToBase64(4)
                        + "\":\""
                        + intToBase64(105)
                        + "\",\""
                        + intToBase64(5)
                        + "\":\""
                        + intToBase64(106)
                        + "\""
                        + "},"
                        // Add an encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse = new MockResponse().setBody(json);
        MockResponse encoderResponse = new MockResponse().setBody(encodeSignalsJS);

        // Wire signals and encoder endpoint to respective responses
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        switch (request.getPath()) {
                            case SIGNALS_PATH:
                                return signalsResponse;
                            case ENCODER_PATH:
                                return encoderResponse;
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                });
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);

        CountDownLatch updateEventLatch = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer =
                new EncoderUpdateEventTestObserver(updateEventLatch, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer);

        callForUri(uri);
        List<DBProtectedSignal> expected =
                Arrays.asList(
                        generateSignal(1, 101),
                        generateSignal(2, 102),
                        generateSignal(3, 103),
                        generateSignal(4, 105),
                        generateSignal(5, 106));
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);

        // Verify that encoder gets registered, downloaded and persisted
        assertEquals(
                "Encoder endpoint should have been registered",
                encoderUri,
                mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri());

        // Wait for the update event to be completed
        updateEventLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch.getCount());

        assertEquals(
                "Downloaded encoder logic should have been same as one wired with encoder uri",
                encodeSignalsJS,
                mEncoderPersistenceDao.getEncoder(BUYER));

        // Validate that the periodic job for encoding would have been scheduled
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));

        // Manually trigger encoding job worker to validate encoding gets done
        mPeriodicEncodingJobWorker
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                .get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        byte[] payload = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(
                "Encoding JS should have returned size of signals as result",
                new byte[] {(byte) expected.size()},
                payload);
    }

    /**
     * We test the behavior of when an encoder gets updated. We can also reverse map which encoder
     * was used in encoding just by looking at the encoded payload output.
     */
    @Test
    public void testSecondUpdateEncoderDoesNotDownloadEncodingLogic() throws Exception {
        String encodeSignalsJS1 =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array( [0x01] ) };\n"
                        + "}\n";
        Uri encoderUri1 = mMockWebServerRule.uriForPath(ENCODER_PATH + "1");
        String json1 =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(2)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Add an encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri1.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse1 = new MockResponse().setBody(json1);
        MockResponse encoderResponse1 = new MockResponse().setBody(encodeSignalsJS1);
        Uri uri1 = mMockWebServerRule.uriForPath(SIGNALS_PATH + "1");

        String encodeSignalsJS2 =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array( [0x02] )};\n"
                        + "}\n";
        Uri encoderUri2 = mMockWebServerRule.uriForPath(ENCODER_PATH + "2");
        String json2 =
                "{"
                        // Add another encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri2.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse2 = new MockResponse().setBody(json2);
        MockResponse encoderResponse2 = new MockResponse().setBody(encodeSignalsJS2);

        // Wire signals and encoder endpoint to respective responses
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        switch (request.getPath()) {
                            case SIGNALS_PATH + "1":
                                return signalsResponse1;
                            case SIGNALS_PATH + "2":
                                return signalsResponse2;
                            case ENCODER_PATH + "1":
                                return encoderResponse1;
                            case ENCODER_PATH + "2":
                                return encoderResponse2;
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                });
        Uri uri2 = mMockWebServerRule.uriForPath(SIGNALS_PATH + "2");

        CountDownLatch updateEventLatch1 = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer1 =
                new EncoderUpdateEventTestObserver(updateEventLatch1, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer1);

        callForUri(uri1);
        List<DBProtectedSignal> expected =
                Arrays.asList(generateSignal(1, 101), generateSignal(2, 102));
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);

        // Wait for the update event to be completed
        updateEventLatch1.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch1.getCount());

        // Manually trigger encoding job worker to validate encoding gets done
        mPeriodicEncodingJobWorker
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                .get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        byte[] payload1A = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(new byte[] {0x01}, payload1A);

        // We make second call with new encoder logic, but encoder logic will not be downloaded
        callForUri(uri2);

        // Manually trigger encoding job worker to check the encoding logic is still the same
        mPeriodicEncodingJobWorker
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                .get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        byte[] payload1B = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(
                "Encoder should have still remained the same", new byte[] {0x01}, payload1B);

        CountDownLatch updateEventLatch2 = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer2 =
                new EncoderUpdateEventTestObserver(updateEventLatch2, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer2);

        // We make second call with new encoder logic, but we clear any registered encoders
        mEncoderEndpointsDao.deleteEncoderEndpoint(BUYER);
        // This should trigger a new download for new encoder logic
        callForUri(uri2);

        // Wait for the update event to be completed
        updateEventLatch2.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch2.getCount());

        // Manually trigger encoding job worker to check the encoding logic is updated
        mPeriodicEncodingJobWorker
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                .get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted, and we used the second encoder
        byte[] payload2 = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(new byte[] {0x02}, payload2);
    }

    @Test
    public void testPeriodicEncodingUpdatesEncoders_Success() throws Exception {
        String encodeSignalsJS =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "  return {'status' : 0, 'results' : new Uint8Array([signals.size])};\n"
                        + "}\n";
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String json =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(2)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Add an encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse = new MockResponse().setBody(json);
        MockResponse encoderResponse = new MockResponse().setBody(encodeSignalsJS);

        CountDownLatch encoderLogicDownloadedLatch = new CountDownLatch(2);

        // Wire signals and encoder endpoint to respective responses
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        switch (request.getPath()) {
                            case SIGNALS_PATH:
                                return signalsResponse;
                            case ENCODER_PATH:
                                encoderLogicDownloadedLatch.countDown();
                                return encoderResponse;
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                });
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);

        CountDownLatch updateEventLatch = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer =
                new EncoderUpdateEventTestObserver(updateEventLatch, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer);

        callForUri(uri);
        List<DBProtectedSignal> expected =
                Arrays.asList(generateSignal(1, 101), generateSignal(2, 102));
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);

        // Verify that encoder gets registered, downloaded and persisted
        assertEquals(
                "Encoder endpoint should have been registered",
                encoderUri,
                mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri());

        // Wait for the update event to be completed
        updateEventLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals(
                "Download Encoder should have been counted down once",
                1,
                encoderLogicDownloadedLatch.getCount());
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch.getCount());

        assertEquals(
                "Downloaded encoder logic should have been same as one wired with encoder uri",
                encodeSignalsJS,
                mEncoderPersistenceDao.getEncoder(BUYER));

        // Validate that the periodic job for encoding would have been scheduled
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));

        FlagsWithEnabledPeriodicEncoding flagsWithLargeUpdateWindow =
                new FlagsWithEnabledPeriodicEncoding() {
                    @Override
                    public long getProtectedSignalsEncoderRefreshWindowSeconds() {
                        return 20L * 24L * 60L * 60L; // 20 days
                    }
                };

        PeriodicEncodingJobWorker jobWorkerWithLargeTimeWindow =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalsDao,
                        mScriptEngine,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        flagsWithLargeUpdateWindow,
                        mEnrollmentDao,
                        mClock,
                        mAdServicesLoggerMock);

        // Manually trigger encoding job worker to validate encoding gets done
        jobWorkerWithLargeTimeWindow
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                .get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        DBEncodedPayload firstEncodingRun = mEncodedPayloadDao.getEncodedPayload(BUYER);
        byte[] payload = firstEncodingRun.getEncodedPayload();
        assertArrayEquals(
                "Encoding JS should have returned size of signals as result",
                new byte[] {(byte) expected.size()},
                payload);
        assertEquals(
                "Encoder should not have been downloaded again",
                1,
                encoderLogicDownloadedLatch.getCount());

        // We trigger the periodic job worker with flags set to tiny update window
        FlagsWithEnabledPeriodicEncoding flagsWithTinyUpdateWindow =
                new FlagsWithEnabledPeriodicEncoding() {
                    @Override
                    public long getProtectedSignalsEncoderRefreshWindowSeconds() {
                        return 0L;
                    }
                };
        PeriodicEncodingJobWorker jobWorkerWithTinyTimeWindow =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalsDao,
                        mScriptEngine,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        flagsWithTinyUpdateWindow,
                        mEnrollmentDao,
                        mClock,
                        mAdServicesLoggerMock);

        // Manually trigger encoding job worker to validate encoding gets done
        jobWorkerWithTinyTimeWindow
                .encodeProtectedSignals(PAS_ENCODING_SOURCE_TYPE)
                .get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        DBEncodedPayload secondEncodingRun = mEncodedPayloadDao.getEncodedPayload(BUYER);
        byte[] payload2 = secondEncodingRun.getEncodedPayload();
        assertArrayEquals(
                "Encoding JS should have returned size of signals as result",
                new byte[] {(byte) expected.size()},
                payload2);
        // The second run should skip based on the logic that we will skip encoding for unchanged
        // buyer.
        assertEquals(secondEncodingRun.getCreationTime(), firstEncodingRun.getCreationTime());

        encoderLogicDownloadedLatch.await(5, TimeUnit.SECONDS);
        assertEquals(
                "Encoder should have been downloaded second time",
                0,
                encoderLogicDownloadedLatch.getCount());
    }

    private void callForUri(Uri uri) throws Exception {
        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        SignalsIntakeE2ETest.CallbackForTesting callback =
                new SignalsIntakeE2ETest.CallbackForTesting();
        mService.updateSignals(input, callback);
        callback.mSuccessLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
    }

    private DBProtectedSignal generateSignal(int key, int value) {
        return DBProtectedSignal.builder()
                .setBuyer(BUYER)
                .setKey(intToBytes(key))
                .setValue(intToBytes(value))
                .setCreationTime(Instant.now())
                .setPackageName(CommonFixture.TEST_PACKAGE_NAME)
                .build();
    }

    static class EncoderUpdateEventTestObserver implements UpdateEncoderEventHandler.Observer {
        CountDownLatch mCountDownLatch;
        AdTechIdentifier mBuyer;
        boolean mResult;

        EncoderUpdateEventTestObserver(
                CountDownLatch latch, AdTechIdentifier buyer, boolean result) {
            mCountDownLatch = latch;
            mBuyer = buyer;
            mResult = result;
        }

        @Override
        public void update(AdTechIdentifier buyer, String eventType, FluentFuture<?> event) {
            try {
                assertEquals("Encoder update event buyer mismatch", mBuyer, buyer);
                assertEquals(
                        "Encoder update event status mismatch",
                        mResult,
                        ((FluentFuture<Boolean>) event).get(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
                mCountDownLatch.countDown();
            } catch (Exception e) {
                throw new IllegalStateException("Encoder update event failed");
            }
        }
    }

    private static class FlagsWithEnabledPeriodicEncoding implements Flags {
        @Override
        public boolean getGaUxFeatureEnabled() {
            return true;
        }

        @Override
        public boolean getProtectedSignalsPeriodicEncodingEnabled() {
            return true;
        }

        @Override
        public boolean getProtectedSignalsEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public String getPasAppAllowList() {
            return CommonFixture.TEST_PACKAGE_NAME;
        }
    }
}
