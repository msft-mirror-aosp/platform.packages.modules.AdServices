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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_DB_PERSIST_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_SMALL;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelperImpl;
import com.android.adservices.service.stats.pas.EncodingJsExecutionStats;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EncodingExecutionLogHelperImplTest {

    @Mock AdServicesLogger mAdServicesLoggerMock;
    @Mock Clock mClockMock;
    @Mock EnrollmentDao mEnrollmentDaoMock;

    private EncodingExecutionLogHelper mEncodingExecutionLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mEncodingExecutionLogger =
                new EncodingExecutionLogHelperImpl(
                        mAdServicesLoggerMock, mClockMock, mEnrollmentDaoMock);
    }

    @Test
    public void testLog() {
        AdTechIdentifier adtechId = AdTechIdentifier.fromString("ABC");
        when(mClockMock.currentTimeMillis()).thenReturn(100L);
        when(mEnrollmentDaoMock.getEnrollmentDataForPASByAdTechIdentifier(adtechId))
                .thenReturn(new EnrollmentData.Builder().setEnrollmentId("123").build());
        mEncodingExecutionLogger.startClock();
        verify(mClockMock).currentTimeMillis();
        mEncodingExecutionLogger.setStatus(JS_RUN_STATUS_DB_PERSIST_FAILURE);
        mEncodingExecutionLogger.setAdtech(adtechId);
        verify(mEnrollmentDaoMock).getEnrollmentDataForPASByAdTechIdentifier(eq(adtechId));
        when(mClockMock.currentTimeMillis()).thenReturn(200L);
        mEncodingExecutionLogger.finish();
        EncodingJsExecutionStats stats =
                EncodingJsExecutionStats.builder()
                        .setJsLatency(SIZE_SMALL)
                        .setRunStatus(JS_RUN_STATUS_DB_PERSIST_FAILURE)
                        .setAdTechId("123")
                        .build();
        verify(mAdServicesLoggerMock).logEncodingJsExecutionStats(eq(stats));
    }
}
