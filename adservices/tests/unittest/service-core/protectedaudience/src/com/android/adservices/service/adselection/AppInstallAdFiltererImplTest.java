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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;
import android.util.Pair;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class AppInstallAdFiltererImplTest {
    private static final String PACKAGE_NAME_TO_FILTER =
            CommonFixture.TEST_PACKAGE_NAME_1 + ".filter";
    private static final AppInstallFilters APP_INSTALL_FILTERS_TO_FILTER =
            new AppInstallFilters.Builder()
                    .setPackageNames(new HashSet<>(Arrays.asList(PACKAGE_NAME_TO_FILTER)))
                    .build();
    private static final DBAdData AD_TO_FILTER_ON_APP_INSTALL =
            DBAdDataFixture.getValidDbAdDataBuilder()
                    .setAdFilters(
                            new AdFilters.Builder()
                                    .setAppInstallFilters(APP_INSTALL_FILTERS_TO_FILTER)
                                    .build())
                    .build();

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

    @Mock private AppInstallDao mAppInstallDaoMock;
    private AppInstallAdFilterer mAdFilterer;

    @Before
    public void setup() {
        mAdFilterer =
                new AppInstallAdFiltererImpl(
                        mAppInstallDaoMock, CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);
    }

    @Test
    public void testFilterContextualAdsDoesNotFilterNullAdFilters() {
        final AdData adData = AD_DATA_BUILDER.setAdFilters(null).build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
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

        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));
        verifyNoMoreInteractions(mAppInstallDaoMock);
    }

    @Test
    public void testFilterContextualAdsDoesNotFilterForAppNotInstalled() {
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(contextualAds, mAdFilterer.filterContextualAds(contextualAds));

        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testFilterContextualAdsFiltersForAppInstalled() {
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(true);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(
                Collections.EMPTY_LIST,
                mAdFilterer.filterContextualAds(contextualAds).getAdsWithBid());
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testFilterContextualAdsFiltersForMixedApps() {
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), eq(CommonFixture.TEST_PACKAGE_NAME_1)))
                .thenReturn(true);
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), eq(CommonFixture.TEST_PACKAGE_NAME_2)))
                .thenReturn(false);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(
                                new HashSet<>(
                                        Arrays.asList(
                                                CommonFixture.TEST_PACKAGE_NAME_1,
                                                CommonFixture.TEST_PACKAGE_NAME_2)))
                        .build();
        final AdData adData =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(ImmutableList.of(new AdWithBid(adData, 1.0)))
                        .build();

        assertEquals(
                Collections.EMPTY_LIST,
                mAdFilterer.filterContextualAds(contextualAds).getAdsWithBid());
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        eq(CommonFixture.VALID_BUYER_1), eq(CommonFixture.TEST_PACKAGE_NAME_2));
    }

    @Test
    public void testFilterContextualAdsFiltersForMixedAds() {
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), eq(CommonFixture.TEST_PACKAGE_NAME_1)))
                .thenReturn(true);
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), eq(CommonFixture.TEST_PACKAGE_NAME_2)))
                .thenReturn(false);
        AppInstallFilters appFilters1 =
                new AppInstallFilters.Builder()
                        .setPackageNames(
                                new HashSet<>(Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_1)))
                        .build();
        AppInstallFilters appFilters2 =
                new AppInstallFilters.Builder()
                        .setPackageNames(
                                new HashSet<>(Arrays.asList(CommonFixture.TEST_PACKAGE_NAME_2)))
                        .build();
        final AdData adData1 =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters1).build())
                        .build();
        final AdData adData2 =
                AD_DATA_BUILDER
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters2).build())
                        .build();
        final SignedContextualAds contextualAds =
                CONTEXTUAL_ADS_BUILDER
                        .setAdsWithBid(
                                ImmutableList.of(
                                        new AdWithBid(adData1, 1.0), new AdWithBid(adData2, 2.0)))
                        .build();

        assertEquals(
                Arrays.asList(new AdWithBid(adData2, 2.0)),
                mAdFilterer.filterContextualAds(contextualAds).getAdsWithBid());
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        eq(CommonFixture.VALID_BUYER_1), eq(CommonFixture.TEST_PACKAGE_NAME_1));
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        eq(CommonFixture.VALID_BUYER_1), eq(CommonFixture.TEST_PACKAGE_NAME_2));
    }

    @Test
    public void testFilterCustomAudiencesNothingFiltered() {
        List<DBCustomAudience> caList =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        assertEquals(caList, mAdFilterer.filterCustomAudiences(caList));
        validateAppInstallDBCalls(caList);
    }

    @Test
    public void testFilterCustomAudiencesOneAdFiltered() {
        List<DBCustomAudience> caListWithoutFilterAd =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        List<DBCustomAudience> caListWithFilterAd =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        caListWithFilterAd.get(0).getAds().add(AD_TO_FILTER_ON_APP_INSTALL);
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        when(mAppInstallDaoMock.canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, PACKAGE_NAME_TO_FILTER))
                .thenReturn(true);

        assertEquals(caListWithoutFilterAd, mAdFilterer.filterCustomAudiences(caListWithFilterAd));
        validateAppInstallDBCalls(caListWithFilterAd);
    }

    @Test
    public void testFilterCustomAudiencesWholeCaFiltered() {
        List<DBCustomAudience> caListWithoutFilteredCa =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_2));
        List<DBCustomAudience> caListWithFilteredCa =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_2));
        DBCustomAudience caToFilter =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_TO_FILTER_ON_APP_INSTALL))
                        .build();
        caListWithFilteredCa.add(caToFilter);
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        when(mAppInstallDaoMock.canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, PACKAGE_NAME_TO_FILTER))
                .thenReturn(true);

        assertEquals(
                caListWithoutFilteredCa, mAdFilterer.filterCustomAudiences(caListWithFilteredCa));
        validateAppInstallDBCalls(caListWithFilteredCa);
    }

    @Test
    public void testFilterCustomAudiencesAllCasFiltered() {
        DBCustomAudience caToFilterOnAppInstall =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_TO_FILTER_ON_APP_INSTALL))
                        .build();
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        when(mAppInstallDaoMock.canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, PACKAGE_NAME_TO_FILTER))
                .thenReturn(true);

        assertEquals(
                Collections.EMPTY_LIST,
                mAdFilterer.filterCustomAudiences(Arrays.asList(caToFilterOnAppInstall)));
        validateAppInstallDBCalls(Arrays.asList(caToFilterOnAppInstall));
    }

    private void validateAppInstallDBCalls(List<DBCustomAudience> caList) {
        /* We want to validate that all the calls that should have been made were made, but we
         * can't just use a captor and compare lists since we can't guarantee the order.
         */
        Map<Pair<AdTechIdentifier, String>, Integer> calls = new HashMap<>();
        for (DBCustomAudience ca : caList) {
            for (DBAdData ad : ca.getAds()) {
                AdFilters filters = ad.getAdFilters();
                if (filters == null || filters.getAppInstallFilters() == null) {
                    continue;
                }
                if (filters.getAppInstallFilters()
                        .getPackageNames()
                        .contains(PACKAGE_NAME_TO_FILTER)) {
                    calls.merge(new Pair<>(ca.getBuyer(), PACKAGE_NAME_TO_FILTER), 1, Integer::sum);
                    continue;
                }
                for (String packageName : filters.getAppInstallFilters().getPackageNames()) {
                    calls.merge(new Pair<>(ca.getBuyer(), packageName), 1, Integer::sum);
                }
            }
        }
        for (Pair<AdTechIdentifier, String> call : calls.keySet()) {
            verify(mAppInstallDaoMock, times(calls.get(call)))
                    .canBuyerFilterPackage(call.first, call.second);
        }
    }
}
