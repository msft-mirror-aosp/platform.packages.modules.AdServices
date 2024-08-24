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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_SUCCESS;

import static org.mockito.Mockito.verifyZeroInteractions;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FetchProcessLoggerNoLoggingImplTest {
    private static final String TEST_AD_TECH_ID = "com.google.android";
    private static final long TEST_JS_DOWNLOAD_START_TIMESTAMP = 100L;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testLogEncodingJsFetchStatsNoLogging() {
        FetchProcessLoggerNoLoggingImpl encodingJsFetchStatsLoggerNoLogging =
                new FetchProcessLoggerNoLoggingImpl();

        encodingJsFetchStatsLoggerNoLogging.setAdTechId(TEST_AD_TECH_ID);
        encodingJsFetchStatsLoggerNoLogging.setJsDownloadStartTimestamp(
                TEST_JS_DOWNLOAD_START_TIMESTAMP);
        encodingJsFetchStatsLoggerNoLogging.logEncodingJsFetchStats(ENCODING_FETCH_STATUS_SUCCESS);
        verifyZeroInteractions(mAdServicesLoggerMock);
    }
}
