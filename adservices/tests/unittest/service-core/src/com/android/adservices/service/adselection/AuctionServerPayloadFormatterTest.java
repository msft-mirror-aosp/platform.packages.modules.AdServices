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

import static com.android.adservices.service.adselection.AuctionServerPayloadFormatter.COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS;
import static com.android.adservices.service.adselection.AuctionServerPayloadFormatter.PAYLOAD_FORMAT_VERSION_LENGTH_BITS;

import com.android.adservices.service.Flags;

import org.junit.Assert;
import org.junit.Test;

public class AuctionServerPayloadFormatterTest {
    @Test
    public void testMetaInfoByte() {
        for (int compressionVersion = 0;
                compressionVersion < (1 << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS);
                compressionVersion++) {
            for (int formatterVersion = 0;
                    formatterVersion < (1 << PAYLOAD_FORMAT_VERSION_LENGTH_BITS);
                    formatterVersion++) {
                byte metaInfoByte =
                        AuctionServerPayloadFormatter.getMetaInfoByte(
                                compressionVersion, formatterVersion);
                Assert.assertEquals(
                        compressionVersion,
                        AuctionServerPayloadFormatter.extractCompressionVersion(metaInfoByte));
                Assert.assertEquals(
                        formatterVersion,
                        AuctionServerPayloadFormatter.extractFormatterVersion(metaInfoByte));
            }
        }
    }

    @Test
    public void testInvalidInputs() {
        int validVersion = 0;
        int invalidCompressionVersion = (1 << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS);
        int invalidFormatterVersion = (1 << PAYLOAD_FORMAT_VERSION_LENGTH_BITS);
        int invalidNegativeVersion = -1;

        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        AuctionServerPayloadFormatter.getMetaInfoByte(
                                invalidCompressionVersion, validVersion));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        AuctionServerPayloadFormatter.getMetaInfoByte(
                                validVersion, invalidFormatterVersion));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        AuctionServerPayloadFormatter.getMetaInfoByte(
                                invalidNegativeVersion, validVersion));
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        AuctionServerPayloadFormatter.getMetaInfoByte(
                                validVersion, invalidNegativeVersion));
    }

    @Test
    public void testCurrentVersionFromFlagsAreValid() {
        Flags flags = new AuctionServerPayloadFormatterTestFlags();

        Assert.assertTrue(
                flags.getFledgeAuctionServerCompressionAlgorithmVersion()
                        < (1 << COMPRESSION_ALGORITHM_VERSION_LENGTH_BITS));
        Assert.assertTrue(flags.getFledgeAuctionServerCompressionAlgorithmVersion() >= 0);
        Assert.assertTrue(
                flags.getFledgeAuctionServerPayloadFormatVersion()
                        < (1 << PAYLOAD_FORMAT_VERSION_LENGTH_BITS));
        Assert.assertTrue(flags.getFledgeAuctionServerPayloadFormatVersion() >= 0);
    }

    public static class AuctionServerPayloadFormatterTestFlags implements Flags {
        @Override
        public int getFledgeAuctionServerCompressionAlgorithmVersion() {
            return FLEDGE_AUCTION_SERVER_COMPRESSION_ALGORITHM_VERSION;
        }

        @Override
        public int getFledgeAuctionServerPayloadFormatVersion() {
            return FLEDGE_AUCTION_SERVER_PAYLOAD_FORMAT_VERSION;
        }
    }
}
