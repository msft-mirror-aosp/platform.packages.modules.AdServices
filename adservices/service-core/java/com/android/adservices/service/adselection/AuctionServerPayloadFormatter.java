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

import java.util.Arrays;

/** Payload formatter interface */
public interface AuctionServerPayloadFormatter {
    int ONE_BYTE_IN_BITS = 8;
    int BYTES_CONVERSION_FACTOR = 1024;
    int MAXIMUM_PAYLOAD_SIZE_IN_BYTES = 64 * BYTES_CONVERSION_FACTOR;

    /** Generates payload from given data */
    FormattedData apply(UnformattedData unformattedData, int compressorVersion);

    /** Extracts the data and the compression algo version from payload */
    UnformattedData extract(FormattedData payload);

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
