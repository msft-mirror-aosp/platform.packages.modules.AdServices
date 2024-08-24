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

package com.android.adservices.service.common.httpclient;

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_NETWORK_FAILURE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_SUCCESS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_TIMEOUT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.ENCODING_FETCH_STATUS_UNSET;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.JS_DOWNLOAD_LATENCY_BUCKETS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.computeSize;
import static com.android.adservices.service.stats.EncodingJsFetchProcessLoggerImplTest.TEST_AD_TECH_ID;
import static com.android.adservices.service.stats.EncodingJsFetchProcessLoggerImplTest.TEST_JS_DOWNLOAD_END_TIMESTAMP;
import static com.android.adservices.service.stats.EncodingJsFetchProcessLoggerImplTest.TEST_JS_DOWNLOAD_START_TIMESTAMP;
import static com.android.adservices.service.stats.EncodingJsFetchProcessLoggerImplTest.TEST_JS_DOWNLOAD_TIME;
import static com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerImplTest.KEY_FETCH_NETWORK_END_TIMESTAMP;
import static com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerImplTest.KEY_FETCH_NETWORK_LATENCY_MS;
import static com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerImplTest.KEY_FETCH_NETWORK_START_TIMESTAMP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.exceptions.AdServicesNetworkException;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import androidx.room.Room;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.common.cache.CacheDatabase;
import com.android.adservices.service.common.cache.CacheEntryDao;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.FetchProcessLogger;
import com.android.adservices.service.stats.ServerAuctionKeyFetchCalledStats;
import com.android.adservices.service.stats.ServerAuctionKeyFetchExecutionLoggerImpl;
import com.android.adservices.service.stats.pas.EncodingFetchStats;
import com.android.adservices.service.stats.pas.EncodingJsFetchProcessLoggerImpl;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ProtectedAudienceAdServicesHttpsClientTest extends AdServicesMockitoTestCase {
    private static final String CACHE_HEADER = "Cache-Control: max-age=60";
    private static final String RESPONSE_HEADER_KEY = "fake_response_header_key";
    private static final String RESPONSE_HEADER_VALUE_1 = "fake_response_header_value_1";
    private static final String RESPONSE_HEADER_VALUE_2 = "fake_response_header_value_2";
    private static final long MAX_AGE_SECONDS = 120;
    private static final long MAX_ENTRIES = 20;
    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();

    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private final String mJsScript = "function test() { return \"hello world\"; }";
    private final String mFetchPayloadPath = "/fetchPayload/";
    private final String mFakeUrl = "https://fakeprivacysandboxdomain.never/this/is/a/fake";
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private AdServicesHttpsClient mClient;
    @Mock private Clock mMockClock;
    private HttpCache mCache;
    private AdServicesLogger mAdServicesLoggerSpy;
    private ArgumentCaptor<EncodingFetchStats> mEncodingJsFetchStatsArgumentCaptor;
    private ArgumentCaptor<ServerAuctionKeyFetchCalledStats>
            mServerAuctionKeyFetchCalledStatsArgumentCaptor;
    private FetchProcessLogger mFetchProcessLogger;

    @Before
    public void setup() throws Exception {
        CacheEntryDao cacheEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();

        mCache = new FledgeHttpCache(cacheEntryDao, MAX_AGE_SECONDS, MAX_ENTRIES);
        mClient = new AdServicesHttpsClient(mExecutorService, mCache);
    }

    @Test
    public void testFetchPayloadResponsesUsesCache() throws Exception {
        setupEncodingJsFetchStatsLogging();

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER)
                                        .addHeader(RESPONSE_HEADER_KEY, RESPONSE_HEADER_VALUE_1)
                                        .addHeader(RESPONSE_HEADER_KEY, RESPONSE_HEADER_VALUE_2);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        mClient.fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(Uri.parse(url.toString()))
                                .setUseCache(true)
                                .setResponseHeaderKeys(ImmutableSet.of(RESPONSE_HEADER_KEY))
                                .setDevContext(DEV_CONTEXT_DISABLED)
                                .build())
                .get();
        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());
        Thread.sleep(500);
        // Flake proofing : In rare but possible scenario where the cache is not done persisting, we
        // will get cache miss, no point asserting further
        assumeTrue(mCache.getCachedEntriesCount() == 1);
        AdServicesHttpClientResponse response =
                mClient.fetchPayloadWithLogging(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(Uri.parse(url.toString()))
                                        .setUseCache(true)
                                        .setResponseHeaderKeys(ImmutableSet.of(RESPONSE_HEADER_KEY))
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build(),
                                mFetchProcessLogger)
                        .get();
        assertEquals(mJsScript, response.getResponseBody());
        assertTrue(
                response.getResponseHeaders()
                        .get(RESPONSE_HEADER_KEY)
                        .contains(RESPONSE_HEADER_VALUE_1));
        assertTrue(
                response.getResponseHeaders()
                        .get(RESPONSE_HEADER_KEY)
                        .contains(RESPONSE_HEADER_VALUE_2));
        assertEquals(
                "Only one header should have been cached", 1, response.getResponseHeaders().size());
        assertEquals("This call should have been cached", 1, server.getRequestCount());

        // Verify the logging of EncodingFetchStats
        verifyEncodingJsFetchStatsLogging(ENCODING_FETCH_STATUS_SUCCESS);
    }

    @Test
    public void testFetchPayloadSuccessfulResponseWithEncodingJsFetchLogging() throws Exception {
        setupEncodingJsFetchStatsLogging();

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                fetchPayloadWithEncodingJsFetchLogging(
                        Uri.parse(url.toString()), DEV_CONTEXT_DISABLED, mFetchProcessLogger);
        assertEquals(mJsScript, result.getResponseBody());

        // Verify the logging of EncodingFetchStats
        verifyEncodingJsFetchStatsLogging(ENCODING_FETCH_STATUS_SUCCESS);
    }

    @Test
    public void testFetchPayloadFailedResponseWithEncodingJsFetchLogging() throws Exception {
        setupEncodingJsFetchStatsLogging();

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                fetchPayloadWithEncodingJsFetchLogging(
                                        Uri.parse(url.toString()),
                                        DEV_CONTEXT_DISABLED,
                                        mFetchProcessLogger));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);

        // Verify the logging of EncodingFetchStats
        verifyEncodingJsFetchStatsLogging(ENCODING_FETCH_STATUS_NETWORK_FAILURE);
    }

    @Test
    public void testFetchPayloadDomainDoesNotExistWithEncodingJsFetchLogging() throws Exception {
        setupEncodingJsFetchStatsLogging();

        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                fetchPayloadWithEncodingJsFetchLogging(
                                        Uri.parse(mFakeUrl),
                                        DEV_CONTEXT_DISABLED,
                                        mFetchProcessLogger));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);

        // Verify the logging of EncodingFetchStats
        verifyEncodingJsFetchStatsLogging(ENCODING_FETCH_STATUS_UNSET);
    }

    @Test
    public void testThrowsIOExceptionWhenConnectionTimesOutWithEncodingJsFetchLogging()
            throws Exception {
        setupEncodingJsFetchStatsLogging();

        int timeoutDeltaMs = 1000;
        int bytesPerPeriod = 1;
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                new MockResponse()
                                        .setBody(mJsScript)
                                        .throttleBody(
                                                bytesPerPeriod,
                                                mClient.getConnectTimeoutMs()
                                                        + mClient.getReadTimeoutMs()
                                                        + timeoutDeltaMs,
                                                TimeUnit.MILLISECONDS)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                fetchPayloadWithEncodingJsFetchLogging(
                                        Uri.parse(url.toString()),
                                        DEV_CONTEXT_DISABLED,
                                        mFetchProcessLogger));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);

        // Verify the logging of EncodingFetchStats
        verifyEncodingJsFetchStatsLogging(ENCODING_FETCH_STATUS_TIMEOUT);
    }

    @Test
    public void testFetchPayloadSuccessfulResponseWithServerAuctionKeyFetchLogging()
            throws Exception {
        setupServerAuctionKeyFetchCalledStatsLogging();

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                mClient.fetchPayloadWithLogging(
                                Uri.parse(url.toString()),
                                DEV_CONTEXT_DISABLED,
                                mFetchProcessLogger)
                        .get();
        assertEquals(mJsScript, result.getResponseBody());

        // Verify the logging of EncodingFetchStats
        verifyServerAuctionKeyFetchCalledStatsLogging(200);
    }

    @Test
    public void testPerformRequestAndGetResponseInBytesWithServerAuctionKeyFetchLogging()
            throws Exception {
        setupServerAuctionKeyFetchCalledStatsLogging();

        byte[] byteResponse = {1, 2, 3, 54};
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(byteResponse)));
        String reportingPath = "/reporting/";
        URL url = server.getUrl(reportingPath);
        byte[] postedBodyInBytes = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBodyInBytes)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                        .build();

        AdServicesHttpClientResponse response =
                mClient.performRequestGetResponseInBase64StringWithLogging(
                                request, mFetchProcessLogger)
                        .get();

        String expectedResponseString = BaseEncoding.base64().encode(byteResponse);
        assertThat(response.getResponseBody()).isEqualTo(expectedResponseString);

        // Verify the logging of EncodingFetchStats
        verifyServerAuctionKeyFetchCalledStatsLogging(200);
    }

    private AdServicesHttpClientResponse fetchPayloadWithEncodingJsFetchLogging(
            Uri uri, DevContext devContext, FetchProcessLogger logger) throws Exception {
        return mClient.fetchPayloadWithLogging(
                        AdServicesHttpClientRequest.builder()
                                .setUri(uri)
                                .setDevContext(devContext)
                                .build(),
                        logger)
                .get();
    }

    private void setupEncodingJsFetchStatsLogging() {
        mAdServicesLoggerSpy = Mockito.spy(AdServicesLoggerImpl.getInstance());
        mEncodingJsFetchStatsArgumentCaptor = ArgumentCaptor.forClass(EncodingFetchStats.class);

        when(mMockClock.currentTimeMillis())
                .thenReturn(TEST_JS_DOWNLOAD_START_TIMESTAMP, TEST_JS_DOWNLOAD_END_TIMESTAMP);
        EncodingFetchStats.Builder encodingJsFetchStatsBuilder = EncodingFetchStats.builder();
        mFetchProcessLogger =
                new EncodingJsFetchProcessLoggerImpl(
                        mAdServicesLoggerSpy, mMockClock, encodingJsFetchStatsBuilder);
        mFetchProcessLogger.setJsDownloadStartTimestamp(mMockClock.currentTimeMillis());
        mFetchProcessLogger.setAdTechId(TEST_AD_TECH_ID);
    }

    private void verifyEncodingJsFetchStatsLogging(int statusCode) {
        verify(mAdServicesLoggerSpy)
                .logEncodingJsFetchStats(mEncodingJsFetchStatsArgumentCaptor.capture());

        EncodingFetchStats stats = mEncodingJsFetchStatsArgumentCaptor.getValue();
        assertThat(stats.getFetchStatus()).isEqualTo(statusCode);
        assertThat(stats.getAdTechId()).isEqualTo(TEST_AD_TECH_ID);
        assertThat(stats.getHttpResponseCode()).isEqualTo(FIELD_UNSET);
        assertThat(stats.getJsDownloadTime())
                .isEqualTo(computeSize(TEST_JS_DOWNLOAD_TIME, JS_DOWNLOAD_LATENCY_BUCKETS));
    }

    private void setupServerAuctionKeyFetchCalledStatsLogging() {
        mAdServicesLoggerSpy = Mockito.spy(AdServicesLoggerImpl.getInstance());
        mServerAuctionKeyFetchCalledStatsArgumentCaptor =
                ArgumentCaptor.forClass(ServerAuctionKeyFetchCalledStats.class);

        when(mMockClock.elapsedRealtime())
                .thenReturn(KEY_FETCH_NETWORK_START_TIMESTAMP, KEY_FETCH_NETWORK_END_TIMESTAMP);
        mFetchProcessLogger =
                new ServerAuctionKeyFetchExecutionLoggerImpl(mMockClock, mAdServicesLoggerSpy);
        mFetchProcessLogger.setSource(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        mFetchProcessLogger.setEncryptionKeySource(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        mFetchProcessLogger.setCoordinatorSource(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
    }

    private void verifyServerAuctionKeyFetchCalledStatsLogging(int statusCode) {
        verify(mAdServicesLoggerSpy)
                .logServerAuctionKeyFetchCalledStats(
                        mServerAuctionKeyFetchCalledStatsArgumentCaptor.capture());

        ServerAuctionKeyFetchCalledStats stats =
                mServerAuctionKeyFetchCalledStatsArgumentCaptor.getValue();
        assertThat(stats.getSource()).isEqualTo(SERVER_AUCTION_KEY_FETCH_SOURCE_AUCTION);
        assertThat(stats.getEncryptionKeySource())
                .isEqualTo(SERVER_AUCTION_ENCRYPTION_KEY_SOURCE_NETWORK);
        assertThat(stats.getCoordinatorSource())
                .isEqualTo(SERVER_AUCTION_COORDINATOR_SOURCE_DEFAULT);
        assertThat(stats.getNetworkStatusCode()).isEqualTo(statusCode);
        assertThat(stats.getNetworkLatencyMillis()).isEqualTo(KEY_FETCH_NETWORK_LATENCY_MS);
    }
}
