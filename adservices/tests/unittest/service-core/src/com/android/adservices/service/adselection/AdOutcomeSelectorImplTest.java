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

import static com.android.adservices.service.adselection.AdOutcomeSelectorImpl.OUTCOME_SELECTION_TIMED_OUT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.adservices.common.AdSelectionSignals;
import android.adservices.http.MockWebServerRule;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdServicesHttpsClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class AdOutcomeSelectorImplTest {
    private static final long AD_SELECTION_ID = 12345L;
    private static final double AD_BID = 10.0;
    private static final String WATERFALL_MEDIATION_LOGIC_PATH = "/waterfallMediationJs/";

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Mock private AdSelectionScriptEngine mMockAdSelectionScriptEngine;

    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private ListeningExecutorService mBlockingExecutorService;
    private ScheduledThreadPoolExecutor mSchedulingExecutor;
    private AdServicesHttpsClient mWebClient;
    private String mWaterfallMediationLogicJs;

    private AdOutcomeSelector mAdOutcomeSelector;
    private Flags mFlags;

    private Dispatcher mDefaultDispatcher;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();
        mSchedulingExecutor = AdServicesExecutors.getScheduler();
        mWebClient = new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor());

        mWaterfallMediationLogicJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    return {'status': 0, 'result': outcomes[0]};\n"
                        + "}";

        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (request.getPath().equals(WATERFALL_MEDIATION_LOGIC_PATH)) {
                            return new MockResponse().setBody(mWaterfallMediationLogicJs);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);

        mFlags =
                new Flags() {
                    @Override
                    public long getAdSelectionSelectingOutcomeTimeoutMs() {
                        return 300;
                    }
                };

        mAdOutcomeSelector =
                new AdOutcomeSelectorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mFlags);
    }

    @Test
    public void testAdOutcomeSelectorReturnsOutcomeSuccess() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Map<Long, Double> adverts = Map.of(AD_SELECTION_ID, AD_BID);
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(WATERFALL_MEDIATION_LOGIC_PATH);

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mWaterfallMediationLogicJs, adverts, signals))
                .thenReturn(Futures.immediateFuture(AD_SELECTION_ID));

        Long selectedOutcomeId =
                waitForFuture(() -> mAdOutcomeSelector.runAdOutcomeSelector(adverts, signals, uri));

        mMockWebServerRule.verifyMockServerRequests(
                server,
                1,
                Collections.singletonList(WATERFALL_MEDIATION_LOGIC_PATH),
                mRequestMatcherExactMatch);
        assertEquals(AD_SELECTION_ID, (long) selectedOutcomeId);
    }

    @Test
    public void testAdOutcomeSelectorReturnsNullSuccess() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Map<Long, Double> adverts = Map.of(AD_SELECTION_ID, AD_BID);
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(WATERFALL_MEDIATION_LOGIC_PATH);

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mWaterfallMediationLogicJs, adverts, signals))
                .thenReturn(Futures.immediateFuture(null));

        Long selectedOutcomeId =
                waitForFuture(() -> mAdOutcomeSelector.runAdOutcomeSelector(adverts, signals, uri));

        mMockWebServerRule.verifyMockServerRequests(
                server,
                1,
                Collections.singletonList(WATERFALL_MEDIATION_LOGIC_PATH),
                mRequestMatcherExactMatch);
        assertNull(selectedOutcomeId);
    }

    @Test
    public void testAdOutcomeSelectorJsonException() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Map<Long, Double> adverts = Map.of(AD_SELECTION_ID, AD_BID);
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(WATERFALL_MEDIATION_LOGIC_PATH);

        String jsonExceptionMessage = "Badly formatted JSON";
        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mWaterfallMediationLogicJs, adverts, signals))
                .thenThrow(new JSONException(jsonExceptionMessage));

        ExecutionException exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                waitForFuture(
                                        () ->
                                                mAdOutcomeSelector.runAdOutcomeSelector(
                                                        adverts, signals, uri)));
        Assert.assertTrue(exception.getCause() instanceof JSONException);
        Assert.assertEquals(exception.getCause().getMessage(), jsonExceptionMessage);
        mMockWebServerRule.verifyMockServerRequests(
                server,
                1,
                Collections.singletonList(WATERFALL_MEDIATION_LOGIC_PATH),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testAdOutcomeSelectorTimeout() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionSelectingOutcomeTimeoutMs() {
                        return 300;
                    }
                };

        Map<Long, Double> adverts = Map.of(AD_SELECTION_ID, AD_BID);
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(WATERFALL_MEDIATION_LOGIC_PATH);

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mWaterfallMediationLogicJs, adverts, signals))
                .thenAnswer(
                        (invocation) ->
                                getOutcomeWithDelay(AD_SELECTION_ID, flagsWithSmallerLimits));

        ExecutionException exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                waitForFuture(
                                        () ->
                                                mAdOutcomeSelector.runAdOutcomeSelector(
                                                        adverts, signals, uri)));
        Assert.assertTrue(exception.getCause() instanceof UncheckedTimeoutException);
        Assert.assertEquals(exception.getCause().getMessage(), OUTCOME_SELECTION_TIMED_OUT);
    }

    private ListenableFuture<Long> getOutcomeWithDelay(Long outcomeId, @NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionSelectingOutcomeTimeoutMs());
                    return outcomeId;
                });
    }

    private <T> T waitForFuture(
            AdsScoreGeneratorImplTest.ThrowingSupplier<ListenableFuture<T>> function)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<T> futureResult = function.get();
        futureResult.addListener(resultLatch::countDown, mLightweightExecutorService);
        resultLatch.await();
        return futureResult.get();
    }
}
