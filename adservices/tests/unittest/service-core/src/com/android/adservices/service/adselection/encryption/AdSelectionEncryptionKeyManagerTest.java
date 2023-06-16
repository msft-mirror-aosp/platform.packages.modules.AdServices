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
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION_TTL_1SECS;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN_TTL_1SECS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.adservices.adselection.AuctionEncryptionKeyFixture;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.concurrent.ExecutorService;

public class AdSelectionEncryptionKeyManagerTest {
    private static final Long EXPIRY_TTL_1SEC = 1L;

    private static final String AUCTION_KEY_FETCH_URI = "https://foo.bar/auctionkey";
    private static final String JOIN_KEY_FETCH_URI = "https://foo.bar/joinkey";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock private AdServicesHttpsClient mMockHttpClient;
    @Spy private Clock mClock = Clock.systemUTC();
    private EncryptionKeyDao mEncryptionKeyDao;
    private Flags mFlags = new AdSelectionEncryptionKeyManagerTestFlags();

    private ExecutorService mLightweightExecutor;
    private AuctionEncryptionKeyParser mAuctionEncryptionKeyParser =
            new AuctionEncryptionKeyParser(mFlags);
    private JoinEncryptionKeyParser mJoinEncryptionKeyParser = new JoinEncryptionKeyParser(mFlags);
    private AdSelectionEncryptionKeyManager mKeyManager;

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mEncryptionKeyDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .encryptionKeyDao();
        mKeyManager =
                new AdSelectionEncryptionKeyManager(
                        mEncryptionKeyDao,
                        mFlags,
                        mClock,
                        mAuctionEncryptionKeyParser,
                        mJoinEncryptionKeyParser,
                        mMockHttpClient,
                        mLightweightExecutor);
    }

    @Test
    public void test_getLatestActiveJoinKey_noJoinKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestActiveAuctionKey_noAuctionKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();
    }

    @Test
    public void test_getLatestActiveJoinKey_noActiveJoinKey_returnsNull() throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_JOIN_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestActiveAuctionKey_noActiveAuctionKey_returnsNull() throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_JOIN_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();
    }

    @Test
    public void test_getLatestActiveJoinKey_returnsActiveJoinKey() throws Exception {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_JOIN_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestActiveKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
        assertThat(actualKey).isNotNull();
        assertThat(actualKey.keyIdentifier()).isEqualTo(ENCRYPTION_KEY_JOIN.getKeyIdentifier());
        assertThat(actualKey.publicKey())
                .isEqualTo(ENCRYPTION_KEY_JOIN.getPublicKey().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void test_getLatestActiveAuctionKey_returnsActiveAuctionKey()
            throws InterruptedException {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_AUCTION_TTL_1SECS));

        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestActiveKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);
        assertThat(actualKey).isNotNull();
        assertThat(actualKey.keyIdentifier()).isEqualTo(ENCRYPTION_KEY_AUCTION.getKeyIdentifier());
        assertThat(actualKey.publicKey())
                .isEqualTo(
                        Base64.getDecoder()
                                .decode(
                                        ENCRYPTION_KEY_AUCTION
                                                .getPublicKey()
                                                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void test_getLatestJoinKey_noJoinKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();
    }

    @Test
    public void test_getLatestAuctionKey_noAuctionKey_returnsNull() {
        assertThat(
                        mKeyManager.getLatestKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();
    }

    @Test
    public void test_getLatestJoinKey_returnsJoinKey() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_JOIN, ENCRYPTION_KEY_JOIN_TTL_1SECS));

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_getLatestAuctionKey_returnsAuctionKey() {
        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_AUCTION_TTL_1SECS));

        AdSelectionEncryptionKey actualKey =
                mKeyManager.getLatestKeyOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);
        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_fetchAndPersistAuctionKey_fetchSuccess_returnsLatestActiveAuctionKey()
            throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(AUCTION_KEY_FETCH_URI)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager.fetchAndPersistActiveKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);

        assertThat(actualKey).isNotNull();
    }

    @Test
    public void test_fetchAndPersistJoinKey_fetchSuccess_returnsLatestActiveJoinKey()
            throws Exception {
        when(mMockHttpClient.fetchPayload(Uri.parse(JOIN_KEY_FETCH_URI)))
                .thenReturn(
                        Futures.immediateFuture(
                                JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager.fetchAndPersistActiveKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);

        assertThat(actualKey).isNotNull();
    }

    private static class AdSelectionEncryptionKeyManagerTestFlags implements Flags {
        AdSelectionEncryptionKeyManagerTestFlags() {}

        @Override
        public String getAdSelectionDataAuctionKeyFetchUri() {
            return AUCTION_KEY_FETCH_URI;
        }

        @Override
        public String getAdSelectionDataJoinKeyFetchUri() {
            return JOIN_KEY_FETCH_URI;
        }

        @Override
        public int getAdSelectionDataAuctionKeySharding() {
            return 5;
        }

        @Override
        public long getAdSelectionDataEncryptionKeyMaxAgeSeconds() {
            return EXPIRY_TTL_1SEC;
        }
    }

    private void addDelayToExpireKeys(long delaySeconds) {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant().plusSeconds(delaySeconds));
    }
}
