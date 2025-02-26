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

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Random;

public final class UpdateAdCounterHistogramInputTest extends AdServicesUnitTestCase {
    private static final Random RANDOM = new Random();
    private static final long VALID_AD_SELECTION_ID = 10;
    private static final String VALID_PACKAGE_NAME = "test.package";

    @Test
    public void testBuildValidInput_success() {
        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();

        expect.that(originalInput.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        expect.that(originalInput.getAdSelectionId()).isEqualTo(VALID_AD_SELECTION_ID);
        expect.that(originalInput.getCallerAdTech()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(originalInput.getCallerPackageName()).isEqualTo(VALID_PACKAGE_NAME);
    }

    @Test
    public void testParcelInput_success() {
        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalInput.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        UpdateAdCounterHistogramInput requestFromParcel =
                UpdateAdCounterHistogramInput.CREATOR.createFromParcel(targetParcel);

        expect.that(requestFromParcel.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_VIEW);
        expect.that(requestFromParcel.getAdSelectionId()).isEqualTo(VALID_AD_SELECTION_ID);
        expect.that(requestFromParcel.getCallerAdTech()).isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.that(requestFromParcel.getCallerPackageName()).isEqualTo(VALID_PACKAGE_NAME);
    }

    @Test
    public void testEqualsIdentical() {
        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();
        UpdateAdCounterHistogramInput identicalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(originalInput, identicalInput);
    }

    @Test
    public void testEqualsDifferent() {
        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();
        UpdateAdCounterHistogramInput differentInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID + 99,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_2,
                                VALID_PACKAGE_NAME)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(originalInput, differentInput);
    }

    @Test
    public void testEqualsNull() {
        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();

        assertThat(originalInput).isNotEqualTo(null);
    }

    @Test
    public void testToString() {
        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .build();

        String expected =
                String.format(
                        "UpdateAdCounterHistogramInput{mAdSelectionId=%s, mAdEventType=%s,"
                                + " mCallerAdTech=%s, mCallerPackageName='%s'}",
                        VALID_AD_SELECTION_ID,
                        FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                        CommonFixture.VALID_BUYER_1,
                        VALID_PACKAGE_NAME);

        assertThat(originalInput.toString()).isEqualTo(expected);
    }

    @Test
    public void testAllSettersOverwrite_success() {
        long otherAdSelectionId = VALID_AD_SELECTION_ID + 1;
        String otherPackageName = VALID_PACKAGE_NAME + "2";

        UpdateAdCounterHistogramInput originalInput =
                new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME)
                        .setAdSelectionId(otherAdSelectionId)
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION)
                        .setCallerAdTech(CommonFixture.VALID_BUYER_2)
                        .setCallerPackageName(otherPackageName)
                        .build();

        expect.that(originalInput.getAdEventType())
                .isEqualTo(FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION);
        expect.that(originalInput.getAdSelectionId()).isEqualTo(otherAdSelectionId);
        expect.that(originalInput.getCallerAdTech()).isEqualTo(CommonFixture.VALID_BUYER_2);
        expect.that(originalInput.getCallerPackageName()).isEqualTo(otherPackageName);
    }

    @Test
    public void testBuildZeroAdSelectionId_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                /* adSelectionId= */ 0,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME));
    }

    @Test
    public void testSetZeroAdSelectionId_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setAdSelectionId(0));
    }

    @Test
    public void testBuildWinType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME));
    }

    @Test
    public void testSetWinType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN));
    }

    @Test
    public void testBuildInvalidType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_INVALID,
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_MIN - 1 - RANDOM.nextInt(10),
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_MAX + 1 + RANDOM.nextInt(10),
                                CommonFixture.VALID_BUYER_1,
                                VALID_PACKAGE_NAME));
    }

    @Test
    public void testSetInvalidType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_INVALID));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setAdEventType(
                                        FrequencyCapFilters.AD_EVENT_TYPE_MIN
                                                - 1
                                                - RANDOM.nextInt(10)));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setAdEventType(
                                        FrequencyCapFilters.AD_EVENT_TYPE_MAX
                                                + 1
                                                + RANDOM.nextInt(10)));
    }

    @Test
    public void testBuildUnsetType_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                /* callerAdTech= */ null,
                                VALID_PACKAGE_NAME));
    }

    @Test
    public void testSetNullCallerAdTech_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setCallerAdTech(null));
    }

    @Test
    public void testBuildUnsetCallerAdTech_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                VALID_AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                /* callerPackageName */ null));
    }

    @Test
    public void testSetNullCallerPackageName_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new UpdateAdCounterHistogramInput.Builder(
                                        VALID_AD_SELECTION_ID,
                                        FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                        CommonFixture.VALID_BUYER_1,
                                        VALID_PACKAGE_NAME)
                                .setCallerPackageName(null));
    }
}
