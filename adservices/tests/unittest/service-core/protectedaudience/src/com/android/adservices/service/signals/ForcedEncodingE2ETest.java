/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.adservices.common.CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.signals.SignalsFixture.assertSignalsUnorderedListEqualsExceptIdAndTime;
import static com.android.adservices.service.signals.SignalsFixture.intToBase64;
import static com.android.adservices.service.signals.SignalsFixture.intToBytes;
import static com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler.ACTION_REGISTER_ENCODER_LOGIC_COMPLETE;
import static com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler.FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED;
import static com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler.FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_EMPTY_RESPONSE_FROM_CLIENT_DOWNLOADING_ENCODER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBEncoderEndpoint;
import com.android.adservices.data.signals.DBEncoderLogicMetadata;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
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
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.shared.testing.BroadcastReceiverSyncCallback;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.concurrency.SimpleSyncCallback;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.collect.ImmutableList;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ExtendedMockitoRule.MockStatic(PeriodicEncodingJobService.class)
@RequiresSdkLevelAtLeastT
public final class ForcedEncodingE2ETest extends AdServicesExtendedMockitoTestCase {
    private static final int TIMEOUT_MS = 30_000;
    private static final boolean ISOLATE_CONSOLE_MESSAGE_IN_LOGS_ENABLED = true;
    private static final IsolateSettings ISOLATE_SETTINGS_WITH_MAX_HEAP_ENFORCEMENT_ENABLED =
            IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                    ISOLATE_CONSOLE_MESSAGE_IN_LOGS_ENABLED);
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final long WAIT_TIME_SECONDS = 1L;
    private static final String SIGNALS_PATH = "/signals";
    private static final Clock CLOCK = Clock.getInstance();
    private static final Instant NOW = Instant.ofEpochMilli(CLOCK.currentTimeMillis());
    private static final List<DBProtectedSignal> DB_PROTECTED_SIGNALS =
            Arrays.asList(
                    getDBProtectedSignal(1, 101),
                    getDBProtectedSignal(2, 102),
                    getDBProtectedSignal(3, 103),
                    getDBProtectedSignal(4, 105),
                    getDBProtectedSignal(5, 106));
    private static final String ENCODER_PATH = "/encoder";
    private static final String ENCODING_SCRIPT_LENGTH_JS =
            "\nfunction encodeSignals(signals, maxSize) {\n"
                    + "   return {'status': 0, 'results': new Uint8Array([signals.size])};\n"
                    + "}\n";
    private static final byte[] ENCODED_PAYLOAD = new byte[] {(byte) DB_PROTECTED_SIGNALS.size()};

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Rule(order = 12)
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    private AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImplMock;

    @Mock
    private UpdateSignalsProcessReportedLoggerImpl mUpdateSignalsProcessReportedLoggerImplMock;

    @Spy
    private FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(new ForcedEncodingE2ETestFlags(), mAdServicesLoggerMock);

    private FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy;

    private Flags mFakeFlags;
    private ProtectedSignalsDao mSignalsDao;
    private EncoderEndpointsDao mEncoderEndpointsDao;
    private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private EnrollmentDao mEnrollmentDao;
    private EncoderPersistenceDao mEncoderPersistenceDao;
    private ListeningExecutorService mLightweightExecutor;
    private ListeningExecutorService mBackgroundExecutor;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private EncoderLogicHandler mEncoderLogicHandler;
    private SignalsScriptEngine mScriptEngine;
    private PeriodicEncodingJobWorker mPeriodicEncodingJobWorker;
    private ForcedEncoderImpl mForcedEncoder;
    private UpdateEncoderEventHandler mUpdateEncoderEventHandler;
    private SignalEvictionController mSignalEvictionController;
    private UpdateProcessorSelector mUpdateProcessorSelector;
    private UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;
    private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilter;
    private UpdatesDownloader mUpdatesDownloader;
    private AdTechUriValidator mAdtechUriValidator;
    private UpdateSignalsOrchestrator mUpdateSignalsOrchestrator;

    private ProtectedSignalsServiceImpl mService;
    @Before
    public void setup() {
        mFakeFlags = new ForcedEncodingE2ETestFlags();

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
                new EnrollmentDao(mSpyContext, DbTestUtil.getSharedDbHelperForTest(), mFakeFlags);
        mEncoderPersistenceDao = EncoderPersistenceDao.getInstance();

        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutor = AdServicesExecutors.getBackgroundExecutor();

        mAdServicesHttpsClient = new AdServicesHttpsClient(mBackgroundExecutor, 2000, 2000, 10000);

        mEncoderLogicHandler =
                new EncoderLogicHandler(
                        mEncoderPersistenceDao,
                        mEncoderEndpointsDao,
                        mEncoderLogicMetadataDao,
                        mSignalsDao,
                        mAdServicesHttpsClient,
                        mBackgroundExecutor,
                        mAdServicesLoggerMock,
                        mFakeFlags);

        RetryStrategy retryStrategy = new NoOpRetryStrategyImpl();
        mScriptEngine =
                new SignalsScriptEngine(
                        ISOLATE_SETTINGS_WITH_MAX_HEAP_ENFORCEMENT_ENABLED::getMaxHeapSizeBytes,
                        retryStrategy,
                        ISOLATE_SETTINGS_WITH_MAX_HEAP_ENFORCEMENT_ENABLED
                                ::getIsolateConsoleMessageInLogsEnabled);

        mPeriodicEncodingJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalsDao,
                        mScriptEngine,
                        mBackgroundExecutor,
                        mLightweightExecutor,
                        mFakeFlags,
                        mEnrollmentDao,
                        CLOCK,
                        mAdServicesLoggerMock);

        mForcedEncoder =
                new ForcedEncoderImpl(
                        mFakeFlags.getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds(),
                        mEncoderLogicHandler,
                        mEncodedPayloadDao,
                        mSignalsDao,
                        mPeriodicEncodingJobWorker,
                        mBackgroundExecutor,
                        CLOCK);

        mUpdateEncoderEventHandler =
                new UpdateEncoderEventHandler(
                        mEncoderEndpointsDao,
                        mEncoderLogicHandler,
                        mSpyContext,
                        AdServicesExecutors.getBackgroundExecutor(),
                        /* isCompletionBroadcastEnabled= */ true,
                        mForcedEncoder,
                        /* isForcedEncodingBroadcastEnabled= */ true);

        int oversubscriptionBytesLimit =
                mFakeFlags.getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes();
        mSignalEvictionController =
                new SignalEvictionController(
                        ImmutableList.of(),
                        mFakeFlags.getProtectedSignalsMaxSignalSizePerBuyerBytes(),
                        oversubscriptionBytesLimit);

        mUpdateProcessorSelector = new UpdateProcessorSelector();

        mUpdateProcessingOrchestrator =
                new UpdateProcessingOrchestrator(
                        mSignalsDao,
                        mUpdateProcessorSelector,
                        mUpdateEncoderEventHandler,
                        mSignalEvictionController,
                        mForcedEncoder);

        mFledgeAuthorizationFilterSpy =
                spy(
                        new FledgeAuthorizationFilter(
                                mSpyContext.getPackageManager(),
                                mEnrollmentDao,
                                mAdServicesLoggerMock));

        mProtectedSignalsServiceFilter =
                new ProtectedSignalsServiceFilter(
                        mSpyContext,
                        mFledgeConsentFilterMock,
                        mFakeFlags,
                        mAppImportanceFilterMock,
                        mFledgeAuthorizationFilterSpy,
                        mFledgeAllowListsFilterSpy,
                        mFledgeApiThrottleFilterMock);

        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);
        doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();

        mUpdatesDownloader = new UpdatesDownloader(mLightweightExecutor, mAdServicesHttpsClient);

        mAdtechUriValidator = new AdTechUriValidator("", "", "", "");

        mUpdateSignalsOrchestrator =
                new UpdateSignalsOrchestrator(
                        mBackgroundExecutor,
                        mUpdatesDownloader,
                        mUpdateProcessingOrchestrator,
                        mAdtechUriValidator,
                        FIXED_CLOCK_TRUNCATED_TO_MILLI);

        mService =
                new ProtectedSignalsServiceImpl(
                        mSpyContext,
                        mUpdateSignalsOrchestrator,
                        mFledgeAuthorizationFilterSpy,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        AdServicesExecutors.getBackgroundExecutor(),
                        mAdServicesLoggerImplMock,
                        mFakeFlags,
                        mMockDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mProtectedSignalsServiceFilter,
                        mEnrollmentDao,
                        mUpdateSignalsProcessReportedLoggerImplMock);

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

    private static String getRawSignalsWithEncoderEndpoint(Uri uri) {
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
                        + "}";

        if (uri != null) {
            // Add an encoder registration event
            json +=
                    ",\"update_encoder\" : {\n"
                            + "\t\"action\" : \"REGISTER\",\n"
                            + "\t\"endpoint\" : \""
                            + uri.toString()
                            + "\"\n"
                            + "  }";
        }

        return json + "}";
    }

    private static String getRawSignals() {
        return getRawSignalsWithEncoderEndpoint(null);
    }

    private static DBProtectedSignal getDBProtectedSignal(int key, int value) {
        return DBProtectedSignal.builder()
                .setBuyer(BUYER)
                .setKey(intToBytes(key))
                .setValue(intToBytes(value))
                .setCreationTime(NOW)
                .setPackageName(TEST_PACKAGE_NAME)
                .build();
    }

    private void setupServer(Map<String, String> signalsMap, Map<String, String> encodersMap)
            throws Exception {
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        String requestPath = request.getPath();
                        if (signalsMap != null && signalsMap.containsKey(requestPath)) {
                            return new MockResponse().setBody(signalsMap.get(requestPath));
                        } else if (encodersMap != null && encodersMap.containsKey(requestPath)) {
                            return new MockResponse().setBody(encodersMap.get(requestPath));
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                });
    }

    @Test
    public void test_existingEncodedPayload_createdBeforeCooldownWindowStart_attempted()
            throws Exception {
        // Generate signals and encoders.
        Uri rawSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String rawSignals = getRawSignalsWithEncoderEndpoint(encoderUri);
        String encoder = ENCODING_SCRIPT_LENGTH_JS;

        // Persist an encoder and its related metadata.
        DBEncoderEndpoint endpoint =
                DBEncoderEndpoint.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setDownloadUri(encoderUri)
                        .build();
        mEncoderEndpointsDao.registerEndpoint(endpoint);
        mEncoderPersistenceDao.persistEncoder(BUYER, ENCODING_SCRIPT_LENGTH_JS);
        DBEncoderLogicMetadata encoderLogicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setVersion(1)
                        .build();
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(encoderLogicEntry);

        // Persist an encoded payload created before the cooldown window start.
        Duration cooldownWindow =
                Duration.ofSeconds(
                        mFakeFlags.getFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds());
        DBEncodedPayload dbEncodedPayload =
                DBEncodedPayload.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW.minus(cooldownWindow))
                        .setVersion(1)
                        .setEncodedPayload(new byte[] {(byte) (DB_PROTECTED_SIGNALS.size() - 1)})
                        .build();
        mEncodedPayloadDao.persistEncodedPayload(dbEncodedPayload);

        BroadcastReceiverSyncCallback forcedEncodingCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED, TIMEOUT_MS);

        BroadcastReceiverSyncCallback downloadCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE, TIMEOUT_MS);

        // Wire signals and encoder endpoint to respective responses.
        setupServer(Map.of(SIGNALS_PATH, rawSignals), Map.of(ENCODER_PATH, encoder));

        // Call updateSignals with uri.
        callUpdateSignalsWith(rawSignalsUri);

        // Verify that expected raw signals are persisted.
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(DB_PROTECTED_SIGNALS, actual);

        // Verify that expected encoder gets registered.
        assertWithMessage("Encoder endpoint should have been registered.")
                .that(mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri())
                .isEqualTo(encoderUri);

        // Wait for broadcast message
        downloadCallback.assertResultReceived();

        // Verify expected encoder is downloaded.
        assertWithMessage(
                        "Downloaded encoder logic should have been same as one wired with encoder"
                                + " uri.")
                .that(mEncoderPersistenceDao.getEncoder(BUYER))
                .isEqualTo(ENCODING_SCRIPT_LENGTH_JS);

        // Make sure the forced encoding job finished before checking the db
        forcedEncodingCallback.assertResultReceived();

        // Verify expected encoded payload is persisted.
        byte[] payload = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertWithMessage("Encoding payload should be the number of raw signals.")
                .that(payload)
                .isEqualTo(ENCODED_PAYLOAD);
    }

    @Test
    public void test_existingEncodedPayload_createdWithinCooldownWindow_skipped() throws Exception {
        // Generate signals and encoders.
        Uri rawSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String rawSignals = getRawSignalsWithEncoderEndpoint(encoderUri);
        String encoder = ENCODING_SCRIPT_LENGTH_JS;

        // Persist an encoder and its related metadata.
        DBEncoderEndpoint endpoint =
                DBEncoderEndpoint.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setDownloadUri(encoderUri)
                        .build();
        mEncoderEndpointsDao.registerEndpoint(endpoint);
        mEncoderPersistenceDao.persistEncoder(BUYER, ENCODING_SCRIPT_LENGTH_JS);
        DBEncoderLogicMetadata encoderLogicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setVersion(1)
                        .build();
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(encoderLogicEntry);

        // Persist an encoded payload created within the cooldown window.
        byte[] differentEncodedPayload = new byte[] {(byte) (DB_PROTECTED_SIGNALS.size() - 1)};
        DBEncodedPayload dbEncodedPayload =
                DBEncodedPayload.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setVersion(1)
                        .setEncodedPayload(differentEncodedPayload)
                        .build();
        mEncodedPayloadDao.persistEncodedPayload(dbEncodedPayload);

        // Wire signals and encoder endpoint to respective responses.
        setupServer(Map.of(SIGNALS_PATH, rawSignals), Map.of(ENCODER_PATH, encoder));

        BroadcastReceiverSyncCallback forcedEncodingCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED, TIMEOUT_MS);

        BroadcastReceiverSyncCallback downloadCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE, TIMEOUT_MS);

        // Call updateSignals with uri.
        callUpdateSignalsWith(rawSignalsUri);

        // Verify that expected raw signals are persisted.
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(DB_PROTECTED_SIGNALS, actual);

        // Verify that expected encoder gets registered.
        assertWithMessage("Encoder endpoint should have been registered.")
                .that(mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri())
                .isEqualTo(encoderUri);

        // Wait for broadcast message
        downloadCallback.assertResultReceived();

        // Verify expected encoder is downloaded.
        assertWithMessage(
                        "Downloaded encoder logic should have been same as one wired with encoder"
                                + " uri.")
                .that(mEncoderPersistenceDao.getEncoder(BUYER))
                .isEqualTo(ENCODING_SCRIPT_LENGTH_JS);

        // Make sure the forced encoding job finished before checking the db
        forcedEncodingCallback.assertResultReceived();

        // Verify expected encoded payload is persisted.
        byte[] payload = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertWithMessage("Encoding payload should be the number of raw signals.")
                .that(payload)
                .isEqualTo(differentEncodedPayload);
    }

    @Test
    public void test_noExistingEncodedPayload_hasRawSignals_attempted() throws Exception {
        // Generate signals and encoders.
        Uri rawSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String rawSignals = getRawSignalsWithEncoderEndpoint(encoderUri);
        String encoder = ENCODING_SCRIPT_LENGTH_JS;

        BroadcastReceiverSyncCallback forcedEncodingCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED, TIMEOUT_MS);

        BroadcastReceiverSyncCallback downloadCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE, TIMEOUT_MS);

        // Wire signals and encoder endpoint to respective responses.
        setupServer(Map.of(SIGNALS_PATH, rawSignals), Map.of(ENCODER_PATH, encoder));

        // Call updateSignals with uri.
        callUpdateSignalsWith(rawSignalsUri);

        // Verify that expected raw signals are persisted.
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(DB_PROTECTED_SIGNALS, actual);

        // Verify that expected encoder gets registered.
        assertWithMessage("Encoder endpoint should have been registered.")
                .that(mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri())
                .isEqualTo(encoderUri);

        downloadCallback.assertResultReceived();

        // Verify expected encoder is downloaded.
        assertWithMessage(
                        "Downloaded encoder logic should have been same as one wired with encoder"
                                + " uri.")
                .that(mEncoderPersistenceDao.getEncoder(BUYER))
                .isEqualTo(ENCODING_SCRIPT_LENGTH_JS);

        // Make sure the forced encoding job finished before checking the db
        forcedEncodingCallback.assertResultReceived();

        // Verify expected encoded payload is persisted.
        byte[] payload = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertWithMessage("Encoding payload should be the number of raw signals.")
                .that(payload)
                .isEqualTo(ENCODED_PAYLOAD);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_EMPTY_RESPONSE_FROM_CLIENT_DOWNLOADING_ENCODER,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
    public void test_noExistingEncodedPayload_downloadedEmptyEncoder_skipped() throws Exception {
        // Generate signals and encoders.
        Uri rawSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String rawSignals = getRawSignalsWithEncoderEndpoint(encoderUri);

        // Wire signals and encoder endpoint to respective responses.
        setupServer(Map.of(SIGNALS_PATH, rawSignals), Map.of(ENCODER_PATH, ""));

        BroadcastReceiverSyncCallback forcedEncodingCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, FORCED_ENCODING_COMPLETED_ENCODING_NOT_ATTEMPTED, TIMEOUT_MS);

        BroadcastReceiverSyncCallback downloadCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE, TIMEOUT_MS);

        // Call updateSignals with uri.
        callUpdateSignalsWith(rawSignalsUri);

        // Verify that expected raw signals are persisted.
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(DB_PROTECTED_SIGNALS, actual);

        // Verify that expected encoder gets registered.
        assertWithMessage("Encoder endpoint should have been registered.")
                .that(mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri())
                .isEqualTo(encoderUri);

        downloadCallback.assertResultReceived();

        // Verify expected encoder is downloaded.
        assertWithMessage("Encoder logic download should have failed.")
                .that(mEncoderPersistenceDao.getEncoder(BUYER))
                .isNull();

        // Make sure the forced encoding job finished before checking the db
        forcedEncodingCallback.assertResultReceived();

        // Verify absent encoded payload.
        assertWithMessage("Encoding payload should be absent.")
                .that(mEncodedPayloadDao.getEncodedPayload(BUYER))
                .isNull();
    }

    @Test
    public void test_noExistingEncodedPayload_downloadAndUpdateFails_skipped() throws Exception {
        // Generate signals and encoders.
        Uri rawSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String rawSignals = getRawSignalsWithEncoderEndpoint(encoderUri);

        // Wire signals and encoder endpoint to respective responses.
        setupServer(Map.of(SIGNALS_PATH, rawSignals), null);

        // Call updateSignals with uri.
        callUpdateSignalsWith(rawSignalsUri);

        // Verify that expected raw signals are persisted.
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(DB_PROTECTED_SIGNALS, actual);

        // Verify that expected encoder gets registered.
        assertWithMessage("Encoder endpoint should have been registered.")
                .that(mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri())
                .isEqualTo(encoderUri);

        // Verify expected encoder is downloaded.
        assertWithMessage("Encoder logic download should have failed.")
                .that(mEncoderPersistenceDao.getEncoder(BUYER))
                .isNull();

        // Verify absent encoded payload.
        assertWithMessage("Encoding payload should be absent.")
                .that(mEncodedPayloadDao.getEncodedPayload(BUYER))
                .isNull();
    }

    @Test
    public void test_noExistingEncodedPayload_previousEncoderExists_attempted() throws Exception {
        // Generate signals and encoders.
        Uri rawSignalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
        String rawSignals = getRawSignalsWithEncoderEndpoint(encoderUri);
        String encoder = ENCODING_SCRIPT_LENGTH_JS;

        // Persist and verify an encoder and its related metadata.
        DBEncoderEndpoint endpoint =
                DBEncoderEndpoint.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setDownloadUri(encoderUri)
                        .build();
        mEncoderEndpointsDao.registerEndpoint(endpoint);
        mEncoderPersistenceDao.persistEncoder(BUYER, ENCODING_SCRIPT_LENGTH_JS);
        DBEncoderLogicMetadata encoderLogicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setVersion(1)
                        .build();
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(encoderLogicEntry);

        // Wire signals and encoder endpoint to respective responses.
        setupServer(Map.of(SIGNALS_PATH, rawSignals), Map.of(ENCODER_PATH, encoder));

        BroadcastReceiverSyncCallback forcedEncodingCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, FORCED_ENCODING_COMPLETED_ENCODING_ATTEMPTED, TIMEOUT_MS);

        BroadcastReceiverSyncCallback downloadCallback =
                new BroadcastReceiverSyncCallback(
                        mSpyContext, ACTION_REGISTER_ENCODER_LOGIC_COMPLETE, TIMEOUT_MS);

        // Call updateSignals with uri.
        callUpdateSignalsWith(rawSignalsUri);

        // Verify that expected raw signals are persisted.
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(DB_PROTECTED_SIGNALS, actual);

        // Verify that expected encoder gets registered.
        assertWithMessage("Encoder endpoint should have been registered.")
                .that(mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri())
                .isEqualTo(encoderUri);

        downloadCallback.assertResultReceived();

        // Verify expected encoder is downloaded.
        assertWithMessage(
                        "Downloaded encoder logic should have been same as one wired with encoder"
                                + " uri.")
                .that(mEncoderPersistenceDao.getEncoder(BUYER))
                .isEqualTo(ENCODING_SCRIPT_LENGTH_JS);

        // Make sure the forced encoding job finished before checking the db
        forcedEncodingCallback.assertResultReceived();

        // Verify expected encoded payload is persisted.
        byte[] payload = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertWithMessage("Encoding payload should be the number of raw signals.")
                .that(payload)
                .isEqualTo(ENCODED_PAYLOAD);
    }

    private void callUpdateSignalsWith(Uri uri) throws Exception {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(uri, TEST_PACKAGE_NAME).build();
        ForcedEncodingE2ETestCallback callback = new ForcedEncodingE2ETestCallback();

        mService.updateSignals(input, callback);

        callback.mSuccessCallBack.assertCalled();
    }

    private static class ForcedEncodingE2ETestCallback implements UpdateSignalsCallback {
        SimpleSyncCallback mSuccessCallBack = new SimpleSyncCallback();
        SimpleSyncCallback mFailureCallBack = new SimpleSyncCallback();
        FledgeErrorResponse mFailureCause =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(123456)
                        .setErrorMessage("INVALID")
                        .build();

        @Override
        public void onSuccess() throws RemoteException {
            mSuccessCallBack.setCalled();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFailureCause = fledgeErrorResponse;
            mFailureCallBack.setCalled();
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    private static class ForcedEncodingE2ETestFlags implements Flags {
        @Override
        public boolean getProtectedSignalsEnabled() {
            return true;
        }

        @Override
        public String getPasAppAllowList() {
            return TEST_PACKAGE_NAME;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeEnableForcedEncodingAfterSignalsUpdate() {
            return true;
        }

        @Override
        public boolean getProtectedSignalsPeriodicEncodingEnabled() {
            return true;
        }
    }
}
