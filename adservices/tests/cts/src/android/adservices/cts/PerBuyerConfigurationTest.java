/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.adservices.adselection.SellerConfigurationFixture.BUYER_1;
import static android.adservices.adselection.SellerConfigurationFixture.BUYER_1_TARGET_SIZE_B;
import static android.adservices.adselection.SellerConfigurationFixture.BUYER_2;
import static android.adservices.adselection.SellerConfigurationFixture.BUYER_2_TARGET_SIZE_B;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.PerBuyerConfiguration;
import android.os.Parcel;

import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

public final class PerBuyerConfigurationTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testPerBuyerConfiguration_success() {
        PerBuyerConfiguration perBuyerConfiguration =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_1)
                        .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                        .build();

        expect.that(perBuyerConfiguration.getBuyer()).isEqualTo(BUYER_1);
        expect.that(perBuyerConfiguration.getTargetInputSizeBytes())
                .isEqualTo(BUYER_1_TARGET_SIZE_B);
    }

    @Test
    public void testPerBuyerConfiguration_withoutTargetSize_success() {
        PerBuyerConfiguration perBuyerConfiguration =
                new PerBuyerConfiguration.Builder().setBuyer(BUYER_1).build();

        expect.that(perBuyerConfiguration.getBuyer()).isEqualTo(BUYER_1);
        expect.that(perBuyerConfiguration.getTargetInputSizeBytes()).isEqualTo(0);
    }

    @Test
    public void testPerBuyerConfiguration_withoutBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new PerBuyerConfiguration.Builder()
                                .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                                .build());
    }

    @Test
    public void testPerBuyerConfiguration_withNegativeTargetSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PerBuyerConfiguration.Builder()
                                .setTargetInputSizeBytes(-1)
                                .setBuyer(BUYER_1)
                                .build());
    }

    @Test
    public void testPerBuyerConfigurationEquals_notPerBuyerConfiguration() {
        EqualsTester et = new EqualsTester(expect);

        PerBuyerConfiguration perBuyerConfiguration =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_1)
                        .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                        .build();

        et.expectObjectsAreNotEqual(perBuyerConfiguration, 1);
    }

    @Test
    public void testParcelPerBuyerConfiguration() {
        PerBuyerConfiguration perBuyerConfiguration =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_1)
                        .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                        .build();

        Parcel p = Parcel.obtain();
        perBuyerConfiguration.writeToParcel(p, 0);
        p.setDataPosition(0);
        PerBuyerConfiguration fromParcel = PerBuyerConfiguration.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getBuyer()).isEqualTo(BUYER_1);
        expect.that(fromParcel.getTargetInputSizeBytes()).isEqualTo(BUYER_1_TARGET_SIZE_B);
    }

    @Test
    public void testDescribeContents() {
        PerBuyerConfiguration perBuyerConfiguration =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_1)
                        .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                        .build();

        expect.that(perBuyerConfiguration.describeContents()).isEqualTo(0);
    }

    @Test
    public void testNewArray() {
        PerBuyerConfiguration[] arr = PerBuyerConfiguration.CREATOR.newArray(10);
        expect.that(arr).hasLength(10);
    }

    @Test
    public void testPerBuyerConfiguration_notEqual() {
        PerBuyerConfiguration perBuyerConfiguration1 =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_1)
                        .setTargetInputSizeBytes(BUYER_1_TARGET_SIZE_B)
                        .build();

        PerBuyerConfiguration perBuyerConfiguration2 =
                new PerBuyerConfiguration.Builder()
                        .setBuyer(BUYER_2)
                        .setTargetInputSizeBytes(BUYER_2_TARGET_SIZE_B)
                        .build();
        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(perBuyerConfiguration1, perBuyerConfiguration2);
    }
}
