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
public class AdFiltererImplTest {
    private static final String PACKAGE_NAME_TO_FILTER =
            CommonFixture.TEST_PACKAGE_NAME_1 + ".filter";
    private static final AppInstallFilters APP_INSTALL_FILTERS_TO_FILTER =
            new AppInstallFilters.Builder()
                    .setPackageNames(new HashSet<>(Arrays.asList(PACKAGE_NAME_TO_FILTER)))
                    .build();
    private static final DBAdData AD_TO_FILTER =
            DBAdDataFixture.getValidDbAdDataBuilder()
                    .setAdFilters(
                            new AdFilters.Builder()
                                    .setAppInstallFilters(APP_INSTALL_FILTERS_TO_FILTER)
                                    .build())
                    .build();

    @Mock private AppInstallDao mAppInstallDaoMock;
    private AdFilterer mAdFilterer;

    @Before
    public void setup() {
        mAdFilterer = new AdFiltererImpl(mAppInstallDaoMock);
    }

    @Test
    public void testFilterNullAdFilters() {
        DBAdData dbAdData = DBAdDataFixture.getValidDbAdDataBuilder().setAdFilters(null).build();
        List<DBAdData> inputList = Arrays.asList(dbAdData);
        assertEquals(
                inputList, mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
        verifyNoMoreInteractions(mAppInstallDaoMock);
    }

    @Test
    public void testFilterNullComponentFilters() {
        DBAdData dbAdData =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder()
                                        .setAppInstallFilters(null)
                                        .setFrequencyCapFilters(null)
                                        .build())
                        .build();
        List<DBAdData> inputList = Arrays.asList(dbAdData);
        assertEquals(
                inputList, mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
        verifyNoMoreInteractions(mAppInstallDaoMock);
    }

    @Test
    public void testAppNotInstalled() {
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        DBAdData dbAdData =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        List<DBAdData> inputList = Arrays.asList(dbAdData);
        assertEquals(
                inputList, mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testAppInstalled() {
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(true);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        DBAdData dbAdData =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        List<DBAdData> inputList = Arrays.asList(dbAdData);
        assertEquals(
                Collections.EMPTY_LIST,
                mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, CommonFixture.TEST_PACKAGE_NAME_1);
    }

    @Test
    public void testMixedApps() {
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
        DBAdData dbAdData =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        List<DBAdData> inputList = Arrays.asList(dbAdData);
        assertEquals(
                Collections.EMPTY_LIST,
                mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        eq(CommonFixture.VALID_BUYER_1), eq(CommonFixture.TEST_PACKAGE_NAME_2));
    }

    @Test
    public void testMixedAds() {
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
        DBAdData dbAdData1 =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters1).build())
                        .build();
        DBAdData dbAdData2 =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters2).build())
                        .build();
        assertEquals(
                Arrays.asList(dbAdData2),
                mAdFilterer.filterContextualAds(
                        Arrays.asList(dbAdData1, dbAdData2), CommonFixture.VALID_BUYER_1));
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        eq(CommonFixture.VALID_BUYER_1), eq(CommonFixture.TEST_PACKAGE_NAME_1));
        verify(mAppInstallDaoMock)
                .canBuyerFilterPackage(
                        eq(CommonFixture.VALID_BUYER_1), eq(CommonFixture.TEST_PACKAGE_NAME_2));
    }

    @Test
    public void testCasNothingFiltered() {
        List<DBCustomAudience> caList =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        assertEquals(caList, mAdFilterer.filterCustomAudiences(caList));
        validateAppInstallDBCalls(caList);
    }

    @Test
    public void testCasOneAdFiltered() {
        List<DBCustomAudience> caListWithoutFilterAd =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        List<DBCustomAudience> caListWithFilterAd =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        caListWithFilterAd.get(0).getAds().add(AD_TO_FILTER);
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        when(mAppInstallDaoMock.canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, PACKAGE_NAME_TO_FILTER))
                .thenReturn(true);

        assertEquals(caListWithoutFilterAd, mAdFilterer.filterCustomAudiences(caListWithFilterAd));
        validateAppInstallDBCalls(caListWithFilterAd);
    }

    @Test
    public void testWholeCaFiltered() {
        List<DBCustomAudience> caListWithoutFilteredCa =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_2));
        List<DBCustomAudience> caListWithFilteredCa =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_2));
        DBCustomAudience caToFilter =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_TO_FILTER))
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
    public void testAllCasFiltered() {
        DBCustomAudience caToFilter =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setAds(Arrays.asList(AD_TO_FILTER))
                        .build();
        when(mAppInstallDaoMock.canBuyerFilterPackage(any(), any())).thenReturn(false);
        when(mAppInstallDaoMock.canBuyerFilterPackage(
                        CommonFixture.VALID_BUYER_1, PACKAGE_NAME_TO_FILTER))
                .thenReturn(true);

        assertEquals(
                Collections.EMPTY_LIST,
                mAdFilterer.filterCustomAudiences(Arrays.asList(caToFilter)));
        validateAppInstallDBCalls(Arrays.asList(caToFilter));
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
