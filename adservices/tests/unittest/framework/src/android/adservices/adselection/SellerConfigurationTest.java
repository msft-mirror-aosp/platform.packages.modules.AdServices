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

package android.adservices.adselection;

import static android.adservices.adselection.SellerConfigurationFixture.PER_BUYER_CONFIGURATION_1;
import static android.adservices.adselection.SellerConfigurationFixture.PER_BUYER_CONFIGURATION_2;
import static android.adservices.adselection.SellerConfigurationFixture.SELLER_CONFIGURATION;
import static android.adservices.adselection.SellerConfigurationFixture.SELLER_TARGET_SIZE_B;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.util.Set;

// TODO(b/342332791): move these tests to cts after unhiding
public class SellerConfigurationTest extends AdServicesUnitTestCase {

    public static final Set<PerBuyerConfiguration> PER_BUYER_CONFIGURATIONS =
            ImmutableSet.of(PER_BUYER_CONFIGURATION_1, PER_BUYER_CONFIGURATION_2);

    @Test
    public void testSellerConfiguration_success() {
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setPerBuyerConfigurations(PER_BUYER_CONFIGURATIONS)
                        .setTargetPayloadSizeBytes(SELLER_TARGET_SIZE_B)
                        .build();

        expect.that(sellerConfiguration.getTargetPayloadSizeBytes())
                .isEqualTo(SELLER_TARGET_SIZE_B);
        expect.that(sellerConfiguration.getPerBuyerConfigurations())
                .isEqualTo(PER_BUYER_CONFIGURATIONS);
    }

    @Test
    public void testSellerConfiguration_withoutPerBuyerConfigurations_success() {
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setTargetPayloadSizeBytes(SELLER_TARGET_SIZE_B)
                        .build();

        expect.that(sellerConfiguration.getTargetPayloadSizeBytes())
                .isEqualTo(SELLER_TARGET_SIZE_B);
        expect.that(sellerConfiguration.getPerBuyerConfigurations()).isNull();
    }

    @Test
    public void testParcelSellerConfiguration() {
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setPerBuyerConfigurations(PER_BUYER_CONFIGURATIONS)
                        .setTargetPayloadSizeBytes(SELLER_TARGET_SIZE_B)
                        .build();

        Parcel p = Parcel.obtain();
        sellerConfiguration.writeToParcel(p, 0);
        p.setDataPosition(0);
        SellerConfiguration fromParcel = SellerConfiguration.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getTargetPayloadSizeBytes()).isEqualTo(SELLER_TARGET_SIZE_B);
        expect.that(fromParcel.getPerBuyerConfigurations()).isEqualTo(PER_BUYER_CONFIGURATIONS);
    }

    @Test
    public void testParcelSellerConfigurationNull() {
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setPerBuyerConfigurations(PER_BUYER_CONFIGURATIONS)
                        .setTargetPayloadSizeBytes(SELLER_TARGET_SIZE_B)
                        .build();

        assertThrows(NullPointerException.class, () -> sellerConfiguration.writeToParcel(null, 0));
    }

    @Test
    public void testParcelSellerConfigurationWithoutPerBuyerConfiguration() {
        SellerConfiguration sellerConfiguration =
                new SellerConfiguration.Builder()
                        .setTargetPayloadSizeBytes(SELLER_TARGET_SIZE_B)
                        .build();

        Parcel p = Parcel.obtain();
        sellerConfiguration.writeToParcel(p, 0);
        p.setDataPosition(0);
        SellerConfiguration fromParcel = SellerConfiguration.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getTargetPayloadSizeBytes()).isEqualTo(SELLER_TARGET_SIZE_B);
        expect.that(fromParcel.getPerBuyerConfigurations()).isNull();
    }

    @Test
    public void testSellerConfiguration_withNegativeTargetSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SellerConfiguration.Builder().setTargetPayloadSizeBytes(-1).build());
    }

    @Test
    public void testSellerConfiguration_withZeroTargetSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SellerConfiguration.Builder().setTargetPayloadSizeBytes(0).build());
    }

    @Test
    public void testSellerConfiguration_withTargetSizeNotSet_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SellerConfiguration.Builder()
                                .setPerBuyerConfigurations(PER_BUYER_CONFIGURATIONS)
                                .build());
    }

    @Test
    public void testNewArray() {
        SellerConfiguration[] arr = SellerConfiguration.CREATOR.newArray(10);
        expect.that(arr).hasLength(10);
    }

    @Test
    public void testDescribeContents() {
        expect.that(SELLER_CONFIGURATION.describeContents()).isEqualTo(0);
    }
}
