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

package android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link FrequencyCapFilters}. */
// TODO(b/221876775): Move to CTS tests once public APIs are unhidden
@SmallTest
public class FrequencyCapFiltersTest {
    @Test
    public void testBuildValidFrequencyCapFilters_success() {
        final FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .build();

        assertThat(originalFilters.getKeyedFrequencyCapsForWinEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(originalFilters.getKeyedFrequencyCapsForImpressionEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(originalFilters.getKeyedFrequencyCapsForViewEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(originalFilters.getKeyedFrequencyCapsForClickEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
    }

    @Test
    public void testParcelFrequencyCapFilters_success() {
        final FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final FrequencyCapFilters filtersFromParcel =
                FrequencyCapFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getKeyedFrequencyCapsForWinEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(filtersFromParcel.getKeyedFrequencyCapsForImpressionEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(filtersFromParcel.getKeyedFrequencyCapsForViewEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(filtersFromParcel.getKeyedFrequencyCapsForClickEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
    }

    @Test
    public void testEqualsIdentical_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters identicalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();

        assertThat(originalFilters.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters differentFilters = new FrequencyCapFilters.Builder().build();

        assertThat(originalFilters.equals(differentFilters)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters nullFilters = null;

        assertThat(originalFilters.equals(nullFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters identicalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();

        assertThat(originalFilters.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters differentFilters = new FrequencyCapFilters.Builder().build();

        assertThat(originalFilters.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testToString() {
        final FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET)
                        .build();

        final String expectedString =
                String.format(
                        "FrequencyCapFilters{mKeyedFrequencyCapsForWinEvents=%s,"
                                + " mKeyedFrequencyCapsForImpressionEvents=%s,"
                                + " mKeyedFrequencyCapsForViewEvents=%s,"
                                + " mKeyedFrequencyCapsForClickEvents=%s}",
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_SET);
        assertThat(originalFilters.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testBuildNullWinCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForWinEvents(null));
    }

    @Test
    public void testBuildNullImpressionCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForImpressionEvents(null));
    }

    @Test
    public void testBuildNullViewCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForViewEvents(null));
    }

    @Test
    public void testBuildNullClickCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForClickEvents(null));
    }

    @Test
    public void testBuildNoSetters_success() {
        final FrequencyCapFilters originalFilters = new FrequencyCapFilters.Builder().build();

        assertThat(originalFilters.getKeyedFrequencyCapsForWinEvents()).isEmpty();
        assertThat(originalFilters.getKeyedFrequencyCapsForImpressionEvents()).isEmpty();
        assertThat(originalFilters.getKeyedFrequencyCapsForViewEvents()).isEmpty();
        assertThat(originalFilters.getKeyedFrequencyCapsForClickEvents()).isEmpty();
    }
}
