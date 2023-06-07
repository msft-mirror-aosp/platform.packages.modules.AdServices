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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.os.RemoteException;

import com.android.adservices.LogUtil;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

@RunWith(MockitoJUnitRunner.class)
public class FetchCustomAudienceImplTest {
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    private final Clock mClock = CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;

    @Spy
    private final AdServicesHttpsClient mHttpClientSpy =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private Uri mFetchUri;
    private FetchCustomAudienceImpl mFetchCustomAudienceImpl;
    private FetchAndJoinCustomAudienceInput mValidInput;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .mockStatic(ConsentManager.class)
                        .startMocking();

        mFetchUri = mMockWebServerRule.uriForPath("/fetch");

        mValidInput =
                new FetchAndJoinCustomAudienceInput.Builder(
                                mFetchUri, CustomAudienceFixture.VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                        .build();

        mFetchCustomAudienceImpl =
                new FetchCustomAudienceImpl(
                        new FetchCustomAudienceFlags(),
                        mClock,
                        mAdServicesLoggerMock,
                        DIRECT_EXECUTOR,
                        mCustomAudienceDaoMock,
                        CallingAppUidSupplierProcessImpl.create(),
                        mConsentManagerMock,
                        mCustomAudienceServiceFilterMock,
                        mHttpClientSpy);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testFetchCustomAudience_disabled() throws Exception {
        Flags fetchCustomAudienceFlagDisabled =
                new FetchCustomAudienceFlags() {
                    @Override
                    public boolean getFledgeFetchCustomAudienceEnabled() {
                        return false;
                    }
                };

        mFetchCustomAudienceImpl =
                new FetchCustomAudienceImpl(
                        fetchCustomAudienceFlagDisabled,
                        mClock,
                        mAdServicesLoggerMock,
                        DIRECT_EXECUTOR,
                        mCustomAudienceDaoMock,
                        CallingAppUidSupplierProcessImpl.create(),
                        mConsentManagerMock,
                        mCustomAudienceServiceFilterMock,
                        mHttpClientSpy);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mValidInput);

        assertFalse(callback.mIsSuccess);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testFetchCustomAudience_runNormally() throws Exception {
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                FetchCustomAudienceFixture
                                                        .getFullSuccessfulJsonResponseString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mValidInput);

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                AdTechIdentifier.fromString("localhost")));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    private FetchCustomAudienceTestCallback callFetchCustomAudience(
            FetchAndJoinCustomAudienceInput input) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceTestCallback callback = new FetchCustomAudienceTestCallback(resultLatch);
        mFetchCustomAudienceImpl.doFetchCustomAudience(input, callback);
        resultLatch.await();
        return callback;
    }

    public static class FetchCustomAudienceTestCallback
            extends FetchAndJoinCustomAudienceCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public FetchCustomAudienceTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() {
            LogUtil.v("Reporting success to FetchCustomAudienceTestCallback.");
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            LogUtil.v("Reporting failure to FetchCustomAudienceTestCallback.");
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class FetchCustomAudienceFlags implements Flags {
        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }
    }
}
