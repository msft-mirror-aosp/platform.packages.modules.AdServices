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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

public class JsFetcherTest {
    private static final String BIDDING_LOGIC_OVERRIDE = "js_override.";
    private static final String BIDDING_LOGIC = "js";
    private static final String APP_PACKAGE_NAME = "com.google.ppapi.test";
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private static final String TRUSTED_BIDDING_OVERRIDE_DATA = "{\"trusted_bidding_data\":1}";
    private static final String FETCH_JAVA_SCRIPT_PATH = "/fetchJavascript/";
    private static final long BUYER_BIDDING_LOGIC_JS_VERSION = 3;

    private static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final String NAME = CustomAudienceFixture.VALID_NAME;
    private static final MockWebServerRule.RequestMatcher<String> REQUEST_MATCHER_EXACT_MATCH =
            String::equals;

    public static final DBCustomAudienceOverride DB_CUSTOM_AUDIENCE_OVERRIDE =
            DBCustomAudienceOverride.builder()
                    .setOwner(OWNER)
                    .setBuyer(BUYER)
                    .setName(NAME)
                    .setAppPackageName(APP_PACKAGE_NAME)
                    .setBiddingLogicJS(BIDDING_LOGIC_OVERRIDE)
                    .setTrustedBiddingData(TRUSTED_BIDDING_OVERRIDE_DATA)
                    .build();
    private DevContext mDevContext =
            DevContext.builder()
                    .setDevOptionsEnabled(false)
                    .setCallingAppPackageName(APP_PACKAGE_NAME)
                    .build();

    private CustomAudienceDao mCustomAudienceDao =
            Room.inMemoryDatabaseBuilder(
                            ApplicationProvider.getApplicationContext(),
                            CustomAudienceDatabase.class)
                    .addTypeConverter(new DBCustomAudience.Converters(true))
                    .build()
                    .customAudienceDao();
    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private AdServicesHttpsClient mWebClient;

    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;
    private Dispatcher mDefaultDispatcher;
    private MockWebServer mServer;
    private MockitoSession mStaticMockSession = null;
    private CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @Mock private RunAdBiddingPerCAExecutionLogger mRunAdBiddingPerCAExecutionLoggerMock;
    private Uri mFetchJsUri;
    private AdServicesHttpClientRequest mFetchJsRequest;

    @Before
    public void setUp() throws Exception {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .startMocking();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mWebClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mDevContext = DevContext.createForDevOptionsDisabled();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true))
                        .build()
                        .customAudienceDao();

        mFetchJsUri = mMockWebServerRule.uriForPath(FETCH_JAVA_SCRIPT_PATH);
        mFetchJsRequest =
                JsVersionHelper.getRequestWithVersionHeader(
                        mFetchJsUri,
                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                        BUYER_BIDDING_LOGIC_JS_VERSION,
                        false);
        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (FETCH_JAVA_SCRIPT_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("js");
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        mRequestMatcherExactMatch = String::equals;
        mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testSuccessfulGetBuyerLogicWithOverride() throws Exception {
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(APP_PACKAGE_NAME)
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE);
        mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        JsFetcher jsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mWebClient);

        FluentFuture<String> buyerDecisionLogicFuture =
                jsFetcher.getBiddingLogic(
                        mFetchJsUri, mCustomAudienceDevOverridesHelper, OWNER, BUYER, NAME);
        String buyerDecisionLogic = waitForFuture(() -> buyerDecisionLogicFuture);
        assertEquals(BIDDING_LOGIC_OVERRIDE, buyerDecisionLogic);
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 0, Collections.emptyList(), REQUEST_MATCHER_EXACT_MATCH);
    }

    @Test
    public void testSuccessfulGetBuyerLogicWithOverrideWithLogger() throws Exception {
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(APP_PACKAGE_NAME)
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(DB_CUSTOM_AUDIENCE_OVERRIDE);
        mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        JsFetcher jsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mWebClient);
        // Logger calls come after the future result is returned
        CountDownLatch loggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .startGetBuyerDecisionLogic();
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGetBuyerDecisionLogic(any());
        FluentFuture<AdServicesHttpClientResponse> buyerDecisionLogicFuture =
                jsFetcher.getBuyerDecisionLogicWithLogger(
                        mFetchJsRequest,
                        mCustomAudienceDevOverridesHelper,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME,
                        mRunAdBiddingPerCAExecutionLoggerMock);
        AdServicesHttpClientResponse buyerDecisionLogic =
                waitForFuture(() -> buyerDecisionLogicFuture);
        String js = buyerDecisionLogic.getResponseBody();
        loggerLatch.await();
        assertEquals(BIDDING_LOGIC_OVERRIDE, js);
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 0, Collections.emptyList(), REQUEST_MATCHER_EXACT_MATCH);
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGetBuyerDecisionLogic();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGetBuyerDecisionLogic(js);
    }

    @Test
    public void testSuccessfulGetBuyerLogicWithoutOverride() throws Exception {

        JsFetcher jsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mWebClient);

        FluentFuture<String> buyerDecisionLogicFuture =
                jsFetcher.getBiddingLogic(
                        mFetchJsUri,
                        mCustomAudienceDevOverridesHelper,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        String buyerDecisionLogic = waitForFuture(() -> buyerDecisionLogicFuture);

        assertEquals(buyerDecisionLogic, BIDDING_LOGIC);
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                1,
                Collections.singletonList(FETCH_JAVA_SCRIPT_PATH),
                REQUEST_MATCHER_EXACT_MATCH);
    }

    @Test
    public void testSuccessfulGetBuyerLogicWithoutOverrideAndLogger() throws Exception {
        JsFetcher jsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mWebClient);
        // Logger calls come after the future result is returned
        CountDownLatch loggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .startGetBuyerDecisionLogic();
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGetBuyerDecisionLogic(any());
        FluentFuture<AdServicesHttpClientResponse> buyerDecisionLogicFuture =
                jsFetcher.getBuyerDecisionLogicWithLogger(
                        mFetchJsRequest,
                        mCustomAudienceDevOverridesHelper,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME,
                        mRunAdBiddingPerCAExecutionLoggerMock);
        String buyerDecisionLogic = waitForFuture(() -> buyerDecisionLogicFuture).getResponseBody();
        loggerLatch.await();
        assertEquals(buyerDecisionLogic, BIDDING_LOGIC);
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                1,
                Collections.singletonList(FETCH_JAVA_SCRIPT_PATH),
                REQUEST_MATCHER_EXACT_MATCH);
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGetBuyerDecisionLogic();
        verify(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGetBuyerDecisionLogic(eq(buyerDecisionLogic));
    }

    private <T> T waitForFuture(JsFetcherTest.ThrowingSupplier<ListenableFuture<T>> function)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<T> futureResult = function.get();
        futureResult.addListener(resultLatch::countDown, mLightweightExecutorService);
        resultLatch.await();
        return futureResult.get();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
