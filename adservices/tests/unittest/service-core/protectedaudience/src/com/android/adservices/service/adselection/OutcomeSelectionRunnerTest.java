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

package com.android.adservices.service.adselection;

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.SelectAdsFromOutcomesExecutionLogger;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@SetLongFlag(
        name = KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS,
        value = EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS)
@SetLongFlag(
        name = KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS,
        value = EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS)
public final class OutcomeSelectionRunnerTest extends AdServicesExtendedMockitoTestCase {
    private static final int CALLER_UID = Process.myUid();
    private static final String MY_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String ANOTHER_CALLER_PACKAGE_NAME = "another.caller.package";
    private static final Uri RENDER_URI_1 = Uri.parse("https://www.domain.com/advert1/");
    private static final Uri RENDER_URI_2 = Uri.parse("https://www.domain.com/advert2/");
    private static final Uri RENDER_URI_3 = Uri.parse("https://www.domain.com/advert3/");
    private static final long AD_SELECTION_ID_1 = 1;
    private static final long AD_SELECTION_ID_2 = 2;
    private static final long AD_SELECTION_ID_3 = 3;
    private static final double BID_1 = 10.0;
    private static final double BID_2 = 20.0;
    private static final double BID_3 = 30.0;
    private static final AdSelectionResultBidAndUri AD_SELECTION_WITH_BID_1 =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setWinningAdBid(BID_1)
                    .setWinningAdRenderUri(RENDER_URI_1)
                    .build();
    private static final AdSelectionResultBidAndUri AD_SELECTION_WITH_BID_2 =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setWinningAdBid(BID_2)
                    .setWinningAdRenderUri(RENDER_URI_2)
                    .build();
    private static final AdSelectionResultBidAndUri AD_SELECTION_WITH_BID_3 =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setWinningAdBid(BID_3)
                    .setWinningAdRenderUri(RENDER_URI_3)
                    .build();

    private AdSelectionEntryDao mAdSelectionEntryDao;
    @Mock private AdOutcomeSelector mAdOutcomeSelectorMock;
    private OutcomeSelectionRunner mOutcomeSelectionRunner;
    // TODO(b/384949821): move to superclass
    private final Flags mFakeFlags = flags.getFlags();
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private ListeningExecutorService mBlockingExecutorService;

    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilter;
    @Mock private SelectAdsFromOutcomesExecutionLogger mSelectAdsFromOutcomesExecutionLoggerMock;

    @Before
    public void setup() {
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        mocker.mockGetDebugFlags(mMockDebugFlags);
        mOutcomeSelectionRunner =
                new OutcomeSelectionRunner(
                        CALLER_UID,
                        mAdOutcomeSelectorMock,
                        mAdSelectionEntryDao,
                        mBlockingExecutorService,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLoggerMock,
                        mContext,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdSelectionServiceFilter,
                        DevContext.createForDevOptionsDisabled(),
                        false);
        doNothing()
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        SAMPLE_SELLER,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
    }

    @Test
    public void testRunOutcomeSelectionInvalidAdSelectionConfigFromOutcomes() {
        List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        persistAdSelectionEntry(adSelectionIdWithBidAndRenderUris.get(0), MY_APP_PACKAGE_NAME);
        // Not persisting index 1
        // Persisting index 2 with a different package name
        persistAdSelectionEntry(
                adSelectionIdWithBidAndRenderUris.get(2), ANOTHER_CALLER_PACKAGE_NAME);

        List<Long> adOutcomesConfigParam =
                adSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionResultBidAndUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        mOutcomeSelectionRunner,
                        config,
                        MY_APP_PACKAGE_NAME,
                        mSelectAdsFromOutcomesExecutionLoggerMock);

        var unused =
                verify(mAdOutcomeSelectorMock, never()).runAdOutcomeSelector(any(), any(), any());
        expect.that(resultsCallback.mIsSuccess).isFalse();
        expect.that(STATUS_INVALID_ARGUMENT)
                .isEqualTo(resultsCallback.mFledgeErrorResponse.getStatusCode());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testRunOutcomeSelectionRevokedUserConsentEmptyResult() {
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        SAMPLE_SELLER,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        true,
                        CALLER_UID,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        for (AdSelectionResultBidAndUri idWithBid : adSelectionIdWithBidAndRenderUris) {
            persistAdSelectionEntry(idWithBid, MY_APP_PACKAGE_NAME);
        }

        List<Long> adOutcomesConfigParam =
                adSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionResultBidAndUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        mOutcomeSelectionRunner,
                        config,
                        MY_APP_PACKAGE_NAME,
                        mSelectAdsFromOutcomesExecutionLoggerMock);

        var unused =
                verify(mAdOutcomeSelectorMock, never()).runAdOutcomeSelector(any(), any(), any());
        expect.that(resultsCallback.mIsSuccess).isTrue();
        expect.that(resultsCallback.mAdSelectionResponse).isNull();

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testRunOutcomeSelectionRevokedUserConsentEmptyResult_UXNotificationNotEnforced() {
        mocker.mockGetConsentNotificationDebugMode(true);

        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        SAMPLE_SELLER,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        false,
                        CALLER_UID,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        for (AdSelectionResultBidAndUri idWithBid : adSelectionIdWithBidAndRenderUris) {
            persistAdSelectionEntry(idWithBid, MY_APP_PACKAGE_NAME);
        }

        List<Long> adOutcomesConfigParam =
                adSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionResultBidAndUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        OutcomeSelectionRunner outcomeSelectionRunner =
                new OutcomeSelectionRunner(
                        CALLER_UID,
                        mAdOutcomeSelectorMock,
                        mAdSelectionEntryDao,
                        mBlockingExecutorService,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLoggerMock,
                        mContext,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdSelectionServiceFilter,
                        DevContext.createForDevOptionsDisabled(),
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        outcomeSelectionRunner,
                        config,
                        MY_APP_PACKAGE_NAME,
                        mSelectAdsFromOutcomesExecutionLoggerMock);

        var unused =
                verify(mAdOutcomeSelectorMock, never()).runAdOutcomeSelector(any(), any(), any());
        expect.that(resultsCallback.mIsSuccess).isTrue();
        expect.that(resultsCallback.mAdSelectionResponse).isNull();

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());

        verify(mAdSelectionServiceFilter)
                .filterRequest(
                        SAMPLE_SELLER,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        false,
                        CALLER_UID,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
    }

    @Test
    @SetLongFlag(name = KEY_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS, value = 300)
    @SetLongFlag(name = KEY_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS, value = 100)
    @SetFlagTrue(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
    public void testRunOutcomeSelectionOrchestrationTimeoutFailure() {
        List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        for (AdSelectionResultBidAndUri idWithBid : adSelectionIdWithBidAndRenderUris) {
            persistAdSelectionEntry(idWithBid, MY_APP_PACKAGE_NAME);
        }

        List<Long> adOutcomesConfigParam =
                adSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionResultBidAndUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        GenericListMatcher matcher = new GenericListMatcher(adSelectionIdWithBidAndRenderUris);
        doAnswer((ignored) -> getSelectedOutcomeWithDelay(AD_SELECTION_ID_1, mFakeFlags))
                .when(mAdOutcomeSelectorMock)
                .runAdOutcomeSelector(
                        argThat(matcher),
                        eq(config),
                        eq(mSelectAdsFromOutcomesExecutionLoggerMock));

        OutcomeSelectionRunner outcomeSelectionRunner =
                new OutcomeSelectionRunner(
                        CALLER_UID,
                        mAdOutcomeSelectorMock,
                        mAdSelectionEntryDao,
                        mBlockingExecutorService,
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLoggerMock,
                        mContext,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdSelectionServiceFilter,
                        DevContext.createForDevOptionsDisabled(),
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        outcomeSelectionRunner,
                        config,
                        MY_APP_PACKAGE_NAME,
                        mSelectAdsFromOutcomesExecutionLoggerMock);

        var unused = verify(mAdOutcomeSelectorMock).runAdOutcomeSelector(any(), any(), any());
        expect.that(resultsCallback.mIsSuccess).isFalse();
        assertWithMessage("mFledgeErrorResponse")
                .that(resultsCallback.mFledgeErrorResponse)
                .isNotNull();
        expect.that(STATUS_TIMEOUT).isEqualTo(resultsCallback.mFledgeErrorResponse.getStatusCode());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS_FROM_OUTCOMES),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_TIMEOUT),
                        anyInt());
    }

    private void persistAdSelectionEntry(
            AdSelectionResultBidAndUri idWithBidAndRenderUri, String callerPackageName) {
        final Uri biddingLogicUri1 = Uri.parse("https://www.domain.com/logic/1");
        final Instant activationTime = Instant.now();
        final String contextualSignals = "contextual_signals";
        final CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        final DBAdSelection dbAdSelectionEntry =
                new DBAdSelection.Builder()
                        .setAdSelectionId(idWithBidAndRenderUri.getAdSelectionId())
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setBuyerContextualSignals(contextualSignals)
                        .setBiddingLogicUri(biddingLogicUri1)
                        .setWinningAdRenderUri(idWithBidAndRenderUri.getWinningAdRenderUri())
                        .setWinningAdBid(idWithBidAndRenderUri.getWinningAdBid())
                        .setCreationTimestamp(activationTime)
                        .setCallerPackageName(callerPackageName)
                        .build();
        mAdSelectionEntryDao.persistAdSelection(dbAdSelectionEntry);
    }

    private OutcomeSelectionRunnerTest.AdSelectionTestCallback invokeRunAdSelectionFromOutcomes(
            OutcomeSelectionRunner outcomeSelectionRunner,
            AdSelectionFromOutcomesConfig config,
            String callerPackageName,
            SelectAdsFromOutcomesExecutionLogger selectAdsFromOutcomesExecutionLogger) {

        // Counted down in the callback
        CountDownLatch countDownLatch = new CountDownLatch(1);
        OutcomeSelectionRunnerTest.AdSelectionTestCallback adSelectionTestCallback =
                new OutcomeSelectionRunnerTest.AdSelectionTestCallback(countDownLatch);

        AdSelectionFromOutcomesInput input =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(config)
                        .setCallerPackageName(callerPackageName)
                        .build();

        outcomeSelectionRunner.runOutcomeSelection(
                input, adSelectionTestCallback, selectAdsFromOutcomesExecutionLogger);
        try {
            adSelectionTestCallback.mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return adSelectionTestCallback;
    }

    private ListenableFuture<Long> getSelectedOutcomeWithDelay(
            Long outcomeId, @NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionFromOutcomesOverallTimeoutMs());
                    return outcomeId;
                });
    }

    private static final class AdSelectionTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mAdSelectionResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) throws RemoteException {
            mIsSuccess = true;
            mAdSelectionResponse = adSelectionResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static final class GenericListMatcher
            implements ArgumentMatcher<List<AdSelectionResultBidAndUri>> {
        private final List<AdSelectionResultBidAndUri> mTruth;

        GenericListMatcher(List<AdSelectionResultBidAndUri> truth) {
            this.mTruth = truth;
        }

        @Override
        public boolean matches(List<AdSelectionResultBidAndUri> argument) {
            return mTruth.size() == argument.size()
                    && new HashSet<>(mTruth).equals(new HashSet<>(argument));
        }
    }
}
