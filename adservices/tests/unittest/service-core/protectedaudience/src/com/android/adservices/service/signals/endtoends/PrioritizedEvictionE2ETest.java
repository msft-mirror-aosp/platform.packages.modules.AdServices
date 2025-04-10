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

package com.android.adservices.service.signals.endtoends;

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.CommonFixture.FIXED_EARLIER_ONE_DAY;
import static android.adservices.common.CommonFixture.FIXED_NEXT_ONE_DAY;
import static android.adservices.common.CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_PRODUCT_METRICS_V1_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLE_PRIORITIZED_EVICTION;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_UPDATE_SCHEMA_VERSION;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_2;
import static com.android.adservices.service.signals.SignalsFixture.KEY_3;
import static com.android.adservices.service.signals.SignalsFixture.KEY_4;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_2;
import static com.android.adservices.service.signals.SignalsFixture.toBase64;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIGNAL_EVICTOR_PRIORITIZED_FIFO;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_SMALL;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsInput;
import android.net.Uri;

import androidx.room.Room;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.annotations.SetPasAppAllowList;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.signals.ForcedEncoder;
import com.android.adservices.service.signals.ProtectedSignalsServiceImpl;
import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import com.android.adservices.service.signals.SignalsFixture.UpdateSignalsSyncCallback;
import com.android.adservices.service.signals.UpdateProcessingOrchestrator;
import com.android.adservices.service.signals.UpdateSignalsOrchestrator;
import com.android.adservices.service.signals.UpdatesDownloader;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.signals.updateprocessors.evictionpriority.EvictionPriorityHandlerFactory;
import com.android.adservices.service.signals.updateprocessors.updateencoder.UpdateEncoderEventHandler;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerFactory;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLoggerImpl;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedStats;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@RequiresSdkLevelAtLeastT
@SetPasAppAllowList
@SetFlagDisabled(KEY_GLOBAL_KILL_SWITCH)
@SetFlagTrue(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagTrue(KEY_PROTECTED_SIGNALS_ENABLED)
@SetFlagTrue(KEY_PAS_PRODUCT_METRICS_V1_ENABLED)
@SetFlagTrue(KEY_PROTECTED_SIGNALS_ENABLE_PRIORITIZED_EVICTION)
@SetIntegerFlag(name = KEY_PROTECTED_SIGNALS_UPDATE_SCHEMA_VERSION, value = 1)
public class PrioritizedEvictionE2ETest extends AdServicesExtendedMockitoTestCase {
    private static final ListeningExecutorService BACKGROUND_EXECUTOR =
            AdServicesExecutors.getBackgroundExecutor();
    private static final ListeningExecutorService LIGHTWEIGHT_EXECUTOR =
            AdServicesExecutors.getLightWeightExecutor();
    private static final Clock SYSTEM_CLOCK = Clock.getInstance();
    private static final String SIGNALS_PATH = "/signals";
    private static final String UPDATE_SCHEMA_VERSION_HEADER =
            "X-PROTECTED-SIGNALS-UPDATE-SCHEMA-VERSION";
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");

    private static final DBProtectedSignal SIGNAL =
            DBProtectedSignal.builder()
                    .setBuyer(BUYER)
                    .setKey(KEY_1)
                    .setValue(VALUE_1)
                    .setCreationTime(FIXED_NOW_TRUNCATED_TO_MILLI)
                    .setPackageName(TEST_PACKAGE_NAME)
                    .setEvictionPriority(EvictionPriority.EVICT_SOONER)
                    .build();

    @Mock private UpdateEncoderEventHandler mUpdateEncoderEventHandlerMock;
    @Mock private ForcedEncoder mForcedEncoderMock;
    @Mock private java.time.Clock mClockMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private FledgeAllowListsFilter mFledgeAllowListsFilterMock;
    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;
    @Mock private AdServicesLoggerImpl mAdServicesLoggerImplMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;

    @Mock
    private UpdateSignalsProcessReportedLoggerFactory
            mUpdateSignalsProcessReportedLoggerFactoryMock;

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private ProtectedSignalsDao mProtectedSignalsDao;
    private ProtectedSignalsServiceImpl mService;

    @Before
    public void setup() {
        mProtectedSignalsDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();

        when(mConsentManagerMock.isPasConsentGiven()).thenReturn(true);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        when(mUpdateSignalsProcessReportedLoggerFactoryMock.getLoggerInstance())
                .thenReturn(
                        new UpdateSignalsProcessReportedLoggerImpl(
                                mAdServicesLoggerImplMock, SYSTEM_CLOCK));

        mService =
                new ProtectedSignalsServiceImpl(
                        mSpyContext,
                        getUpdateSignalsOrchestrator(),
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        BACKGROUND_EXECUTOR,
                        mAdServicesLoggerImplMock,
                        mFakeFlags,
                        mFakeDebugFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new ProtectedSignalsServiceFilter(
                                mSpyContext,
                                mFledgeConsentFilterMock,
                                mFakeFlags,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterMock,
                                mFledgeApiThrottleFilterMock),
                        mEnrollmentDaoMock,
                        mUpdateSignalsProcessReportedLoggerFactoryMock);
    }

    private UpdateSignalsOrchestrator getUpdateSignalsOrchestrator() {
        return new UpdateSignalsOrchestrator(
                BACKGROUND_EXECUTOR,
                new UpdatesDownloader(
                        LIGHTWEIGHT_EXECUTOR,
                        new AdServicesHttpsClient(
                                BACKGROUND_EXECUTOR,
                                /* connectTimeoutMs= */ 2000,
                                /* readTimeoutMs= */ 2000,
                                /* maxBytes= */ 10000),
                        mFakeFlags.getProtectedSignalsUpdateSchemaVersion()),
                getUpdateProcessingOrchestrator(),
                new AdTechUriValidator(
                        /* adTechRole= */ "",
                        /* adTechIdentifier= */ "",
                        /* className= */ "",
                        /* uriFieldName= */ ""),
                mClockMock);
    }

    private UpdateProcessingOrchestrator getUpdateProcessingOrchestrator() {
        return new UpdateProcessingOrchestrator(
                mProtectedSignalsDao,
                new UpdateProcessorSelector(
                        new EvictionPriorityHandlerFactory(
                                mFakeFlags.getProtectedSignalsEnablePrioritizedEviction())),
                mUpdateEncoderEventHandlerMock,
                new SignalEvictionController(
                        mFakeFlags.getProtectedSignalsMaxSignalSizePerBuyerBytes(),
                        mFakeFlags
                                .getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes(),
                        mFakeFlags.getProtectedSignalsEnablePrioritizedEviction()),
                mForcedEncoderMock);
    }

    @Test
    public void testUpdateSignals_simplePut() throws Exception {
        String updateJson =
                String.format(
                        Locale.ENGLISH,
                        """
                        {
                          "put": {
                            "%s": {
                              "value": "%s",
                              "eviction_priority": "%s"
                            }
                          }
                        }
                        """,
                        toBase64(SIGNAL.getKey()),
                        toBase64(SIGNAL.getValue()),
                        SIGNAL.getEvictionPriority());

        when(mClockMock.instant()).thenReturn(SIGNAL.getCreationTime());
        List<DBProtectedSignal> updatedSignals =
                updateAndReturnPersistedBuyerSignals(
                        updateJson,
                        UpdateSchemaVersion.V1,
                        SIGNAL.getPackageName(),
                        SIGNAL.getBuyer());

        expect.withMessage("updatedSignals").that(updatedSignals).containsExactly(SIGNAL);

        UpdateSignalsProcessReportedStats expectedStats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(0)
                        .setAdservicesApiStatusCode(STATUS_SUCCESS)
                        .setSignalsWrittenCount(1)
                        .setKeysStoredCount(1)
                        .setValuesStoredCount(1)
                        .setEvictionRulesCount(0)
                        .setPerBuyerSignalSize(SIZE_VERY_SMALL)
                        .setMeanRawProtectedSignalsSizeBytes(5)
                        .setMinRawProtectedSignalsSizeBytes(5)
                        .setMaxRawProtectedSignalsSizeBytes(5)
                        .setSignalEvictorsUsed(ImmutableSet.of())
                        .setUpdatedSignalEvictionPriorities(
                                ImmutableSet.of(SIGNAL.getEvictionPriority()))
                        .setEvictedSignalEvictionPriorities(ImmutableSet.of())
                        .setPerBuyerEvictedSignalSize(SIZE_UNSET)
                        .setUpdatedSignalsWithEvictionPriorityCount(1)
                        .setSignalUpdateSchemaVersion(UpdateSchemaVersion.V1)
                        .build();
        verifyExpectedStatsLogged(expectedStats);
    }

    @Test
    public void testUpdateSignals_simplePutIfNotPresent() throws Exception {
        String updateJson =
                String.format(
                        Locale.ENGLISH,
                        """
                        {
                          "put_if_not_present": {
                            "%s": {
                              "value": "%s",
                              "eviction_priority": "%s"
                            }
                          }
                        }
                        """,
                        toBase64(SIGNAL.getKey()),
                        toBase64(SIGNAL.getValue()),
                        SIGNAL.getEvictionPriority());

        when(mClockMock.instant()).thenReturn(SIGNAL.getCreationTime());
        List<DBProtectedSignal> updatedSignals =
                updateAndReturnPersistedBuyerSignals(
                        updateJson,
                        UpdateSchemaVersion.V1,
                        SIGNAL.getPackageName(),
                        SIGNAL.getBuyer());

        expect.withMessage("updatedSignals").that(updatedSignals).containsExactly(SIGNAL);

        UpdateSignalsProcessReportedStats expectedStats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(0)
                        .setAdservicesApiStatusCode(STATUS_SUCCESS)
                        .setSignalsWrittenCount(1)
                        .setKeysStoredCount(1)
                        .setValuesStoredCount(1)
                        .setEvictionRulesCount(0)
                        .setPerBuyerSignalSize(SIZE_VERY_SMALL)
                        .setMeanRawProtectedSignalsSizeBytes(5)
                        .setMinRawProtectedSignalsSizeBytes(5)
                        .setMaxRawProtectedSignalsSizeBytes(5)
                        .setSignalEvictorsUsed(ImmutableSet.of())
                        .setUpdatedSignalEvictionPriorities(
                                ImmutableSet.of(SIGNAL.getEvictionPriority()))
                        .setEvictedSignalEvictionPriorities(ImmutableSet.of())
                        .setPerBuyerEvictedSignalSize(SIZE_UNSET)
                        .setUpdatedSignalsWithEvictionPriorityCount(1)
                        .setSignalUpdateSchemaVersion(UpdateSchemaVersion.V1)
                        .build();
        verifyExpectedStatsLogged(expectedStats);
    }

    @Test
    public void testUpdateSignals_simpleAppend() throws Exception {
        String updateJson =
                String.format(
                        Locale.ENGLISH,
                        """
                        {
                          "append": {
                            "%s": {
                              "values": ["%s"],
                              "eviction_priority": "%s",
                              "max_signals": "10"
                            }
                          }
                        }
                        """,
                        toBase64(SIGNAL.getKey()),
                        toBase64(SIGNAL.getValue()),
                        SIGNAL.getEvictionPriority());

        when(mClockMock.instant()).thenReturn(SIGNAL.getCreationTime());
        List<DBProtectedSignal> updatedSignals =
                updateAndReturnPersistedBuyerSignals(
                        updateJson,
                        UpdateSchemaVersion.V1,
                        SIGNAL.getPackageName(),
                        SIGNAL.getBuyer());

        expect.withMessage("updatedSignals").that(updatedSignals).containsExactly(SIGNAL);

        UpdateSignalsProcessReportedStats expectedStats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(0)
                        .setAdservicesApiStatusCode(STATUS_SUCCESS)
                        .setSignalsWrittenCount(1)
                        .setKeysStoredCount(1)
                        .setValuesStoredCount(1)
                        .setEvictionRulesCount(0)
                        .setPerBuyerSignalSize(SIZE_VERY_SMALL)
                        .setMeanRawProtectedSignalsSizeBytes(5)
                        .setMinRawProtectedSignalsSizeBytes(5)
                        .setMaxRawProtectedSignalsSizeBytes(5)
                        .setSignalEvictorsUsed(ImmutableSet.of())
                        .setUpdatedSignalEvictionPriorities(
                                ImmutableSet.of(SIGNAL.getEvictionPriority()))
                        .setEvictedSignalEvictionPriorities(ImmutableSet.of())
                        .setPerBuyerEvictedSignalSize(SIZE_UNSET)
                        .setUpdatedSignalsWithEvictionPriorityCount(1)
                        .setSignalUpdateSchemaVersion(UpdateSchemaVersion.V1)
                        .build();
        verifyExpectedStatsLogged(expectedStats);
    }

    @Test
    public void testUpdateSignals_simpleRemove() throws Exception {
        mProtectedSignalsDao.insertAndDelete(
                SIGNAL.getBuyer(),
                SIGNAL.getCreationTime(),
                ImmutableList.of(SIGNAL),
                /* signalsToDelete= */ ImmutableList.of());

        List<DBProtectedSignal> initialSignals =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        assertWithMessage("initialSignals").that(initialSignals).containsExactly(SIGNAL);

        String updateJson =
                String.format(
                        Locale.ENGLISH,
                        """
                        {
                          "remove": ["%s"]
                        }
                        """,
                        toBase64(SIGNAL.getKey()));

        when(mClockMock.instant()).thenReturn(SIGNAL.getCreationTime());
        List<DBProtectedSignal> updatedSignals =
                updateAndReturnPersistedBuyerSignals(
                        updateJson,
                        UpdateSchemaVersion.V1,
                        SIGNAL.getPackageName(),
                        SIGNAL.getBuyer());

        expect.withMessage("updatedSignals").that(updatedSignals).isEmpty();

        UpdateSignalsProcessReportedStats expectedStats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(0)
                        .setAdservicesApiStatusCode(STATUS_SUCCESS)
                        .setSignalsWrittenCount(1)
                        .setKeysStoredCount(1)
                        .setValuesStoredCount(1)
                        .setEvictionRulesCount(0)
                        .setPerBuyerSignalSize(SIZE_UNSET)
                        .setMeanRawProtectedSignalsSizeBytes(0)
                        .setMinRawProtectedSignalsSizeBytes(0)
                        .setMaxRawProtectedSignalsSizeBytes(0)
                        .setSignalEvictorsUsed(ImmutableSet.of())
                        .setUpdatedSignalEvictionPriorities(ImmutableSet.of())
                        .setEvictedSignalEvictionPriorities(ImmutableSet.of())
                        .setPerBuyerEvictedSignalSize(SIZE_UNSET)
                        .setUpdatedSignalsWithEvictionPriorityCount(0)
                        .setSignalUpdateSchemaVersion(UpdateSchemaVersion.V1)
                        .build();
        verifyExpectedStatsLogged(expectedStats);
    }

    @Test
    public void testUpdateSignals_simpleUpdateProperties() throws Exception {
        mProtectedSignalsDao.insertAndDelete(
                SIGNAL.getBuyer(),
                SIGNAL.getCreationTime(),
                ImmutableList.of(SIGNAL),
                /* signalsToDelete= */ ImmutableList.of());

        List<DBProtectedSignal> initialSignals =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        assertWithMessage("initialSignals").that(initialSignals).containsExactly(SIGNAL);

        String updateJson =
                String.format(
                        Locale.ENGLISH,
                        """
                        {
                          "update_properties": {
                            "%s": {
                              "eviction_priority": "%s"
                            }
                          }
                        }
                        """,
                        toBase64(SIGNAL.getKey()),
                        EvictionPriority.EVICT_LATER);

        when(mClockMock.instant()).thenReturn(SIGNAL.getCreationTime());
        List<DBProtectedSignal> updatedSignals =
                updateAndReturnPersistedBuyerSignals(
                        updateJson,
                        UpdateSchemaVersion.V1,
                        SIGNAL.getPackageName(),
                        SIGNAL.getBuyer());

        DBProtectedSignal expectedSignal =
                SIGNAL.toBuilder().setEvictionPriority(EvictionPriority.EVICT_LATER).build();
        expect.withMessage("updatedSignals").that(updatedSignals).containsExactly(expectedSignal);

        UpdateSignalsProcessReportedStats expectedStats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(0)
                        .setAdservicesApiStatusCode(STATUS_SUCCESS)
                        .setSignalsWrittenCount(2)
                        .setKeysStoredCount(1)
                        .setValuesStoredCount(2)
                        .setEvictionRulesCount(0)
                        .setPerBuyerSignalSize(SIZE_VERY_SMALL)
                        .setMeanRawProtectedSignalsSizeBytes(2.5f)
                        .setMinRawProtectedSignalsSizeBytes(5)
                        .setMaxRawProtectedSignalsSizeBytes(5)
                        .setSignalEvictorsUsed(ImmutableSet.of())
                        .setUpdatedSignalEvictionPriorities(
                                ImmutableSet.of(EvictionPriority.EVICT_LATER))
                        .setEvictedSignalEvictionPriorities(ImmutableSet.of())
                        .setPerBuyerEvictedSignalSize(SIZE_UNSET)
                        .setUpdatedSignalsWithEvictionPriorityCount(1)
                        .setSignalUpdateSchemaVersion(UpdateSchemaVersion.V1)
                        .build();
        verifyExpectedStatsLogged(expectedStats);
    }

    /**
     * Tests prioritized eviction following a Put update.
     *
     * <ol>
     *   <li>Initially, the DB contains: [signalToEvict1, signalToEvict2, signalToRetain1,
     *       signalToRetain2], with a total size of 24 bytes.
     *   <li>newSignal is added via Put, increasing the total size to 29 bytes and triggering
     *       eviction.
     *   <li>signalToEvict1 has the lowest priority and is evicted, lowering the total size to 22.
     *   <li>signalToEvict2 and signalToRetain1 have the next lowest priority, but signalToEvict2 is
     *       older; signalToEvict2 is evicted, lowering the total size to 17 and ending eviction.
     *   <li>Finally, the DB contains: [signalToRetain1, signalToRetain2, newSignal].
     * </ol>
     */
    @Test
    @SetIntegerFlag(name = KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_BYTES, value = 18)
    @SetIntegerFlag(
            name = KEY_PROTECTED_SIGNALS_MAX_SIGNAL_SIZE_PER_BUYER_WITH_OVERSUBSCIPTION_BYTES,
            value = 25)
    public void testUpdateSignals_putAboveMaxSignalSize_triggersEviction() throws Exception {
        DBProtectedSignal signalToEvict1 = SIGNAL.toBuilder().setValue(VALUE_2).build();
        DBProtectedSignal signalToEvict2 =
                SIGNAL.toBuilder()
                        .setKey(KEY_2)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .build();
        DBProtectedSignal signalToRetain1 =
                SIGNAL.toBuilder()
                        .setKey(KEY_2)
                        .setValue(VALUE_2)
                        .setEvictionPriority(EvictionPriority.DEFAULT)
                        .setCreationTime(FIXED_NEXT_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .build();
        DBProtectedSignal signalToRetain2 =
                SIGNAL.toBuilder()
                        .setKey(KEY_3)
                        .setEvictionPriority(EvictionPriority.EVICT_LATER)
                        .setCreationTime(FIXED_EARLIER_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .build();

        List<DBProtectedSignal> expectedInitialSignals =
                ImmutableList.of(signalToEvict1, signalToEvict2, signalToRetain1, signalToRetain2);

        mProtectedSignalsDao.insertAndDelete(
                SIGNAL.getBuyer(),
                SIGNAL.getCreationTime(),
                expectedInitialSignals,
                /* signalsToDelete= */ ImmutableList.of());

        List<DBProtectedSignal> initialSignals =
                mProtectedSignalsDao.getSignalsByBuyer(SIGNAL.getBuyer());
        assertWithMessage("initialSignals")
                .that(initialSignals)
                .containsExactlyElementsIn(expectedInitialSignals);

        DBProtectedSignal newSignal =
                SIGNAL.toBuilder()
                        .setKey(KEY_4)
                        .setEvictionPriority(EvictionPriority.EVICT_LATER)
                        .build();

        String updateJson =
                String.format(
                        Locale.ENGLISH,
                        """
                        {
                          "put": {
                            "%s": {
                              "value": "%s",
                              "eviction_priority": "%s"
                            }
                          }
                        }
                        """,
                        toBase64(newSignal.getKey()),
                        toBase64(newSignal.getValue()),
                        newSignal.getEvictionPriority());

        when(mClockMock.instant()).thenReturn(newSignal.getCreationTime());
        List<DBProtectedSignal> updatedSignals =
                updateAndReturnPersistedBuyerSignals(
                        updateJson,
                        UpdateSchemaVersion.V1,
                        SIGNAL.getPackageName(),
                        SIGNAL.getBuyer());

        List<DBProtectedSignal> expectedUpdatedSignals =
                ImmutableList.of(signalToRetain1, signalToRetain2, newSignal);
        expect.withMessage("updatedSignals")
                .that(updatedSignals)
                .containsExactlyElementsIn(expectedUpdatedSignals);

        UpdateSignalsProcessReportedStats expectedStats =
                UpdateSignalsProcessReportedStats.builder()
                        .setUpdateSignalsProcessLatencyMillis(0)
                        .setAdservicesApiStatusCode(STATUS_SUCCESS)
                        .setSignalsWrittenCount(1)
                        .setKeysStoredCount(1)
                        .setValuesStoredCount(1)
                        .setEvictionRulesCount(2)
                        .setPerBuyerSignalSize(SIZE_SMALL)
                        .setMeanRawProtectedSignalsSizeBytes(17)
                        .setMinRawProtectedSignalsSizeBytes(5)
                        .setMaxRawProtectedSignalsSizeBytes(7)
                        .setSignalEvictorsUsed(ImmutableSet.of(SIGNAL_EVICTOR_PRIORITIZED_FIFO))
                        .setUpdatedSignalEvictionPriorities(
                                ImmutableSet.of(EvictionPriority.EVICT_LATER))
                        .setEvictedSignalEvictionPriorities(
                                ImmutableSet.of(
                                        EvictionPriority.EVICT_SOONER, EvictionPriority.DEFAULT))
                        .setPerBuyerEvictedSignalSize(SIZE_SMALL)
                        .setUpdatedSignalsWithEvictionPriorityCount(1)
                        .setSignalUpdateSchemaVersion(UpdateSchemaVersion.V1)
                        .build();
        verifyExpectedStatsLogged(expectedStats);
    }

    private List<DBProtectedSignal> updateAndReturnPersistedBuyerSignals(
            String updateJson, int updateSchemaVersion, String packageName, AdTechIdentifier buyer)
            throws Exception {
        mockSignalUpdateServer(updateJson, updateSchemaVersion);
        Uri signalsUri = mMockWebServerRule.uriForPath(SIGNALS_PATH);

        UpdateSignalsInput updateSignalsInput =
                new UpdateSignalsInput.Builder(signalsUri, packageName).build();
        UpdateSignalsSyncCallback callback = new UpdateSignalsSyncCallback();
        mService.updateSignals(updateSignalsInput, callback);

        callback.assertResultReceived();
        return mProtectedSignalsDao.getSignalsByBuyer(buyer);
    }

    private void mockSignalUpdateServer(String updateJson, int updateSchemaVersion)
            throws Exception {
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return switch (request.getPath()) {
                            case SIGNALS_PATH ->
                                    new MockResponse()
                                            .setBody(updateJson)
                                            .setHeader(
                                                    UPDATE_SCHEMA_VERSION_HEADER,
                                                    updateSchemaVersion);
                            case null, default -> new MockResponse().setResponseCode(404);
                        };
                    }
                });
    }

    private void verifyExpectedStatsLogged(UpdateSignalsProcessReportedStats expectedStats) {
        ArgumentCaptor<UpdateSignalsProcessReportedStats> statsCaptor =
                ArgumentCaptor.forClass(UpdateSignalsProcessReportedStats.class);
        verify(mAdServicesLoggerImplMock, timeout(500))
                .logUpdateSignalsProcessReportedStats(statsCaptor.capture());
        UpdateSignalsProcessReportedStats stats = statsCaptor.getValue();

        expect.withMessage("adServicesApiStatusCode")
                .that(stats.getAdservicesApiStatusCode())
                .isEqualTo(expectedStats.getAdservicesApiStatusCode());
        expect.withMessage("signalsWrittenCount")
                .that(stats.getSignalsWrittenCount())
                .isEqualTo(expectedStats.getSignalsWrittenCount());
        expect.withMessage("keysStoredCount")
                .that(stats.getKeysStoredCount())
                .isEqualTo(expectedStats.getKeysStoredCount());
        expect.withMessage("valuesStoredCount")
                .that(stats.getValuesStoredCount())
                .isEqualTo(expectedStats.getValuesStoredCount());
        expect.withMessage("evictionRulesCount")
                .that(stats.getEvictionRulesCount())
                .isEqualTo(expectedStats.getEvictionRulesCount());
        expect.withMessage("perBuyerSignalSize")
                .that(stats.getPerBuyerSignalSize())
                .isEqualTo(expectedStats.getPerBuyerSignalSize());
        expect.withMessage("meanRawProtectedSignalsSizeBytes")
                .that(stats.getMeanRawProtectedSignalsSizeBytes())
                .isEqualTo(expectedStats.getMeanRawProtectedSignalsSizeBytes());
        expect.withMessage("maxRawProtectedSignalsSizeBytes")
                .that(stats.getMaxRawProtectedSignalsSizeBytes())
                .isEqualTo(expectedStats.getMaxRawProtectedSignalsSizeBytes());
        expect.withMessage("minRawProtectedSignalsSizeBytes")
                .that(stats.getMinRawProtectedSignalsSizeBytes())
                .isEqualTo(expectedStats.getMinRawProtectedSignalsSizeBytes());
        expect.withMessage("signalEvictorsUsed")
                .that(stats.getSignalEvictorsUsed())
                .isEqualTo(expectedStats.getSignalEvictorsUsed());
        expect.withMessage("updatedSignalEvictionPriorities")
                .that(stats.getUpdatedSignalEvictionPriorities())
                .isEqualTo(expectedStats.getUpdatedSignalEvictionPriorities());
        expect.withMessage("evictedSignalEvictionPriorities")
                .that(stats.getEvictedSignalEvictionPriorities())
                .isEqualTo(expectedStats.getEvictedSignalEvictionPriorities());
        expect.withMessage("perBuyerEvictedSignalSize")
                .that(stats.getPerBuyerEvictedSignalSize())
                .isEqualTo(expectedStats.getPerBuyerEvictedSignalSize());
        expect.withMessage("updatedSignalWithEvictionPriorityCount")
                .that(stats.getUpdatedSignalsWithEvictionPriorityCount())
                .isEqualTo(expectedStats.getUpdatedSignalsWithEvictionPriorityCount());
        expect.withMessage("signalUpdateSchemaVersion")
                .that(stats.getSignalUpdateSchemaVersion())
                .isEqualTo(expectedStats.getSignalUpdateSchemaVersion());
    }
}
