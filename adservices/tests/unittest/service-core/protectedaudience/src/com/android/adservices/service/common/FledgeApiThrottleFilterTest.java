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

package com.android.adservices.service.common;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyString;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CommonFixture;
import android.os.LimitExceededException;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesLogger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class FledgeApiThrottleFilterTest extends AdServicesExtendedMockitoTestCase {
    @Mock private Throttler mThrottlerMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    private FledgeApiThrottleFilter mFledgeApiThrottleFilter;

    @Before
    public void setup() {
        mFledgeApiThrottleFilter =
                new FledgeApiThrottleFilter(mThrottlerMock, mAdServicesLoggerMock);
    }

    @Test
    public void testAssertCallerNotThrottled_notThrottled_doesNotThrowOrLogError() {
        when(mThrottlerMock.tryAcquire(any(), anyString())).thenReturn(true);

        mFledgeApiThrottleFilter.assertCallerNotThrottled(
                CommonFixture.TEST_PACKAGE_NAME,
                Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS);

        verifyNoMoreInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testAssertCallerNotThrottled_throttled_throwsAndLogsError() {
        when(mThrottlerMock.tryAcquire(any(), anyString())).thenReturn(false);

        assertThrows(
                LimitExceededException.class,
                () ->
                        mFledgeApiThrottleFilter.assertCallerNotThrottled(
                                CommonFixture.TEST_PACKAGE_NAME,
                                Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                                AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE),
                        eq(CommonFixture.TEST_PACKAGE_NAME),
                        eq(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED),
                        anyInt());
    }
}
