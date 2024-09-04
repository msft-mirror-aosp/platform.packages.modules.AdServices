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

package com.android.adservices.service.shell.signals;

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.PPAPI_AND_SYSTEM_SERVER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.os.IBinder;

import androidx.room.Room;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AppPackageNameRetriever;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.signals.ProtectedSignalsServiceImpl;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.signals.SignalsScriptEngine;
import com.android.adservices.service.signals.UpdateProcessingOrchestrator;
import com.android.adservices.service.signals.UpdateSignalsOrchestrator;
import com.android.adservices.service.signals.UpdatesDownloader;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelperImpl;
import com.android.adservices.service.stats.pas.EncodingJobRunStats;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.util.Clock;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpyStatic(FlagsFactory.class)
@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = PPAPI_AND_SYSTEM_SERVER)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@EnableDebugFlag(KEY_PROTECTED_APP_SIGNALS_CLI_ENABLED)
@RequiresSdkLevelAtLeastT(reason = "Protected App Signals is available on T+")
public final class TriggerEncodingCommandE2ETest extends AdServicesExtendedMockitoTestCase {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private static final String PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final String SIGNALS_ENCODING_SCRIPT_PATH = "/script";
    private static final String SIGNALS_ENCODING_SCRIPT =
            "function encodeSignals(signals, maxSize) {\n"
                    + "   return {'status' : 0, 'results' : new Uint8Array([signals.length])};\n"
                    + "}";
    private static final String SIGNALS_UPDATE_PATH = "/signals";
    private static final String SIGNALS_ENCODING_SCRIPT_TEMPLATE = "<encode-signals-script>";
    private static final String SIGNALS_UPDATE_JSON =
            "{\n"
                    + "  \"put\": {\n"
                    + "    \"AAAAAQ==\": \"AAAAZQ==\",\n"
                    + "    \"AAAAAg==\": \"AAAAZg==\"\n"
                    + "  },\n"
                    + "  \"append\": {\n"
                    + "    \"AAAAAw==\": {\n"
                    + "      \"values\": [\"AAAAZw==\"],\n"
                    + "      \"max_signals\": 3\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"put_if_not_present\": {\n"
                    + "    \"AAAABA==\": \"AAAAaQ==\",\n"
                    + "    \"AAAABQ==\": \"AAAAag==\"\n"
                    + "  },\n"
                    + "  \"update_encoder\": {\n"
                    + "    \"action\": \"REGISTER\",\n"
                    + "    \"endpoint\": \""
                    + SIGNALS_ENCODING_SCRIPT_TEMPLATE
                    + "\"\n"
                    + "  }\n"
                    + "}";
    private static final int PAS_API_TIMEOUT_SEC = 5;
    // Use a very low value as we are using a Room in-memory database here.
    public static final int PAS_DATABASE_WRITE_LATENCY_SLEEP_MS = 1000;

    private EncodedPayloadDao mEncodedPayloadDao;
    private TriggerEncodingCommand mTriggerEncodingCommand;

    private final MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    mContext, "adservices_untrusted_test_server.p12", "adservices_test");
    private EncoderEndpointsDao mEncoderEndpointDao;
    private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private ProtectedSignalsServiceImpl mProtectedSignalsService;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private UpdateSignalsProcessReportedLoggerImpl mUpdateSignalsProcessReportedLoggerMock;
    private ProtectedSignalsDao mProtectedSignalsDao;

    @Before
    public void setUp() throws Exception {
        AdservicesTestHelper.killAdservicesProcess(mContext);
        ProtectedSignalsDatabase protectedSignalsDatabase =
                Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class).build();
        AdServicesLogger logger = AdServicesLoggerImpl.getInstance();
        Flags flags = new TestFlags();
        mEncodedPayloadDao = protectedSignalsDatabase.getEncodedPayloadDao();
        mProtectedSignalsDao = protectedSignalsDatabase.protectedSignalsDao();
        mEncoderEndpointDao = protectedSignalsDatabase.getEncoderEndpointsDao();
        mEncoderLogicMetadataDao = protectedSignalsDatabase.getEncoderLogicMetadataDao();
        AdServicesHttpsClient httpClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        EncoderLogicHandler encoderLogicHandler =
                new EncoderLogicHandler(
                        EncoderPersistenceDao.getInstance(mContext),
                        mEncoderEndpointDao,
                        mEncoderLogicMetadataDao,
                        mProtectedSignalsDao,
                        httpClient,
                        AdServicesExecutors.getBackgroundExecutor(),
                        logger,
                        flags);
        RetryStrategy retryStrategy =
                RetryStrategyFactory.createInstance(
                                flags.getAdServicesRetryStrategyEnabled(),
                                AdServicesExecutors.getLightWeightExecutor())
                        .createRetryStrategy(flags.getAdServicesJsScriptEngineMaxRetryAttempts());
        mocker.mockGetFlags(flags);
        int maxJsFailures =
                flags.getProtectedSignalsMaxJsFailureExecutionOnCertainVersionBeforeStop();
        boolean jsIsolateMessagesInLogs = true;
        SignalsProviderAndArgumentFactory signalsProviderAndArgumentFactory =
                new SignalsProviderAndArgumentFactory(mProtectedSignalsDao, true);
        mTriggerEncodingCommand =
                new TriggerEncodingCommand(
                        new PeriodicEncodingJobRunner(
                                signalsProviderAndArgumentFactory,
                                mProtectedSignalsDao,
                                new SignalsScriptEngine(
                                        flags::getIsolateMaxHeapSizeBytes,
                                        retryStrategy,
                                        () -> jsIsolateMessagesInLogs),
                                maxJsFailures,
                                flags.getProtectedSignalsEncodedPayloadMaxSizeBytes(),
                                encoderLogicHandler,
                                protectedSignalsDatabase.getEncodedPayloadDao(),
                                AdServicesExecutors.getBackgroundExecutor(),
                                AdServicesExecutors.getLightWeightExecutor()),
                        encoderLogicHandler,
                        new EncodingExecutionLogHelperImpl(
                                logger, Clock.getInstance(), EnrollmentDao.getInstance()),
                        new EncodingJobRunStatsLoggerImpl(logger, EncodingJobRunStats.builder()),
                        mEncoderLogicMetadataDao);

        mProtectedSignalsService =
                new ProtectedSignalsServiceImpl(
                        mContext,
                        new UpdateSignalsOrchestrator(
                                AdServicesExecutors.getBackgroundExecutor(),
                                new UpdatesDownloader(
                                        AdServicesExecutors.getLightWeightExecutor(), httpClient),
                                new UpdateProcessingOrchestrator(
                                        mProtectedSignalsDao,
                                        new UpdateProcessorSelector(),
                                        new UpdateEncoderEventHandler(
                                                mEncoderEndpointDao, encoderLogicHandler),
                                        new SignalEvictionController()),
                                new AdTechUriValidator(
                                        "caller",
                                        "",
                                        TriggerEncodingCommandE2ETest.class.getName(),
                                        "updateUri"),
                                java.time.Clock.systemUTC()),
                        FledgeAuthorizationFilter.create(mContext, logger),
                        mConsentManagerMock,
                        new TestDevContextFilter(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        logger,
                        flags,
                        new CallingAppUidSupplierProcessImpl(),
                        new ProtectedSignalsServiceFilter(
                                mContext,
                                new FledgeConsentFilter(mConsentManagerMock, logger),
                                flags,
                                AppImportanceFilter.create(
                                        mContext, flags::getForegroundStatuslLevelForValidation),
                                FledgeAuthorizationFilter.create(mContext, logger),
                                new FledgeAllowListsFilter(flags, logger),
                                new FledgeApiThrottleFilter(Throttler.getInstance(flags), logger)),
                        EnrollmentDao.getInstance(),
                        mUpdateSignalsProcessReportedLoggerMock);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false);
        MockitoAnnotations.initMocks(this);
    }

    @Ignore("b/359519167")
    @Test
    public void run_happyPath_encodedSignalsArePresentInDb() throws Exception {
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (request.getPath().equals(SIGNALS_ENCODING_SCRIPT_PATH)) {
                            return new MockResponse().setBody(SIGNALS_ENCODING_SCRIPT);
                        } else if (request.getPath().equals(SIGNALS_UPDATE_PATH)) {
                            return new MockResponse()
                                    .setBody(
                                            SIGNALS_UPDATE_JSON.replace(
                                                    SIGNALS_ENCODING_SCRIPT_TEMPLATE,
                                                    mMockWebServerRule
                                                            .uriForPath(
                                                                    SIGNALS_ENCODING_SCRIPT_PATH)
                                                            .toString()));
                        }
                        throw new IllegalStateException("invalid path");
                    }
                });
        assertThat(mEncoderEndpointDao.getEndpoint(BUYER)).isNull();
        CountDownLatch latch = new CountDownLatch(1);
        mProtectedSignalsService.updateSignals(
                new UpdateSignalsInput.Builder(
                                mMockWebServerRule.uriForPath(SIGNALS_UPDATE_PATH), PACKAGE_NAME)
                        .setCallerPackageName(PACKAGE_NAME)
                        .build(),
                new UpdateSignalsCallback() {
                    @Override
                    public void onSuccess() {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(FledgeErrorResponse responseParcel) {
                        latch.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
        assertThat(latch.await(PAS_API_TIMEOUT_SEC, TimeUnit.SECONDS)).isTrue();
        // Wait for database event before continuing. PAS updates are asynchronous so this is the
        // only way to consistently wait. Adding this after the updateSignals API returns lowers any
        // potential flakiness.
        sLogger.v("Sleeping while PAS update writes take place...");
        Thread.sleep(PAS_DATABASE_WRITE_LATENCY_SLEEP_MS);
        sLogger.v("Checking if encoding endpoint is registered...");
        assertThat(mEncoderEndpointDao.getEndpoint(BUYER)).isNotNull();
        assertThat(mEncodedPayloadDao.doesEncodedPayloadExist(BUYER)).isFalse();
        assertThat(mProtectedSignalsDao.getSignalsByBuyer(BUYER)).isNotEmpty();
        sLogger.v("Signals present on device but no encoded payload.");

        PrintWriter devNull =
                new PrintWriter(new OutputStreamWriter(new FileOutputStream("/dev/null")));
        mTriggerEncodingCommand.run(
                devNull,
                devNull,
                new String[] {
                    SignalsShellCommandFactory.COMMAND_PREFIX,
                    TriggerEncodingCommand.CMD,
                    SignalsShellCommandArgs.BUYER,
                    BUYER.toString(),
                });

        assertThat(mEncoderEndpointDao.getEndpoint(BUYER)).isNotNull();
        assertThat(mEncoderLogicMetadataDao.doesEncoderExist(BUYER)).isTrue();
        assertThat(mEncodedPayloadDao.doesEncodedPayloadExist(BUYER)).isTrue();
        DBEncodedPayload payload = mEncodedPayloadDao.getEncodedPayload(BUYER);
        assertThat(payload.getVersion()).isEqualTo(0);
        assertThat(payload.getEncodedPayload()).isEqualTo(new byte[] {0x00});
    }

    private static final class TestDevContextFilter extends DevContextFilter {

        TestDevContextFilter() {
            super(
                    sContext.getContentResolver(),
                    sContext.getPackageManager(),
                    AppPackageNameRetriever.create(sContext));
        }

        @Override
        public DevContext createDevContext() throws IllegalStateException {
            return DevContext.createForDevIdentity();
        }
    }

    private static final class TestFlags implements Flags {

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getProtectedSignalsEnabled() {
            return true;
        }

        @Override
        public int getConsentSourceOfTruth() {
            return FlagsConstants.PPAPI_AND_SYSTEM_SERVER;
        }

        @Override
        public boolean getEnforceForegroundStatusForSignals() {
            return false;
        }
    }
}
