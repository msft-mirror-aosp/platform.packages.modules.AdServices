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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_DOWNLOAD_LATENCY_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIZE_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.service.stats.pas.EncodingFetchStats;
import com.android.adservices.service.stats.pas.EncodingJsFetchProcessLoggerImpl;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class EncodingJsFetchProcessLoggerImplTest {
    public static final String TEST_AD_TECH_ID = "com.google.android";
    public static final long TEST_JS_DOWNLOAD_START_TIMESTAMP = 100L;
    public static final long TEST_JS_DOWNLOAD_END_TIMESTAMP = 120L;
    public static final int TEST_JS_DOWNLOAD_TIME =
            (int) (TEST_JS_DOWNLOAD_END_TIMESTAMP - TEST_JS_DOWNLOAD_START_TIMESTAMP);
    @Mock private Clock mMockClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Spy private EncodingFetchStats.Builder mBuilderSpy;
    private FetchProcessLogger mFetchProcessLogger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBuilderSpy = Mockito.spy(EncodingFetchStats.builder());
        mFetchProcessLogger =
                new EncodingJsFetchProcessLoggerImpl(
                        mAdServicesLoggerMock, mMockClockMock, mBuilderSpy);
    }

    @Test
    public void testSetAdTechId() {
        mFetchProcessLogger.setAdTechId(TEST_AD_TECH_ID);
        verify(mBuilderSpy).setAdTechId(TEST_AD_TECH_ID);
    }

    @Test
    public void testEncodingJsFetchStatsLogger_successLogging() {
        ArgumentCaptor<EncodingFetchStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingFetchStats.class);

        when(mMockClockMock.currentTimeMillis()).thenReturn(TEST_JS_DOWNLOAD_END_TIMESTAMP);

        int statusCode = ENCODING_FETCH_STATUS_SUCCESS;
        mFetchProcessLogger.setJsDownloadStartTimestamp(TEST_JS_DOWNLOAD_START_TIMESTAMP);
        mFetchProcessLogger.setAdTechId(TEST_AD_TECH_ID);
        mFetchProcessLogger.logEncodingJsFetchStats(statusCode);

        // Verify the logging of EncodingFetchStats
        verify(mAdServicesLoggerMock).logEncodingJsFetchStats(argumentCaptor.capture());

        EncodingFetchStats stats = argumentCaptor.getValue();
        assertThat(stats.getFetchStatus()).isEqualTo(statusCode);
        assertThat(stats.getAdTechId()).isEqualTo(TEST_AD_TECH_ID);
        assertThat(stats.getHttpResponseCode()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getJsDownloadTime())
                .isEqualTo(computeSize(TEST_JS_DOWNLOAD_TIME, JS_DOWNLOAD_LATENCY_BUCKETS));
    }

    @Test
    public void testEncodingJsFetchStatsLogger_withoutJsDownloadStartTimestamp() {
        ArgumentCaptor<EncodingFetchStats> argumentCaptor =
                ArgumentCaptor.forClass(EncodingFetchStats.class);

        when(mMockClockMock.currentTimeMillis()).thenReturn(TEST_JS_DOWNLOAD_END_TIMESTAMP);

        int statusCode = ENCODING_FETCH_STATUS_SUCCESS;
        mFetchProcessLogger.setAdTechId(TEST_AD_TECH_ID);
        mFetchProcessLogger.logEncodingJsFetchStats(statusCode);

        // Verify the logging of EncodingFetchStats
        verify(mAdServicesLoggerMock).logEncodingJsFetchStats(argumentCaptor.capture());

        EncodingFetchStats stats = argumentCaptor.getValue();
        assertThat(stats.getFetchStatus()).isEqualTo(statusCode);
        assertThat(stats.getAdTechId()).isEqualTo(TEST_AD_TECH_ID);
        assertThat(stats.getHttpResponseCode()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getJsDownloadTime()).isEqualTo(SIZE_UNSET);
    }
}
