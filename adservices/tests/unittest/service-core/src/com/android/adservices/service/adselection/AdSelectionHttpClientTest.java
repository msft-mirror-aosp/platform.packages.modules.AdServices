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

package com.android.adservices.service.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Test;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SmallTest
public class AdSelectionHttpClientTest {
    private final String mDummyDomain = "http://www.domain.com/adverts/123";
    private final String mJsScript = "function test() { return \"hello world\"; }";
    private final String mReportingPath = "/reporting/";
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final AdSelectionHttpClient mClient = new AdSelectionHttpClient();
    private final int mTimeoutDelta = 1000;
    private final int mBytesPerPeriod = 1;

    @Test
    public void testReportUrlSuccessfulResponse() throws Exception {
        MockWebServer server = setupServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);

        int responseCode = mClient.reportUrl(Uri.parse(url.toString()));
        assertTrue(AdSelectionHttpClient.isSuccessfulResponse(responseCode));
    }

    @Test
    public void testReportUrlCorrectPath() throws Exception {
        MockWebServer server = setupServer(ImmutableList.of(new MockResponse()));
        URL url = server.getUrl(mReportingPath);
        mClient.reportUrl(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mReportingPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testReportUrlFailedResponse() throws Exception {
        MockWebServer server =
                setupServer(ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mReportingPath);

        int responseCode = mClient.reportUrl(Uri.parse(url.toString()));
        assertFalse(AdSelectionHttpClient.isSuccessfulResponse(responseCode));
    }

    @Test
    public void testReportUrlDomainDoesNotExist() throws Exception {
        setupServer(ImmutableList.of(new MockResponse()));

        int responseCode = mClient.reportUrl(Uri.parse(mDummyDomain));
        assertFalse(AdSelectionHttpClient.isSuccessfulResponse(responseCode));
    }

    @Test
    public void testFetchJavascriptSuccessfulResponse() throws Exception {
        MockWebServer server = setupServer(ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchJavaScriptPath);

        String result = mClient.fetchJavascript(Uri.parse(url.toString()));
        assertEquals(mJsScript, result);
    }

    @Test
    public void testFetchJavascriptCorrectPath() throws Exception {
        MockWebServer server = setupServer(ImmutableList.of(new MockResponse().setBody(mJsScript)));
        URL url = server.getUrl(mFetchJavaScriptPath);
        mClient.fetchJavascript(Uri.parse(url.toString()));

        RecordedRequest request1 = server.takeRequest();
        assertEquals(mFetchJavaScriptPath, request1.getPath());
        assertEquals("GET", request1.getMethod());
    }

    @Test
    public void testFetchJavascriptFailedResponse() throws Exception {
        MockWebServer server =
                setupServer(ImmutableList.of(new MockResponse().setResponseCode(305)));
        URL url = server.getUrl(mFetchJavaScriptPath);

        assertThrows(
                IllegalStateException.class,
                () -> {
                    mClient.fetchJavascript(Uri.parse(url.toString()));
                });
    }

    @Test
    public void testFetchJavascriptDomainDoesNotExist() throws Exception {
        setupServer(ImmutableList.of(new MockResponse()));
        assertThrows(
                IllegalStateException.class,
                () -> {
                    mClient.fetchJavascript(Uri.parse(mDummyDomain));
                });
    }

    @Test
    public void testThrowsIllegalStateExceptionWhenConnectionTimesOut() throws Exception {
        MockWebServer server =
                setupServer(
                        ImmutableList.of(
                                new MockResponse()
                                        .setBody(mJsScript)
                                        .throttleBody(
                                                mBytesPerPeriod,
                                                mClient.getTimeoutMS() + mTimeoutDelta,
                                                TimeUnit.MILLISECONDS)));
        URL url = server.getUrl(mReportingPath);

        assertThrows(
                IllegalStateException.class,
                () -> {
                    mClient.fetchJavascript(Uri.parse(url.toString()));
                });
    }

    private MockWebServer setupServer(List<MockResponse> responses) throws Exception {
        MockWebServer server = new MockWebServer();
        for (MockResponse response : responses) {
            server.enqueue(response);
        }
        server.play();
        return server;
    }
}
