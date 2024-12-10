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

import static org.mockito.Mockito.verifyZeroInteractions;

import com.android.adservices.service.stats.pas.EncodingJobRunStatsLoggerNoLoggingImpl;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class EncodingJobRunStatsLoggerNoLoggingImplTest {
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testEncodingJobRunStatsLoggerNoLogging() {
        EncodingJobRunStatsLoggerNoLoggingImpl encodingJobRunStatsLoggerNoLogging =
                new EncodingJobRunStatsLoggerNoLoggingImpl();

        encodingJobRunStatsLoggerNoLogging.addOneSignalEncodingFailures();
        encodingJobRunStatsLoggerNoLogging.addOneSignalEncodingSkips();
        encodingJobRunStatsLoggerNoLogging.addOneSignalEncodingFailures();
        encodingJobRunStatsLoggerNoLogging.addOneSignalEncodingSkips();
        encodingJobRunStatsLoggerNoLogging.setSizeOfFilteredBuyerEncodingList(5);
        encodingJobRunStatsLoggerNoLogging.resetStatsWithEncodingSourceType(
                AdsRelevanceStatusUtils.PAS_ENCODING_SOURCE_TYPE_ENCODING_JOB_SERVICE);
        encodingJobRunStatsLoggerNoLogging.logEncodingJobRunStats();

        verifyZeroInteractions(mAdServicesLoggerMock);
    }
}
