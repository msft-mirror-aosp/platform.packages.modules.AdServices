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

import static android.adservices.exceptions.RetryableAdServicesNetworkException.DEFAULT_RETRY_AFTER_VALUE;

import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.EMPTY_BODY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.exceptions.AdServicesNetworkException;
import android.adservices.exceptions.RetryableAdServicesNetworkException;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import androidx.room.Room;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.common.cache.CacheDatabase;
import com.android.adservices.service.common.cache.CacheEntryDao;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.SelectAdsFromOutcomesApiCalledStats;
import com.android.adservices.service.stats.SelectAdsFromOutcomesExecutionLogger;
import com.android.adservices.service.stats.SelectAdsFromOutcomesExecutionLoggerImpl;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

public final class AdServicesHttpsClientTest extends AdServicesMockitoTestCase {
    private static final String CACHE_HEADER = "Cache-Control: max-age=60";
    private static final String NO_CACHE_HEADER = "Cache-Control: no-cache";
    private static final String RESPONSE_HEADER_KEY = "fake_response_header_key";
    private static final String REQUEST_PROPERTY_KEY = "X_REQUEST_KEY";
    private static final String REQUEST_PROPERTY_VALUE = "Fake_Value";
    private static final long MAX_AGE_SECONDS = 120;
    private static final long MAX_ENTRIES = 20;
    private static final DevContext DEV_CONTEXT_DISABLED = DevContext.createForDevOptionsDisabled();
    private static final DevContext DEV_CONTEXT_ENABLED =
            DevContext.builder(sPackageName).setDeviceDevOptionsEnabled(true).build();

    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private final String mJsScript = "function test() { return \"hello world\"; }";
    private final String mReportingPath = "/reporting/";
    private final String mFetchPayloadPath = "/fetchPayload/";
    private final String mFakeUrl = "https://fakeprivacysandboxdomain.never/this/is/a/fake";

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private AdServicesHttpsClient mClient;
    @Mock private AdServicesHttpsClient.UriConverter mUriConverterMock;
    @Mock private URL mUrlMock;
    @Mock private HttpsURLConnection mURLConnectionMock;
    @Mock private InputStream mInputStreamMock;
    @Mock private Clock mMockClock;
    private HttpCache mCache;
    private String mData;
    private AdServicesLogger mAdServicesLoggerSpy;
    private final long mStartDownloadTimestamp = 98L;
    private final long mEndDownloadTimestamp = 199L;

    @Before
    public void setup() throws Exception {
        CacheEntryDao cacheEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();

        mCache = new FledgeHttpCache(cacheEntryDao, MAX_AGE_SECONDS, MAX_ENTRIES);
        mClient = new AdServicesHttpsClient(mExecutorService, mCache);
        mData = new JSONObject().put("key", "value").toString();
    }

    @Test
    public void testGetAndReadNothingSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        assertThat(getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED)).isNull();
    }

    @Test
    public void testGetAndReadNothingSuccessfulResponse_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        assertThat(getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_ENABLED)).isNull();
    }

    @Test
    public void testGetAndReadNothingCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mReportingPath);
        expect.that(request1.getMethod()).isEqualTo("GET");
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
                        () -> getAndReadNothing(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testGetAndReadNothingDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> getAndReadNothing(Uri.parse(mFakeUrl), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testGetAndReadNothingThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                getAndReadNothing(
                                        Uri.parse("http://google.com"), DEV_CONTEXT_DISABLED));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFetchPayloadSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);
        expect.that(result.getResponseBody()).isEqualTo(mJsScript);
    }

    @Test
    public void testFetchPayloadSuccessfulResponse_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_ENABLED);
        expect.that(result.getResponseBody()).isEqualTo(mJsScript);
    }

    @Test
    public void testFetchPayloadCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);
        fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mFetchPayloadPath);
        expect.that(request1.getMethod()).isEqualTo("GET");
    }

    @Test
    public void testFetchPayloadFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testFetchPayloadDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(mFakeUrl), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testThrowsIOExceptionWhenConnectionTimesOut() throws Exception {
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
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testFetchPayloadThrowsExceptionIfUsingPlainTextHttp() {
        Exception wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse("http://google.com"), DEV_CONTEXT_DISABLED));

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
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
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

        ListenableFuture<AdServicesHttpClientResponse> futureResponse =
                mClient.fetchPayload(Uri.parse((mFakeUrl)), DEV_CONTEXT_DISABLED);

        // There could be some lag between fetch call and connection opening
        verify(mUrlMock, timeout(delayMs)).openConnection();
        // We cancel the future while the request is going on
        assertWithMessage("The request should have been ongoing, until being force-cancelled now")
                .that(futureResponse.cancel(true))
                .isTrue();
        // Given the resources are set to be eventually closed, we add a timeout
        verify(mURLConnectionMock, timeout(waitForEventualCompletionMs).atLeast(1)).disconnect();
        verify(mInputStreamMock, timeout(waitForEventualCompletionMs).atLeast(1)).close();
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

        ListenableFuture<AdServicesHttpClientResponse> futureResponse =
                mClient.fetchPayload(Uri.parse((mFakeUrl)), DEV_CONTEXT_DISABLED);

        // There could be some lag between fetch call and connection opening
        verify(mUrlMock, timeout(delayMs)).openConnection();
        // Given the resources are set to be eventually closed, we add a timeout
        verify(mInputStreamMock, timeout(waitForEventualCompletionMs).atLeast(1)).close();
        verify(mURLConnectionMock, timeout(waitForEventualCompletionMs).atLeast(1)).disconnect();
        assertWithMessage("The future response for fetchPayload should have been completed")
                .that(futureResponse.isDone())
                .isTrue();
    }

    @Test
    public void testFetchPayloadResponsesSkipsHeaderIfAbsent() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                return new MockResponse().setBody(mJsScript);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        AdServicesHttpClientResponse response =
                mClient.fetchPayload(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(Uri.parse(url.toString()))
                                        .setUseCache(false)
                                        .setResponseHeaderKeys(ImmutableSet.of(RESPONSE_HEADER_KEY))
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build())
                        .get();
        expect.that(response.getResponseBody()).isEqualTo(mJsScript);
        expect.withMessage("No header should have been returned")
                .that(response.getResponseHeaders())
                .hasSize(0);
    }

    @Test
    public void testFetchPayloadContainsRequestProperties() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                assertWithMessage("Request header mismatch")
                                        .that(request.getHeader(REQUEST_PROPERTY_KEY))
                                        .isEqualTo(REQUEST_PROPERTY_VALUE);
                                return new MockResponse().setBody(mJsScript);
                            }
                        });
        URL url = server.getUrl(mFetchPayloadPath);
        mClient.fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(Uri.parse(url.toString()))
                                .setUseCache(false)
                                .setRequestProperties(
                                        ImmutableMap.of(
                                                REQUEST_PROPERTY_KEY, REQUEST_PROPERTY_VALUE))
                                .setDevContext(DEV_CONTEXT_DISABLED)
                                .build())
                .get();
    }

    @Test
    public void testAdServiceRequestResponseDefault_Empty() {
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.EMPTY)
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .build();

        expect.that(request.getRequestProperties()).isEmpty();
        expect.that(request.getResponseHeaderKeys()).isEmpty();
        expect.that(request.getUseCache()).isFalse();

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder().setResponseBody("").build();

        expect.that(response.getResponseHeaders()).isEmpty();
    }

    @Test
    public void testCreateAdServicesRequestResponse_Success() {
        Uri uri = Uri.parse("www.google.com");
        ImmutableMap<String, String> requestProperties = ImmutableMap.of("key", "value");
        ImmutableSet<String> responseHeaderKeys = ImmutableSet.of("entry1", "entry2");

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.create(
                        uri,
                        requestProperties,
                        responseHeaderKeys,
                        false,
                        DEV_CONTEXT_DISABLED,
                        AdServicesHttpUtil.HttpMethodType.GET,
                        EMPTY_BODY);

        expect.that(request.getUri()).isEqualTo(uri);
        expect.that(request.getRequestProperties()).isEqualTo(requestProperties);
        expect.that(request.getResponseHeaderKeys()).isEqualTo(responseHeaderKeys);
        expect.that(request.getUseCache()).isFalse();

        String body = "Fake response body";
        ImmutableMap<String, List<String>> responseHeaders =
                ImmutableMap.of("key", List.of("value1", "value2"));
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.create(body, responseHeaders);

        expect.that(response.getResponseBody()).isEqualTo(body);
        expect.that(response.getResponseHeaders()).isEqualTo(responseHeaders);
    }

    @Test
    public void testCreateAdServicesRequestResponse_Success_DevOptionsEnabled() {
        Uri uri = Uri.parse("www.google.com");
        ImmutableMap<String, String> requestProperties = ImmutableMap.of("key", "value");
        ImmutableSet<String> responseHeaderKeys = ImmutableSet.of("entry1", "entry2");

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.create(
                        uri,
                        requestProperties,
                        responseHeaderKeys,
                        false,
                        DEV_CONTEXT_ENABLED,
                        AdServicesHttpUtil.HttpMethodType.GET,
                        EMPTY_BODY);

        expect.that(request.getUri()).isEqualTo(uri);
        expect.that(request.getRequestProperties()).isEqualTo(requestProperties);
        expect.that(request.getResponseHeaderKeys()).isEqualTo(responseHeaderKeys);
        expect.that(request.getUseCache()).isFalse();

        String body = "Fake response body";
        ImmutableMap<String, List<String>> responseHeaders =
                ImmutableMap.of("key", List.of("value1", "value2"));
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.create(body, responseHeaders);

        expect.that(response.getResponseBody()).isEqualTo(body);
        expect.that(response.getResponseHeaders()).isEqualTo(responseHeaders);
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

        mClient.fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mFetchPayloadPath);
        expect.that(request1.getMethod()).isEqualTo("GET");
        expect.that(server.getRequestCount()).isEqualTo(1);

        AdServicesHttpClientResponse response =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);
        expect.that(response.getResponseBody()).isEqualTo(mJsScript);
        expect.withMessage("This call should not have been cached")
                .that(server.getRequestCount())
                .isEqualTo(2);
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

        mClient.fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mFetchPayloadPath);
        expect.that(request1.getMethod()).isEqualTo("GET");
        expect.that(server.getRequestCount()).isEqualTo(1);

        AdServicesHttpClientResponse response =
                fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED);
        expect.that(response.getResponseBody()).isEqualTo(mJsScript);
        expect.withMessage("This call should not have been cached")
                .that(server.getRequestCount())
                .isEqualTo(2);
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
        HttpCache cache = CacheProviderFactory.create(mContext, disableCacheFlags);
        AdServicesHttpsClient client = new AdServicesHttpsClient(mExecutorService, cache);

        client.fetchPayload(
                AdServicesHttpClientRequest.builder()
                        .setUri(Uri.parse(url.toString()))
                        .setUseCache(true)
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .build());

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mFetchPayloadPath);
        expect.that(request1.getMethod()).isEqualTo("GET");
        expect.that(server.getRequestCount()).isEqualTo(1);

        AdServicesHttpClientResponse response =
                client.fetchPayload(
                                AdServicesHttpClientRequest.builder()
                                        .setUri(Uri.parse(url.toString()))
                                        .setUseCache(true)
                                        .setDevContext(DEV_CONTEXT_DISABLED)
                                        .build())
                        .get();
        expect.that(response.getResponseBody()).isEqualTo(mJsScript);
        expect.withMessage("This call should not have been cached")
                .that(server.getRequestCount())
                .isEqualTo(2);
    }

    @Test
    public void testPostJsonSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        assertThat(postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED)).isNull();
    }

    @Test
    public void testPostJsonCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mReportingPath);
        expect.that(request1.getMethod()).isEqualTo("POST");
    }

    @Test
    public void testPostJsonCorrectPath_DevOptionsEnabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_ENABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getPath()).isEqualTo(mReportingPath);
        expect.that(request1.getMethod()).isEqualTo("POST");
    }

    @Test
    public void testPostJsonCorrectData() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED);

        RecordedRequest request1 = server.takeRequest();
        expect.that(request1.getMethod()).isEqualTo("POST");
        expect.that(request1.getUtf8Body()).isEqualTo(mData);
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
                        () -> postJson(Uri.parse(url.toString()), mData, DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testPostJsonDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> postJson(Uri.parse(mFakeUrl), mData, DEV_CONTEXT_DISABLED));
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testPostJsonThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                postJson(
                                        Uri.parse("http://google.com"),
                                        mData,
                                        DEV_CONTEXT_DISABLED));

        assertThat(wrapperExecutionException.getCause())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testFailedResponseWithStatusCode() throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mMockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL url = server.getUrl(mFetchPayloadPath);

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(wrapperException.getCause()).isInstanceOf(AdServicesNetworkException.class);

        // Assert the expected AdServicesNetworkException is thrown.
        AdServicesNetworkException exception =
                (AdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
    }

    @Test
    public void testFailedResponseWithStatusCodeAndRetryAfter() throws Exception {
        MockResponse response =
                new MockResponse().setResponseCode(429).setHeader("Retry-After", 1000);
        MockWebServer server = mMockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL url = server.getUrl(mFetchPayloadPath);

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(wrapperException.getCause())
                .isInstanceOf(RetryableAdServicesNetworkException.class);

        // Assert the expected RetryableAdServicesNetworkException is thrown.
        RetryableAdServicesNetworkException exception =
                (RetryableAdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
        assertThat(exception.getRetryAfter()).isEqualTo(Duration.ofMillis(1000));
    }

    @Test
    public void testFailedResponseWithStatusCodeAndRetryAfterWithNoRetryHeader() throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mMockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL url = server.getUrl(mFetchPayloadPath);

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        assertThat(wrapperException.getCause())
                .isInstanceOf(RetryableAdServicesNetworkException.class);

        // Assert the expected RetryableAdServicesNetworkException is thrown.
        RetryableAdServicesNetworkException exception =
                (RetryableAdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
        assertThat(exception.getRetryAfter()).isEqualTo(DEFAULT_RETRY_AFTER_VALUE);
    }

    @Test
    public void testFetchPayloadDomainIsLocalhost_DevOptionsDisabled() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchPayloadPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_DISABLED));
        // Verify we are pinging a local domain.
        assertThat(WebAddresses.isLocalhost(Uri.parse(url.toString()))).isTrue();
        assertThat(exception.getCause()).isInstanceOf(AdServicesNetworkException.class);
    }

    @Test
    public void testFetchPayloadDomainIsLocalhost_DevOptionsEnabled() throws Exception {
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

        AdServicesHttpClientResponse response =
                mClient.fetchPayload(Uri.parse(url.toString()), DEV_CONTEXT_ENABLED).get();

        // Verify we are pinging a local domain.
        assertThat(WebAddresses.isLocalhost(Uri.parse(url.toString()))).isTrue();
        expect.that(response.getResponseBody()).isEqualTo(mJsScript);
    }

    @Test
    public void testperformRequestAndGetResponseInBytes_postsCorrectData() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBody = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBody)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                        .build();

        mClient.performRequestGetResponseInBase64String(request).get();

        RecordedRequest recordedRequest1 = server.takeRequest();
        assertThat(recordedRequest1.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.POST.name());
        assertThat(recordedRequest1.getBody()).isEqualTo(postedBody);
    }

    @Test
    public void performRequestGetResponseBytes_getRequestNonEmptyBody_requestBodyShouldBeEmpty()
            throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBody = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBody)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                        .build();

        mClient.performRequestGetResponseInBase64String(request).get();

        RecordedRequest recordedRequest = server.takeRequest();
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.GET.name());
        assertThat(recordedRequest.getBody()).isEqualTo(EMPTY_BODY);
    }

    @Test
    public void testperformRequestAndGetResponseInBytes_shouldReturnResponseInBytes()
            throws Exception {
        byte[] byteResponse = {1, 2, 3, 54};
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(byteResponse)));
        URL url = server.getUrl(mReportingPath);
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
                mClient.performRequestGetResponseInBase64String(request).get();

        String expectedResponseString = BaseEncoding.base64().encode(byteResponse);
        assertThat(response.getResponseBody()).isEqualTo(expectedResponseString);
    }

    @Test
    public void performRequestGetResponseBytes_failedStatusCode_shouldThrowErrorWithCorrectCode()
            throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(429)));
        URL url = server.getUrl(mReportingPath);
        byte[] postedBody = {1, 2, 3};
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(
                                AdServicesHttpUtil.REQUEST_PROPERTIES_PROTOBUF_CONTENT_TYPE)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBody)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.GET)
                        .build();

        // Assert future chain throws an AdServicesNetworkException.
        Exception wrapperException =
                assertThrows(
                        ExecutionException.class,
                        () -> mClient.performRequestGetResponseInBase64String(request).get());
        assertThat(wrapperException.getCause()).isInstanceOf(AdServicesNetworkException.class);

        // Assert the expected AdServicesNetworkException is thrown.
        AdServicesNetworkException exception =
                (AdServicesNetworkException) wrapperException.getCause();
        assertThat(exception.getErrorCode())
                .isEqualTo(AdServicesNetworkException.ERROR_TOO_MANY_REQUESTS);
    }

    @Test
    public void testPerformRequestAndGetResponseInString_shouldReturnResponseString()
            throws Exception {
        String stringResponse = "This is a plain String response which could also be a JSON String";
        byte[] postedBodyInBytes = "{[1,2,3]}".getBytes(StandardCharsets.UTF_8);

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        assertThat(new String(postedBodyInBytes))
                                .isEqualTo(new String(request.getBody()));
                        return new MockResponse().setBody(stringResponse);
                    }
                };
        MockWebServer server = mMockWebServerRule.startMockWebServer(dispatcher);
        URL url = server.getUrl(mReportingPath);

        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(
                        "Content-Type", "application/json",
                        "Accept", "application/json");

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(Uri.parse(url.toString()))
                        .setDevContext(DEV_CONTEXT_DISABLED)
                        .setBodyInBytes(postedBodyInBytes)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                        .build();

        AdServicesHttpClientResponse response =
                mClient.performRequestGetResponseInPlainString(request).get();

        expect.that(server.getRequestCount()).isEqualTo(1);
        assertThat(response.getResponseBody()).isEqualTo(stringResponse);
    }

    @Test
    public void testFetchPayloadSuccessfulResponseWithSelectAdsFromOutcomesLogging()
            throws Exception {
        SelectAdsFromOutcomesExecutionLogger executionLogger =
                setupSelectAdsFromOutcomesApiCalledStatsLogging();

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchPayloadPath);

        AdServicesHttpClientResponse result =
                mClient.fetchPayloadWithLogging(
                                Uri.parse(url.toString()), DEV_CONTEXT_DISABLED, executionLogger)
                        .get();
        expect.that(result.getResponseBody()).isEqualTo(mJsScript);

        // Verify the logging of SelectAdsFromOutcomesApiCalledStats
        verifySelectAdsFromOutcomesApiCalledStatsLogging(executionLogger, 200);
    }

    @Test
    public void testPickRequiredHeaderFields() {
        ImmutableMap<String, List<String>> allHeaders =
                ImmutableMap.of(
                        "key1", ImmutableList.of("value1"), "key2", ImmutableList.of("value2"));
        ImmutableSet<String> requiredHeaderKeys = ImmutableSet.of("key1");

        Map<String, List<String>> result =
                mClient.pickRequiredHeaderFields(allHeaders, requiredHeaderKeys);
        assertThat(result).isEqualTo(ImmutableMap.of("key1", ImmutableList.of("value1")));
    }

    @Test
    public void testPickRequiredHeaderFieldsCaseInsensitive() {
        ImmutableMap<String, List<String>> allHeaders =
                ImmutableMap.of(
                        "KEY1", ImmutableList.of("value1"), "KEY2", ImmutableList.of("value2"));
        ImmutableSet<String> requiredHeaderKeys = ImmutableSet.of("key1", "key2");

        Map<String, List<String>> result =
                mClient.pickRequiredHeaderFields(allHeaders, requiredHeaderKeys);
        assertThat(result)
                .isEqualTo(
                        ImmutableMap.of(
                                "key1",
                                ImmutableList.of("value1"),
                                "key2",
                                ImmutableList.of("value2")));
    }

    private AdServicesHttpClientResponse fetchPayload(Uri uri, DevContext devContext)
            throws Exception {
        return mClient.fetchPayload(uri, devContext).get();
    }

    private Void getAndReadNothing(Uri uri, DevContext devContext) throws Exception {
        return mClient.getAndReadNothing(uri, devContext).get();
    }

    private Void postJson(Uri uri, String data, DevContext devContext) throws Exception {
        return mClient.postPlainText(uri, data, devContext).get();
    }

    private SelectAdsFromOutcomesExecutionLogger setupSelectAdsFromOutcomesApiCalledStatsLogging() {
        mAdServicesLoggerSpy = Mockito.spy(AdServicesLoggerImpl.getInstance());

        when(mMockClock.elapsedRealtime())
                .thenReturn(mStartDownloadTimestamp, mEndDownloadTimestamp);
        return new SelectAdsFromOutcomesExecutionLoggerImpl(mMockClock, mAdServicesLoggerSpy);
    }

    private void verifySelectAdsFromOutcomesApiCalledStatsLogging(
            SelectAdsFromOutcomesExecutionLogger executionLogger, int statusCode) {
        ArgumentCaptor<SelectAdsFromOutcomesApiCalledStats> argumentCaptor =
                ArgumentCaptor.forClass(SelectAdsFromOutcomesApiCalledStats.class);
        executionLogger.logSelectAdsFromOutcomesApiCalledStats();
        verify(mAdServicesLoggerSpy)
                .logSelectAdsFromOutcomesApiCalledStats(argumentCaptor.capture());
        SelectAdsFromOutcomesApiCalledStats stats = argumentCaptor.getValue();

        int downloadLatency = (int) (mEndDownloadTimestamp - mStartDownloadTimestamp);
        assertThat(stats.getDownloadLatencyMillis()).isEqualTo(downloadLatency);
        assertThat(stats.getDownloadResultCode()).isEqualTo(statusCode);
    }
}
