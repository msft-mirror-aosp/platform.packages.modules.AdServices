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

import android.adservices.common.AdFilters;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class AdFiltererNoOpImplTest {
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
    private AdFilterer mAdFilterer;

    @Before
    public void setup() {
        mAdFilterer = new AdFiltererNoOpImpl();
    }

    @Test
    public void testFilterNullAdFilters() {
        DBAdData dbAdData = DBAdDataFixture.getValidDbAdDataBuilder().setAdFilters(null).build();
        List<DBAdData> inputList = Arrays.asList(dbAdData);
        assertEquals(
                inputList, mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
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
    }

    @Test
    public void testAppInstallFilter() {
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
    }

    @Test
    public void testMultipleApps() {
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
                inputList, mAdFilterer.filterContextualAds(inputList, CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testMultipleAds() {
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
        List<DBAdData> inputList = Arrays.asList(dbAdData1, dbAdData2);
        assertEquals(
                inputList,
                mAdFilterer.filterContextualAds(
                        Arrays.asList(dbAdData1, dbAdData2), CommonFixture.VALID_BUYER_1));
    }

    @Test
    public void testCas() {
        List<DBCustomAudience> caList =
                DBCustomAudienceFixture.getListOfBuyersCustomAudiences(
                        Arrays.asList(CommonFixture.VALID_BUYER_1, CommonFixture.VALID_BUYER_2));
        assertEquals(caList, mAdFilterer.filterCustomAudiences(caList));
    }
}
