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

import static android.adservices.adselection.AuctionEncryptionKeyFixture.AUCTION_KEY_1;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION_TTL_1SECS;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN_TTL_1SECS;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.RESPONSE_PROPERTIES_CONTENT_TYPE;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AuctionEncryptionKeyFixture;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.FetchProcessLogger;
import com.android.adservices.service.stats.ServerAuctionKeyFetchCalledStats;
import com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerImpl;
import com.android.adservices.shared.testing.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class AdSelectionEncryptionKeyManagerTest {
    private static final Long EXPIRY_TTL_1SEC = 1L;

    private static final int TIMEOUT_MS = 500;

    private static final String AUCTION_KEY_FETCH_URI = "https://foo.bar/auctionkey";
    private static final String JOIN_KEY_FETCH_URI = "https://foo.bar/joinkey";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock private AdServicesHttpsClient mMockHttpClient;
    @Spy private Clock mClock = Clock.systemUTC();
    private EncryptionKeyDao mEncryptionKeyDao;
    private Flags mFlags = new AdSelectionEncryptionKeyManagerTestFlags();
    private AdServicesLogger mAdServicesLoggerSpy = Mockito.spy(AdServicesLoggerImpl.getInstance());
    private com.android.adservices.shared.util.Clock mLoggerClock =
            com.android.adservices.shared.util.Clock.getInstance();
    private FetchProcessLogger mKeyFetchLogger =
            Mockito.spy(
                    new ServerAuctionKeyFetchExecutionLoggerImpl(
                            mLoggerClock, mAdServicesLoggerSpy));

    private ExecutorService mLightweightExecutor;
    private AuctionEncryptionKeyParser mAuctionEncryptionKeyParser =
            new AuctionEncryptionKeyParser(mFlags);
    private JoinEncryptionKeyParser mJoinEncryptionKeyParser = new JoinEncryptionKeyParser(mFlags);
    private AdSelectionEncryptionKeyManager mKeyManager;
    private DevContext mDevContext;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();

        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mEncryptionKeyDao =
                Room.inMemoryDatabaseBuilder(context, AdSelectionServerDatabase.class)
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
                        mLightweightExecutor,
                        mAdServicesLoggerSpy);

        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(context.getPackageName())
                        .build();
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
                .isEqualTo(
                        BaseEncoding.base16()
                                .lowerCase()
                                .decode(ENCRYPTION_KEY_JOIN.getPublicKey()));
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
    public void test_getAbsentAdSelectionEncryptionKeyTypes() {
        assertThat(mKeyManager.getAbsentAdSelectionEncryptionKeyTypes())
                .containsExactly(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
    }

    @Test
    public void test_getAbsentAdSelectionEncryptionKeyTypes_onlyJoinInDb_AuctionKeyIsMissing() {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_JOIN));

        assertThat(mKeyManager.getAbsentAdSelectionEncryptionKeyTypes())
                .containsExactly(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION);
    }

    @Test
    public void test_getAbsentAdSelectionEncryptionKeyTypes_onlyAuctionInDb_JoinKeyIsMissing() {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));

        assertThat(mKeyManager.getAbsentAdSelectionEncryptionKeyTypes())
                .containsExactly(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN);
    }

    @Test
    public void test_getAbsentAdSelectionEncryptionKeyTypes_bothKeysInDB_nothingIsMissing() {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_JOIN));

        assertThat(mKeyManager.getAbsentAdSelectionEncryptionKeyTypes()).isEmpty();
    }

    @Test
    public void test_fetchAndPersistAuctionKey_fetchSuccess_returnsLatestActiveAuctionKey()
            throws Exception {
        when(mMockHttpClient.fetchPayloadWithLogging(
                        eq(Uri.parse(AUCTION_KEY_FETCH_URI)),
                        eq(mDevContext),
                        any(FetchProcessLogger.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                mDevContext,
                                mKeyFetchLogger)
                        .get();

        assertThat(actualKey).isNotNull();

        verify(mKeyFetchLogger)
                .setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        verify(mKeyFetchLogger).setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
    }

    @Test
    public void test_fetchAndPersistJoinKey_fetchSuccess_returnsLatestActiveJoinKey()
            throws Exception {
        AdServicesHttpClientRequest fetchKeyRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.parse(JOIN_KEY_FETCH_URI))
                        .setRequestProperties(REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setResponseHeaderKeys(RESPONSE_PROPERTIES_CONTENT_TYPE)
                        .setDevContext(mDevContext)
                        .build();
        when(mMockHttpClient.performRequestGetResponseInBase64StringWithLogging(
                        fetchKeyRequest, mKeyFetchLogger))
                .thenReturn(
                        Futures.immediateFuture(
                                JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse()));

        assertThat(
                        mKeyManager.getLatestActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN))
                .isNull();

        AdSelectionEncryptionKey actualKey =
                mKeyManager
                        .fetchPersistAndGetActiveKeyOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                                TIMEOUT_MS,
                                mDevContext,
                                mKeyFetchLogger)
                        .get();

        assertThat(actualKey).isNotNull();

        verify(mKeyFetchLogger)
                .setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
    }

    @Test
    public void test_getLatestOhttpKeyConfig_refreshFlagOn_withExpiredKey_returnsNewKey()
            throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);
        when(mMockHttpClient.fetchPayloadWithLogging(
                        eq(Uri.parse(AUCTION_KEY_FETCH_URI)),
                        eq(mDevContext),
                        any(FetchProcessLogger.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture
                                        .mockAuctionKeyFetchResponseWithOneKey()));

        mKeyManager =
                new AdSelectionEncryptionKeyManager(
                        mEncryptionKeyDao,
                        new RefreshKeysFlagOn(),
                        mClock,
                        mAuctionEncryptionKeyParser,
                        mJoinEncryptionKeyParser,
                        mMockHttpClient,
                        mLightweightExecutor,
                        mAdServicesLoggerSpy);

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null,
                                mDevContext)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION, 2);
        assertThat(keys.size()).isEqualTo(1);
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfig_refreshFlagOff_withExpiredKey_returnsExpiredKey()
            throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);
        when(mMockHttpClient.fetchPayloadWithLogging(
                        eq(Uri.parse(AUCTION_KEY_FETCH_URI)),
                        eq(mDevContext),
                        any(FetchProcessLogger.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture
                                        .mockAuctionKeyFetchResponseWithOneKey()));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null,
                                mDevContext)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder().decode(ENCRYPTION_KEY_AUCTION_TTL_1SECS.getPublicKey());
        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION, 2);
        assertThat(keys.size()).isEqualTo(1);
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_typeAuction_returnsLatestKey() throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null,
                                mDevContext)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_typeAuction_withLogging() throws Exception {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null,
                                mDevContext)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);

        verify(mAdServicesLoggerSpy).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());
        ServerAuctionKeyFetchCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getSource()).isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        assertThat(stats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE);
        assertThat(stats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        assertThat(stats.getNetworkStatusCode()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getNetworkLatencyMillis()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_withExpiredKey_shouldReturnExpiredKey()
            throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null,
                                mDevContext)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION_TTL_1SECS
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void test_getLatestOhttpKeyConfigOfType_withExpiredKey_withLogging() throws Exception {
        ArgumentCaptor<ServerAuctionKeyFetchCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                null,
                                mDevContext)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION_TTL_1SECS
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
        verify(mAdServicesLoggerSpy).logServerAuctionKeyFetchCalledStats(argumentCaptor.capture());
        ServerAuctionKeyFetchCalledStats stats = argumentCaptor.getValue();
        assertThat(stats.getSource()).isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        assertThat(stats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_DATABASE);
        assertThat(stats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        assertThat(stats.getNetworkStatusCode()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getNetworkLatencyMillis()).isEqualTo(FIELD_UNSET);
    }

    @Test
    public void
            test_getLatestActiveOhttpKeyConfig_withExpiredKey_shouldFetchAndPersistAndReturnNewKey()
                    throws Exception {
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);
        when(mMockHttpClient.fetchPayloadWithLogging(
                        eq(Uri.parse(AUCTION_KEY_FETCH_URI)),
                        eq(mDevContext),
                        any(FetchProcessLogger.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture
                                        .mockAuctionKeyFetchResponseWithOneKey()));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestActiveOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                mDevContext,
                                mKeyFetchLogger)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        List<DBEncryptionKey> keys =
                mEncryptionKeyDao.getLatestExpiryNKeysOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION, 2);
        assertThat(keys.size()).isEqualTo(1);
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void
            test_getLatestActiveOhttpKeyConfigOfType_withNoKey_shouldFetchPersistAndReturnNewKey()
                    throws Exception {
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);
        when(mMockHttpClient.fetchPayloadWithLogging(
                        eq(Uri.parse(AUCTION_KEY_FETCH_URI)),
                        eq(mDevContext),
                        any(FetchProcessLogger.class)))
                .thenReturn(
                        Futures.immediateFuture(
                                AuctionEncryptionKeyFixture
                                        .mockAuctionKeyFetchResponseWithOneKey()));

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestActiveOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                mDevContext,
                                mKeyFetchLogger)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(AUCTION_KEY_1.publicKey().getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    @Test
    public void
            test_getLatestActiveOhttpKeyConfigOfType_withActiveAndExpiredKey_shouldGetActiveKey()
                    throws Exception {
        mEncryptionKeyDao.insertAllKeys(
                ImmutableList.of(ENCRYPTION_KEY_AUCTION, ENCRYPTION_KEY_AUCTION_TTL_1SECS));
        addDelayToExpireKeys(EXPIRY_TTL_1SEC);

        ObliviousHttpKeyConfig actualKeyConfig =
                mKeyManager
                        .getLatestActiveOhttpKeyConfigOfType(
                                AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                                TIMEOUT_MS,
                                mDevContext,
                                mKeyFetchLogger)
                        .get();

        byte[] expectedPublicKey =
                Base64.getDecoder()
                        .decode(
                                ENCRYPTION_KEY_AUCTION
                                        .getPublicKey()
                                        .getBytes(StandardCharsets.UTF_8));
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
        assertThat(actualKeyConfig.getPublicKey()).isEqualTo(expectedPublicKey);
    }

    private static class AdSelectionEncryptionKeyManagerTestFlags implements Flags {
        AdSelectionEncryptionKeyManagerTestFlags() {}

        @Override
        public String getFledgeAuctionServerAuctionKeyFetchUri() {
            return AUCTION_KEY_FETCH_URI;
        }

        @Override
        public String getFledgeAuctionServerJoinKeyFetchUri() {
            return JOIN_KEY_FETCH_URI;
        }

        @Override
        public int getFledgeAuctionServerAuctionKeySharding() {
            return 5;
        }

        @Override
        public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
            return EXPIRY_TTL_1SEC;
        }

        @Override
        public boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
            return true;
        }
    }

    private void addDelayToExpireKeys(long delaySeconds) {
        when(mClock.instant()).thenReturn(Clock.systemUTC().instant().plusSeconds(delaySeconds));
    }

    private static class RefreshKeysFlagOn extends AdSelectionEncryptionKeyManagerTestFlags {

        @Override
        public boolean getFledgeAuctionServerRefreshExpiredKeysDuringAuction() {
            return true;
        }
    }
}
