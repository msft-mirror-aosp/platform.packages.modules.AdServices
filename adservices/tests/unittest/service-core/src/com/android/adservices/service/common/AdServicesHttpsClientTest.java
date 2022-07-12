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

import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.MockWebServerRuleFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@SmallTest
public class AdServicesHttpsClientTest {
    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();
    private final String mJsScript = "function test() { return \"hello world\"; }";
    private final String mReportingPath = "/reporting/";
    private final String mFetchPayloadPath = "/fetchPayload/";
    private final AdServicesHttpsClient mClient = new AdServicesHttpsClient(mExecutorService);
    private final int mTimeoutDeltaMs = 1000;
    private final int mBytesPerPeriod = 1;

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Test
    public void testReportUrlSuccessfulResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        assertThat(reportUrl(Uri.parse(url.toString()))).isNull();
    }

    @Test
    public void testReportUrlCorrectPath() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        reportUrl(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testReportUrlFailedResponse() throws Exception {
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mReportingPath);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportUrl(Uri.parse(url.toString()));
                        });
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testReportUrlDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportUrl(Uri.parse("https://www.domain.com/adverts/123"));
                        });
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testReportUrlThrowsExceptionIfUsingPlainTextHttp() {
        ExecutionException wrapperExecutionException =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchPayload(Uri.parse("http://google.com")));

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
                        ExecutionException.class,
                        () -> {
                            fetchPayload(Uri.parse(url.toString()));
                        });
        assertThat(exception.getCause()).isInstanceOf(IOException.class);
    }

    @Test
    public void testFetchPayloadDomainDoesNotExist() throws Exception {
        mMockWebServerRule.startMockWebServer(ImmutableList.of(new MockResponse()));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            fetchPayload(Uri.parse("https://www.domain.com/adverts/123"));
                        });
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
                        ExecutionException.class,
                        () -> {
                            fetchPayload(Uri.parse(url.toString()));
                        });
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

    private String fetchPayload(Uri uri) throws Exception {
        return mClient.fetchPayload(uri).get();
    }

    private Void reportUrl(Uri uri) throws Exception {
        return mClient.reportUrl(uri).get();
    }
}
