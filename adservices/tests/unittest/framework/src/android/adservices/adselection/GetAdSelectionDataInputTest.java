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

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public final class GetAdSelectionDataInputTest extends AdServicesUnitTestCase {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String ANOTHER_CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME_1;

    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier ANOTHER_SELLER = AdSelectionConfigFixture.SELLER_1;

    private static final Uri COORDINATOR_ORIGIN = Uri.parse("https://example.com");
    private static final Uri ANOTHER_COORDINATOR_ORIGIN = Uri.parse("https://google.com");

    @Test
    public void testBuildGetAdSelectionDataInput() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .build();

        expect.that(getAdSelectionDataInput.getSeller()).isEqualTo(SELLER);
        expect.that(getAdSelectionDataInput.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(getAdSelectionDataInput.getCoordinatorOriginUri())
                .isEqualTo(COORDINATOR_ORIGIN);
        expect.that(getAdSelectionDataInput.getSellerConfiguration()).isNull();
    }

    @Test
    public void testBuildGetAdSelectionDataInputWithSellerConfiguration() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        expect.that(getAdSelectionDataInput.getSeller()).isEqualTo(SELLER);
        expect.that(getAdSelectionDataInput.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(getAdSelectionDataInput.getCoordinatorOriginUri())
                .isEqualTo(COORDINATOR_ORIGIN);
        expect.that(getAdSelectionDataInput.getSellerConfiguration())
                .isEqualTo(SellerConfigurationFixture.SELLER_CONFIGURATION);
    }

    @Test
    public void testParcelGetAdSelectionDataInput() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataInput.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataInput fromParcel = GetAdSelectionDataInput.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getSeller()).isEqualTo(SELLER);
        expect.that(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(fromParcel.getCoordinatorOriginUri()).isEqualTo(COORDINATOR_ORIGIN);
        expect.that(fromParcel.getSellerConfiguration())
                .isEqualTo(SellerConfigurationFixture.SELLER_CONFIGURATION);
    }

    @Test
    public void testParcelGetAdSelectionDataInputWithNullValues() {
        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(null)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(null)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataInput.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataInput fromParcel = GetAdSelectionDataInput.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getSeller()).isNull();
        expect.that(fromParcel.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(fromParcel.getCoordinatorOriginUri()).isNull();
    }

    @Test
    public void testGetAdSelectionDataInputWithSameValuesAreEqual() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testGetAdSelectionDataInputWithDifferentPackageNameValuesAreNotEqual() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(ANOTHER_CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
    }

    @Test
    public void testGetAdSelectionDataInputWithDifferentCoordinatorValuesAreNotEqual() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .build();

        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(ANOTHER_COORDINATOR_ORIGIN)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
    }

    @Test
    public void testGetAdSelectionDataInputDescribeContents() {
        GetAdSelectionDataInput obj =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .build();

        expect.that(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testNotEqualGetAdSelectionDataInputsHaveDifferentHashCodes() {
        GetAdSelectionDataInput obj1 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .build();
        GetAdSelectionDataInput obj2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(ANOTHER_CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .build();
        GetAdSelectionDataInput obj3 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(ANOTHER_SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(COORDINATOR_ORIGIN)
                        .build();
        GetAdSelectionDataInput obj4 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(ANOTHER_COORDINATOR_ORIGIN)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3, obj4);
    }
}
