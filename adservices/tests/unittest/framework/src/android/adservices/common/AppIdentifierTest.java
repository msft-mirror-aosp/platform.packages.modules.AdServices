/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

@SmallTest
public class AppIdentifierTest {
    @Test
    public void testAppIdentifierCreatorArray() {
        int arrayLen = 10;
        assertArrayEquals(new AppIdentifier[arrayLen], AppIdentifier.CREATOR.newArray(arrayLen));
    }

    @Test
    public void testBuildGetAppIdentifierSuccess() {
        AppIdentifier appIdentifier = new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME);
        assertEquals(CommonFixture.TEST_PACKAGE_NAME, appIdentifier.getPackageName());
    }

    @Test
    public void testParcelAppIdentifierSuccess() {
        AppIdentifier preParcel = new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME);

        Parcel parcel = Parcel.obtain();
        preParcel.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        AppIdentifier postParcel = AppIdentifier.CREATOR.createFromParcel(parcel);

        assertEquals(preParcel, postParcel);
    }

    @Test
    public void testAppIdentifierDescribeContents() {
        assertEquals(0, new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME).describeContents());
    }

    @Test
    public void testBuildAppIdentifierWithNullInputFails() {
        assertThrows(NullPointerException.class, () -> new AppIdentifier(null));
    }

    @Test
    public void testAppIdentifierEquals() {
        AppIdentifier appMatch1 = new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME);
        AppIdentifier appMatch2 = new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME);
        AppIdentifier appMismatch =
                new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME + ".mismatch");

        assertEquals(appMatch1, appMatch2);
        assertNotEquals(appMatch1, appMismatch);
        assertNotEquals(appMatch2, appMismatch);
    }

    @Test
    public void testAppIdentifierToString() {
        AppIdentifier appIdentifier = new AppIdentifier(CommonFixture.TEST_PACKAGE_NAME);
        assertFalse(appIdentifier.toString().isEmpty());
    }
}
