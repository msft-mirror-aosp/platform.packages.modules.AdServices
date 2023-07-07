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

import static com.android.adservices.service.adselection.AuctionServerPayloadFormatter.MAXIMUM_PAYLOAD_SIZE_IN_BYTES;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormatterV0.PAYLOAD_SIZE_EXCEEDS_LIMIT;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class AuctionServerPayloadFormatterV0Test {
    private static final int VALID_COMPRESSOR_VERSION = 0;
    private static final int EXPECTED_META_INFO_BYTE =
            0; // both formatter and compressor versions are 0
    private static final int DATA_START = 5;
    private AuctionServerPayloadFormatterV0 mAuctionServerPayloadFormatterV0;

    @Parameter public byte[] data;

    @Parameter(1)
    public int expectedSizeInBytes;

    @Parameters
    public static Collection<Object[]> data() {
        return List.of(
                new Object[] {new byte[] {}, 8 /*bytes*/},
                new Object[] {new byte[] {2, 3, 5, 7, 11, 13}, 16 /*bytes*/},
                new Object[] {new byte[] {2, 3, 5, 7, 11, 13, 15, 17, 19, 23, 29}, 16 /*bytes*/},
                new Object[] {
                    new byte[] {2, 3, 5, 7, 11, 13, 15, 17, 19, 23, 29, 31}, 32 /*bytes*/
                },
                new Object[] {new byte[MAXIMUM_PAYLOAD_SIZE_IN_BYTES + 1], -1});
    }

    @Before
    public void setup() {
        mAuctionServerPayloadFormatterV0 = new AuctionServerPayloadFormatterV0();
    }

    @Test
    public void testDataPadding_returnsPaddedData_success() {
        AuctionServerPayloadFormatter.UnformattedData input =
                AuctionServerPayloadFormatter.UnformattedData.create(data);

        if (expectedSizeInBytes > 0) {
            AuctionServerPayloadFormatter.FormattedData formatted =
                    mAuctionServerPayloadFormatterV0.apply(input, VALID_COMPRESSOR_VERSION);
            Assert.assertEquals(EXPECTED_META_INFO_BYTE, formatted.getData()[0]);
            Assert.assertArrayEquals(
                    data,
                    Arrays.copyOfRange(formatted.getData(), DATA_START, DATA_START + data.length));
            AuctionServerPayloadFormatter.UnformattedData unformattedData =
                    mAuctionServerPayloadFormatterV0.extract(formatted);
            Assert.assertArrayEquals(data, unformattedData.getData());
        } else {
            ThrowingRunnable runnable =
                    () -> mAuctionServerPayloadFormatterV0.apply(input, VALID_COMPRESSOR_VERSION);
            Assert.assertThrows(
                    PAYLOAD_SIZE_EXCEEDS_LIMIT, IllegalArgumentException.class, runnable);
        }
    }
}
