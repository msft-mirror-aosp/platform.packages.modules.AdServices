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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AssetFileDescriptorUtil;
import android.content.res.AssetFileDescriptor;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.concurrent.ExecutorService;

public final class GetAdSelectionDataResponseTest extends AdServicesUnitTestCase {
    private static final byte[] AD_SELECTION_RESULT = new byte[] {1, 2, 3, 4};
    private static final byte[] ANOTHER_AD_SELECTION_RESULT = new byte[] {5, 6, 7, 8};
    private static final long TEST_AD_SELECTION_ID = 12345;
    private static final ExecutorService BLOCKING_EXECUTOR =
            AdServicesExecutors.getBlockingExecutor();

    @Test
    public void testBuildGetAdSelectionDataResponse() {
        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        expect.that(getAdSelectionDataResponse.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        expect.that(getAdSelectionDataResponse.getAdSelectionData()).isEqualTo(AD_SELECTION_RESULT);
        expect.that(getAdSelectionDataResponse.getAssetFileDescriptor()).isNull();
    }

    @Test
    public void testBuildGetAdSelectionDataResponseWithAssetFileDescriptor() throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(
                        AD_SELECTION_RESULT, BLOCKING_EXECUTOR);

        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAssetFileDescriptor(assetFileDescriptor)
                        .build();

        expect.that(getAdSelectionDataResponse.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        expect.that(getAdSelectionDataResponse.getAdSelectionData()).isNull();
        byte[] result =
                AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(assetFileDescriptor);
        expect.that(result).isEqualTo(AD_SELECTION_RESULT);
    }

    @Test
    public void testParcelGetAdSelectionDataResponse() {
        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataResponse fromParcel =
                GetAdSelectionDataResponse.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        expect.that(fromParcel.getAdSelectionData()).isEqualTo(AD_SELECTION_RESULT);
        expect.that(getAdSelectionDataResponse.getAssetFileDescriptor()).isNull();
    }

    @Test
    public void testParcelGetAdSelectionDataResponseWithAssetFileDescriptor() throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                android.adservices.common.AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(
                        AD_SELECTION_RESULT, BLOCKING_EXECUTOR);

        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAssetFileDescriptor(assetFileDescriptor)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataResponse fromParcel =
                GetAdSelectionDataResponse.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        expect.that(fromParcel.getAdSelectionData()).isNull();
        byte[] result =
                AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(
                        fromParcel.getAssetFileDescriptor());
        expect.that(result).isEqualTo(AD_SELECTION_RESULT);
    }

    @Test
    public void testParcelGetAdSelectionDataResponseWithNullValues() {
        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(null)
                        .build();

        Parcel p = Parcel.obtain();
        getAdSelectionDataResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        GetAdSelectionDataResponse fromParcel =
                GetAdSelectionDataResponse.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        expect.that(fromParcel.getAdSelectionData()).isNull();
        expect.that(getAdSelectionDataResponse.getAssetFileDescriptor()).isNull();
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GetAdSelectionDataResponse.Builder()
                                // Not setting AdSelectionId making it null.
                                .setAdSelectionData(AD_SELECTION_RESULT)
                                .build());
    }

    @Test
    public void testGetAdSelectionDataResponseWithSameValuesAreEqual() {
        GetAdSelectionDataResponse obj1 =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        GetAdSelectionDataResponse obj2 =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreEqual(obj1, obj2);
    }

    @Test
    public void testGetAdSelectionDataResponseWithDifferentValuesAreNotEqual() {
        GetAdSelectionDataResponse obj1 =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        GetAdSelectionDataResponse obj2 =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(ANOTHER_AD_SELECTION_RESULT)
                        .build();

        GetAdSelectionDataResponse obj3 =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(13579)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        EqualsTester et = new EqualsTester(expect);
        et.expectObjectsAreNotEqual(obj1, obj2);
        et.expectObjectsAreNotEqual(obj1, obj3);
        et.expectObjectsAreNotEqual(obj2, obj3);
    }

    @Test
    public void testGetAdSelectionDataResponseDescribeContents() {
        GetAdSelectionDataResponse obj =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        expect.that(obj.describeContents()).isEqualTo(0);
    }

    @Test
    public void testMutabilityForAdSelectionData() {
        byte originalValue = 1;
        byte[] adSelectionData = new byte[] {originalValue};
        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(adSelectionData)
                        .build();
        expect.that(getAdSelectionDataResponse.getAdSelectionData()).isEqualTo(adSelectionData);

        byte newValue = 5;
        adSelectionData[0] = newValue;
        assertThat(getAdSelectionDataResponse.getAdSelectionData()).isNotNull();
        assertThat(getAdSelectionDataResponse.getAdSelectionData()).isNotEmpty();
        assertThat(getAdSelectionDataResponse.getAdSelectionData()[0]).isEqualTo(originalValue);
    }
}
