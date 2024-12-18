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

import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.AD_FILTERING_END;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.AD_FILTERING_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.AD_FILTERING_START;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.APP_INSTALL_FILTERING_END;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.APP_INSTALL_FILTERING_LATENCY_MS;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.APP_INSTALL_FILTERING_START;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.FREQUENCY_CAP_FILTERING_LATENCY_MS;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.FREQ_CAP_FILTERING_END;
import static com.android.adservices.service.stats.AdFilteringLoggerImplTestFixture.FREQ_CAP_FILTERING_START;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CONTEXTUAL_ADS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AdFilteringLoggerImplTest {
    @Captor ArgumentCaptor<AdFilteringProcessAdSelectionReportedStats> mAdFilteringLoggerCaptor;
    @Mock private Clock mMockClock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testContextualAdsFiltering_success() {
        int numOfContextualAdsFiltered = 10;
        int numOfContextualAdsFilteredOutOfBiddingInvalidSignatures = 20;
        int numOfContextualAdsFilteredOutOfBiddingNoAds = 30;
        int totalNumOfContextualAdsBeforeFiltering = 40;
        int numOfAdCounterKeysInFcapFilters = 50;
        int numOfPackageInAppInstallFilters = 60;
        int numOfDbOperations = 70;
        int totalNumOfAdsBeforeFiltering = 80;
        int shouldBeIgnored = 999;
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        AD_FILTERING_START,
                        APP_INSTALL_FILTERING_START,
                        APP_INSTALL_FILTERING_END,
                        FREQ_CAP_FILTERING_START,
                        FREQ_CAP_FILTERING_END,
                        AD_FILTERING_END);

        AdFilteringLogger logger = getAdFilteringLogger(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS);
        logger.setAdFilteringStartTimestamp();
        logger.setAppInstallStartTimestamp();
        logger.setAppInstallEndTimestamp();
        logger.setFrequencyCapStartTimestamp();
        logger.setFrequencyCapEndTimestamp();
        logger.setAdFilteringEndTimestamp();
        logger.setNumOfContextualAdsFiltered(numOfContextualAdsFiltered);
        logger.setTotalNumOfAdsBeforeFiltering(totalNumOfAdsBeforeFiltering);
        logger.setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(
                numOfContextualAdsFilteredOutOfBiddingInvalidSignatures);
        logger.setNumOfContextualAdsFilteredOutOfBiddingNoAds(
                numOfContextualAdsFilteredOutOfBiddingNoAds);
        logger.setTotalNumOfContextualAdsBeforeFiltering(totalNumOfContextualAdsBeforeFiltering);
        logger.setNumOfAdCounterKeysInFcapFilters(numOfAdCounterKeysInFcapFilters);
        logger.setNumOfPackagesInAppInstallFilters(numOfPackageInAppInstallFilters);
        logger.setNumOfDbOperations(numOfDbOperations);
        // Custom audience specific metrics should be ignored when filter processing type is
        // contextual ads
        logger.setNumOfAdsFilteredOutOfBidding(shouldBeIgnored);
        logger.setNumOfCustomAudiencesFilteredOutOfBidding(shouldBeIgnored);
        logger.setTotalNumOfCustomAudiencesBeforeFiltering(shouldBeIgnored);
        logger.close();

        verify(mAdServicesLoggerMock)
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringLoggerCaptor.capture());
        AdFilteringProcessAdSelectionReportedStats stats = mAdFilteringLoggerCaptor.getValue();

        assertThat(stats.getLatencyInMillisOfAppInstallFiltering())
                .isEqualTo(APP_INSTALL_FILTERING_LATENCY_MS);
        assertThat(stats.getLatencyInMillisOfFcapFilters())
                .isEqualTo(FREQUENCY_CAP_FILTERING_LATENCY_MS);
        assertThat(stats.getLatencyInMillisOfAllAdFiltering())
                .isEqualTo(AD_FILTERING_OVERALL_LATENCY_MS);
        assertThat(stats.getFilterProcessType()).isEqualTo(FILTER_PROCESS_TYPE_CONTEXTUAL_ADS);
        assertThat(stats.getNumOfContextualAdsFiltered()).isEqualTo(numOfContextualAdsFiltered);
        assertThat(stats.getTotalNumOfAdsBeforeFiltering()).isEqualTo(totalNumOfAdsBeforeFiltering);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures())
                .isEqualTo(numOfContextualAdsFilteredOutOfBiddingInvalidSignatures);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds())
                .isEqualTo(numOfContextualAdsFilteredOutOfBiddingNoAds);
        assertThat(stats.getTotalNumOfContextualAdsBeforeFiltering())
                .isEqualTo(totalNumOfContextualAdsBeforeFiltering);
        assertThat(stats.getNumOfAdCounterKeysInFcapFilters())
                .isEqualTo(numOfAdCounterKeysInFcapFilters);
        assertThat(stats.getNumOfPackageInAppInstallFilters())
                .isEqualTo(numOfPackageInAppInstallFilters);
        assertThat(stats.getNumOfDbOperations()).isEqualTo(numOfDbOperations);
        assertThat(stats.getNumOfAdsFilteredOutOfBidding()).isEqualTo(0);
        assertThat(stats.getNumOfCustomAudiencesFilteredOutOfBidding()).isEqualTo(0);
        assertThat(stats.getTotalNumOfCustomAudiencesBeforeFiltering()).isEqualTo(0);
    }

    @Test
    public void testCustomAudienceFiltering_success() {
        int numOfAdsFilteredOutOfBidding = 10;
        int numOfCustomAudiencesFilteredOutOfBidding = 20;
        int totalNumOfCustomAudiencesBeforeFiltering = 30;
        int numOfAdCounterKeysInFcapFilters = 40;
        int numOfPackageInAppInstallFilters = 50;
        int numOfDbOperations = 60;
        int totalNumOfAdsBeforeFiltering = 70;
        int shouldBeIgnored = 999;
        when(mMockClock.elapsedRealtime())
                .thenReturn(
                        AD_FILTERING_START,
                        APP_INSTALL_FILTERING_START,
                        APP_INSTALL_FILTERING_END,
                        FREQ_CAP_FILTERING_START,
                        FREQ_CAP_FILTERING_END,
                        AD_FILTERING_END);

        AdFilteringLogger logger = getAdFilteringLogger(FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES);
        logger.setAdFilteringStartTimestamp();
        logger.setAppInstallStartTimestamp();
        logger.setAppInstallEndTimestamp();
        logger.setFrequencyCapStartTimestamp();
        logger.setFrequencyCapEndTimestamp();
        logger.setAdFilteringEndTimestamp();
        logger.setNumOfAdsFilteredOutOfBidding(numOfAdsFilteredOutOfBidding);
        logger.setNumOfCustomAudiencesFilteredOutOfBidding(
                numOfCustomAudiencesFilteredOutOfBidding);
        logger.setTotalNumOfCustomAudiencesBeforeFiltering(
                totalNumOfCustomAudiencesBeforeFiltering);
        logger.setTotalNumOfAdsBeforeFiltering(totalNumOfAdsBeforeFiltering);
        logger.setNumOfAdCounterKeysInFcapFilters(numOfAdCounterKeysInFcapFilters);
        logger.setNumOfPackagesInAppInstallFilters(numOfPackageInAppInstallFilters);
        logger.setNumOfDbOperations(numOfDbOperations);
        // Contextual ads specific metrics should be ignored when filter processing type is custom
        // audience
        logger.setNumOfContextualAdsFiltered(shouldBeIgnored);
        logger.setNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures(shouldBeIgnored);
        logger.setNumOfContextualAdsFilteredOutOfBiddingNoAds(shouldBeIgnored);
        logger.setTotalNumOfContextualAdsBeforeFiltering(shouldBeIgnored);

        logger.close();

        verify(mAdServicesLoggerMock)
                .logAdFilteringProcessAdSelectionReportedStats(mAdFilteringLoggerCaptor.capture());
        AdFilteringProcessAdSelectionReportedStats stats = mAdFilteringLoggerCaptor.getValue();

        assertThat(stats.getLatencyInMillisOfAppInstallFiltering())
                .isEqualTo(APP_INSTALL_FILTERING_LATENCY_MS);
        assertThat(stats.getLatencyInMillisOfFcapFilters())
                .isEqualTo(FREQUENCY_CAP_FILTERING_LATENCY_MS);
        assertThat(stats.getLatencyInMillisOfAllAdFiltering())
                .isEqualTo(AD_FILTERING_OVERALL_LATENCY_MS);
        assertThat(stats.getFilterProcessType()).isEqualTo(FILTER_PROCESS_TYPE_CUSTOM_AUDIENCES);
        assertThat(stats.getNumOfAdsFilteredOutOfBidding()).isEqualTo(numOfAdsFilteredOutOfBidding);
        assertThat(stats.getTotalNumOfAdsBeforeFiltering()).isEqualTo(totalNumOfAdsBeforeFiltering);
        assertThat(stats.getNumOfCustomAudiencesFilteredOutOfBidding())
                .isEqualTo(numOfCustomAudiencesFilteredOutOfBidding);
        assertThat(stats.getTotalNumOfCustomAudiencesBeforeFiltering())
                .isEqualTo(totalNumOfCustomAudiencesBeforeFiltering);
        assertThat(stats.getNumOfAdCounterKeysInFcapFilters())
                .isEqualTo(numOfAdCounterKeysInFcapFilters);
        assertThat(stats.getNumOfPackageInAppInstallFilters())
                .isEqualTo(numOfPackageInAppInstallFilters);
        assertThat(stats.getNumOfDbOperations()).isEqualTo(numOfDbOperations);
        assertThat(stats.getNumOfContextualAdsFiltered()).isEqualTo(0);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingInvalidSignatures()).isEqualTo(0);
        assertThat(stats.getNumOfContextualAdsFilteredOutOfBiddingNoAds()).isEqualTo(0);
        assertThat(stats.getTotalNumOfContextualAdsBeforeFiltering()).isEqualTo(0);
    }

    private AdFilteringLogger getAdFilteringLogger(int filterProcessingType) {
        return new AdFilteringLoggerImpl(filterProcessingType, mAdServicesLoggerMock, mMockClock);
    }
}
