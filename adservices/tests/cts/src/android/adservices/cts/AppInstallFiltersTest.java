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

import static org.junit.Assert.assertThrows;

import android.adservices.common.AppInstallFilters;
import android.adservices.common.AppInstallFiltersFixture;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

/** Unit tests for {@link AppInstallFilters}. */
public final class AppInstallFiltersTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testBuildValidAppInstallFilters_success() {
        AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        assertThat(originalFilters.getPackageNames())
                .containsExactlyElementsIn(CommonFixture.PACKAGE_SET);
    }

    @Test
    public void testParcelAppInstallFilters_success() {
        AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        AppInstallFilters filtersFromParcel =
                AppInstallFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getPackageNames())
                .containsExactlyElementsIn(CommonFixture.PACKAGE_SET);
    }

    @Test
    public void testEqualsIdentical_success() {
        AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        AppInstallFilters identicalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        AppInstallFilters differentFilters = new AppInstallFilters.Builder().build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(originalFilters, identicalFilters);
        et.expectObjectsAreNotEqual(originalFilters, differentFilters);
        et.expectObjectsAreNotEqual(originalFilters, null);
    }

    @Test
    public void testToString() {
        AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        String expectedString =
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
        AppInstallFilters originalFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.getPackageNames()).isEmpty();
    }

    @Test
    public void testCreatorNewArray_success() {
        AppInstallFilters[] filtersArray = AppInstallFilters.CREATOR.newArray(2);

        assertThat(filtersArray).hasLength(2);
        assertThat(filtersArray[0]).isNull();
        assertThat(filtersArray[1]).isNull();
    }

    @Test
    public void testAppInstallFiltersDescribeContents_success() {
        AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();

        assertThat(originalFilters.describeContents()).isEqualTo(0);
    }
}
