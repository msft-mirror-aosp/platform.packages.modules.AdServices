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

import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.BYTES_CONVERSION_FACTOR;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.DATA_SIZE_PADDING_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.META_INFO_LENGTH_BYTE;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormattingUtil.getMetaInfoByte;

import android.adservices.exceptions.UnsupportedPayloadSizeException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.profiling.Tracing;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Implementation of {@link AuctionServerPayloadFormatter} and {@link AuctionServerPayloadExtractor}
 * that pads up to the seller max size.
 */
public final class AuctionServerPayloadFormatterExactSize
        implements AuctionServerPayloadFormatter, AuctionServerPayloadExtractor {
    public static final int VERSION = 2;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static final String DATA_SIZE_MISMATCH =
            "Data size extracted from padded bytes is longer than the rest of the data";

    public static final String DATA_SIZE_LARGER_THAN_TARGET =
            "Data size is larger than the target size";

    private final int mTargetPayloadSizeBytes;

    AuctionServerPayloadFormatterExactSize(int targetPayloadSizeBytes) {
        mTargetPayloadSizeBytes = targetPayloadSizeBytes;
    }

    @Override
    public AuctionServerPayloadFormattedData apply(
            AuctionServerPayloadUnformattedData unformattedData, int compressorVersion) {
        Objects.requireNonNull(unformattedData);

        int traceCookie = Tracing.beginAsyncSection(Tracing.FORMAT_PAYLOAD_EXACT_SIZE);

        byte[] data = unformattedData.getData();

        int lengthWithMetadataBytes =
                META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + data.length;

        if (lengthWithMetadataBytes > mTargetPayloadSizeBytes) {
            throw new UnsupportedPayloadSizeException(
                    lengthWithMetadataBytes / BYTES_CONVERSION_FACTOR,
                    DATA_SIZE_LARGER_THAN_TARGET);
        }

        // Empty payload to fill in
        byte[] payload = new byte[mTargetPayloadSizeBytes];

        // Fill in
        payload[0] = getMetaInfoByte(compressorVersion, VERSION);
        sLogger.v("Meta info byte added: %d", payload[0]);

        byte[] dataSizeBytes =
                ByteBuffer.allocate(DATA_SIZE_PADDING_LENGTH_BYTE).putInt(data.length).array();
        System.arraycopy(
                dataSizeBytes,
                /* srcPos= */ 0,
                payload,
                META_INFO_LENGTH_BYTE,
                DATA_SIZE_PADDING_LENGTH_BYTE);
        sLogger.v(
                "Data size bytes are added: %s for size: %d",
                Arrays.toString(dataSizeBytes), data.length);
        System.arraycopy(
                data,
                /* srcPos= */ 0,
                payload,
                META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE,
                data.length);

        AuctionServerPayloadFormattedData formattedData =
                AuctionServerPayloadFormattedData.create(payload);
        Tracing.endAsyncSection(Tracing.FORMAT_PAYLOAD_EXACT_SIZE, traceCookie);
        return formattedData;
    }

    @Override
    public AuctionServerPayloadUnformattedData extract(
            AuctionServerPayloadFormattedData formattedData) {
        byte[] payload = formattedData.getData();

        // Next 4 bytes encode the size of the data
        byte[] sizeBytes = new byte[DATA_SIZE_PADDING_LENGTH_BYTE];
        System.arraycopy(
                payload,
                META_INFO_LENGTH_BYTE,
                sizeBytes,
                /* destPos= */ 0,
                DATA_SIZE_PADDING_LENGTH_BYTE);
        int dataSize = ByteBuffer.wrap(sizeBytes).getInt();

        if (META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE + dataSize > payload.length) {
            sLogger.e(DATA_SIZE_MISMATCH);
            throw new IllegalArgumentException(DATA_SIZE_MISMATCH);
        }

        // Extract the data
        byte[] data = new byte[dataSize];
        System.arraycopy(
                payload,
                META_INFO_LENGTH_BYTE + DATA_SIZE_PADDING_LENGTH_BYTE,
                data,
                /* destPos= */ 0,
                dataSize);

        return AuctionServerPayloadUnformattedData.create(data);
    }
}
