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

package android.adservices.signals;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@RequiresSdkLevelAtLeastT
public final class UpdateSignalsInputTest extends AdServicesUnitTestCase {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String OTHER_PACKAGE = CommonFixture.TEST_PACKAGE_NAME_2;

    @Test
    public void testBuild() {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        expect.that(input.getUpdateUri()).isEqualTo(URI);
        expect.that(input.getCallerPackageName()).isEqualTo(PACKAGE);
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new UpdateSignalsInput.Builder(null, PACKAGE).build());
    }

    @Test
    public void testBuildNullPackage_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new UpdateSignalsInput.Builder(URI, null).build());
    }

    @Test
    public void testParceling() {
        UpdateSignalsInput expected = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        Parcel parcel = Parcel.obtain();

        expected.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UpdateSignalsInput actual = UpdateSignalsInput.CREATOR.createFromParcel(parcel);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testUnParcelNullUri() {
        Parcel parcel = Parcel.obtain();

        Uri.writeToParcel(parcel, null);
        parcel.writeString(PACKAGE);
        parcel.setDataPosition(0);
        assertThrows(
                NullPointerException.class,
                () -> UpdateSignalsInput.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testUnParcelNullPackage() {
        Parcel parcel = Parcel.obtain();

        Uri.writeToParcel(parcel, URI);
        parcel.writeString(null);
        parcel.setDataPosition(0);
        assertThrows(
                NullPointerException.class,
                () -> UpdateSignalsInput.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testNewArray() {
        int arrayLength = 5;
        UpdateSignalsInput[] array = UpdateSignalsInput.CREATOR.newArray(arrayLength);
        assertThat(array).hasLength(arrayLength);
    }

    @Test
    public void testDescribeContents() {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertThat(input.describeContents()).isEqualTo(0);
    }

    @Test
    public void testEqualsEqual() {
        EqualsTester et = new EqualsTester(expect);
        UpdateSignalsInput identical1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        UpdateSignalsInput identical2 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        et.expectObjectsAreEqual(identical1, identical2);
    }

    @Test
    public void testEqualsNotEqualSameClass() {
        EqualsTester et = new EqualsTester(expect);
        UpdateSignalsInput different1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        UpdateSignalsInput different2 = new UpdateSignalsInput.Builder(URI, OTHER_PACKAGE).build();
        et.expectObjectsAreNotEqual(different1, different2);
    }

    @Test
    public void testEqualsNotEqualDifferentClass() {
        EqualsTester et = new EqualsTester(expect);
        UpdateSignalsInput input1 = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        et.expectObjectsAreNotEqual(input1, new Object());
    }

    @Test
    public void testToString() {
        UpdateSignalsInput input = new UpdateSignalsInput.Builder(URI, PACKAGE).build();
        assertThat(input.toString())
                .isEqualTo(
                        "UpdateSignalsInput{"
                                + "mUpdateUri="
                                + URI
                                + ", mCallerPackageName='"
                                + PACKAGE
                                + '\''
                                + '}');
    }
}
