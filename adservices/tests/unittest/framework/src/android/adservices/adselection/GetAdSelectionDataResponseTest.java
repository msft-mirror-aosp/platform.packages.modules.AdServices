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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.content.res.AssetFileDescriptor;
import android.os.Parcel;

import org.junit.Test;


public class GetAdSelectionDataResponseTest {
    private static final byte[] AD_SELECTION_RESULT = new byte[] {1, 2, 3, 4};
    private static final byte[] ANOTHER_AD_SELECTION_RESULT = new byte[] {5, 6, 7, 8};
    private static final long TEST_AD_SELECTION_ID = 12345;

    @Test
    public void testBuildGetAdSelectionDataResponse() {
        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        assertThat(getAdSelectionDataResponse.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(getAdSelectionDataResponse.getAdSelectionData()).isEqualTo(AD_SELECTION_RESULT);
        assertThat(getAdSelectionDataResponse.getAssetFileDescriptor()).isNull();
    }

    @Test
    public void testBuildGetAdSelectionDataResponseWithAssetFileDescriptor() throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(AD_SELECTION_RESULT);
        byte[] result = new byte[AD_SELECTION_RESULT.length];

        GetAdSelectionDataResponse getAdSelectionDataResponse =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAssetFileDescriptor(assetFileDescriptor)
                        .build();

        assertThat(getAdSelectionDataResponse.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(getAdSelectionDataResponse.getAdSelectionData()).isNull();
        AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(result, assetFileDescriptor);
        assertThat(result).isEqualTo(AD_SELECTION_RESULT);
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

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(fromParcel.getAdSelectionData()).isEqualTo(AD_SELECTION_RESULT);
        assertThat(getAdSelectionDataResponse.getAssetFileDescriptor()).isNull();
    }

    @Test
    public void testParcelGetAdSelectionDataResponseWithAssetFileDescriptor() throws Exception {
        AssetFileDescriptor assetFileDescriptor =
                AssetFileDescriptorUtil.setupAssetFileDescriptorResponse(AD_SELECTION_RESULT);
        byte[] result = new byte[AD_SELECTION_RESULT.length];

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

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(fromParcel.getAdSelectionData()).isNull();
        AssetFileDescriptorUtil.readAssetFileDescriptorIntoBuffer(
                result, fromParcel.getAssetFileDescriptor());
        assertThat(result).isEqualTo(AD_SELECTION_RESULT);
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

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(fromParcel.getAdSelectionData()).isNull();
        assertThat(getAdSelectionDataResponse.getAssetFileDescriptor()).isNull();
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new GetAdSelectionDataResponse.Builder()
                            // Not setting AdSelectionId making it null.
                            .setAdSelectionData(AD_SELECTION_RESULT)
                            .build();
                });
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

        assertThat(obj1).isEqualTo(obj2);
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

        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testGetAdSelectionDataResponseDescribeContents() {
        GetAdSelectionDataResponse obj =
                new GetAdSelectionDataResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        assertEquals(0, obj.describeContents());
    }

    @Test
    public void testEqualGetAdSelectionDataResponsesHaveSameHashCode() {
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

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualGetAdSelectionDataResponsesHaveDifferentHashCodes() {
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

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
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
        assertThat(getAdSelectionDataResponse.getAdSelectionData()).isEqualTo(adSelectionData);

        byte newValue = 5;
        adSelectionData[0] = newValue;
        assertThat(getAdSelectionDataResponse.getAdSelectionData()).isNotNull();
        assertThat(getAdSelectionDataResponse.getAdSelectionData()[0]).isEqualTo(originalValue);
    }
}
