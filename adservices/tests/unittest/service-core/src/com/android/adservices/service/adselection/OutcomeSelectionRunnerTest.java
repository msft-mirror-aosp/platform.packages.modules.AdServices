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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.android.adservices.service.adselection.AdSelectionFromOutcomesConfigValidator.AD_SELECTION_IDS_DONT_EXIST;
import static com.android.adservices.service.common.Throttler.ApiKey.UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OutcomeSelectionRunnerTest {
    // Time allowed by current test async calls to respond
    private static final int RESPONSE_TIMEOUT_SECONDS = 3;

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
    private static final AdSelectionIdWithBidAndRenderUri AD_SELECTION_WITH_BID_1 =
            AdSelectionIdWithBidAndRenderUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setBid(BID_1)
                    .setRenderUri(RENDER_URI_1)
                    .build();
    private static final AdSelectionIdWithBidAndRenderUri AD_SELECTION_WITH_BID_2 =
            AdSelectionIdWithBidAndRenderUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setBid(BID_2)
                    .setRenderUri(RENDER_URI_2)
                    .build();
    private static final AdSelectionIdWithBidAndRenderUri AD_SELECTION_WITH_BID_3 =
            AdSelectionIdWithBidAndRenderUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setBid(BID_3)
                    .setRenderUri(RENDER_URI_3)
                    .build();

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private AdSelectionEntryDao mAdSelectionEntryDao;
    @Mock private AdOutcomeSelector mAdOutcomeSelectorMock;
    private OutcomeSelectionRunner mOutcomeSelectionRunner;
    private final Flags mFlags =
            new Flags() {
                @Override
                public long getAdSelectionSelectingOutcomeTimeoutMs() {
                    return 300;
                }

                @Override
                public boolean getDisableFledgeEnrollmentCheck() {
                    return true;
                }
            };
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private final FledgeAuthorizationFilter mFledgeAuthorizationFilter =
            new FledgeAuthorizationFilter(
                    mContext.getPackageManager(),
                    new EnrollmentDao(mContext, DbTestUtil.getDbHelperForTest()),
                    mAdServicesLoggerMock);
    @Mock private AppImportanceFilter mAppImportanceFilter;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private Throttler mMockThrottler;
    private Supplier<Throttler> mThrottlerSupplier = () -> mMockThrottler;
    private final FledgeAllowListsFilter mFledgeAllowListsFilter =
            new FledgeAllowListsFilter(mFlags, mAdServicesLoggerMock);
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        //                        .spyStatic(JSScriptEngine.class)
                        // mAdServicesLoggerMock is not referenced in many tests
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mOutcomeSelectionRunner =
                new OutcomeSelectionRunner(
                        CALLER_UID,
                        mAdOutcomeSelectorMock,
                        mAdSelectionEntryDao,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        AdServicesExecutors.getScheduler(),
                        mAdServicesLoggerMock,
                        mFledgeAuthorizationFilter,
                        mAppImportanceFilter,
                        mThrottlerSupplier,
                        mFledgeAllowListsFilter,
                        mConsentManagerMock,
                        mContext,
                        mFlags);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testRunOutcomeSelectionSuccess() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        doReturn(true).when(mMockThrottler).tryAcquire(eq(UNKNOWN), anyString());

        List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        for (AdSelectionIdWithBidAndRenderUri idWithBid : adSelectionIdWithBidAndRenderUris) {
            persistAdSelectionEntry(idWithBid, MY_APP_PACKAGE_NAME);
        }

        List<Long> adOutcomesConfigParam =
                adSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionIdWithBidAndRenderUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        GenericListMatcher matcher = new GenericListMatcher(adSelectionIdWithBidAndRenderUris);
        doReturn(FluentFuture.from(Futures.immediateFuture(AD_SELECTION_ID_1)))
                .when(mAdOutcomeSelectorMock)
                .runAdOutcomeSelector(argThat(matcher), eq(config));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        mOutcomeSelectionRunner, config, MY_APP_PACKAGE_NAME);

        verify(mAdOutcomeSelectorMock, only()).runAdOutcomeSelector(argThat(matcher), eq(config));
        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(AD_SELECTION_ID_1, resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(RENDER_URI_1, resultsCallback.mAdSelectionResponse.getRenderUri());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testRunOutcomeSelectionAdSelectionIdOwnedByDifferentAppFailure() {
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManagerMock).getConsent();
        doReturn(true).when(mMockThrottler).tryAcquire(eq(UNKNOWN), anyString());

        List<AdSelectionIdWithBidAndRenderUri> AdSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        persistAdSelectionEntry(AdSelectionIdWithBidAndRenderUris.get(0), MY_APP_PACKAGE_NAME);
        // Not persisting index 1
        // Persisting index 2 with a different package name
        persistAdSelectionEntry(
                AdSelectionIdWithBidAndRenderUris.get(2), ANOTHER_CALLER_PACKAGE_NAME);

        List<Long> adOutcomesConfigParam =
                AdSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionIdWithBidAndRenderUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        mOutcomeSelectionRunner, config, MY_APP_PACKAGE_NAME);

        verify(mAdOutcomeSelectorMock, never()).runAdOutcomeSelector(any(), any());
        assertFalse(resultsCallback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, resultsCallback.mFledgeErrorResponse.getStatusCode());
        assertTrue(
                resultsCallback
                        .mFledgeErrorResponse
                        .getErrorMessage()
                        .contains(
                                String.format(
                                        AD_SELECTION_IDS_DONT_EXIST,
                                        List.of(AD_SELECTION_ID_2, AD_SELECTION_ID_3))));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testRunOutcomeSelectionRevokedUserConsentEmptyResult() {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();
        doReturn(true).when(mMockThrottler).tryAcquire(eq(UNKNOWN), anyString());

        List<AdSelectionIdWithBidAndRenderUri> adSelectionIdWithBidAndRenderUris =
                List.of(AD_SELECTION_WITH_BID_1, AD_SELECTION_WITH_BID_2, AD_SELECTION_WITH_BID_3);
        for (AdSelectionIdWithBidAndRenderUri idWithBid : adSelectionIdWithBidAndRenderUris) {
            persistAdSelectionEntry(idWithBid, MY_APP_PACKAGE_NAME);
        }

        List<Long> adOutcomesConfigParam =
                adSelectionIdWithBidAndRenderUris.stream()
                        .map(AdSelectionIdWithBidAndRenderUri::getAdSelectionId)
                        .collect(Collectors.toList());

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adOutcomesConfigParam);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionFromOutcomes(
                        mOutcomeSelectionRunner, config, MY_APP_PACKAGE_NAME);

        verify(mAdOutcomeSelectorMock, never()).runAdOutcomeSelector(any(), any());
        assertTrue(resultsCallback.mIsSuccess);
        assertNull(resultsCallback.mAdSelectionResponse);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    private void persistAdSelectionEntry(
            AdSelectionIdWithBidAndRenderUri idWithBidAndRenderUri, String callerPackageName) {
        final Uri biddingLogicUri1 = Uri.parse("https://www.domain.com/logic/1");
        final Instant activationTime = Instant.now();
        final String contextualSignals = "contextual_signals";
        final CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        final DBAdSelection dbAdSelectionEntry =
                new DBAdSelection.Builder()
                        .setAdSelectionId(idWithBidAndRenderUri.getAdSelectionId())
                        .setCustomAudienceSignals(customAudienceSignals)
                        .setContextualSignals(contextualSignals)
                        .setBiddingLogicUri(biddingLogicUri1)
                        .setWinningAdRenderUri(idWithBidAndRenderUri.getRenderUri())
                        .setWinningAdBid(idWithBidAndRenderUri.getBid())
                        .setCreationTimestamp(activationTime)
                        .setCallerPackageName(callerPackageName)
                        .build();
        mAdSelectionEntryDao.persistAdSelection(dbAdSelectionEntry);
    }

    private OutcomeSelectionRunnerTest.AdSelectionTestCallback invokeRunAdSelectionFromOutcomes(
            OutcomeSelectionRunner outcomeSelectionRunner,
            AdSelectionFromOutcomesConfig config,
            String callerPackageName) {

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch countDownLatch = new CountDownLatch(2);
        OutcomeSelectionRunnerTest.AdSelectionTestCallback adSelectionTestCallback =
                new OutcomeSelectionRunnerTest.AdSelectionTestCallback(countDownLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    countDownLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        AdSelectionFromOutcomesInput input =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(config)
                        .setCallerPackageName(callerPackageName)
                        .build();

        outcomeSelectionRunner.runOutcomeSelection(input, adSelectionTestCallback);
        try {
            adSelectionTestCallback.mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return adSelectionTestCallback;
    }

    static class AdSelectionTestCallback extends AdSelectionCallback.Stub {

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

    static class GenericListMatcher
            implements ArgumentMatcher<List<AdSelectionIdWithBidAndRenderUri>> {
        private final List<AdSelectionIdWithBidAndRenderUri> mTruth;

        GenericListMatcher(List<AdSelectionIdWithBidAndRenderUri> truth) {
            this.mTruth = truth;
        }

        @Override
        public boolean matches(List<AdSelectionIdWithBidAndRenderUri> argument) {
            return mTruth.size() == argument.size()
                    && new HashSet<>(mTruth).equals(new HashSet<>(argument));
        }
    }
}
