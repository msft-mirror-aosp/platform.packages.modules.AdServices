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

package com.android.adservices.service.adselection.encryption;

import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.DEFAULT_JOIN_HEADERS;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.JOIN_PUBLIC_KEY_1;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class JoinEncryptionKeyParserTest {
    private static final long DEFAULT_MAX_AGE_SECONDS = 100L;

    private Flags mFlags = new JoinEncryptionKeyParserTestFlags();
    private JoinEncryptionKeyParser mJoinEncryptionKeyParser;

    @Before
    public void setUp() {
        mJoinEncryptionKeyParser = new JoinEncryptionKeyParser(mFlags);
    }

    @Test
    public void parseDbEncryptionKey_nullDbEncryptionKey_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> mJoinEncryptionKeyParser.parseDbEncryptionKey(null));
    }

    @Test
    public void parseDbEncryptionKey_wrongKeyType_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mJoinEncryptionKeyParser.parseDbEncryptionKey(ENCRYPTION_KEY_AUCTION));
    }

    @Test
    public void parseDbEncryptionKey_returnsSuccess() {
        assertThat(mJoinEncryptionKeyParser.parseDbEncryptionKey(ENCRYPTION_KEY_JOIN))
                .isEqualTo(
                        AdSelectionEncryptionKey.builder()
                                .setKeyType(
                                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN)
                                .setKeyIdentifier(ENCRYPTION_KEY_JOIN.getKeyIdentifier())
                                .setPublicKey(
                                        ENCRYPTION_KEY_JOIN
                                                .getPublicKey()
                                                .getBytes(StandardCharsets.UTF_8))
                                .build());
    }

    @Test
    public void getDbEncryptionKeys_missingHeaders_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mJoinEncryptionKeyParser.getDbEncryptionKeys(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody("")
                                        .build()));
    }

    @Test
    public void getDbEncryptionKeys_emptyResponseBody_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mJoinEncryptionKeyParser.getDbEncryptionKeys(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody("")
                                        .setResponseHeaders(DEFAULT_JOIN_HEADERS)
                                        .build()));
    }

    @Test
    public void getDbEncryptionKeys_missingMaxAge_usesDefaultAge() throws JSONException {
        List<DBEncryptionKey> keys =
                mJoinEncryptionKeyParser.getDbEncryptionKeys(mockJoinKeyFetchResponse());

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getKeyIdentifier()).isEqualTo("0");
        assertThat(keys.get(0).getPublicKey()).isEqualTo(JOIN_PUBLIC_KEY_1);
        assertThat(keys.get(0).getEncryptionKeyType())
                .isEqualTo(EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN);
        assertThat(keys.get(0).getExpiryTtlSeconds()).isEqualTo(DEFAULT_MAX_AGE_SECONDS);
    }

    private static class JoinEncryptionKeyParserTestFlags implements Flags {
        JoinEncryptionKeyParserTestFlags() {}

        @Override
        public long getAdSelectionDataEncryptionKeyMaxAgeSeconds() {
            return DEFAULT_MAX_AGE_SECONDS;
        }
    }
}
