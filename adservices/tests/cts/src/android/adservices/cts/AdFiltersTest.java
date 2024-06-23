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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdFilters;
import android.adservices.common.AdFiltersFixture;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.AppInstallFiltersFixture;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFiltersFixture;
import android.os.Parcel;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

/** Unit tests for {@link AdFilters}. */
public final class AdFiltersTest extends CtsAdServicesDeviceTestCase {
    private static final String DIFFERENT_PACKAGE_NAME =
            CommonFixture.TEST_PACKAGE_NAME_1 + "arbitrary";

    private static final AdFilters APP_INSTALL_ONLY_FILTER =
            new AdFilters.Builder()
                    .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                    .build();

    @Test
    public void testBuildValidAdFilters_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();

        assertThat(originalFilters.getFrequencyCapFilters())
                .isEqualTo(FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS);
    }

    @Test
    public void testParcelAdFilters_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        AdFilters filtersFromParcel = AdFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getFrequencyCapFilters())
                .isEqualTo(originalFilters.getFrequencyCapFilters());
    }

    @Test
    public void testBuildNullAdFilters_success() {
        AdFilters originalFilters = new AdFilters.Builder().setFrequencyCapFilters(null).build();

        assertThat(originalFilters.getFrequencyCapFilters()).isNull();
    }

    @Test
    public void testBuildNoSetters_success() {
        AdFilters originalFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.getFrequencyCapFilters()).isNull();
    }

    @Test
    public void testEqualsIdentical_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();

        assertThat(originalFilters.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        AdFilters differentFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.equals(differentFilters)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        AdFilters nullFilters = null;

        assertThat(originalFilters.equals(nullFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();

        assertThat(originalFilters.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        AdFilters differentFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testBuildNoSettersAppInstallOnly_success() {
        AdFilters originalFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.getAppInstallFilters()).isNull();
    }

    @Test
    public void testBuildNullAdFiltersAppInstallOnly_success() {
        AdFilters originalFilters = new AdFilters.Builder().setAppInstallFilters(null).build();

        assertThat(originalFilters.getAppInstallFilters()).isNull();
    }

    @Test
    public void testBuildValidAdFiltersAppInstallOnly_success() {
        assertThat(APP_INSTALL_ONLY_FILTER.getAppInstallFilters())
                .isEqualTo(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS);
    }

    @Test
    public void testParcelAdFiltersAppInstallOnly_success() {
        Parcel targetParcel = Parcel.obtain();
        APP_INSTALL_ONLY_FILTER.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        AdFilters filtersFromParcel = AdFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getAppInstallFilters())
                .isEqualTo(APP_INSTALL_ONLY_FILTER.getAppInstallFilters());
    }

    @Test
    public void testEqualsIdenticalAppInstallOnly_success() {
        AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferentAppInstallOnly_success() {
        AdFilters differentFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(
                                new AppInstallFilters.Builder()
                                        .setPackageNames(
                                                new HashSet<>(
                                                        Arrays.asList(DIFFERENT_PACKAGE_NAME)))
                                        .build())
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.equals(differentFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdenticalAppInstallOnly_success() {
        AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferentAppInstallOnly_success() {
        AdFilters differentFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(
                                new AppInstallFilters.Builder()
                                        .setPackageNames(
                                                new HashSet<>(
                                                        Arrays.asList(DIFFERENT_PACKAGE_NAME)))
                                        .build())
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testToString() {
        AdFilters originalFilters = AdFiltersFixture.getValidUnhiddenFilters();

        expect.that(originalFilters.toString()).contains("AdFilters{");
        expect.that(originalFilters.toString())
                .contains(
                        "mFrequencyCapFilters="
                                + FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS);
        expect.that(originalFilters.toString())
                .contains(
                        "mAppInstallFilters=" + AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS);
    }

    @Test
    public void testAdFiltersDescribeContents() {
        AdFilters adFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(adFilters.describeContents()).isEqualTo(0);
    }
}
