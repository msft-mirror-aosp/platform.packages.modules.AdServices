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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AuctionServerPayloadFormatterExactSize.DATA_SIZE_LARGER_THAN_TARGET;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormatterExactSize.DATA_SIZE_MISMATCH;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.BYTES_CONVERSION_FACTOR;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.DATA_SIZE_PADDING_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.getMetaInfoByte;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.exceptions.UnsupportedPayloadSizeException;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AuctionServerPayloadFormatterExactSizeTest extends AdServicesUnitTestCase {
    private static final int VALID_COMPRESSOR_VERSION = 0;
    private static final byte EXPECTED_META_INFO_BYTE = 64;
    private static final int DATA_START = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE;
    private static final int TARGET_SIZE_KB = 5 * BYTES_CONVERSION_FACTOR;

    private AuctionServerPayloadFormatterExactSize mAuctionServerPayloadFormatterExactSize;

    private static byte[] getRandomByteArray(int size) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] result = new byte[size];
        secureRandom.nextBytes(result);
        return result;
    }

    @Before
    public void setup() {
        mAuctionServerPayloadFormatterExactSize =
                new AuctionServerPayloadFormatterExactSize(TARGET_SIZE_KB);
    }

    @Test
    public void testEmptyInput_roundupToOnlyTargetSize_success() {
        testDataPaddingAndDispadding(new byte[] {}, TARGET_SIZE_KB);
    }

    @Test
    public void testSmallInput_roundupToOnlyTargetSize_success() {
        testDataPaddingAndDispadding(getRandomByteArray(50), TARGET_SIZE_KB);
    }

    @Test
    public void testLargeInput_roundupToOnlyTargetSize_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(4 * BYTES_CONVERSION_FACTOR), TARGET_SIZE_KB);
    }

    @Test
    public void testRandomInputReturnsTargetSize_success() {
        testDataPaddingAndDispadding(
                getRandomByteArray(
                        TARGET_SIZE_KB - META_INFO_LENGTH_BYTE - DATA_SIZE_PADDING_LENGTH_BYTE),
                TARGET_SIZE_KB);
    }

    @Test
    public void testNotAccountForMetaData_throwsISE() {
        UnsupportedPayloadSizeException exception =
                assertThrows(
                        UnsupportedPayloadSizeException.class,
                        () ->
                                mAuctionServerPayloadFormatterExactSize.apply(
                                        AuctionServerPayloadUnformattedData.create(
                                                getRandomByteArray(TARGET_SIZE_KB)),
                                        VALID_COMPRESSOR_VERSION));
        assertWithMessage("Exception message")
                .that(exception.getMessage())
                .isEqualTo(DATA_SIZE_LARGER_THAN_TARGET);
        assertWithMessage("Payload size in exception")
                .that(exception.getPayloadSizeKb())
                .isEqualTo(
                        (TARGET_SIZE_KB + META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE)
                                / BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testTooLargePayload_throwsISE() {
        UnsupportedPayloadSizeException exception =
                assertThrows(
                        UnsupportedPayloadSizeException.class,
                        () ->
                                mAuctionServerPayloadFormatterExactSize.apply(
                                        AuctionServerPayloadUnformattedData.create(
                                                getRandomByteArray(6 * BYTES_CONVERSION_FACTOR)),
                                        VALID_COMPRESSOR_VERSION));
        assertWithMessage("Exception message")
                .that(exception.getMessage())
                .isEqualTo(DATA_SIZE_LARGER_THAN_TARGET);
        assertWithMessage("Payload size in exception")
                .that(exception.getPayloadSizeKb())
                .isEqualTo(
                        (6 * BYTES_CONVERSION_FACTOR
                                        + META_INFO_LENGTH_BYTE
                                        + DATA_SIZE_PADDING_LENGTH_BYTE)
                                / BYTES_CONVERSION_FACTOR);
    }

    @Test
    public void testExtractDataSizeMismatch_throwsISE() {
        byte[] data = new byte[BYTES_CONVERSION_FACTOR];
        data[0] =
                getMetaInfoByte(
                        VALID_COMPRESSOR_VERSION, AuctionServerPayloadFormatterExactSize.VERSION);
        // set data size to be larger than actual size
        byte[] dataSizeBytes =
                ByteBuffer.allocate(DATA_SIZE_PADDING_LENGTH_BYTE)
                        .putInt(BYTES_CONVERSION_FACTOR + 64)
                        .array();
        System.arraycopy(
                dataSizeBytes, 0, data, META_INFO_LENGTH_BYTE, DATA_SIZE_PADDING_LENGTH_BYTE);

        AuctionServerPayloadFormattedData formattedData =
                AuctionServerPayloadFormattedData.create(data);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mAuctionServerPayloadFormatterExactSize.extract(formattedData));
        assertThat(exception).hasMessageThat().isEqualTo(DATA_SIZE_MISMATCH);
    }

    private void testDataPaddingAndDispadding(byte[] data, int expectedSizeInBytes) {
        AuctionServerPayloadUnformattedData input =
                AuctionServerPayloadUnformattedData.create(data);

        AuctionServerPayloadFormattedData formatted =
                mAuctionServerPayloadFormatterExactSize.apply(input, VALID_COMPRESSOR_VERSION);
        AuctionServerPayloadUnformattedData unformattedData =
                mAuctionServerPayloadFormatterExactSize.extract(formatted);

        assertWithMessage("Formatted data un-formatted correctly.")
                .that(data)
                .isEqualTo(unformattedData.getData());

        validateFormattedData(data, expectedSizeInBytes, formatted);
    }

    private void validateFormattedData(
            byte[] data, int expectedSizeInBytes, AuctionServerPayloadFormattedData formatted) {
        assertWithMessage("Formatted data length")
                .that(formatted.getData().length)
                .isEqualTo(expectedSizeInBytes);
        expect.that(formatted.getData()[0]).isEqualTo(EXPECTED_META_INFO_BYTE);
        expect.that(
                        ByteBuffer.wrap(
                                        formatted.getData(),
                                        META_INFO_LENGTH_BYTE,
                                        DATA_SIZE_PADDING_LENGTH_BYTE)
                                .getInt())
                .isEqualTo(data.length);
        assertArrayEquals(
                "Original data and packed data mismatch.",
                data,
                Arrays.copyOfRange(formatted.getData(), DATA_START, DATA_START + data.length));

        for (int i = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + data.length;
                i < expectedSizeInBytes;
                i++) {
            expect.that(formatted.getData()[i]).isEqualTo((byte) 0);
        }
    }
}
