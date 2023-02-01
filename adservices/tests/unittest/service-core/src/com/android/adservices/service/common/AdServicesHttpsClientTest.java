/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.cache.CacheDatabase;
import com.android.adservices.service.common.cache.CacheEntryDao;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

@SmallTest
public class AdServicesHttpsClientTest {
    @Spy private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String CACHE_HEADER = "Cache-Control: max-age=60";
    private static final String NO_CACHE_HEADER = "Cache-Control: no-cache";
    private static final long MAX_AGE_SECONDS = 120;
    private static final long MAX_ENTRIES = 20;
    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private final String mJsScript = "function test() { return \"hello world\"; }";
    private final String mReportingPath = "/reporting/";
    private final String mFetchPayloadPath = "/fetchPayload/";
    private final String mFakeUrl = "https://fakeprivacysandboxdomain.never/this/is/a/fake";
    private final int mTimeoutDeltaMs = 1000;
    private final int mBytesPerPeriod = 1;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    private AdServicesHttpsClient mClient;
    @Mock private AdServicesHttpsClient.UriConverter mUriConverterMock;
    @Mock private URL mUrlMock;
    @Mock private HttpsURLConnection mURLConnectionMock;
    @Mock private InputStream mInputStreamMock;
    private HttpCache mCache;
    private CacheEntryDao mCacheEntryDao;
    private JSONObject mEventData;

    @Before
    public void setup() throws Exception {
        mCacheEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();

        mCache = new FledgeHttpCache(mCacheEntryDao, MAX_AGE_SECONDS, MAX_ENTRIES);
        mClient = new AdServicesHttpsClient(mExecutorService, mCache);
        mEventData = new JSONObject().put("key", "value");
    }

    @Test
    public void testGetAndReadNothingSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        assertThat(getAndReadNothing(Uri.parse(url.toString()))).isNull();
    }

    @Test
    public void testGetAndReadNothingCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        getAndReadNothing(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testGetAndReadNothingFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mReportingPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> getAndReadNothing(Uri.parse(url.toString())));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testGetAndReadNothingDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class, () -> getAndReadNothing(Uri.parse(mFakeUrl)));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testGetAndReadNothingThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () -> getAndReadNothing(Uri.parse("http://google.com")));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFetchPayloadSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        String result = fetchPayload(Uri.parse(url.toString()));
        assertEquals(mJsScript, result);
    }

    @Test
    public void testFetchPayloadCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);
        fetchPayload(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testFetchPayloadFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class, () -> fetchPayload(Uri.parse(url.toString())));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testFetchPayloadDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(ExecutionException.class, () -> fetchPayload(Uri.parse(mFakeUrl)));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testThrowsIOExceptionWhenConnectionTimesOut() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                new MockResponse()
                                        .setBody(mJsScript)
                                        .throttleBody(
                                                mBytesPerPeriod,
                                                mClient.getConnectTimeoutMs()
                                                        + mClient.getReadTimeoutMs()
                                                        + mTimeoutDeltaMs,
                                                TimeUnit.MILLISECONDS)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class, () -> fetchPayload(Uri.parse(url.toString())));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testFetchPayloadThrowsExceptionIfUsingPlainTextHttp() {
        Exception wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse("http://google.com")));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testInputStreamToStringThrowsExceptionWhenExceedingMaxSize() throws Exception {
        // Creating a client with a max byte size of 5;
        int defaultTimeoutMs = 5000;
        mClient =
                new AdServicesHttpsClient(mExecutorService, defaultTimeoutMs, defaultTimeoutMs, 5);

        // Setting a response of size 6
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody("123456")));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class, () -> fetchPayload(Uri.parse(url.toString())));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testHttpsClientFreesResourcesWhenCancelled() throws Exception {
        // Creating a client with large default limits
        int defaultTimeoutMs = 5000;
        int defaultMaxSizeBytes = 5000;
        int delayMs = 4000;
        long waitForEventualCompletionMs = delayMs * 4L;
        mClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBackgroundExecutor(),
                        defaultTimeoutMs,
                        defaultTimeoutMs,
                        defaultMaxSizeBytes,
                        mUriConverterMock,
                        mCache);

        doReturn(mUrlMock).when(mUriConverterMock).toUrl(any(Uri.class));
        doReturn(mURLConnectionMock).when(mUrlMock).openConnection();
        doReturn(mInputStreamMock).when(mURLConnectionMock).getInputStream();
        doAnswer(new AnswersWithDelay(delayMs, new Returns(202)))
                .when(mURLConnectionMock)
                .getResponseCode();

        ListenableFuture<String> futureResponse = mClient.fetchPayload(Uri.parse((mFakeUrl)));

        // There could be some lag between fetch call and connection opening
        verify(mUrlMock, timeout(delayMs)).openConnection();
        // We cancel the future while the request is going on
        assertTrue(
                "The request should have been ongoing, until being force-cancelled now",
                futureResponse.cancel(true));
        // Given the resources are set to be eventually closed, we add a timeout
        verify(mInputStreamMock, timeout(waitForEventualCompletionMs).atLeast(1)).close();
        verify(mURLConnectionMock, timeout(waitForEventualCompletionMs).atLeast(1)).disconnect();
    }

    @Test
    public void testHttpsClientFreesResourcesInNormalFlow() throws Exception {
        // Creating a client with large default limits
        int defaultTimeoutMs = 5000;
        int defaultMaxSizeBytes = 5000;
        int delayMs = 2000;
        long waitForEventualCompletionMs = delayMs * 4L;
        mClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBackgroundExecutor(),
                        defaultTimeoutMs,
                        defaultTimeoutMs,
                        defaultMaxSizeBytes,
                        mUriConverterMock,
                        mCache);

        doReturn(mUrlMock).when(mUriConverterMock).toUrl(any(Uri.class));
        doReturn(mURLConnectionMock).when(mUrlMock).openConnection();
        doReturn(mInputStreamMock).when(mURLConnectionMock).getInputStream();
        doReturn(202).when(mURLConnectionMock).getResponseCode();

        ListenableFuture<String> futureResponse = mClient.fetchPayload(Uri.parse((mFakeUrl)));

        // There could be some lag between fetch call and connection opening
        verify(mUrlMock, timeout(delayMs)).openConnection();
        // Given the resources are set to be eventually closed, we add a timeout
        verify(mInputStreamMock, timeout(waitForEventualCompletionMs).atLeast(1)).close();
        verify(mURLConnectionMock, timeout(waitForEventualCompletionMs).atLeast(1)).disconnect();
        assertTrue(
                "The future response for fetchPayload should have been completed",
                futureResponse.isDone());
    }

    @Test
    public void testFetchPayloadResponsesUsesCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        mClient.fetchPayload(Uri.parse(url.toString()), true).get();
        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());
        Thread.sleep(500);
        // Flake proofing : In rare but possible scenario where the cache is not done persisting, we
        // will get cache miss, no point asserting further
        assumeTrue(mCache.getCachedEntriesCount() == 1);
        String response = mClient.fetchPayload(Uri.parse(url.toString()), true).get();
        assertEquals(mJsScript, response);
        assertEquals("This call should have been cached", 1, server.getRequestCount());
    }

    @Test
    public void testFetchPayloadResponsesDefaultSkipsCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        mClient.fetchPayload(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());

        String response = mClient.fetchPayload(Uri.parse(url.toString())).get();
        assertEquals(mJsScript, response);
        assertEquals("This call should not have been cached", 2, server.getRequestCount());
    }

    @Test
    public void testFetchPayloadResponsesNoCacheHeaderSkipsCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(NO_CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        mClient.fetchPayload(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());

        String response = mClient.fetchPayload(Uri.parse(url.toString())).get();
        assertEquals(mJsScript, response);
        assertEquals("This call should not have been cached", 2, server.getRequestCount());
    }

    @Test
    public void testFetchPayloadCacheDisabledSkipsCache() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse()
                                        .setBody(mJsScript)
                                        .addHeader(CACHE_HEADER);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);

        Flags disableCacheFlags =
                new Flags() {
                    @Override
                    public boolean getFledgeHttpCachingEnabled() {
                        return false;
                    }
                };
        HttpCache cache = CacheProviderFactory.create(CONTEXT, disableCacheFlags);
        AdServicesHttpsClient client = new AdServicesHttpsClient(mExecutorService, cache);

        client.fetchPayload(Uri.parse(url.toString()), true);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchPayloadPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
        assertEquals(1, server.getRequestCount());

        String response = client.fetchPayload(Uri.parse(url.toString()), true).get();
        assertEquals(mJsScript, response);
        assertEquals("This call should not have been cached", 2, server.getRequestCount());
    }

    @Test
    public void testPostJsonSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        assertThat(postJson(Uri.parse(url.toString()), mEventData)).isNull();
    }

    @Test
    public void testPostJsonCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mEventData);

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("POST", request1.getMethod());
    }

    @Test
    public void testPostJsonCorrectData() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mEventData);

        RecordedRequest request1 = server.takeRequest();
        assertEquals("POST", request1.getMethod());
        assertEquals(mEventData.toString(), request1.getUtf8Body());
    }

    @Test
    public void testPostJsonFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mReportingPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> postJson(Uri.parse(url.toString()), mEventData));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testPostJsonDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class, () -> postJson(Uri.parse(mFakeUrl), mEventData));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testPostJsonThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () -> postJson(Uri.parse("http://google.com"), mEventData));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String fetchPayload(Uri uri) throws Exception {
        return mClient.fetchPayload(uri).get();
    }

    private Void getAndReadNothing(Uri uri) throws Exception {
        return mClient.getAndReadNothing(uri).get();
    }

    private Void postJson(Uri uri, JSONObject eventData) throws Exception {
        return mClient.postJson(uri, eventData).get();
    }
}
