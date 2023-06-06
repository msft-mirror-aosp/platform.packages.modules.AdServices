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

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Data padding and padding removal class. */
public class AuctionServerPayloadFormatterV0 implements AuctionServerPayloadFormatter {
    public static final int VERSION = 0;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int[] AVAILABLE_BUCKET_SIZES_IN_KB = new int[] {0, 1, 2, 4, 8, 16, 32, 64};

    @VisibleForTesting
    static final String PAYLOAD_SIZE_EXCEEDS_LIMIT = "Payload exceeds maximum size of 64KB";

    private static final String DATA_SIZE_MISMATCH =
            "Data size extracted from padded bytes is longer than the rest of the data";
    @VisibleForTesting static final int PAYLOAD_FORMAT_VERSION_LENGTH_BITS = 3;
    @VisibleForTesting static final int COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS = 5;
    private static final int META_INFO_LENGTH_BYTE =
            (PAYLOAD_FORMAT_VERSION_LENGTH_BITS + COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS)
                    / ONE_BYTE_IN_BITS;
    private static final int DATA_SIZE_PADDING_LENGTH_BYTE = 4;
    @NonNull private final ByteBuffer mByteBuffer;

    public AuctionServerPayloadFormatterV0() {
        mByteBuffer = ByteBuffer.allocate(DATA_SIZE_PADDING_LENGTH_BYTE);
    }

    /**
     * Creates the payload of size [1KB, 2KB, 4KB, 8KB, 16KB, 32KB, 64KB]. If the payload is greater
     * than 64KB throw exception.
     *
     * <ul>
     *   <li>First 1 byte represents meta info
     *       <ul>
     *         <li>3 bits for payload format version
     *         <li>5 bits for compression algorithm version
     *       </ul>
     *   <li>Next 4 bytes are size of the given data
     *   <li>Next {@code N} bytes are the given data where N is {@code data.length}
     *   <li>Next {@code bucketSize - N - 4 - 1} are padded zeros
     * </ul>
     *
     * @throws IllegalArgumentException when payload size exceeds size limit
     */
    public FormattedData apply(@NonNull UnformattedData unformattedData, int compressorVersion) {
        Objects.requireNonNull(unformattedData);

        byte[] data = unformattedData.getData();

        // Empty payload to fill in
        byte[] payload = new byte[getPayloadBucketSize(data.length)];

        // Fill in
        payload[0] = getMetaInfoByte(compressorVersion);
        System.arraycopy(
                mByteBuffer.putInt(data.length).array(),
                0,
                payload,
                META_INFO_LENGTH_BYTE,
                DATA_SIZE_PADDING_LENGTH_BYTE);
        System.arraycopy(
                data,
                0,
                payload,
                META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE,
                data.length);

        return FormattedData.create(payload);
    }

    /** Extracts the original payload from padded data and the compression algorithm identifier. */
    public UnformattedData extract(FormattedData formattedData) {
        byte[] payload = formattedData.getData();

        // Next 4 bytes encode the size of the data
        byte[] sizeBytes = new byte[DATA_SIZE_PADDING_LENGTH_BYTE];
        System.arraycopy(
                payload, META_INFO_LENGTH_BYTE, sizeBytes, 0, DATA_SIZE_PADDING_LENGTH_BYTE);
        int dataSize = ByteBuffer.wrap(sizeBytes).getInt();

        if (META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + dataSize > payload.length) {
            sLogger.e(DATA_SIZE_MISMATCH);
            throw new IllegalArgumentException(DATA_SIZE_MISMATCH);
        }

        // Extract the data
        byte[] data = new byte[dataSize];
        System.arraycopy(
                payload, META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE, data, 0, dataSize);

        return UnformattedData.create(data);
    }

    private byte getMetaInfoByte(int compressionVersion) {
        String payloadFormatterVersionBits =
                getFixLengthVersionString(
                        AuctionServerPayloadFormatterV0.VERSION,
                        PAYLOAD_FORMAT_VERSION_LENGTH_BITS);
        String compressionAlgorithmVersionBits =
                getFixLengthVersionString(
                        compressionVersion, COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS);
        int base2 = 2;
        return Byte.parseByte(payloadFormatterVersionBits + compressionAlgorithmVersionBits, base2);
    }

    private int getPayloadBucketSize(int dataLength) {
        int payloadSize = META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + dataLength;

        // Check if sizeInBytes exceeds 64KB limit
        // TODO(b/285182469): Implement payload size management
        if (payloadSize > MAXIMUM_PAYLOAD_SIZE_IN_BYTES) {
            sLogger.e(PAYLOAD_SIZE_EXCEEDS_LIMIT);
            throw new IllegalArgumentException(PAYLOAD_SIZE_EXCEEDS_LIMIT);
        }

        // Convert B to KB and round up if necessary
        int sizeInKB = (payloadSize + 1023) >> 10; // Equivalent to (sizeInBytes + 1023) / 1024

        //        int bucketSizeKB = nextPowerOf2(sizeInKB); // get next power of two
        int bucketSizeKB = Arrays.binarySearch(AVAILABLE_BUCKET_SIZES_IN_KB, sizeInKB);

        // Convert KB back to bytes and return
        return bucketSizeKB << 10; // Equivalent to bucketSizeKB * 1024
    }

    /** Returns a fix sized string for a given version int aligned right */
    private String getFixLengthVersionString(int version, int length) {
        return String.format("%0" + length + "d", version);
    }
}
