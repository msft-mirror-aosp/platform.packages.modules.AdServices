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
import com.android.adservices.service.common.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;

public class JsFetcherTest {
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private String mFetchJavaScriptPath = "/fetchJavascript/";
    private Uri mFetchJsUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);
    private String mAppPackageName = "com.google.ppapi.test";

    private DevContext mDevContext;
    private CustomAudienceDao mCustomAudienceDao;
    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private AdServicesHttpsClient mWebClient;

    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;
    private Dispatcher mDefaultDispatcher;
    private MockWebServer mServer;

    @Before
    public void setUp() throws Exception {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mWebClient = new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor());
        mDevContext = DevContext.createForDevOptionsDisabled();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        mFetchJsUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);
        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (mFetchJavaScriptPath.equals(request.getPath())) {
                            return new MockResponse().setBody("js");
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mServer = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);
    }

    @Test
    public void testSuccessfulGetBuyerLogicWithoutOverride() {
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(mAppPackageName)
                        .build();
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);

        JsFetcher jsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        customAudienceDevOverridesHelper,
                        mWebClient);
        jsFetcher.getBuyerDecisionLogic(
                mFetchJsUri,
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                CustomAudienceFixture.VALID_NAME);

        mMockWebServerRule.verifyMockServerRequests(
                mServer, 0, Collections.emptyList(), mRequestMatcherExactMatch);
    }

    @Test
    public void testSuccessfulGetBuyerLogicWithOverride() throws Exception {
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(false)
                        .setCallingAppPackageName(mAppPackageName)
                        .build();
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        JsFetcher jsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        customAudienceDevOverridesHelper,
                        mWebClient);

        FluentFuture<String> buyerDecisionLogicFuture =
                jsFetcher.getBuyerDecisionLogic(
                        mFetchJsUri,
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME);
        String buyerDecisionLogic = waitForFuture(() -> buyerDecisionLogicFuture);

        assertEquals(buyerDecisionLogic, "js");
        mMockWebServerRule.verifyMockServerRequests(
                mServer,
                1,
                Collections.singletonList(mFetchJavaScriptPath),
                mRequestMatcherExactMatch);
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
