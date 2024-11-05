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

import static android.adservices.adselection.SellerConfigurationFixture.SELLER_CONFIGURATION;

import static com.android.adservices.service.adselection.AuctionServerPayloadFormatterFactory.NO_IMPLEMENTATION_FOUND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;

import org.junit.Test;
import org.mockito.Mock;

public class AuctionServerPayloadFormatterFactoryTest {
    private static final int V0_VERSION = AuctionServerPayloadFormatterV0.VERSION;
    private static final int V1_VERSION = AuctionServerPayloadFormatterExcessiveMaxSize.VERSION;
    private static final int V2_VERSION = AuctionServerPayloadFormatterExactSize.VERSION;
    private static final int INVALID_VERSION = Integer.MAX_VALUE;

    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Test
    public void testCreateFormatter_validVersion_returnImplementationSuccess() {
        AuctionServerPayloadFormatter formatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        V0_VERSION,
                        Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES,
                        /* sellerConfiguration= */ null);
        assertTrue(formatter instanceof AuctionServerPayloadFormatterV0);
    }

    @Test
    public void testCreateExtractor_validVersion_returnImplementationSuccess() {
        AuctionServerPayloadExtractor extractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        V0_VERSION, mAdServicesLoggerMock);
        assertTrue(extractor instanceof AuctionServerPayloadFormatterV0);
    }

    @Test
    public void testCreateFormatter_excessiveMaxSizeVersion_returnImplementationSuccess() {
        AuctionServerPayloadFormatter formatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        V1_VERSION,
                        Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES,
                        /* sellerConfiguration= */ null);
        assertThat(formatter).isInstanceOf(AuctionServerPayloadFormatterExcessiveMaxSize.class);
    }

    @Test
    public void
            testCreateFormatter_exactSizeVersion_nullSellerConfiguration_returnExcessiveImplementationSuccess() {
        AuctionServerPayloadFormatter formatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        V2_VERSION,
                        Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES,
                        /* sellerConfiguration= */ null);
        assertThat(formatter).isInstanceOf(AuctionServerPayloadFormatterExcessiveMaxSize.class);
    }

    @Test
    public void
            testCreateFormatter_exactSizeVersion_withSellerConfiguration_returnExactImplementationSuccess() {
        AuctionServerPayloadFormatter formatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        V2_VERSION,
                        Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES,
                        SELLER_CONFIGURATION);
        assertThat(formatter).isInstanceOf(AuctionServerPayloadFormatterExactSize.class);
    }

    @Test
    public void testCreateExtractor_excessiveMaxSizeVersion_returnImplementationSuccess() {
        AuctionServerPayloadExtractor extractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        V1_VERSION, mAdServicesLoggerMock);
        assertTrue(extractor instanceof AuctionServerPayloadFormatterExcessiveMaxSize);
    }

    @Test
    public void testCreateExtractor_exactSizeVersion_returnImplementationSuccess() {
        AuctionServerPayloadExtractor extractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        V2_VERSION, mAdServicesLoggerMock);
        assertTrue(extractor instanceof AuctionServerPayloadFormatterExactSize);
    }

    @Test
    public void testCreateFormatter_invalidVersion_throwsExceptionFailure() {
        assertThrows(
                String.format(
                        NO_IMPLEMENTATION_FOUND,
                        AuctionServerPayloadFormatter.class.getName(),
                        INVALID_VERSION),
                IllegalArgumentException.class,
                () ->
                        AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                                INVALID_VERSION,
                                Flags.FLEDGE_AUCTION_SERVER_PAYLOAD_BUCKET_SIZES,
                                /* sellerConfiguration= */ null));
    }

    @Test
    public void testCreateExtractor_invalidVersion_throwsExceptionFailure() {
        assertThrows(
                String.format(
                        NO_IMPLEMENTATION_FOUND,
                        AuctionServerPayloadExtractor.class.getName(),
                        INVALID_VERSION),
                IllegalArgumentException.class,
                () ->
                        AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                                INVALID_VERSION, mAdServicesLoggerMock));
    }
}
