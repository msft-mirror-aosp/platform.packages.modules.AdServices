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
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.net.Uri;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@MockStatic(ScheduleCustomAudienceUpdateJobService.class)
@MockStatic(ConsentManager.class)
public final class ScheduleCustomAudienceUpdateImplTest extends AdServicesExtendedMockitoTestCase {
    private static final int API_NAME =
            AD_SERVICES_API_CALLED__API_NAME__SCHEDULE_CUSTOM_AUDIENCE_UPDATE;

    private static final Uri UPDATE_URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    @Captor private ArgumentCaptor<DBScheduledCustomAudienceUpdate> mUpdateCaptor;
    @Captor private ArgumentCaptor<List<PartialCustomAudience>> mListArgumentCaptor;
    private ListeningExecutorService mBackgroundExecutorService;
    private int mCallingAppUid;
    @Mock private Flags mFlagsMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    private ScheduleCustomAudienceUpdateImpl mScheduleCustomAudienceUpdateImpl;
    private DevContext mDevContext;

    @Before
    public void setup() {
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mCallingAppUid = CallingAppUidSupplierProcessImpl.create().getCallingAppUid();
        when(mFlagsMock.getFledgeScheduleCustomAudienceUpdateEnabled()).thenReturn(true);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        mDevContext = DevContext.builder(PACKAGE).setDevOptionsEnabled(false).build();
        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
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
                        mFlagsMock,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);

        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);
        when(mFlagsMock.getEnforceForegroundStatusForSignals()).thenReturn(true);

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
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledUpdateAndPartialCustomAudienceList(
                        mUpdateCaptor.capture(), mListArgumentCaptor.capture(), eq(false));
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_withShouldReplacePendingUpdateTrue_Success()
            throws Exception {
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                UPDATE_URI,
                                PACKAGE,
                                Duration.ofMinutes(50),
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
                        eq(mCallingAppUid),
                        eq(API_NAME),
                        eq(FLEDGE_API_SCHEDULE_CUSTOM_AUDIENCE_UPDATE),
                        eq(mDevContext));

        verify(mCustomAudienceDaoMock)
                .insertScheduledUpdateAndPartialCustomAudienceList(
                        mUpdateCaptor.capture(), mListArgumentCaptor.capture(), eq(true));
        assertEquals(BUYER, mUpdateCaptor.getValue().getBuyer());
        assertTrue(mListArgumentCaptor.getValue().isEmpty());
        assertTrue(callback.isSuccess());
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
                                Duration.ofMinutes(50),
                                Collections.emptyList())
                        .build();

        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(UPDATE_URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(false),
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
                        mFlagsMock,
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
                                Duration.ofMinutes(50),
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
                        mFlagsMock,
                        mAdServicesLoggerMock,
                        mBackgroundExecutorService,
                        mCustomAudienceServiceFilterMock,
                        mCustomAudienceDaoMock);

        ScheduleUpdateTestCallback callback = callScheduleUpdate(input, impl);

        verifyNoMoreInteractions(mCustomAudienceServiceFilterMock);
        verifyNoMoreInteractions(mCustomAudienceDaoMock);
        assertTrue(callback.isSuccess());
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
