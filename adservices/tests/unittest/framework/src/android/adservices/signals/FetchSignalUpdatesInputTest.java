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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;

public class FetchSignalUpdatesInputTest {

    private static final Uri URI = Uri.parse("https://example.com/somecoolsignals");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String OTHER_PACKAGE = CommonFixture.TEST_PACKAGE_NAME_2;

    @Test
    public void testBuild() {
        FetchSignalUpdatesInput input = new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        assertEquals(URI, input.getFetchUri());
        assertEquals(PACKAGE, input.getCallerPackageName());
    }

    @Test
    public void testBuildNullUri_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FetchSignalUpdatesInput.Builder(null, PACKAGE).build());
    }

    @Test
    public void testBuildNullPackage_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FetchSignalUpdatesInput.Builder(URI, null).build());
    }

    @Test
    public void testParceling() {
        FetchSignalUpdatesInput expected =
                new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        Parcel parcel = Parcel.obtain();

        expected.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FetchSignalUpdatesInput actual = FetchSignalUpdatesInput.CREATOR.createFromParcel(parcel);

        assertEquals(expected, actual);
    }

    @Test
    public void testUnParcelNullUri() {
        Parcel parcel = Parcel.obtain();

        Uri.writeToParcel(parcel, null);
        parcel.writeString(PACKAGE);
        parcel.setDataPosition(0);
        assertThrows(
                NullPointerException.class,
                () -> FetchSignalUpdatesInput.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testUnParcelNullPackage() {
        Parcel parcel = Parcel.obtain();

        Uri.writeToParcel(parcel, URI);
        parcel.writeString(null);
        parcel.setDataPosition(0);
        assertThrows(
                NullPointerException.class,
                () -> FetchSignalUpdatesInput.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testNewArray() {
        int arrayLength = 5;
        FetchSignalUpdatesInput[] array = FetchSignalUpdatesInput.CREATOR.newArray(arrayLength);
        assertEquals(arrayLength, array.length);
    }

    @Test
    public void testDescribeContents() {
        FetchSignalUpdatesInput input = new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        assertEquals(0, input.describeContents());
    }

    @Test
    public void testEqualsEqual() {
        FetchSignalUpdatesInput identical1 =
                new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        FetchSignalUpdatesInput identical2 =
                new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        assertEquals(identical1, identical2);
    }

    @Test
    public void testEqualsNotEqualSameClass() {
        FetchSignalUpdatesInput different1 =
                new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        FetchSignalUpdatesInput different2 =
                new FetchSignalUpdatesInput.Builder(URI, OTHER_PACKAGE).build();
        assertNotEquals(different1, different2);
    }

    @Test
    public void testEqualsNotEqualDifferentClass() {
        FetchSignalUpdatesInput input1 = new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        assertNotEquals(input1, new Object());
    }

    @Test
    public void testHash() {
        FetchSignalUpdatesInput identical1 =
                new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        FetchSignalUpdatesInput identical2 =
                new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        assertEquals(identical1.hashCode(), identical2.hashCode());
    }

    @Test
    public void testToString() {
        FetchSignalUpdatesInput input = new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();
        assertEquals(
                "FetchSignalUpdatesInput{"
                        + "mFetchUri="
                        + URI
                        + ", mCallerPackageName='"
                        + PACKAGE
                        + '\''
                        + '}',
                input.toString());
    }
}
