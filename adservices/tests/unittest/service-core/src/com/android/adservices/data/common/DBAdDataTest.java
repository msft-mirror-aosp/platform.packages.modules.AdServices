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

package com.android.adservices.data.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class DBAdDataTest extends AdServicesMockitoTestCase {
    private static final AdData SAMPLE_AD_DATA =
            AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                    .setAdRenderId("ad-render-id")
                    .build();
    private static final AdDataConversionStrategy CONVERSION_STRATEGY_ALL_FEATURES_ENABLED =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true, true);

    @Test
    public void testConstructor() {
        DBAdData dbAdData =
                new DBAdData(
                        SAMPLE_AD_DATA.getRenderUri(),
                        SAMPLE_AD_DATA.getMetadata(),
                        SAMPLE_AD_DATA.getAdCounterKeys(),
                        SAMPLE_AD_DATA.getAdFilters(),
                        SAMPLE_AD_DATA.getAdRenderId());
        assertEqualsServiceObject(SAMPLE_AD_DATA, dbAdData);
    }

    @Test
    public void testConstructorNullsSucceed() {
        DBAdData dbAdData =
                new DBAdData(
                        SAMPLE_AD_DATA.getRenderUri(),
                        SAMPLE_AD_DATA.getMetadata(),
                        new HashSet<>(),
                        null,
                        null);
        expect.that(dbAdData.getRenderUri()).isEqualTo(SAMPLE_AD_DATA.getRenderUri());
        expect.that(dbAdData.getMetadata()).isEqualTo(SAMPLE_AD_DATA.getMetadata());
        expect.that(dbAdData.getAdCounterKeys()).isEqualTo(Collections.EMPTY_SET);
        expect.that(dbAdData.getAdFilters()).isNull();
        expect.that(dbAdData.getAdRenderId()).isNull();
    }

    @Test
    public void testRenderUriNullFails() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DBAdData(
                                null, SAMPLE_AD_DATA.getMetadata(), new HashSet<>(), null, null));
    }

    @Test
    public void testMetadataNullFails() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DBAdData(
                                SAMPLE_AD_DATA.getRenderUri(), null, new HashSet<>(), null, null));
    }

    @Test
    public void testAdCounterKeysNullFails() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DBAdData(
                                SAMPLE_AD_DATA.getRenderUri(),
                                SAMPLE_AD_DATA.getMetadata(),
                                null,
                                null,
                                null));
    }

    @Test
    public void testFromServiceObject() {
        DBAdData dbAdData =
                CONVERSION_STRATEGY_ALL_FEATURES_ENABLED.fromServiceObject(SAMPLE_AD_DATA).build();
        assertEqualsServiceObject(SAMPLE_AD_DATA, dbAdData);
    }

    @Test
    public void testFromServiceObjectBothFilteringDisabled() {
        AdData original = AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 1);
        DBAdData dbAdData =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(false, false, false)
                        .fromServiceObject(original)
                        .build();
        AdData noFilters =
                new AdData.Builder()
                        .setRenderUri(original.getRenderUri())
                        .setMetadata(original.getMetadata())
                        .build();
        assertEqualsServiceObject(noFilters, dbAdData);
    }

    @Test
    public void testFromServiceObjectAppInstallFilteringDisabledFrequencyCapEnabled() {
        AdData original = AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 1);
        DBAdData dbAdData =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, false, false)
                        .fromServiceObject(original)
                        .build();
        assertThat(dbAdData.getAdFilters()).isNotNull();
        expect.that(dbAdData.getAdFilters().getFrequencyCapFilters())
                .isEqualTo(original.getAdFilters().getFrequencyCapFilters());
        expect.that(dbAdData.getAdFilters().getAppInstallFilters()).isNull();
    }

    @Test
    public void testFromServiceObjectFrequencyCapFilteringDisabledAppInstallEnabled() {
        AdData original = AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 1);
        DBAdData dbAdData =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(false, true, false)
                        .fromServiceObject(original)
                        .build();
        assertThat(dbAdData.getAdFilters()).isNotNull();
        expect.that(dbAdData.getAdFilters().getFrequencyCapFilters()).isNull();
        expect.that(dbAdData.getAdFilters().getAppInstallFilters())
                .isEqualTo(original.getAdFilters().getAppInstallFilters());
    }

    @Test
    public void testFromServiceObjectBothFiltersEnabled() {
        AdData original = AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 1);
        DBAdData dbAdData =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true, false)
                        .fromServiceObject(original)
                        .build();
        assertThat(dbAdData.getAdFilters()).isNotNull();
        expect.that(dbAdData.getAdFilters().getFrequencyCapFilters())
                .isEqualTo(original.getAdFilters().getFrequencyCapFilters());
        expect.that(dbAdData.getAdFilters().getAppInstallFilters())
                .isEqualTo(original.getAdFilters().getAppInstallFilters());
    }

    @Test
    public void testFromServiceObjectAdRenderIdDisabled() {
        AdData original =
                AdDataFixture.getValidFilterAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 1)
                        .setAdRenderId("render_id")
                        .build();
        DBAdData dbAdData =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(false, false, false)
                        .fromServiceObject(original)
                        .build();
        assertThat(dbAdData.getAdRenderId()).isNull();
    }

    @Test
    public void testSize() {
        int[] size = new int[1];
        size[0] += SAMPLE_AD_DATA.getRenderUri().toString().getBytes(StandardCharsets.UTF_8).length;
        size[0] += SAMPLE_AD_DATA.getMetadata().getBytes(StandardCharsets.UTF_8).length;
        SAMPLE_AD_DATA.getAdCounterKeys().forEach(x -> size[0] += 4);
        size[0] += SAMPLE_AD_DATA.getAdFilters().getSizeInBytes();
        size[0] += SAMPLE_AD_DATA.getAdRenderId().getBytes(StandardCharsets.UTF_8).length;
        assertThat(
                        CONVERSION_STRATEGY_ALL_FEATURES_ENABLED
                                .fromServiceObject(SAMPLE_AD_DATA)
                                .build()
                                .size())
                .isEqualTo(size[0]);
    }

    @Test
    public void testSizeNulls() {
        int[] size = new int[1];
        size[0] += SAMPLE_AD_DATA.getRenderUri().toString().getBytes(StandardCharsets.UTF_8).length;
        size[0] += SAMPLE_AD_DATA.getMetadata().getBytes(StandardCharsets.UTF_8).length;
        DBAdData dbAdData =
                new DBAdData(
                        SAMPLE_AD_DATA.getRenderUri(),
                        SAMPLE_AD_DATA.getMetadata(),
                        Set.of(),
                        null,
                        null);
        assertThat(dbAdData.size()).isEqualTo(size[0]);
    }

    @Test
    public void testEquals() {
        DBAdData dbAdData1 =
                CONVERSION_STRATEGY_ALL_FEATURES_ENABLED.fromServiceObject(SAMPLE_AD_DATA).build();
        DBAdData dbAdData2 =
                CONVERSION_STRATEGY_ALL_FEATURES_ENABLED.fromServiceObject(SAMPLE_AD_DATA).build();

        DBAdData dbAdDataDifferent =
                new DBAdData(
                        SAMPLE_AD_DATA.getRenderUri(),
                        SAMPLE_AD_DATA.getMetadata(),
                        Set.of(),
                        null,
                        null);

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(dbAdData1, dbAdData2);
        et.expectObjectsAreNotEqual(dbAdData1, dbAdDataDifferent);
    }

    @Test
    public void testToString() {
        DBAdData dbAdData = new DBAdData(Uri.parse("https://a.com"), "{}", Set.of(), null, null);
        assertThat(dbAdData.toString())
                .isEqualTo(
                        "DBAdData{mRenderUri=https://a.com, mMetadata='{}', mAdCounterKeys=[], "
                                + "mAdFilters=null, mAdRenderId='null'}");
    }

    @Test
    public void testBuilder() {
        DBAdData dbAdData =
                new DBAdData.Builder()
                        .setRenderUri(SAMPLE_AD_DATA.getRenderUri())
                        .setMetadata(SAMPLE_AD_DATA.getMetadata())
                        .setAdCounterKeys(SAMPLE_AD_DATA.getAdCounterKeys())
                        .setAdFilters(SAMPLE_AD_DATA.getAdFilters())
                        .build();
        assertEqualsServiceObject(SAMPLE_AD_DATA, dbAdData);
    }

    private void assertEqualsServiceObject(AdData expected, DBAdData test) {
        expect.that(test.getRenderUri()).isEqualTo(expected.getRenderUri());
        expect.that(test.getMetadata()).isEqualTo(expected.getMetadata());
        expect.that(test.getAdCounterKeys()).isEqualTo(expected.getAdCounterKeys());
        expect.that(test.getAdFilters()).isEqualTo(expected.getAdFilters());
    }
}
