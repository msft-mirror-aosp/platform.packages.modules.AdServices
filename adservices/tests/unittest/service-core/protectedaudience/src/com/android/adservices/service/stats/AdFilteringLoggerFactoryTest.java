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

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CONTEXTUAL_ADS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class AdFilteringLoggerFactoryTest {

    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Captor
    private ArgumentCaptor<AdFilteringProcessAdSelectionReportedStats> mAdFilteringStatsCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testBothMetricsFlagsOn_returnsAdFilteringLoggerImpl() {
        Flags flags =
                new Flags() {
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }

                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return true;
                    }
                };
        AdFilteringLoggerFactory loggerFactory =
                new AdFilteringLoggerFactory(mAdServicesLoggerMock, flags);

        AdFilteringLogger contextualAdsLogger = loggerFactory.getContextualAdFilteringLogger();
        contextualAdsLogger.close();
        AdFilteringLogger customAudienceLogger = loggerFactory.getCustomAudienceFilteringLogger();
        customAudienceLogger.close();

        assertThat(loggerFactory.getContextualAdFilteringLogger())
                .isInstanceOf(AdFilteringLoggerImpl.class);
        assertThat(loggerFactory.getCustomAudienceFilteringLogger())
                .isInstanceOf(AdFilteringLoggerImpl.class);

        verify(mAdServicesLoggerMock, times(2))
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringStatsCaptor.capture());
        List<AdFilteringProcessAdSelectionReportedStats> adFilteringStatsList =
                mAdFilteringStatsCaptor.getAllValues();
        assertThat(adFilteringStatsList.get(0).getFilterProcessType())
                .isEqualTo(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS);
        assertThat(adFilteringStatsList.get(1).getFilterProcessType())
                .isEqualTo(FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES);
    }

    @Test
    public void testAppInstallMetricsFlagOn_returnsAdFilteringLoggerImpl() {
        Flags flags =
                new Flags() {
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return true;
                    }

                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return false;
                    }
                };
        AdFilteringLoggerFactory loggerFactory =
                new AdFilteringLoggerFactory(mAdServicesLoggerMock, flags);

        AdFilteringLogger contextualAdsLogger = loggerFactory.getContextualAdFilteringLogger();
        contextualAdsLogger.close();
        AdFilteringLogger customAudienceLogger = loggerFactory.getCustomAudienceFilteringLogger();
        customAudienceLogger.close();

        assertThat(loggerFactory.getContextualAdFilteringLogger())
                .isInstanceOf(AdFilteringLoggerImpl.class);
        assertThat(loggerFactory.getCustomAudienceFilteringLogger())
                .isInstanceOf(AdFilteringLoggerImpl.class);

        verify(mAdServicesLoggerMock, times(2))
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringStatsCaptor.capture());
        List<AdFilteringProcessAdSelectionReportedStats> adFilteringStatsList =
                mAdFilteringStatsCaptor.getAllValues();
        assertThat(adFilteringStatsList.get(0).getFilterProcessType())
                .isEqualTo(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS);
        assertThat(adFilteringStatsList.get(1).getFilterProcessType())
                .isEqualTo(FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES);
    }

    @Test
    public void testFreqCapMetricsFlagOn_returnsAdFilteringLoggerImpl() {
        Flags flags =
                new Flags() {
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return false;
                    }

                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return true;
                    }
                };
        AdFilteringLoggerFactory loggerFactory =
                new AdFilteringLoggerFactory(mAdServicesLoggerMock, flags);

        AdFilteringLogger contextualAdsLogger = loggerFactory.getContextualAdFilteringLogger();
        contextualAdsLogger.close();
        AdFilteringLogger customAudienceLogger = loggerFactory.getCustomAudienceFilteringLogger();
        customAudienceLogger.close();

        assertThat(loggerFactory.getContextualAdFilteringLogger())
                .isInstanceOf(AdFilteringLoggerImpl.class);
        assertThat(loggerFactory.getCustomAudienceFilteringLogger())
                .isInstanceOf(AdFilteringLoggerImpl.class);

        verify(mAdServicesLoggerMock, times(2))
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringStatsCaptor.capture());
        List<AdFilteringProcessAdSelectionReportedStats> adFilteringStatsList =
                mAdFilteringStatsCaptor.getAllValues();
        assertThat(adFilteringStatsList.get(0).getFilterProcessType())
                .isEqualTo(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS);
        assertThat(adFilteringStatsList.get(1).getFilterProcessType())
                .isEqualTo(FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES);
    }

    @Test
    public void testBothMetricsFlagsOff_returnsANoOpLoggerImpl() {
        Flags flags =
                new Flags() {
                    public boolean getFledgeAppInstallFilteringMetricsEnabled() {
                        return false;
                    }

                    public boolean getFledgeFrequencyCapFilteringMetricsEnabled() {
                        return false;
                    }
                };
        AdFilteringLoggerFactory loggerFactory =
                new AdFilteringLoggerFactory(mAdServicesLoggerMock, flags);

        AdFilteringLogger contextualAdsLogger = loggerFactory.getContextualAdFilteringLogger();
        contextualAdsLogger.close();
        AdFilteringLogger customAudienceLogger = loggerFactory.getCustomAudienceFilteringLogger();
        customAudienceLogger.close();

        assertThat(loggerFactory.getContextualAdFilteringLogger())
                .isInstanceOf(AdFilteringLoggerNoOp.class);
        assertThat(loggerFactory.getCustomAudienceFilteringLogger())
                .isInstanceOf(AdFilteringLoggerNoOp.class);
        verifyZeroInteractions(mAdServicesLoggerMock);
    }
}
