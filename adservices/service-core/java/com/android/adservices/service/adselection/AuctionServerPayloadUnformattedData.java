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

/**
 * Represents unformatted data, input to {@link AuctionServerPayloadFormatter#apply} and output from
 * {@link AuctionServerPayloadExtractor#extract}
 */
public class AuctionServerPayloadUnformattedData {
    private final byte[] mData;

    private AuctionServerPayloadUnformattedData(byte[] data) {
        this.mData = data;
    }

    /**
     * @return data
     */
    public byte[] getData() {
        return Arrays.copyOf(mData, mData.length);
    }

    /** Creates {@link AuctionServerPayloadUnformattedData} */
    public static AuctionServerPayloadUnformattedData create(byte[] data) {
        return new AuctionServerPayloadUnformattedData(Arrays.copyOf(data, data.length));
    }
}
