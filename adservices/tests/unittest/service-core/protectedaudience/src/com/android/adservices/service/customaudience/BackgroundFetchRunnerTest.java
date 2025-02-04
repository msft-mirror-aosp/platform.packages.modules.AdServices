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

package com.android.adservices.service.customaudience;

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.OMIT_ADS_VALUE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.http.MockWebServerRule;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.CustomAudienceLoggerFactory;
import com.android.adservices.service.stats.UpdateCustomAudienceExecutionLogger;
import com.android.adservices.shared.testing.SkipLoggingUsageRule;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@SpyStatic(FlagsFactory.class)
@SetFlagEnabled(KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED)
// TODO (b/384952360): refine CEL related verifications later
@SkipLoggingUsageRule(reason = "b/384952360")
public final class BackgroundFetchRunnerTest extends AdServicesExtendedMockitoTestCase {

    private final String mFetchPath = "/fetch";

    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private PackageManager mPackageManagerMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private CustomAudienceLoggerFactory mCustomAudienceLoggerFactoryMock;
    @Mock private UpdateCustomAudienceExecutionLogger mUpdateCustomAudienceExecutionLoggerMock;

    private BackgroundFetchRunner mBackgroundFetchRunnerSpy;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    private Uri mFetchUri;

    @Before
    public void setup() {
        mocker.mockGetFlags(mFakeFlags);
        when(mCustomAudienceLoggerFactoryMock.getUpdateCustomAudienceExecutionLogger())
                .thenReturn(mUpdateCustomAudienceExecutionLoggerMock);

        mBackgroundFetchRunnerSpy =
                ExtendedMockito.spy(
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mAppInstallDaoMock,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                mFakeFlags,
                                mCustomAudienceLoggerFactoryMock));

        mFetchUri = mMockWebServerRule.uriForPath(mFetchPath);
    }

    @Test
    public void testDeleteExpiredCustomAudiences() {
        mBackgroundFetchRunnerSpy.deleteExpiredCustomAudiences(CommonFixture.FIXED_NOW);

        verify(mCustomAudienceDaoMock).deleteAllExpiredCustomAudienceData(CommonFixture.FIXED_NOW);
    }

    @Test
    public void testDeleteDisallowedOwnerCustomAudiences() {
        doReturn(
                        CustomAudienceStats.builder()
                                .setTotalCustomAudienceCount(10)
                                .setTotalOwnerCount(2)
                                .build())
                .when(mCustomAudienceDaoMock)
                .deleteAllDisallowedOwnerCustomAudienceData(any(), any());

        mBackgroundFetchRunnerSpy.deleteDisallowedOwnerCustomAudiences();

        verify(mCustomAudienceDaoMock)
                .deleteAllDisallowedOwnerCustomAudienceData(mPackageManagerMock, mFakeFlags);
    }

    @Test
    public void deleteDisallowedPackageAppInstallEntries() {
        doReturn(2).when(mAppInstallDaoMock).deleteAllDisallowedPackageEntries(any(), any());

        mBackgroundFetchRunnerSpy.deleteDisallowedPackageAppInstallEntries();

        verify(mAppInstallDaoMock)
                .deleteAllDisallowedPackageEntries(mPackageManagerMock, mFakeFlags);
    }

    @Test
    public void testDeleteDisallowedBuyerCustomAudiences() {
        doReturn(
                        CustomAudienceStats.builder()
                                .setTotalCustomAudienceCount(12)
                                .setTotalBuyerCount(3)
                                .build())
                .when(mCustomAudienceDaoMock)
                .deleteAllDisallowedBuyerCustomAudienceData(any(), any());

        mBackgroundFetchRunnerSpy.deleteDisallowedBuyerCustomAudiences();

        verify(mCustomAudienceDaoMock)
                .deleteAllDisallowedBuyerCustomAudienceData(mEnrollmentDaoMock, mFakeFlags);
    }

    @Test
    public void testBackgroundFetchRunnerNullInputsCauseFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                null,
                                mAppInstallDaoMock,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                mFakeFlags,
                                mCustomAudienceLoggerFactoryMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                null,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                mFakeFlags,
                                mCustomAudienceLoggerFactoryMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mAppInstallDaoMock,
                                null,
                                mEnrollmentDaoMock,
                                mFakeFlags,
                                mCustomAudienceLoggerFactoryMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mAppInstallDaoMock,
                                mPackageManagerMock,
                                null,
                                mFakeFlags,
                                mCustomAudienceLoggerFactoryMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mAppInstallDaoMock,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                null,
                                mCustomAudienceLoggerFactoryMock));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mAppInstallDaoMock,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                mFakeFlags,
                                null));
    }

    @Test
    public void testUpdateCustomAudienceWithEmptyUpdate()
            throws ExecutionException, InterruptedException {
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);

        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse().build();
        doReturn(FluentFuture.from(immediateFuture(updatableData)))
                .when(mBackgroundFetchRunnerSpy)
                .fetchAndValidateCustomAudienceUpdatableData(any(), any(), any(), anyBoolean());

        Instant originalEligibleUpdateTime = CommonFixture.FIXED_NOW.minusMillis(60L * 1000L);
        Instant expectedEligibleUpdateTime =
                DBCustomAudienceBackgroundFetchData
                        .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                CommonFixture.FIXED_NOW, mFakeFlags);
        DBCustomAudienceBackgroundFetchData originalFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .setIsDebuggable(false)
                        .build();
        DBCustomAudienceBackgroundFetchData expectedFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(expectedEligibleUpdateTime)
                        .setIsDebuggable(false)
                        .build();

        mBackgroundFetchRunnerSpy
                .updateCustomAudience(CommonFixture.FIXED_NOW, originalFetchData)
                .get();

        verify(mCustomAudienceDaoMock)
                .updateCustomAudienceAndBackgroundFetchData(
                        eq(expectedFetchData), eq(updatableData));
        verify(mCustomAudienceDaoMock, never()).persistCustomAudienceBackgroundFetchData(any());
    }

    @Test
    public void testUpdateCustomAudienceWithFailedUpdate()
            throws ExecutionException, InterruptedException {
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse().build();

        doReturn(FluentFuture.from(immediateFuture(updatableData)))
                .when(mBackgroundFetchRunnerSpy)
                .fetchAndValidateCustomAudienceUpdatableData(any(), any(), any(), anyBoolean());

        Instant originalEligibleUpdateTime = CommonFixture.FIXED_NOW.minusMillis(60L * 1000L);
        DBCustomAudienceBackgroundFetchData originalFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .setIsDebuggable(false)
                        .build();
        DBCustomAudienceBackgroundFetchData expectedFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .setNumValidationFailures(1)
                        .setIsDebuggable(false)
                        .build();

        mBackgroundFetchRunnerSpy
                .updateCustomAudience(CommonFixture.FIXED_NOW, originalFetchData)
                .get();

        verify(mCustomAudienceDaoMock, never())
                .updateCustomAudienceAndBackgroundFetchData(any(), any());
        verify(mCustomAudienceDaoMock)
                .persistCustomAudienceBackgroundFetchData(eq(expectedFetchData));
    }

    @Test
    public void testFetchAndValidateSuccessfulEmptyCustomAudienceUpdatableData() throws Exception {
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                CustomAudienceUpdatableDataFixture
                                                        .getEmptyJsonResponseString())));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse().build();

        CustomAudienceUpdatableData updatableData =
                mBackgroundFetchRunnerSpy
                        .fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @Test
    public void testFetchAndValidateSuccessfulFullCustomAudienceUpdatableData() throws Exception {
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                CustomAudienceUpdatableDataFixture
                                                        .getFullSuccessfulJsonResponseString())));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderFullSuccessfulResponse().build();

        CustomAudienceUpdatableData updatableData =
                mBackgroundFetchRunnerSpy
                        .fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_APP_INSTALL_FILTERING_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_REQUEST_FLAGS_ENABLED)
    public void
            testFetchAndValidateSuccessfulFullCustomAudienceUpdatableDataWithAuctionServerRequestFlagsEnabled()
                    throws Exception {
        BackgroundFetchRunner runner =
                new BackgroundFetchRunner(
                        mCustomAudienceDaoMock,
                        mAppInstallDaoMock,
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        mFakeFlags,
                        mCustomAudienceLoggerFactoryMock);

        String jsonResponseString =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                        CommonFixture.VALID_BUYER_1)
                                .build(),
                        DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1),
                        ImmutableList.of(OMIT_ADS_VALUE));

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(jsonResponseString)));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderFullSuccessfulResponse()
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build();

        CustomAudienceUpdatableData updatableData =
                runner.fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @Test
    public void
            testFetchAndValidateSuccessfulFullCustomAudienceUpdatableDataWithAuctionServerRequestFlagsDisabled()
                    throws Exception {
        BackgroundFetchRunner runner =
                new BackgroundFetchRunner(
                        mCustomAudienceDaoMock,
                        mAppInstallDaoMock,
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        mFakeFlags,
                        mCustomAudienceLoggerFactoryMock);

        String jsonResponseString =
                CustomAudienceUpdatableDataFixture.toJsonResponseString(
                        CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS.toString(),
                        DBTrustedBiddingDataFixture.getValidBuilderByBuyer(
                                        CommonFixture.VALID_BUYER_1)
                                .build(),
                        DBAdDataFixture.getValidDbAdDataListByBuyer(CommonFixture.VALID_BUYER_1),
                        ImmutableList.of(OMIT_ADS_VALUE));

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(jsonResponseString)));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderFullSuccessfulResponse().build();

        CustomAudienceUpdatableData updatableData =
                runner.fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @SetIntegerFlag(name = KEY_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS, value = 50)
    @Test
    public void testFetchAndValidateCustomAudienceUpdatableDataNetworkTimeout() throws Exception {
        BackgroundFetchRunner runnerWithSmallLimits =
                new BackgroundFetchRunner(
                        mCustomAudienceDaoMock,
                        mAppInstallDaoMock,
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        mFakeFlags,
                        mCustomAudienceLoggerFactoryMock);

        CountDownLatch responseLatch = new CountDownLatch(1);
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request)
                                    throws InterruptedException {
                                Thread.sleep(70);
                                try {
                                    return new MockResponse()
                                            .setBody(
                                                    CustomAudienceUpdatableDataFixture
                                                            .getFullSuccessfulJsonResponseString());
                                } catch (JSONException exception) {
                                    mLog.e(exception, "Failed to create JSON full response");
                                    return null;
                                } finally {
                                    responseLatch.countDown();
                                }
                            }
                        });
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse()
                        .setInitialUpdateResult(
                                BackgroundFetchRunner.UpdateResultType.NETWORK_FAILURE)
                        .build();

        CustomAudienceUpdatableData updatableData =
                runnerWithSmallLimits
                        .fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertTrue(responseLatch.await(150, TimeUnit.MILLISECONDS));
        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @Test
    public void testFetchWithInvalidUriAndValidateCustomAudienceUpdatableData() throws Exception {
        Uri invalidFetchUri = Uri.parse("https://localhost:-1/fetch");
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                CustomAudienceUpdatableDataFixture
                                                        .getFullSuccessfulJsonResponseString())));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse()
                        .setInitialUpdateResult(BackgroundFetchRunner.UpdateResultType.UNKNOWN)
                        .build();

        CustomAudienceUpdatableData updatableData =
                mBackgroundFetchRunnerSpy
                        .fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                invalidFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(0, mockWebServer.getRequestCount());
    }

    @Test
    public void testFetchAndValidateCustomAudienceUpdatableDataResponseTooLarge() throws Exception {
        class FlagsWithSmallLimits implements Flags {
            @Override
            public int getFledgeBackgroundFetchMaxResponseSizeB() {
                return 10;
            }
        }

        BackgroundFetchRunner runnerWithSmallLimits =
                new BackgroundFetchRunner(
                        mCustomAudienceDaoMock,
                        mAppInstallDaoMock,
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        new FlagsWithSmallLimits(),
                        mCustomAudienceLoggerFactoryMock);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                CustomAudienceUpdatableDataFixture
                                                        .getFullSuccessfulJsonResponseString())));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse()
                        .setInitialUpdateResult(
                                BackgroundFetchRunner.UpdateResultType.NETWORK_FAILURE)
                        .build();

        CustomAudienceUpdatableData updatableData =
                runnerWithSmallLimits
                        .fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                DevContext.createForDevOptionsDisabled()
                                        .getDeviceDevOptionsEnabled())
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @Test
    public void testFetchAndValidateCustomAudienceUpdatableDataDebuggable() throws Exception {
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                CustomAudienceUpdatableDataFixture
                                                        .getFullSuccessfulJsonResponseString())));
        CustomAudienceUpdatableData expectedUpdatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderFullSuccessfulResponse().build();

        CustomAudienceUpdatableData updatableData =
                mBackgroundFetchRunnerSpy
                        .fetchAndValidateCustomAudienceUpdatableData(
                                CommonFixture.FIXED_NOW,
                                CommonFixture.VALID_BUYER_1,
                                mFetchUri,
                                /* debuggable= */ true)
                        .get();

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }
}
