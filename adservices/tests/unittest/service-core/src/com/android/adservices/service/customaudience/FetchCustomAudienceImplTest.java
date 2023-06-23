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

import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;

import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponseString;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
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
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
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
    private static final int API_NAME =
            AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
    private final Clock mClock = CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(100);
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    @Spy
    private final AdServicesHttpsClient mHttpClientSpy =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private Uri mFetchUri;
    private FetchCustomAudienceImpl mFetchCustomAudienceImpl;
    private FetchAndJoinCustomAudienceInput.Builder mInputBuilder;
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

        mInputBuilder =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        Flags flags = new FetchCustomAudienceFlags();

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDaoMock, mFrequencyCapDaoMock, flags);

        mFetchCustomAudienceImpl = getImplWithFlags(flags);

        doReturn(BUYER)
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        doReturn(
                        CustomAudienceStats.builder()
                                .setTotalCustomAudienceCount(1)
                                .setBuyer(BUYER)
                                .setOwner(VALID_OWNER)
                                .setPerOwnerCustomAudienceCount(1)
                                .setPerBuyerCustomAudienceCount(1)
                                .setTotalBuyerCount(1)
                                .setTotalOwnerCount(1)
                                .build())
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceStats(eq(VALID_OWNER));
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testImpl_disabled() throws Exception {
        // Use flag value to disable the API.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public boolean getFledgeFetchCustomAudienceEnabled() {
                                return false;
                            }
                        });

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        VALID_BUYER_1))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(0, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INTERNAL_ERROR), anyInt());
    }

    @Test
    public void testImpl_invalidPackageName_throws() throws Exception {
        String otherPackageName = VALID_OWNER + "incorrectPackage";

        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        otherPackageName,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        FetchCustomAudienceTestCallback callback =
                callFetchCustomAudience(
                        mInputBuilder.setCallerPackageName(otherPackageName).build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_UNAUTHORIZED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_UNAUTHORIZED), anyInt());
    }

    @Test
    public void testImpl_throttled_throws() throws Exception {
        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_RATE_LIMIT_REACHED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                RATE_LIMIT_REACHED_ERROR_MESSAGE, callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_RATE_LIMIT_REACHED), anyInt());
    }

    @Test
    public void testImpl_failedForegroundCheck_throws() throws Exception {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_BACKGROUND_CALLER, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_BACKGROUND_CALLER), anyInt());
    }

    @Test
    public void testImpl_failedEnrollmentCheck_throws() throws Exception {
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
    }

    @Test
    public void testImpl_appCannotUsePPAPI_throws() throws Exception {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_CALLER_NOT_ALLOWED), anyInt());
    }

    @Test
    public void testImpl_revokedConsent_failsSilently() throws Exception {
        doThrow(new ConsentManager.RevokedConsentException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertTrue(callback.mIsSuccess);

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_USER_CONSENT_REVOKED), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_quotaExhausted_throws() throws Exception {
        // Use flag values with a clearly small quota limits.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public long getFledgeCustomAudienceMaxOwnerCount() {
                                return 0;
                            }

                            @Override
                            public long getFledgeCustomAudienceMaxCount() {
                                return 0;
                            }

                            @Override
                            public long getFledgeCustomAudiencePerAppMaxCount() {
                                return 0;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidName_throws() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeCustomAudienceMaxNameSizeB() {
                                return 1;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidActivationTime_throws() throws Exception {
        mInputBuilder.setActivationTime(INVALID_DELAYED_ACTIVATION_TIME);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidExpirationTime_throws() throws Exception {
        mInputBuilder.setExpirationTime(INVALID_BEYOND_MAX_EXPIRATION_TIME);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidUserBiddingSignals_throws() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB() {
                                return 1;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_customHeaderExceedsLimit_throws() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB() {
                                return 1;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_runNormally() throws Exception {
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(getFullSuccessfulJsonResponseString(BUYER))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(eq(API_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    private FetchCustomAudienceTestCallback callFetchCustomAudience(
            FetchAndJoinCustomAudienceInput input) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceTestCallback callback = new FetchCustomAudienceTestCallback(resultLatch);
        mFetchCustomAudienceImpl.doFetchCustomAudience(input, callback);
        resultLatch.await();
        return callback;
    }

    private FetchCustomAudienceImpl getImplWithFlags(Flags flags) {
        return new FetchCustomAudienceImpl(
                flags,
                mClock,
                mAdServicesLoggerMock,
                DIRECT_EXECUTOR,
                mCustomAudienceDaoMock,
                CallingAppUidSupplierProcessImpl.create(),
                mCustomAudienceServiceFilterMock,
                mHttpClientSpy,
                mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                mAdRenderIdValidator,
                AD_DATA_CONVERSION_STRATEGY);
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
