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
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_JS_REFERENCE_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_MEDIUM;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_SMALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_LARGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_VERY_SMALL;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelperImpl;
import com.android.adservices.service.stats.pas.EncodingJsExecutionStats;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class EncodingExecutionLogHelperImplTest extends AdServicesMockitoTestCase {

    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private Clock mClockMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;

    private EncodingExecutionLogHelper mEncodingExecutionLogger;
    private AdTechIdentifier mAdtechId;

    @Before
    public void setUp() {
        mEncodingExecutionLogger =
                new EncodingExecutionLogHelperImpl(
                        mAdServicesLoggerMock, mClockMock, mEnrollmentDaoMock);
        mAdtechId = AdTechIdentifier.fromString("ABC");
        when(mClockMock.currentTimeMillis()).thenReturn(100L);
        when(mEnrollmentDaoMock.getEnrollmentDataForPASByAdTechIdentifier(mAdtechId))
                .thenReturn(new EnrollmentData.Builder().setEnrollmentId("123").build());
    }

    @Test
    public void testLog_jsLatencyAndSignalSizeWithVerySmallBucketSize() {
        // When JsLatency is smaller than 50 milliseconds,
        // encodedSignalSize is smaller than 10 bytes,
        // EncodingJsExecution should log SIZE_VERY_SMALL in both metrics.
        setupEncodingExecutionLog(
                /* endTimestamp = */ 145L,
                /* encodedSignalSize = */ 9,
                /* jsRunStatus = */ JS_RUN_STATUS_SUCCESS);
        verifyEncodingExecutionLog(
                /* expectedJsLatencyInBucket = */ SIZE_VERY_SMALL,
                /* expectedEncodedSignalSizeInBucket = */ SIZE_VERY_SMALL,
                /* expectedJsRunStatus = */ JS_RUN_STATUS_SUCCESS);
    }

    @Test
    public void testLog_jsLatencyAndSignalSizeWithSmallBucketSize() {
        // When JsLatency is smaller than 200 milliseconds and larger or equal to 20,
        // encodedSignalSize is smaller than 100 bytes and larger or equal to 10,
        // EncodingJsExecution should log SIZE_SMALL in both metrics.
        setupEncodingExecutionLog(
                /* endTimestamp = */ 150L,
                /* encodedSignalSize = */ 10,
                /* jsRunStatus = */ JS_RUN_STATUS_DB_PERSIST_FAILURE);
        verifyEncodingExecutionLog(
                /* expectedJsLatencyInBucket = */ SIZE_SMALL,
                /* expectedEncodedSignalSizeInBucket = */ SIZE_SMALL,
                /* expectedJsRunStatus = */ JS_RUN_STATUS_DB_PERSIST_FAILURE);
    }

    @Test
    public void testLog_jsLatencyAndSignalSizeWithMediumBucketSize() {
        // When JsLatency is smaller than 1000 milliseconds and larger or equal to 200,
        // encodedSignalSize is smaller than 500 bytes and larger or equal to 100,
        // EncodingJsExecution should log SIZE_MEDIUM in both metrics.
        setupEncodingExecutionLog(
                /* endTimestamp = */ 1099L,
                /* encodedSignalSize = */ 420,
                /* jsRunStatus = */ JS_RUN_STATUS_JS_REFERENCE_ERROR);
        verifyEncodingExecutionLog(
                /* expectedJsLatencyInBucket = */ SIZE_MEDIUM,
                /* expectedEncodedSignalSizeInBucket = */ SIZE_MEDIUM,
                /* expectedJsRunStatus = */ JS_RUN_STATUS_JS_REFERENCE_ERROR);
    }

    @Test
    public void testLog_jsLatencyAndSignalSizeWithLargeBucketSize() {
        // When JsLatency is smaller than 2000 milliseconds and larger or equal to 1000,
        // encodedSignalSize is smaller than 5000 bytes and larger or equal to 500,
        // EncodingJsExecution should log SIZE_LARGE in both metrics.
        setupEncodingExecutionLog(
                /* endTimestamp = */ 2024L,
                /* encodedSignalSize = */ 4999,
                /* jsRunStatus = */ JS_RUN_STATUS_SUCCESS);
        verifyEncodingExecutionLog(
                /* expectedJsLatencyInBucket = */ SIZE_LARGE,
                /* expectedEncodedSignalSizeInBucket = */ SIZE_LARGE,
                /* expectedJsRunStatus = */ JS_RUN_STATUS_SUCCESS);
    }

    @Test
    public void testLog_jsLatencyAndSignalSizeWithVeryLargeBucketSize() {
        // When JsLatency is larger or equal to 2000 milliseconds,
        // encodedSignalSize is lager or equal to 5000 bytes,
        // EncodingJsExecution should log SIZE_VERY_LARGE in both metrics.
        setupEncodingExecutionLog(
                /* endTimestamp = */ 3000L,
                /* encodedSignalSize = */ 5000,
                /* jsRunStatus = */ JS_RUN_STATUS_SUCCESS);
        verifyEncodingExecutionLog(
                /* expectedJsLatencyInBucket = */ SIZE_VERY_LARGE,
                /* expectedEncodedSignalSizeInBucket = */ SIZE_VERY_LARGE,
                /* expectedJsRunStatus = */ JS_RUN_STATUS_SUCCESS);
    }

    private void setupEncodingExecutionLog(
            long endTimestamp,
            int encodedSignalSize,
            @AdsRelevanceStatusUtils.JsRunStatus int jsRunStatus) {
        mEncodingExecutionLogger.startClock();
        verify(mClockMock).currentTimeMillis();
        mEncodingExecutionLogger.setStatus(jsRunStatus);
        mEncodingExecutionLogger.setEncodedSignalSize(encodedSignalSize);
        mEncodingExecutionLogger.setAdtech(mAdtechId);
        verify(mEnrollmentDaoMock).getEnrollmentDataForPASByAdTechIdentifier(eq(mAdtechId));
        when(mClockMock.currentTimeMillis()).thenReturn(endTimestamp);
        mEncodingExecutionLogger.finish();
    }

    private void verifyEncodingExecutionLog(
            @AdsRelevanceStatusUtils.Size int expectedJsLatencyInBucket,
            @AdsRelevanceStatusUtils.Size int expectedEncodedSignalSizeInBucket,
            @AdsRelevanceStatusUtils.JsRunStatus int expectedJsRunStatus) {
        EncodingJsExecutionStats stats =
                EncodingJsExecutionStats.builder()
                        .setJsLatency(expectedJsLatencyInBucket)
                        .setRunStatus(expectedJsRunStatus)
                        .setAdTechId("123")
                        .setEncodedSignalsSize(expectedEncodedSignalSizeInBucket)
                        .build();
        verify(mAdServicesLoggerMock).logEncodingJsExecutionStats(eq(stats));
    }
}
