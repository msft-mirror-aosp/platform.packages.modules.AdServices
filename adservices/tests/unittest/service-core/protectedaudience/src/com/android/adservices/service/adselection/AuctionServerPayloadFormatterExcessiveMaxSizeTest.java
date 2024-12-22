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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.BYTES_CONVERSION_FACTOR;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.DATA_SIZE_PADDING_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

public class AuctionServerPayloadFormatterExcessiveMaxSizeTest {
    private static final int VALID_COMPRESSOR_VERSION = 0;
    private static final byte EXPECTED_META_INFO_BYTE = 32;
    private static final int DATA_START = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE;
    private static final int USUAL_MAXIMUM_PAYLOAD_SIZE_IN_BYTES =
            Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES.stream().max(Integer::compare).get();
    private AuctionServerPayloadFormatterExcessiveMaxSize
            mAuctionServerPayloadFormatterExcessiveMaxSize;

    private static byte[] getRandomByteArray(int size) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] result = new byte[size];
        secureRandom.nextBytes(result);
        return result;
    }

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mAuctionServerPayloadFormatterExcessiveMaxSize =
                new AuctionServerPayloadFormatterExcessiveMaxSize();
    }

    @Test
    public void testEmptyInput_roundupToMinimumBucketSize_success() {
        testDataPaddingAndDispadding(new byte[] {}, 1024);
    }

    @Test
    public void testInputSmallerThanMinimumBucketSize_roundupToMinimum_success() {
        testDataPaddingAndDispadding(new byte[] {2, 3, 4}, 1024);
    }

    @Test
    public void testInputSmallerThenABucketSize_roundUpToUseSmallestAvailableBucket_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(3 * BYTES_CONVERSION_FACTOR), 4 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputEqualToAvailableBucketSize_useThatSize_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        4 * BYTES_CONVERSION_FACTOR
                                - META_INFO_LENGTH_BYTE
                                - DATA_SIZE_PADDING_LENGTH_BYTE),
                4 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputLargerThanABucketSize_roundUpToUseSmallestAvailableBucketSize_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(4 * BYTES_CONVERSION_FACTOR), 8 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputSizeEqualToLargestAvailableBucketSize_useTheBucket_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        USUAL_MAXIMUM_PAYLOAD_SIZE_IN_BYTES
                                - META_INFO_LENGTH_BYTE
                                - DATA_SIZE_PADDING_LENGTH_BYTE),
                USUAL_MAXIMUM_PAYLOAD_SIZE_IN_BYTES);
    }

    @Test
    public void testReuseFormatter() {
        testDataPaddingAndDispadding(
                getRandomByteArray(4 * BYTES_CONVERSION_FACTOR), 8 * BYTES_CONVERSION_FACTOR);
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        4 * BYTES_CONVERSION_FACTOR
                                - META_INFO_LENGTH_BYTE
                                - DATA_SIZE_PADDING_LENGTH_BYTE),
                4 * BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testInputSizeLargerThenLargestBucket_doesNotThrowISE() {
        testDataPaddingAndDispadding(
                getRandomByteArray(USUAL_MAXIMUM_PAYLOAD_SIZE_IN_BYTES),
                2 * USUAL_MAXIMUM_PAYLOAD_SIZE_IN_BYTES);
    }

    public void testDataPaddingAndDispadding(byte[] data, int expectedSizeInBytes) {
        AuctionServerPayloadUnformattedData input =
                AuctionServerPayloadUnformattedData.create(data);

        AuctionServerPayloadFormattedData formatted =
                mAuctionServerPayloadFormatterExcessiveMaxSize.apply(
                        input, VALID_COMPRESSOR_VERSION);
        AuctionServerPayloadUnformattedData unformattedData =
                mAuctionServerPayloadFormatterExcessiveMaxSize.extract(formatted);

        assertArrayEquals(
                "Formatted data not un-formatted correctly.", data, unformattedData.getData());

        validateFormattedData(data, expectedSizeInBytes, formatted);
    }

    private void validateFormattedData(
            byte[] data, int expectedSizeInBytes, AuctionServerPayloadFormattedData formatted) {
        assertEquals(
                "data length (bucket size) mismatch.",
                expectedSizeInBytes,
                formatted.getData().length);
        assertEquals("meta info byte mismatch.", EXPECTED_META_INFO_BYTE, formatted.getData()[0]);
        assertEquals(
                "data size bytes mismatch.",
                data.length,
                ByteBuffer.wrap(
                                formatted.getData(),
                                META_INFO_LENGTH_BYTE,
                                DATA_SIZE_PADDING_LENGTH_BYTE)
                        .getInt());
        assertArrayEquals(
                "Original data and packed data mismatch.",
                data,
                Arrays.copyOfRange(formatted.getData(), DATA_START, DATA_START + data.length));

        for (int i = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + data.length;
                i < expectedSizeInBytes;
                i++) {
            assertEquals("padding bytes not set to zero", (byte) 0, formatted.getData()[i]);
        }
    }
}
