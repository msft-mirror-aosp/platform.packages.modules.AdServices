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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.FrequencyCapFiltersFixture;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

/** Unit tests for {@link FrequencyCapFilters}. */
public final class FrequencyCapFiltersTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testBuildValidFrequencyCapFilters_success() {
        FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();

        expect.that(originalFilters.getKeyedFrequencyCapsForWinEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(originalFilters.getKeyedFrequencyCapsForImpressionEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(originalFilters.getKeyedFrequencyCapsForViewEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(originalFilters.getKeyedFrequencyCapsForClickEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
    }

    @Test
    public void testParcelFrequencyCapFilters_success() {
        FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        FrequencyCapFilters filtersFromParcel =
                FrequencyCapFilters.CREATOR.createFromParcel(targetParcel);

        expect.that(filtersFromParcel.getKeyedFrequencyCapsForWinEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(filtersFromParcel.getKeyedFrequencyCapsForImpressionEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(filtersFromParcel.getKeyedFrequencyCapsForViewEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(filtersFromParcel.getKeyedFrequencyCapsForClickEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
    }

    @Test
    public void testEqualsIdentical_success() {
        FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        FrequencyCapFilters identicalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        FrequencyCapFilters differentFilters = new FrequencyCapFilters.Builder().build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(originalFilters, identicalFilters);
        et.expectObjectsAreNotEqual(originalFilters, differentFilters);
        et.expectObjectsAreNotEqual(originalFilters, null);
    }

    @Test
    public void testToString() {
        FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();

        String expectedString =
                String.format(
                        "FrequencyCapFilters{mKeyedFrequencyCapsForWinEvents=%s,"
                                + " mKeyedFrequencyCapsForImpressionEvents=%s,"
                                + " mKeyedFrequencyCapsForViewEvents=%s,"
                                + " mKeyedFrequencyCapsForClickEvents=%s}",
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        expect.that(originalFilters.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testBuildNullWinCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForWinEvents(null));
    }

    @Test
    public void testBuildWinCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForWinEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
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
    public void testBuildImpressionCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForImpressionEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNullViewCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForViewEvents(null));
    }

    @Test
    public void testBuildViewCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForViewEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNullClickCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForClickEvents(null));
    }

    @Test
    public void testBuildClickCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForClickEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNoSetters_success() {
        FrequencyCapFilters originalFilters = new FrequencyCapFilters.Builder().build();

        expect.that(originalFilters.getKeyedFrequencyCapsForWinEvents()).isEmpty();
        expect.that(originalFilters.getKeyedFrequencyCapsForImpressionEvents()).isEmpty();
        expect.that(originalFilters.getKeyedFrequencyCapsForViewEvents()).isEmpty();
        expect.that(originalFilters.getKeyedFrequencyCapsForClickEvents()).isEmpty();
    }

    @Test
    public void testBuildExcessiveNumberOfWinFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForWinEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfImpressionFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForImpressionEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfViewFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForViewEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfClickFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForClickEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfTotalFilters_throws() {
        int distributedNumFilters = FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS / 4;
        ImmutableList.Builder<KeyedFrequencyCap> listBuilder = ImmutableList.builder();
        for (int key = 0; key < distributedNumFilters; key++) {
            listBuilder.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }

        // Add a spread number of filters across the first three types
        FrequencyCapFilters.Builder filtersBuilder =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(listBuilder.build())
                        .setKeyedFrequencyCapsForImpressionEvents(listBuilder.build())
                        .setKeyedFrequencyCapsForViewEvents(listBuilder.build());

        // Add extra filters to the list so that the total is exceeded
        int numExtraFiltersToExceed =
                FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS - (4 * distributedNumFilters) + 1;
        for (int key = 0; key < numExtraFiltersToExceed; key++) {
            listBuilder.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }
        filtersBuilder.setKeyedFrequencyCapsForClickEvents(listBuilder.build());

        assertThrows(IllegalArgumentException.class, filtersBuilder::build);
    }

    @Test
    public void testFrequencyCapFiltersDescribeContents() {
        FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();
        assertWithMessage("describeContents").that(originalFilters.describeContents()).isEqualTo(0);
    }
}
