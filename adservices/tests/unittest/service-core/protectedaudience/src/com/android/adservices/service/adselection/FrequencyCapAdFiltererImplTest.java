/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.FrequencyCapFiltersFixture;
import android.adservices.common.KeyedFrequencyCapFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class FrequencyCapAdFiltererImplTest {
    private static final AdData.Builder AD_DATA_BUILDER =
            AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0);

    private static final AdData AD_DATA = AD_DATA_BUILDER.build();

    private static final AdData AD_DATA_NO_FILTER =
            new AdData.Builder()
                    .setRenderUri(AD_DATA.getRenderUri())
                    .setMetadata(AD_DATA.getMetadata())
                    .build();

    private static final SignedContextualAds.Builder CONTEXTUAL_ADS_BUILDER =
            SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder()
                    .setAdsWithBid(ImmutableList.of(new AdWithBid(AD_DATA, 1.0)))
                    .setBuyer(CommonFixture.VALID_BUYER_1)
                    .setDecisionLogicUri(
                            CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/decisionPath/"));

    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    private FrequencyCapAdFilterer mFrequencyCapAdFilterer;

    @Before
    public void setup() {
        mFrequencyCapAdFilterer =
                new FrequencyCapAdFiltererImpl(
                        mFrequencyCapDaoMock, CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);
    }

    @Test
    public void testFilterContextualAdsDoesNotFilterNullAdFilters() {
        final AdData adData = AD_DATA_BUILDER.setAdFilters(null).build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(contextualAds, mFrequencyCapAdFilterer.filterContextualAds(contextualAds));
    }

    @Test
    public void testFilterContextualAdsDoesNotFilterNullComponentFilters() {
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setAppInstallFilters(null)
                                        .setFrequencyCapFilters(null)
                                        .build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(contextualAds, mFrequencyCapAdFilterer.filterContextualAds(contextualAds));
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterContextualAdsWithEmptyFrequencyCapFilters() {
        doReturn(KeyedFrequencyCapFixture.FILTER_EXCEED_COUNT)
                .when(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(anyInt(), any(), anyInt(), any());

        final AdData adDataNotFiltered =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                new FrequencyCapFilters.Builder().build())
                                        .build())
                        .build();
        final AdData dataNoFilters = AD_DATA_NO_FILTER;
        List<AdWithBid> adsWithBid =
                ImmutableList.of(
                        new AdWithBid(adDataNotFiltered, 1.0), new AdWithBid(dataNoFilters, 2.0));

        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER.setAdsWithBid(adsWithBid).build();

        assertThat(mFrequencyCapAdFilterer.filterContextualAds(contextualAds).getAdsWithBid())
                .containsExactlyElementsIn(adsWithBid);

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterContextualAdsForNonMatchingFrequencyCap() {
        doReturn(KeyedFrequencyCapFixture.FILTER_UNDER_MAX_COUNT)
                .when(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(anyInt(), any(), anyInt(), any());

        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS)
                                        .build())
                        .build();
        final AdData dataNoFilters = AD_DATA_NO_FILTER;
        List<AdWithBid> adsWithBid =
                ImmutableList.of(new AdWithBid(adData, 1.0), new AdWithBid(dataNoFilters, 2.0));

        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER.setAdsWithBid(adsWithBid).build();

        assertThat(mFrequencyCapAdFilterer.filterContextualAds(contextualAds).getAdsWithBid())
                .containsExactlyElementsIn(adsWithBid);

        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION), any());
        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_VIEW), any());
        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK), any());
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterContextualAdsForMatchingFrequencyCap() {
        doReturn(KeyedFrequencyCapFixture.FILTER_EXCEED_COUNT)
                .when(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(anyInt(), any(), anyInt(), any());

        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS)
                                        .build())
                        .build();
        final AdData dataNoFilters = AD_DATA_NO_FILTER;
        List<AdWithBid> adsWithBid =
                ImmutableList.of(new AdWithBid(adData, 1.0), new AdWithBid(dataNoFilters, 2.0));
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER.setAdsWithBid(adsWithBid).build();

        assertThat(mFrequencyCapAdFilterer.filterContextualAds(contextualAds).getAdsWithBid())
                .containsExactly(new AdWithBid(dataNoFilters, 2.0));
        verify(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(anyInt(), any(), anyInt(), any());
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterContextualAdsDoNotFilterWinFrequencyCaps() {
        final AdData adDataOnlyWinFilters =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS_ONLY_WIN)
                                        .build())
                        .build();
        final AdData dataNoFilters = AD_DATA_NO_FILTER;
        List<AdWithBid> adsWithBid =
                ImmutableList.of(
                        new AdWithBid(adDataOnlyWinFilters, 1.0),
                        new AdWithBid(dataNoFilters, 2.0));
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER.setAdsWithBid(adsWithBid).build();

        assertThat(mFrequencyCapAdFilterer.filterContextualAds(contextualAds).getAdsWithBid())
                .containsExactlyElementsIn(adsWithBid);

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterCustomAudiencesNothingFiltered() {
        List<DBCustomAudience> caList =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        assertEquals(caList, mFrequencyCapAdFilterer.filterCustomAudiences(caList));
    }

    @Test
    public void testFilterCustomAudiencesWithEmptyFrequencyCapFilters() {
        DBCustomAudience caWithEmptyFrequencyCapFilters =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(
                                Arrays.asList(
                                        DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                                                .setAdFilters(
                                                        new AdFilters.Builder()
                                                                .setFrequencyCapFilters(
                                                                        new FrequencyCapFilters
                                                                                        .Builder()
                                                                                .build())
                                                                .build())
                                                .build(),
                                        DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS))
                        .build();

        List<DBCustomAudience> inputList =
                Arrays.asList(
                        caWithEmptyFrequencyCapFilters,
                        DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        assertThat(mFrequencyCapAdFilterer.filterCustomAudiences(inputList))
                .containsExactlyElementsIn(inputList);

        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterCustomAudiencesWithNonMatchingFrequencyCapFilters() {
        doReturn(KeyedFrequencyCapFixture.FILTER_UNDER_MAX_COUNT)
                .when(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(anyInt(), any(), anyInt(), any());

        DBAdData adDataNotFiltered =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS)
                                        .build())
                        .build();

        DBCustomAudience caWithEmptyFrequencyCapFilters =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(
                                Arrays.asList(
                                        adDataNotFiltered,
                                        DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS))
                        .build();

        List<DBCustomAudience> inputList =
                Arrays.asList(
                        caWithEmptyFrequencyCapFilters,
                        DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        assertThat(mFrequencyCapAdFilterer.filterCustomAudiences(inputList))
                .containsExactlyElementsIn(inputList);

        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForCustomAudienceAfterTime(
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_WIN),
                        any());
        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION), any());
        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_VIEW), any());
        verify(
                        mFrequencyCapDaoMock,
                        times(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.size()))
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK), any());
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterCustomAudiencesWithMatchingNonWinFrequencyCapFilters() {
        doReturn(KeyedFrequencyCapFixture.FILTER_EXCEED_COUNT)
                .when(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(anyInt(), any(), anyInt(), any());

        DBAdData adDataWithImpressionFilter =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS_ONLY_IMPRESSION)
                                        .build())
                        .build();

        DBAdData adDataWithViewFilter =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS_ONLY_VIEW)
                                        .build())
                        .build();

        DBAdData adDataWithClickFilter =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS_ONLY_CLICK)
                                        .build())
                        .build();

        DBCustomAudience caWithNonWinFrequencyCapFilters =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(
                                Arrays.asList(
                                        adDataWithImpressionFilter,
                                        adDataWithViewFilter,
                                        adDataWithClickFilter,
                                        DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS))
                        .build();

        DBCustomAudience caWithEmptyFrequencyCapFilters =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS))
                        .build();

        List<DBCustomAudience> inputList =
                Arrays.asList(
                        caWithNonWinFrequencyCapFilters,
                        DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        assertThat(mFrequencyCapAdFilterer.filterCustomAudiences(inputList))
                .containsExactly(
                        caWithEmptyFrequencyCapFilters,
                        DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        verify(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION), any());
        verify(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_VIEW), any());
        verify(mFrequencyCapDaoMock)
                .getNumEventsForBuyerAfterTime(
                        anyInt(), any(), eq(FrequencyCapFilters.AD_EVENT_TYPE_CLICK), any());
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }

    @Test
    public void testFilterCustomAudiencesWithMatchingWinFrequencyCapFilters() {
        doReturn(KeyedFrequencyCapFixture.FILTER_EXCEED_COUNT)
                .when(mFrequencyCapDaoMock)
                .getNumEventsForCustomAudienceAfterTime(
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_WIN),
                        any());

        DBAdData adDataWithWinFilter =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setFrequencyCapFilters(
                                                FrequencyCapFiltersFixture
                                                        .VALID_FREQUENCY_CAP_FILTERS_ONLY_WIN)
                                        .build())
                        .build();

        DBCustomAudience caWithWinFrequencyCapFilters =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(
                                Arrays.asList(
                                        adDataWithWinFilter,
                                        DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS))
                        .build();

        DBCustomAudience caWithEmptyFrequencyCapFilters =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS))
                        .build();

        List<DBCustomAudience> inputList =
                Arrays.asList(
                        caWithWinFrequencyCapFilters,
                        DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        assertThat(mFrequencyCapAdFilterer.filterCustomAudiences(inputList))
                .containsExactly(
                        caWithEmptyFrequencyCapFilters,
                        DBCustomAudienceFixture.VALID_DB_CUSTOM_AUDIENCE_NO_FILTERS);

        verify(mFrequencyCapDaoMock)
                .getNumEventsForCustomAudienceAfterTime(
                        anyInt(),
                        any(),
                        any(),
                        any(),
                        eq(FrequencyCapFilters.AD_EVENT_TYPE_WIN),
                        any());
        verifyNoMoreInteractions(mFrequencyCapDaoMock);
    }
}
