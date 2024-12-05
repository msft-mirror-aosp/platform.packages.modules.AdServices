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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.ScheduleUpdateTestCallback;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_DID_OVERWRITE_EXISTING_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_REJECTED_BY_EXISTING_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertWithMessage;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.net.Uri;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdateScheduleAttemptedStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@MockStatic(ScheduleCustomAudienceUpdateJobService.class)
@MockStatic(ConsentManager.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
public final class ScheduleCustomAudienceUpdateImplTest extends AdServicesExtendedMockitoTestCase {
    private static final int API_NAME =
            AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;

    private static final Uri UPDATE_URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final int MIN_DELAY_IN_MINUTES = 50;
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    private static final AdSelectionSignals USER_BIDDING_SIGNALS_1 =
            AdSelectionSignals.fromString("{\"ExampleBiddingSignal1\":1}");
    @Captor private ArgumentCaptor<DBScheduledCustomAudienceUpdate> mUpdateCaptor;
    @Captor private ArgumentCaptor<List<PartialCustomAudience>> mPartialCaListArgumentCaptor;
    @Captor private ArgumentCaptor<List<String>> mCaToLeaveListArgumentCaptor;

    @Captor
    private ArgumentCaptor<ScheduledCustomAudienceUpdateScheduleAttemptedStats> mStatsCaptor;

    private ListeningExecutorService mBackgroundExecutorService;
    private int mCallingAppUid;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    private ScheduleCustomAudienceUpdateImpl mScheduleCustomAudienceUpdateImpl;
    private DevContext mDevContext;
    private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setup() {
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mCallingAppUid = CallingAppUidSupplierProcessImpl.create().getCallingAppUid();
        mocker.mockGetFlags(mMockFlags);
        mocker.mockGetDebugFlags(mMockDebugFlags);
        when(mMockFlags.getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests())
                .thenReturn(false);
        when(mMockFlags.getFledgeScheduleCustomAudienceUpdateEnabled()).thenReturn(true);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        mDevContext = DevContext.builder(PACKAGE).setDeviceDevOptionsEnabled(false).build();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext)))
                .thenReturn(BUYER);
        mScheduleCustomAudienceUpdateImpl =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);
        when(mMockFlags.getDisableFledgeEnrollmentCheck()).thenReturn(false);
        when(mMockFlags.getEnforceForegroundStatusForSignals()).thenReturn(true);
        mocker.mockGetConsentNotificationDebugMode(false);

        doNothing()
                .when(
                        () ->
                                ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                        any(), any(), anyBoolean()));
    }

    @Test
    public void testScheduleCustomAudienceUpdate_withShouldReplacePendingUpdateFalse_Success()
            throws Exception {
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, mScheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledCustomAudienceUpdate(
                        mUpdateCaptor.capture(),
                        mPartialCaListArgumentCaptor.capture(),
                        mCaToLeaveListArgumentCaptor.capture(),
                        eq(false),
                        any());
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mPartialCaListArgumentCaptor.getValue().isEmpty());
        assertTrue(mCaToLeaveListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
    }

    @Test
    public void
            testScheduleCustomAudienceUpdate_withShouldReplacePendingUpdateFalse_SuccessWithUXNotificationEnforcementDisabled()
                    throws Exception {
        mocker.mockGetConsentNotificationDebugMode(true);

        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext)))
                .thenReturn(BUYER);

        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, mScheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledCustomAudienceUpdate(
                        mUpdateCaptor.capture(),
                        mPartialCaListArgumentCaptor.capture(),
                        mCaToLeaveListArgumentCaptor.capture(),
                        eq(false),
                        any());
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mPartialCaListArgumentCaptor.getValue().isEmpty());
        assertTrue(mCaToLeaveListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
    }

    @Test
    public void
            testScheduleCustomAudienceUpdate_withShouldReplacePendingUpdateFalse_WithAdditionalScheduleRequestsTrue()
                    throws Exception {
        mocker.mockGetConsentNotificationDebugMode(true);
        when(mMockFlags.getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests())
                .thenReturn(true);

        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext)))
                .thenReturn(BUYER);

        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImpl =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);

        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(50),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledCustomAudienceUpdate(
                        mUpdateCaptor.capture(),
                        mPartialCaListArgumentCaptor.capture(),
                        mCaToLeaveListArgumentCaptor.capture(),
                        eq(false),
                        any());
        assertTrue(mUpdateCaptor.getValue().getAllowScheduleInResponse());
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mPartialCaListArgumentCaptor.getValue().isEmpty());
        assertTrue(mCaToLeaveListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
    }

    @Test
    public void
            testScheduleCustomAudienceUpdate_withShouldReplacePendingUpdateFalse_SuccessWithAdditionalScheduleRequestsFalse()
                    throws Exception {
        mocker.mockGetConsentNotificationDebugMode(true);

        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext)))
                .thenReturn(BUYER);

        when(mMockFlags.getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests())
                .thenReturn(false);

        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(50),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, mScheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledCustomAudienceUpdate(
                        mUpdateCaptor.capture(),
                        mPartialCaListArgumentCaptor.capture(),
                        mCaToLeaveListArgumentCaptor.capture(),
                        eq(false),
                        any());
        assertFalse(mUpdateCaptor.getValue().getAllowScheduleInResponse());
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mPartialCaListArgumentCaptor.getValue().isEmpty());
        assertTrue(mCaToLeaveListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_withShouldReplacePendingUpdateTrue_Success()
            throws Exception {
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(true)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, mScheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledCustomAudienceUpdate(
                        mUpdateCaptor.capture(),
                        mPartialCaListArgumentCaptor.capture(),
                        mCaToLeaveListArgumentCaptor.capture(),
                        eq(true),
                        any());
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mPartialCaListArgumentCaptor.getValue().isEmpty());
        assertTrue(mCaToLeaveListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
    }

    @Test
    public void testScheduleCAUpdate_withNoExistingUpdate_LogsStatsCorrectly() throws Exception {
        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImplWithActualDao =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDao);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(true)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImplWithActualDao);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateScheduleAttemptedStats(mStatsCaptor.capture());
        ScheduledCustomAudienceUpdateScheduleAttemptedStats actualStats = mStatsCaptor.getValue();
        assertWithMessage("Minimum delay in minutes")
                .that(actualStats.getMinimumDelayInMinutes())
                .isEqualTo(MIN_DELAY_IN_MINUTES);
        assertWithMessage("Number of partial custom audiences")
                .that(actualStats.getNumberOfPartialCustomAudiences())
                .isEqualTo(0);
        assertWithMessage("Existing update status")
                .that(actualStats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE);
        assertWithMessage("Callback status").that(callback.isSuccess()).isTrue();
    }

    @Test
    public void testScheduleCAUpdate_failingWithFilteringException_LogsStatsCorrectly()
            throws Exception {
        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImplWithActualDao =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDao);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(true)
                        .build();

        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImplWithActualDao);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateScheduleAttemptedStats(mStatsCaptor.capture());
        ScheduledCustomAudienceUpdateScheduleAttemptedStats actualStats = mStatsCaptor.getValue();
        assertWithMessage("Minimum delay in minutes")
                .that(actualStats.getMinimumDelayInMinutes())
                .isEqualTo(MIN_DELAY_IN_MINUTES);
        assertWithMessage("Number of partial custom audiences")
                .that(actualStats.getNumberOfPartialCustomAudiences())
                .isEqualTo(0);
        assertWithMessage("Existing update status")
                .that(actualStats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_UNKNOWN);
        assertWithMessage("Callback status").that(callback.isSuccess()).isFalse();
    }

    @Test
    public void testScheduleCAUpdate_withNonZeroPartialCustomAudience_LogsStatsCorrectly()
            throws Exception {
        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImplWithActualDao =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDao);
        String partialCaName1 = "partial_ca_1";
        String partialCaName2 = "partial_ca_2";
        PartialCustomAudience partialCustomAudience1 =
                new PartialCustomAudience.Builder(partialCaName1)
                        .setActivationTime(CommonFixture.FIXED_NOW)
                        .setExpirationTime(CommonFixture.FIXED_NEXT_ONE_DAY)
                        .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                        .build();
        PartialCustomAudience partialCustomAudience2 =
                new PartialCustomAudience.Builder(partialCaName2)
                        .setActivationTime(CommonFixture.FIXED_NOW)
                        .setExpirationTime(CommonFixture.FIXED_NEXT_ONE_DAY)
                        .setUserBiddingSignals(USER_BIDDING_SIGNALS_1)
                        .build();
        List<PartialCustomAudience> partialCustomAudienceList =
                Arrays.asList(partialCustomAudience1, partialCustomAudience2);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                partialCustomAudienceList)
                        .setShouldReplacePendingUpdates(true)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImplWithActualDao);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateScheduleAttemptedStats(mStatsCaptor.capture());
        ScheduledCustomAudienceUpdateScheduleAttemptedStats actualStats = mStatsCaptor.getValue();
        assertWithMessage("Minimum delay in minutes")
                .that(actualStats.getMinimumDelayInMinutes())
                .isEqualTo(MIN_DELAY_IN_MINUTES);
        assertWithMessage("Number of partial custom audiences")
                .that(actualStats.getNumberOfPartialCustomAudiences())
                .isEqualTo(partialCustomAudienceList.size());
        assertWithMessage("Existing update status")
                .that(actualStats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_NO_EXISTING_UPDATE);
        assertWithMessage("Callback status").that(callback.isSuccess()).isTrue();
    }

    @Test
    public void testScheduleCAUpdate_rejectedByExistingUpdate_LogsStatsCorrectly()
            throws Exception {
        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImplWithActualDao =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDao);
        DBScheduledCustomAudienceUpdate scheduledUpdate =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateUri(UPDATE_URI)
                        .setOwner(PACKAGE)
                        .setBuyer(AdTechIdentifier.fromString(UPDATE_URI.getHost()))
                        .setCreationTime(Instant.now())
                        .setScheduledTime(Instant.now())
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(scheduledUpdate);

        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImplWithActualDao);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateScheduleAttemptedStats(mStatsCaptor.capture());
        ScheduledCustomAudienceUpdateScheduleAttemptedStats actualStats = mStatsCaptor.getValue();
        assertWithMessage("Minimum delay in minutes")
                .that(actualStats.getMinimumDelayInMinutes())
                .isEqualTo(MIN_DELAY_IN_MINUTES);
        assertWithMessage("Number of partial custom audiences")
                .that(actualStats.getNumberOfPartialCustomAudiences())
                .isEqualTo(0);
        assertWithMessage("Existing update status")
                .that(actualStats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_REJECTED_BY_EXISTING_UPDATE);
        assertWithMessage("Callback status").that(callback.isSuccess()).isFalse();
    }

    @Test
    public void testScheduleCAUpdate_overwritingExistingUpdate_LogsStatsCorrectly()
            throws Exception {
        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImplWithActualDao =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDao);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(true)
                        .build();

        DBScheduledCustomAudienceUpdate scheduledUpdate =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateUri(input.getUpdateUri())
                        .setOwner(input.getCallerPackageName())
                        .setBuyer(AdTechIdentifier.fromString(input.getUpdateUri().getHost()))
                        .setCreationTime(Instant.now())
                        .setScheduledTime(Instant.now())
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(scheduledUpdate);

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImplWithActualDao);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateScheduleAttemptedStats(mStatsCaptor.capture());
        ScheduledCustomAudienceUpdateScheduleAttemptedStats actualStats = mStatsCaptor.getValue();
        assertWithMessage("Minimum delay in minutes")
                .that(actualStats.getMinimumDelayInMinutes())
                .isEqualTo(MIN_DELAY_IN_MINUTES);
        assertWithMessage("Number of partial custom audiences")
                .that(actualStats.getNumberOfPartialCustomAudiences())
                .isEqualTo(0);
        assertWithMessage("Existing update status")
                .that(actualStats.getExistingUpdateStatus())
                .isEqualTo(SCHEDULE_CA_UPDATE_EXISTING_UPDATE_STATUS_DID_OVERWRITE_EXISTING_UPDATE);
        assertWithMessage("Callback status").that(callback.isSuccess()).isTrue();
    }

    @Test
    public void testScheduleCustomAudienceUpdate_OverDelay_Failure() throws Exception {
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(10000),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, mScheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verifyNoMoreInteractions(mCustomAudienceDaoMock);
        assertFalse(callback.isSuccess());
        assertEquals(
                "Delay Time not within permissible limits",
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_NullInput_Failure() throws Exception {
        ScheduleCustomAudienceUpdateInput input = null;

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, mScheduleCustomAudienceUpdateImpl);

        verifyNoMoreInteractions(mCustomAudienceServiceFilterMock);
        verifyNoMoreInteractions(mCustomAudienceDaoMock);
        assertFalse(callback.isSuccess());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_MissingBuyer_Failure() throws Exception {
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .build();

        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext)))
                .thenReturn(null);

        ScheduleCustomAudienceUpdateImpl impl =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);

        ScheduleUpdateTestCallback callback = callScheduleUpdate(input, impl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verifyNoMoreInteractions(mCustomAudienceDaoMock);
        assertFalse(callback.isSuccess());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_MissingConsent_SilentFailure() throws Exception {
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(MIN_DELAY_IN_MINUTES),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);

        ScheduleCustomAudienceUpdateImpl impl =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);

        ScheduleUpdateTestCallback callback = callScheduleUpdate(input, impl);

        verifyNoMoreInteractions(mCustomAudienceServiceFilterMock);
        verifyNoMoreInteractions(mCustomAudienceDaoMock);
        assertTrue(callback.isSuccess());
    }

    @Test
    public void testScheduleCAUpdate_ForegroundEnforcement_FiltersRequest() throws Exception {
        when(mMockFlags.getEnforceForegroundStatusForScheduleCustomAudience()).thenReturn(true);

        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(false),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext)))
                .thenReturn(BUYER);

        ScheduleCustomAudienceUpdateImpl scheduleCustomAudienceUpdateImpl =
                new ScheduleCustomAudienceUpdateImpl(
                        mContext,
                        mConsentManagerMock,
                        mCallingAppUid,
                        mMockFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);

        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(50),
                                Collections.emptyList())
                        .setShouldReplacePendingUpdates(false)
                        .build();

        ScheduleUpdateTestCallback callback =
                callScheduleUpdate(input, scheduleCustomAudienceUpdateImpl);

        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(true),
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));
        verifyNoMoreInteractions(mCustomAudienceDaoMock);
        expect.withMessage("callback.isSuccess()").that(callback.isSuccess()).isFalse();
    }

    private ScheduleUpdateTestCallback callScheduleUpdate(
            ScheduleCustomAudienceUpdateInput input, ScheduleCustomAudienceUpdateImpl impl)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        ScheduleUpdateTestCallback testCallback = new ScheduleUpdateTestCallback(latch);
        impl.doScheduleCustomAudienceUpdate(input, testCallback, mDevContext);
        latch.await();
        return testCallback;
    }
}
