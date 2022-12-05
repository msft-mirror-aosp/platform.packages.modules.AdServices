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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdServicesLoggerUtil.UNSET;
import static com.android.adservices.service.stats.UpdateCustomAudienceExecutionLoggerTest.UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.android.adservices.LogUtil;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.Clock;
import com.android.adservices.service.stats.UpdateCustomAudienceExecutionLogger;
import com.android.adservices.service.stats.UpdateCustomAudienceProcessReportedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BackgroundFetchRunnerTest {
    private final Flags mFlags = FlagsFactory.getFlagsForTest();
    private final String mFetchPath = "/fetch";

    private MockitoSession mStaticMockSession = null;

    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private PackageManager mPackageManagerMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;

    private BackgroundFetchRunner mBackgroundFetchRunnerSpy;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    private Uri mFetchUri;
    @Mock Clock mClockMock;
    @Mock AdServicesLogger mAdServicesLoggerMock;
    private UpdateCustomAudienceExecutionLogger mUpdateCustomAudienceExecutionLogger;

    @Captor
    private ArgumentCaptor<UpdateCustomAudienceProcessReportedStats>
            mUpdateCustomAudienceProcessReportedStatsArgumentCaptor;

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .startMocking();
        when(mClockMock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        mUpdateCustomAudienceExecutionLogger =
                new UpdateCustomAudienceExecutionLogger(mClockMock, mAdServicesLoggerMock);
        mBackgroundFetchRunnerSpy =
                ExtendedMockito.spy(
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                mFlags));

        mFetchUri = mMockWebServerRule.uriForPath(mFetchPath);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
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
                .deleteAllDisallowedOwnerCustomAudienceData(mPackageManagerMock, mFlags);
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
                .deleteAllDisallowedBuyerCustomAudienceData(mEnrollmentDaoMock, mFlags);
    }

    @Test
    public void testBackgroundFetchRunnerNullInputsCauseFailure() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                null, mPackageManagerMock, mEnrollmentDaoMock, mFlags));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock, null, mEnrollmentDaoMock, mFlags));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock, mPackageManagerMock, null, mFlags));
        assertThrows(
                NullPointerException.class,
                () ->
                        new BackgroundFetchRunner(
                                mCustomAudienceDaoMock,
                                mPackageManagerMock,
                                mEnrollmentDaoMock,
                                null));
    }

    @Test
    public void testUpdateCustomAudienceWithEmptyUpdate() {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptySuccessfulResponse().build();
        doReturn(updatableData)
                .when(mBackgroundFetchRunnerSpy)
                .fetchAndValidateCustomAudienceUpdatableData(any(), any(), any());

        Instant originalEligibleUpdateTime = CommonFixture.FIXED_NOW.minusMillis(60L * 1000L);
        Instant expectedEligibleUpdateTime =
                DBCustomAudienceBackgroundFetchData
                        .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                CommonFixture.FIXED_NOW, mFlags);
        DBCustomAudienceBackgroundFetchData originalFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .build();
        DBCustomAudienceBackgroundFetchData expectedFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(expectedEligibleUpdateTime)
                        .build();
        when(mClockMock.elapsedRealtime())
                .thenReturn(UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        mBackgroundFetchRunnerSpy.updateCustomAudience(
                CommonFixture.FIXED_NOW, originalFetchData, mUpdateCustomAudienceExecutionLogger);

        verify(mCustomAudienceDaoMock)
                .updateCustomAudienceAndBackgroundFetchData(
                        eq(expectedFetchData), eq(updatableData));
        verify(mCustomAudienceDaoMock, never()).persistCustomAudienceBackgroundFetchData(any());
        verifyUpdateCustomAudienceLogging(UNSET, UNSET, STATUS_SUCCESS);
    }

    @Test
    public void testUpdateCustomAudienceWithFailedUpdate() {
        CustomAudienceUpdatableData updatableData =
                CustomAudienceUpdatableDataFixture.getValidBuilderEmptyFailedResponse().build();
        doReturn(updatableData)
                .when(mBackgroundFetchRunnerSpy)
                .fetchAndValidateCustomAudienceUpdatableData(any(), any(), any());

        Instant originalEligibleUpdateTime = CommonFixture.FIXED_NOW.minusMillis(60L * 1000L);
        DBCustomAudienceBackgroundFetchData originalFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .build();
        DBCustomAudienceBackgroundFetchData expectedFetchData =
                DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setEligibleUpdateTime(originalEligibleUpdateTime)
                        .setNumValidationFailures(1)
                        .build();
        when(mClockMock.elapsedRealtime())
                .thenReturn(UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        mBackgroundFetchRunnerSpy.updateCustomAudience(
                CommonFixture.FIXED_NOW, originalFetchData, mUpdateCustomAudienceExecutionLogger);

        verify(mCustomAudienceDaoMock, never())
                .updateCustomAudienceAndBackgroundFetchData(any(), any());
        verify(mCustomAudienceDaoMock)
                .persistCustomAudienceBackgroundFetchData(eq(expectedFetchData));
        verifyUpdateCustomAudienceLogging(UNSET, UNSET, STATUS_INTERNAL_ERROR);
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
                mBackgroundFetchRunnerSpy.fetchAndValidateCustomAudienceUpdatableData(
                        CommonFixture.FIXED_NOW, CommonFixture.VALID_BUYER_1, mFetchUri);

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
                mBackgroundFetchRunnerSpy.fetchAndValidateCustomAudienceUpdatableData(
                        CommonFixture.FIXED_NOW, CommonFixture.VALID_BUYER_1, mFetchUri);

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    @Test
    public void testFetchAndValidateCustomAudienceUpdatableDataNetworkTimeout() throws Exception {
        class FlagsWithSmallLimits implements Flags {
            @Override
            public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
                return 30;
            }

            @Override
            public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
                return 50;
            }
        }

        BackgroundFetchRunner runnerWithSmallLimits =
                new BackgroundFetchRunner(
                        mCustomAudienceDaoMock,
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        new FlagsWithSmallLimits());

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
                                    LogUtil.e(exception, "Failed to create JSON full response");
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
                runnerWithSmallLimits.fetchAndValidateCustomAudienceUpdatableData(
                        CommonFixture.FIXED_NOW, CommonFixture.VALID_BUYER_1, mFetchUri);

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
                mBackgroundFetchRunnerSpy.fetchAndValidateCustomAudienceUpdatableData(
                        CommonFixture.FIXED_NOW, CommonFixture.VALID_BUYER_1, invalidFetchUri);

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
                        mPackageManagerMock,
                        mEnrollmentDaoMock,
                        new FlagsWithSmallLimits());

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
                runnerWithSmallLimits.fetchAndValidateCustomAudienceUpdatableData(
                        CommonFixture.FIXED_NOW, CommonFixture.VALID_BUYER_1, mFetchUri);

        assertEquals(expectedUpdatableData, updatableData);

        assertEquals(1, mockWebServer.getRequestCount());
        RecordedRequest fetchRequest = mockWebServer.takeRequest();
        assertEquals(mFetchPath, fetchRequest.getPath());
    }

    private void verifyUpdateCustomAudienceLogging(
            int numOfAds, int dataSizeOfAdsInBytes, int resultCode) {
        verify(mAdServicesLoggerMock)
                .logUpdateCustomAudienceProcessReportedStats(
                        mUpdateCustomAudienceProcessReportedStatsArgumentCaptor.capture());
        UpdateCustomAudienceProcessReportedStats updateCustomAudienceProcessReportedStats =
                mUpdateCustomAudienceProcessReportedStatsArgumentCaptor.getValue();

        assertThat(updateCustomAudienceProcessReportedStats.getLatencyInMills())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - UPDATE_CUSTOM_AUDIENCE_START_TIMESTAMP));
        assertThat(updateCustomAudienceProcessReportedStats.getResultCode()).isEqualTo(resultCode);
        assertThat(updateCustomAudienceProcessReportedStats.getNumOfAds()).isEqualTo(numOfAds);
        assertThat(updateCustomAudienceProcessReportedStats.getDataSizeOfAdsInBytes())
                .isEqualTo(dataSizeOfAdsInBytes);
    }
}
