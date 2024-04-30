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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_RUN_STATUS_UNSET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ReportImpressionExecutionLoggerImplTest {
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testReportImpressionStatsLogger_successLogging() {
        ReportImpressionExecutionLogger reportImpressionStatsLogger =
                new ReportImpressionExecutionLoggerImpl(
                        mAdServicesLoggerMock, new ReportImpressionExecutionLoggerTestFlags());
        ArgumentCaptor<ReportImpressionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportImpressionApiCalledStats.class);

        reportImpressionStatsLogger.setReportWinBuyerAdditionalSignalsContainedAdCost(true);
        reportImpressionStatsLogger.setReportWinBuyerAdditionalSignalsContainedDataVersion(true);
        reportImpressionStatsLogger.setReportResultSellerAdditionalSignalsContainedDataVersion(
                true);
        reportImpressionStatsLogger.setReportWinJsScriptResultCode(1);
        reportImpressionStatsLogger.setReportResultJsScriptResultCode(2);

        reportImpressionStatsLogger.logReportImpressionApiCalledStats();

        verify(mAdServicesLoggerMock).logReportImpressionApiCalledStats(argumentCaptor.capture());

        ReportImpressionApiCalledStats stats = argumentCaptor.getValue();

        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedAdCost()).isEqualTo(true);
        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedDataVersion()).isEqualTo(true);
        assertThat(stats.getReportResultSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(true);
        assertThat(stats.getReportWinJsScriptResultCode()).isEqualTo(1);
        assertThat(stats.getReportResultJsScriptResultCode()).isEqualTo(2);
    }

    @Test
    public void testReportImpressionStatsLogger_default() {
        ReportImpressionExecutionLogger reportImpressionStatsLogger =
                new ReportImpressionExecutionLoggerImpl(
                        mAdServicesLoggerMock, new ReportImpressionExecutionLoggerTestFlags());
        ArgumentCaptor<ReportImpressionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportImpressionApiCalledStats.class);

        reportImpressionStatsLogger.logReportImpressionApiCalledStats();

        verify(mAdServicesLoggerMock).logReportImpressionApiCalledStats(argumentCaptor.capture());

        ReportImpressionApiCalledStats stats = argumentCaptor.getValue();

        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedAdCost()).isEqualTo(false);
        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedDataVersion()).isEqualTo(false);
        assertThat(stats.getReportResultSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(false);
        assertThat(stats.getReportWinJsScriptResultCode()).isEqualTo(JS_RUN_STATUS_UNSET);
        assertThat(stats.getReportResultJsScriptResultCode()).isEqualTo(JS_RUN_STATUS_UNSET);
    }

    @Test
    public void testReportImpressionStatsLogger_disabledCpcBillingMetrics() {
        ReportImpressionExecutionLogger reportImpressionStatsLogger =
                new ReportImpressionExecutionLoggerImpl(
                        mAdServicesLoggerMock,
                        new ReportImpressionExecutionLoggerTestFlags() {
                            @Override
                            public boolean getFledgeCpcBillingMetricsEnabled() {
                                return false;
                            }
                        });
        ArgumentCaptor<ReportImpressionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportImpressionApiCalledStats.class);

        reportImpressionStatsLogger.setReportWinBuyerAdditionalSignalsContainedAdCost(true);
        reportImpressionStatsLogger.setReportWinBuyerAdditionalSignalsContainedDataVersion(true);
        reportImpressionStatsLogger.setReportResultSellerAdditionalSignalsContainedDataVersion(
                true);
        reportImpressionStatsLogger.setReportWinJsScriptResultCode(1);
        reportImpressionStatsLogger.setReportResultJsScriptResultCode(2);

        reportImpressionStatsLogger.logReportImpressionApiCalledStats();

        verify(mAdServicesLoggerMock).logReportImpressionApiCalledStats(argumentCaptor.capture());

        ReportImpressionApiCalledStats stats = argumentCaptor.getValue();

        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedAdCost()).isEqualTo(false);
        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedDataVersion()).isEqualTo(true);
        assertThat(stats.getReportResultSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(true);
        assertThat(stats.getReportWinJsScriptResultCode()).isEqualTo(1);
        assertThat(stats.getReportResultJsScriptResultCode()).isEqualTo(2);
    }

    @Test
    public void testReportImpressionStatsLogger_disabledDataVersionHeaderMetrics() {
        ReportImpressionExecutionLogger reportImpressionStatsLogger =
                new ReportImpressionExecutionLoggerImpl(
                        mAdServicesLoggerMock,
                        new ReportImpressionExecutionLoggerTestFlags() {
                            @Override
                            public boolean getFledgeDataVersionHeaderMetricsEnabled() {
                                return false;
                            }
                        });
        ArgumentCaptor<ReportImpressionApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ReportImpressionApiCalledStats.class);

        reportImpressionStatsLogger.setReportWinBuyerAdditionalSignalsContainedAdCost(true);
        reportImpressionStatsLogger.setReportWinBuyerAdditionalSignalsContainedDataVersion(true);
        reportImpressionStatsLogger.setReportResultSellerAdditionalSignalsContainedDataVersion(
                true);
        reportImpressionStatsLogger.setReportWinJsScriptResultCode(1);
        reportImpressionStatsLogger.setReportResultJsScriptResultCode(2);

        reportImpressionStatsLogger.logReportImpressionApiCalledStats();

        verify(mAdServicesLoggerMock).logReportImpressionApiCalledStats(argumentCaptor.capture());

        ReportImpressionApiCalledStats stats = argumentCaptor.getValue();

        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedAdCost()).isEqualTo(true);
        assertThat(stats.getReportWinBuyerAdditionalSignalsContainedDataVersion()).isEqualTo(false);
        assertThat(stats.getReportResultSellerAdditionalSignalsContainedDataVersion())
                .isEqualTo(false);
        assertThat(stats.getReportWinJsScriptResultCode()).isEqualTo(1);
        assertThat(stats.getReportResultJsScriptResultCode()).isEqualTo(2);
    }

    private static class ReportImpressionExecutionLoggerTestFlags implements Flags {
        @Override
        public boolean getFledgeReportImpressionApiMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeCpcBillingMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeDataVersionHeaderMetricsEnabled() {
            return true;
        }
    }
}
