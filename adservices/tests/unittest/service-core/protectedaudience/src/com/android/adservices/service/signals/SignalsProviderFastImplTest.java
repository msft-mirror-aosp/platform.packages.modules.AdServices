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

package com.android.adservices.service.signals;

import static com.android.adservices.service.signals.ProtectedSignalsFixture.getHexString;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SignalsProviderFastImplTest {

    @Rule public MockitoRule mRule = MockitoJUnit.rule();

    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;

    private static final Instant NOW = CommonFixture.FIXED_NOW;
    @Mock private ProtectedSignalsDao mProtectedSignalsDao;

    private SignalsProviderFastImpl mSignalStorage;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mSignalStorage = new SignalsProviderFastImpl(mProtectedSignalsDao);
    }

    @Test
    public void test_getSignals_noSignalsForBuyer_returnsEmptyMap() {
        Mockito.when(mProtectedSignalsDao.getSignalsByBuyer(BUYER))
                .thenReturn(Collections.emptyList());

        Assert.assertEquals(
                "There should have been no signals", 0, mSignalStorage.getSignals(BUYER).size());
    }

    @Test
    public void test_getSignals_oneSignal_returnsSignal() {
        String seed = "seed";

        List<DBProtectedSignal> dbProtectedSignalList =
                new ArrayList<>(List.of(generateDBProtectedSignal(seed)));

        Mockito.when(mProtectedSignalsDao.getSignalsByBuyer(BUYER))
                .thenReturn(dbProtectedSignalList);

        Assert.assertEquals(
                "There should have been a single entry of signal",
                1,
                mSignalStorage.getSignals(BUYER).size());
        List<ProtectedSignal> protectedSignals =
                mSignalStorage.getSignals(BUYER).get(generateKey(seed));

        Assert.assertEquals(NOW, protectedSignals.get(0).getCreationTime());
        Assert.assertEquals(generateValue(seed), protectedSignals.get(0).getHexEncodedValue());
        Assert.assertEquals(generatePackageName(seed), protectedSignals.get(0).getPackageName());
    }

    @Test
    public void test_getSignals_multipleSignals_returnsSignals() {
        String seed1 = "seed1";
        String seed2 = "seed2";

        List<DBProtectedSignal> dbProtectedSignalList =
                new ArrayList<>(
                        List.of(
                                generateDBProtectedSignal(seed1),
                                generateDBProtectedSignal(seed1),
                                generateDBProtectedSignal(seed2)));

        Mockito.when(mProtectedSignalsDao.getSignalsByBuyer(BUYER))
                .thenReturn(dbProtectedSignalList);

        Assert.assertEquals(
                "There should have been multiple entries of signals",
                2,
                mSignalStorage.getSignals(BUYER).size());
        List<ProtectedSignal> protectedSignalsSeed1 =
                mSignalStorage.getSignals(BUYER).get(generateKey(seed1));

        Assert.assertEquals(
                "This key should have had two signals", 2, protectedSignalsSeed1.size());
        Assert.assertEquals(
                "This key should have had one signal",
                1,
                mSignalStorage.getSignals(BUYER).get(generateKey(seed2)).size());
    }

    private DBProtectedSignal generateDBProtectedSignal(String seed) {
        byte[] key = ("TestKey" + seed).getBytes();
        byte[] value = ("TestValue" + seed).getBytes();
        String packageName = generatePackageName(seed);

        Random rand = new Random();
        DBProtectedSignal dbProtectedSignal =
                DBProtectedSignal.builder()
                        .setBuyer(BUYER)
                        .setCreationTime(NOW)
                        .setId(rand.nextLong())
                        .setKey(key)
                        .setValue(value)
                        .setPackageName(packageName)
                        .build();

        return dbProtectedSignal;
    }

    private String generateKey(String seed) {
        String key = "TestKey" + seed;
        return getHexString(key.getBytes());
    }

    private String generateValue(String seed) {
        String value = "TestValue" + seed;
        return getHexString(value.getBytes());
    }

    private String generatePackageName(String seed) {
        return "com.fake.package" + seed;
    }
}
