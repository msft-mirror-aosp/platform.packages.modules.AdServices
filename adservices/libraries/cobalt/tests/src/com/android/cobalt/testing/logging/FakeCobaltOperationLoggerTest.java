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

package com.android.cobalt.testing.logging;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Before;
import org.junit.Test;

public final class FakeCobaltOperationLoggerTest extends AdServicesUnitTestCase {
    private static final int METRIC_ID_1 = 1;
    private static final int REPORT_ID_1 = 2;
    private static final int METRIC_ID_2 = 3;
    private static final int REPORT_ID_2 = 4;

    private FakeCobaltOperationLogger mLogger;

    @Before
    public void setUp() {
        mLogger = new FakeCobaltOperationLogger();
    }

    @Test
    public void testLogStringBufferMaxExceeded_success() throws Exception {
        mLogger.logStringBufferMaxExceeded(METRIC_ID_1, REPORT_ID_1);
        mLogger.logStringBufferMaxExceeded(METRIC_ID_1, REPORT_ID_1);
        mLogger.logStringBufferMaxExceeded(METRIC_ID_2, REPORT_ID_2);

        assertThat(mLogger.getNumStringBufferMaxExceededOccurrences(METRIC_ID_1, REPORT_ID_1))
                .isEqualTo(2);
        assertThat(mLogger.getNumStringBufferMaxExceededOccurrences(METRIC_ID_2, REPORT_ID_2))
                .isEqualTo(1);
    }

    @Test
    public void testGetNumLogStringBufferMaxExceededOccurrences_notLoggedMetricReportReturnsZero()
            throws Exception {
        assertThat(mLogger.getNumStringBufferMaxExceededOccurrences(METRIC_ID_1, REPORT_ID_1))
                .isEqualTo(0);
    }

    @Test
    public void testLogEventVectorBufferMaxExceeded_success() throws Exception {
        mLogger.logEventVectorBufferMaxExceeded(METRIC_ID_1, REPORT_ID_1);
        mLogger.logEventVectorBufferMaxExceeded(METRIC_ID_1, REPORT_ID_1);
        mLogger.logEventVectorBufferMaxExceeded(METRIC_ID_2, REPORT_ID_2);

        assertThat(mLogger.getNumEventVectorBufferMaxExceededOccurrences(METRIC_ID_1, REPORT_ID_1))
                .isEqualTo(2);
        assertThat(mLogger.getNumEventVectorBufferMaxExceededOccurrences(METRIC_ID_2, REPORT_ID_2))
                .isEqualTo(1);
    }

    @Test
    public void
            testGetNumLogEventVectorBufferMaxExceededOccurrences_notLoggedMetricReportReturnsZero()
                    throws Exception {
        assertThat(mLogger.getNumEventVectorBufferMaxExceededOccurrences(METRIC_ID_1, REPORT_ID_1))
                .isEqualTo(0);
    }

    @Test
    public void testLogUploadFailure_success() throws Exception {
        mLogger.logUploadFailure();
        mLogger.logUploadFailure();
        assertThat(mLogger.getNumUploadSuccessOccurrences()).isEqualTo(0);
        assertThat(mLogger.getNumUploadFailureOccurrences()).isEqualTo(2);
    }

    @Test
    public void testLogUploadSuccess_success() throws Exception {
        mLogger.logUploadSuccess();
        mLogger.logUploadSuccess();

        assertThat(mLogger.getNumUploadSuccessOccurrences()).isEqualTo(2);
        assertThat(mLogger.getNumUploadFailureOccurrences()).isEqualTo(0);
    }
}
