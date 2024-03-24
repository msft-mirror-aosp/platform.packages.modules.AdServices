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

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AppInstallFilters;
import android.adservices.common.AppInstallFiltersFixture;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.annotations.SetFlagEnabled;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link AppInstallFilters}. */
@SmallTest
@SetFlagEnabled(KEY_FLEDGE_AD_SELECTION_FILTERING_ENABLED)
public class AppInstallFiltersTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidAppInstallFilters_success() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        assertThat(originalFilters.getPackageNames())
                .containsExactlyElementsIn(CommonFixture.PACKAGE_SET);
    }

    @Test
    public void testParcelAppInstallFilters_success() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final AppInstallFilters filtersFromParcel =
                AppInstallFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getPackageNames())
                .containsExactlyElementsIn(CommonFixture.PACKAGE_SET);
    }

    @Test
    public void testEqualsIdentical_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters identicalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();

        assertThat(originalFilters.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters differentFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.equals(differentFilters)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters nullFilters = null;

        assertThat(originalFilters.equals(nullFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters identicalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();

        assertThat(originalFilters.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters differentFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testToString() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        final String expectedString =
                String.format("AppInstallFilters{mPackageNames=%s}", CommonFixture.PACKAGE_SET);
        assertThat(originalFilters.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testBuildNullPackageNames_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new AppInstallFilters.Builder().setPackageNames(null));
    }

    @Test
    public void testBuildNoSetters_success() {
        final AppInstallFilters originalFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.getPackageNames()).isEmpty();
    }

    @Test
    public void testCreatorNewArray_success() {
        AppInstallFilters[] filtersArray = AppInstallFilters.CREATOR.newArray(2);

        assertThat(filtersArray.length).isEqualTo(2);
        assertThat(filtersArray[0]).isNull();
        assertThat(filtersArray[1]).isNull();
    }

    @Test
    public void testAppInstallFiltersDescribeContents_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();

        assertThat(originalFilters.describeContents()).isEqualTo(0);
    }
}
