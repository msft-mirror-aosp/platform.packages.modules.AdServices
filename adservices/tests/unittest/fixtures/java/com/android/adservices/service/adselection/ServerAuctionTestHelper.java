/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.adservices.adselection.AuctionEncryptionKeyFixture;
import android.adservices.adselection.GetAdSelectionDataResponse;

import com.android.adservices.ohttp.ObliviousHttpGateway;
import com.android.adservices.ohttp.OhttpGatewayPrivateKey;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Helper class for testing server auction. Primarily does server side decryption of
 * getAdSelectionData response, server side encryption of AuctionResult which can then be decrypted
 * by the client via persistAdSelectionResult and a few other utility methods.
 */
public class ServerAuctionTestHelper {

    private static final String DEFAULT_PRIVATE_KEY_HEX =
            "e7b292f49df28b8065992cdeadbc9d032a0e09e8476cb6d8d507212e7be3b9b4";
    private static final String DEFAULT_PUBLIC_KEY_HEX =
            "87ey8XZPXAd+/+ytKv2GFUWW5j9zdepSJ2G4gebDwyM=";
    private static final String DEFAULT_KEY_ID_HEX = "400bed24-c62f-46e0-a1ad-211361ad771a";
    private static final int DEFAULT_COMPRESSION_ALGORITHM_VERSION = 2;
    private static final int DEFAULT_PAYLOAD_FORMAT_VERSION = 0;
    private static final ImmutableList<Integer> DEFAULT_PAYLOAD_BUCKET_SIZES =
            ImmutableList.of(0, 1024, 2048, 4096, 8192, 16384, 32768, 65536);

    private final OhttpGatewayPrivateKey mPrivateKey;
    public final AuctionEncryptionKeyFixture.AuctionKey mAuctionKey;
    private final AuctionServerDataCompressor mAuctionServerDataCompressor;
    private final AuctionServerPayloadFormatter mAuctionServerPayloadFormatter;
    private final AuctionServerPayloadExtractor mAuctionServerPayloadExtractor;

    /**
     * Gets an instance of the helper with default public/private key pair and default compressor
     * and payload formatter versions
     */
    public static ServerAuctionTestHelper getDefaultInstance(AdServicesLogger adServicesLogger) {
        return new ServerAuctionTestHelper(
                DEFAULT_KEY_ID_HEX,
                DEFAULT_PRIVATE_KEY_HEX,
                DEFAULT_PUBLIC_KEY_HEX,
                DEFAULT_PAYLOAD_BUCKET_SIZES,
                DEFAULT_PAYLOAD_FORMAT_VERSION,
                DEFAULT_COMPRESSION_ALGORITHM_VERSION,
                adServicesLogger);
    }

    public ServerAuctionTestHelper(
            String idHex,
            String privateKeyHex,
            String publicKeyHex,
            ImmutableList<Integer> payloadBucketSizes,
            int payloadFormatterVersion,
            int compressorVersion,
            AdServicesLogger adServicesLogger) {
        mPrivateKey =
                OhttpGatewayPrivateKey.create(
                        BaseEncoding.base16().lowerCase().decode(privateKeyHex));
        mAuctionKey =
                AuctionEncryptionKeyFixture.AuctionKey.builder()
                        .setKeyId(idHex)
                        .setPublicKey(publicKeyHex)
                        .build();
        mAuctionServerDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(compressorVersion);
        mAuctionServerPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        payloadFormatterVersion,
                        payloadBucketSizes,
                        /* sellerConfiguration= */ null);
        mAuctionServerPayloadExtractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        payloadFormatterVersion, adServicesLogger);
    }

    /** Get an HTTP response with the public auction key */
    public AdServicesHttpClientResponse getPublicAuctionKeyHttpResponse() throws JSONException {
        return AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(mAuctionKey);
    }

    /** Get a map of decompressed buyer inputs from a protected auction input */
    public Map<String, BiddingAuctionServers.BuyerInput> getDecompressedBuyerInputs(
            BiddingAuctionServers.ProtectedAuctionInput protectedAuctionInput) throws Exception {
        Map<String, BiddingAuctionServers.BuyerInput> decompressedBuyerInputs = new HashMap<>();
        for (Map.Entry<String, ByteString> entry :
                protectedAuctionInput.getBuyerInputMap().entrySet()) {
            byte[] buyerInputBytes = entry.getValue().toByteArray();
            byte[] decompressed =
                    mAuctionServerDataCompressor
                            .decompress(
                                    AuctionServerDataCompressor.CompressedData.create(
                                            buyerInputBytes))
                            .getData();
            decompressedBuyerInputs.put(
                    entry.getKey(), BiddingAuctionServers.BuyerInput.parseFrom(decompressed));
        }
        return decompressedBuyerInputs;
    }

    /** Get a protected auction input from the encrypted ad selection data */
    public BiddingAuctionServers.ProtectedAuctionInput decryptAdSelectionData(
            byte[] adSelectionData) throws Exception {
        byte[] decrypted =
                ObliviousHttpGateway.decrypt(mPrivateKey, Objects.requireNonNull(adSelectionData));
        AuctionServerPayloadUnformattedData unformatted =
                mAuctionServerPayloadExtractor.extract(
                        AuctionServerPayloadFormattedData.create(decrypted));
        return BiddingAuctionServers.ProtectedAuctionInput.parseFrom(unformatted.getData());
    }

    /**
     * Returns an encrypted server response based on the output of getAdSelectionData() and
     * unencrypted auction result bytes
     */
    public byte[] encryptServerAuctionResult(
            GetAdSelectionDataResponse adSelectionData,
            BiddingAuctionServers.AuctionResult auctionResult)
            throws UnsupportedHpkeAlgorithmException, IOException {
        return ObliviousHttpGateway.encrypt(
                mPrivateKey,
                Objects.requireNonNull(adSelectionData.getAdSelectionData()),
                prepareAuctionResultBytes(auctionResult));
    }

    private byte[] prepareAuctionResultBytes(BiddingAuctionServers.AuctionResult auctionResult) {
        byte[] auctionResultBytes = auctionResult.toByteArray();
        AuctionServerDataCompressor.CompressedData compressedData =
                mAuctionServerDataCompressor.compress(
                        AuctionServerDataCompressor.UncompressedData.create(auctionResultBytes));
        AuctionServerPayloadFormattedData formattedData =
                mAuctionServerPayloadFormatter.apply(
                        AuctionServerPayloadUnformattedData.create(compressedData.getData()),
                        AuctionServerDataCompressorGzip.VERSION);
        return formattedData.getData();
    }
}
