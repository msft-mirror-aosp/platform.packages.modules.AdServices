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

import com.android.adservices.LoggerFactory;

import java.util.Arrays;

/**
 * Payload formatter interface defines methods for a payload formatter.
 *
 * <p>Each formatted data contains a meta info byte as its first byte where the first 3 bits are for
 * compression algorithm version and the last 5 bits are for the formatter version.
 *
 * <p>This interface also includes methods for creating and extracting meta info byte.
 */
public interface AuctionServerPayloadFormatter {
    LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    int ONE_BYTE_IN_BITS = 8;
    int BYTES_CONVERSION_FACTOR = 1024;
    int MAXIMUM_PAYLOAD_SIZE_IN_BYTES = 64 * BYTES_CONVERSION_FACTOR;
    int PAYLOAD_FORMAT_VERSION_LENGTH_BITS = 3;
    int COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS = 5;
    int META_INFO_LENGTH_BYTE =
            (PAYLOAD_FORMAT_VERSION_LENGTH_BITS + COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS)
                    / ONE_BYTE_IN_BITS;

    /** Generates payload from given data */
    FormattedData apply(UnformattedData unformattedData, int compressorVersion);

    /** Extracts the data and the compression algo version from payload */
    UnformattedData extract(FormattedData payload);

    /** Creates meta info byte from given version integers. */
    static byte getMetaInfoByte(int compressionVersion, int formatterVersion) {
        int formatterVersionLowerLimit = 0;
        int formatterVersionUpperLimit = (1 << PAYLOAD_FORMAT_VERSION_LENGTH_BITS) - 1;
        if (formatterVersion < formatterVersionLowerLimit
                || formatterVersion > formatterVersionUpperLimit) {
            String err =
                    String.format(
                            "Formatter version must be between %s and %s. Given version: %s",
                            formatterVersionLowerLimit,
                            formatterVersionUpperLimit,
                            formatterVersion);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }

        int compressionVersionLowerLimit = 0;
        int compressionVersionUpperLimit = (1 << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS) - 1;
        if (compressionVersion < compressionVersionLowerLimit
                || compressionVersion > compressionVersionUpperLimit) {
            String err =
                    String.format(
                            "Compression version must be between %s and %s. Given version: %s",
                            compressionVersionLowerLimit,
                            compressionVersionUpperLimit,
                            compressionVersion);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }

        // Left-shift the compressionVersion by the length of formatter bits, then bitwise OR with
        // formatterVersion.
        return (byte)
                ((compressionVersion << PAYLOAD_FORMAT_VERSION_LENGTH_BITS) | formatterVersion);
    }

    /** Extracts compression version from a byte */
    static int extractCompressionVersion(byte metaInfoByte) {
        // 0xFF is used to make sure the shift fills with 0s instead of sign-extending
        return (metaInfoByte & 0xFF) >>> PAYLOAD_FORMAT_VERSION_LENGTH_BITS;
    }

    /** Extracts formatter version from a byte */
    static int extractFormatterVersion(byte metaInfoByte) {
        // 0x07 (which is 7 in decimal or 111 in binary) is used to make sure we only
        // keep the lower 3 bits of the byte (which is the formatter version)
        return metaInfoByte & ((1 << PAYLOAD_FORMAT_VERSION_LENGTH_BITS) - 1);
    }

    /**
     * Represents unformatted data, input to {@link AuctionServerPayloadFormatter#apply} and output
     * from {@link AuctionServerPayloadFormatter#extract}
     */
    class UnformattedData {
        private final byte[] mData;

        private UnformattedData(byte[] data) {
            this.mData = data;
        }

        /**
         * @return data
         */
        public byte[] getData() {
            return Arrays.copyOf(mData, mData.length);
        }

        /** Creates {@link UnformattedData} */
        public static UnformattedData create(byte[] data) {
            return new UnformattedData(Arrays.copyOf(data, data.length));
        }
    }

    /**
     * Represents formatted data, input to {@link AuctionServerPayloadFormatter#extract} and output
     * from {@link AuctionServerPayloadFormatter#apply}
     */
    class FormattedData {
        private final byte[] mData;

        private FormattedData(byte[] data) {
            this.mData = data;
        }

        /**
         * @return data
         */
        public byte[] getData() {
            return Arrays.copyOf(mData, mData.length);
        }

        /** Creates {@link FormattedData} */
        public static FormattedData create(byte[] data) {
            return new FormattedData(Arrays.copyOf(data, data.length));
        }
    }
}
