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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.time.Instant;

public final class SetAdCounterHistogramOverrideInputTest extends AdServicesUnitTestCase {
    private static final ImmutableList<Instant> HISTOGRAM_TIMESTAMPS =
            ImmutableList.of(
                    CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                    CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusMillis(500));
    private static final String NAME = "test_ca_name";

    @Test
    public void testBuildValidInput_success() {
        SetAdCounterHistogramOverrideInput originalInput =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        expect.that(originalInput.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        expect.that(originalInput.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        expect.that(originalInput.getHistogramTimestamps()).isEqualTo(HISTOGRAM_TIMESTAMPS);
        expect.that(originalInput.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(originalInput.getCustomAudienceOwner())
                .isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        expect.that(originalInput.getCustomAudienceName()).isEqualTo(NAME);
    }

    @Test
    public void testParcelValidInput_success() {
        SetAdCounterHistogramOverrideInput originalInput =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalInput.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        SetAdCounterHistogramOverrideInput inputFromParcel =
                SetAdCounterHistogramOverrideInput.CREATOR.createFromParcel(targetParcel);

        expect.that(inputFromParcel.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        expect.that(inputFromParcel.getAdCounterKey()).isEqualTo(KeyedFrequencyCapFixture.KEY1);
        expect.that(inputFromParcel.getHistogramTimestamps()).isEqualTo(HISTOGRAM_TIMESTAMPS);
        expect.that(inputFromParcel.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(inputFromParcel.getCustomAudienceOwner())
                .isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        expect.that(inputFromParcel.getCustomAudienceName()).isEqualTo(NAME);
    }

    @Test
    public void testCreatorNewArray_success() {
        int arraySize = 10;

        assertArrayEquals(
                new SetAdCounterHistogramOverrideInput[arraySize],
                SetAdCounterHistogramOverrideInput.CREATOR.newArray(arraySize));
    }

    @Test
    public void testToString() {
        SetAdCounterHistogramOverrideInput originalInput =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        String expected =
                String.format(
                        "SetAdCounterHistogramOverrideInput{mAdEventType=%d, mAdCounterKey=%d,"
                                + " mHistogramTimestamps=%s, mBuyer=%s, mCustomAudienceOwner='%s',"
                                + " mCustomAudienceName='%s'}",
                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                        KeyedFrequencyCapFixture.KEY1,
                        HISTOGRAM_TIMESTAMPS,
                        CommonFixture.VALID_BUYER_1,
                        CommonFixture.TEST_PACKAGE_NAME,
                        NAME);

        assertThat(originalInput.toString()).isEqualTo(expected);
    }

    @Test
    public void testSetNullHistogramTimestamps_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideInput.Builder()
                                .setHistogramTimestamps(null));
    }

    @Test
    public void testSetNullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new SetAdCounterHistogramOverrideInput.Builder().setBuyer(null));
    }

    @Test
    public void testSetNullCustomAudienceOwner_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideInput.Builder()
                                .setCustomAudienceOwner(null));
    }

    @Test
    public void testSetNullCustomAudienceName_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new SetAdCounterHistogramOverrideInput.Builder().setCustomAudienceName(null));
    }

    @Test
    public void testBuildUnsetAdEventType() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SetAdCounterHistogramOverrideInput.Builder()
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(NAME)
                                .build());
    }

    @Test
    public void testBuildUnsetAdCounterKey_success() {
        SetAdCounterHistogramOverrideInput originalInput =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                        .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        expect.that(originalInput.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        expect.that(originalInput.getAdCounterKey()).isEqualTo(0);
        expect.that(originalInput.getHistogramTimestamps()).isEqualTo(HISTOGRAM_TIMESTAMPS);
        expect.that(originalInput.getBuyer()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(originalInput.getCustomAudienceOwner())
                .isEqualTo(CommonFixture.TEST_PACKAGE_NAME);
        expect.that(originalInput.getCustomAudienceName()).isEqualTo(NAME);
    }

    @Test
    public void testBuildUnsetHistogramTimestamps_success() {
        SetAdCounterHistogramOverrideInput originalInput =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName(NAME)
                        .build();

        assertThat(originalInput.getHistogramTimestamps()).isEmpty();
    }

    @Test
    public void testBuildUnsetBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideInput.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .setCustomAudienceName(NAME)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceOwner_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideInput.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceName(NAME)
                                .build());
    }

    @Test
    public void testBuildUnsetCustomAudienceName_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SetAdCounterHistogramOverrideInput.Builder()
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                                .setHistogramTimestamps(HISTOGRAM_TIMESTAMPS)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                                .build());
    }
}
