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

package com.android.adservices.service.topics;

import static android.adservices.topics.TopicsManager.RESULT_OK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.GetTopicsResult;
import android.adservices.topics.IGetTopicsCallback;
import android.app.sdksandbox.SandboxedSdkContext;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.test.mock.MockContext;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.ResultCode;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit test for {@link com.android.adservices.service.topics.TopicsServiceImpl}. */
public class TopicsServiceImplTest {
    private static final String TEST_APP_PACKAGE_NAME = "com.android.adservices.servicecoretest";
    private static final String INVALID_PACKAGE_NAME = "com.do_not_exists";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;
    private static final String SDK_PACKAGE_NAME = "test_package_name";
    private static final String TOPICS_API_ALLOW_LIST = "com.android.adservices.servicecoretest";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());

    private CountDownLatch mGetTopicsCallbackLatch;
    private CallerMetadata mCallerMetadata;
    private TopicsWorker mTopicsWorker;
    private BlockedTopicsManager mBlockedTopicsManager;
    private TopicsDao mTopicsDao;
    private GetTopicsParam mRequest;

    @Mock private EpochManager mMockEpochManager;
    @Mock private ConsentManager mConsentManager;
    @Mock private PackageManager mPackageManager;
    @Mock private Flags mMockFlags;
    @Mock private Clock mClock;
    @Mock private SandboxedSdkContext mMockSdkContext;
    @Mock private Throttler mMockThrottler;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Clean DB before each test
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
        CacheManager cacheManager = new CacheManager(mMockEpochManager, mTopicsDao, mMockFlags);

        mBlockedTopicsManager = new BlockedTopicsManager(mTopicsDao);
        AppUpdateManager appUpdateManager =
                new AppUpdateManager(mTopicsDao, new Random(), mMockFlags);
        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        cacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        mCallerMetadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(100L).build();
        mRequest =
                new GetTopicsParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkName(SOME_SDK_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockSdkContext.getPackageManager()).thenReturn(mPackageManager);
        when(mMockSdkContext.getSdkName()).thenReturn(SOME_SDK_NAME);
        when(mMockFlags.getPpapiAppAllowList()).thenReturn(TOPICS_API_ALLOW_LIST);

        // Rate Limit is not reached.
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.TOPICS_API_SDK_NAME), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(
                        eq(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(true);
    }

    @Test
    public void checkAllowList_emptyList() {
        // Empty allow list, don't allow any app.
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("");
        invokeGetTopicsAndVerifyError(mContext, ResultCode.RESULT_UNAUTHORIZED_CALL);
    }

    @Test
    public void checkThrottler_rateLimitReached_forSdkName() {
        // A SDK calls Topics API.
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkName(SOME_SDK_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        // Rate Limit Reached.
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.TOPICS_API_SDK_NAME), anyString()))
                .thenReturn(false);
        invokeGetTopicsAndVerifyError(mContext, ResultCode.RESULT_RATE_LIMIT_REACHED, request);
    }

    @Test
    public void checkThrottler_rateLimitReached_forAppPackageName() {
        // App calls Topics API directly, not via a SDK.
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkName("") // Empty SdkName implies the app calls Topic API directly.
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        // Rate Limit Reached.
        when(mMockThrottler.tryAcquire(
                        eq(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(false);
        invokeGetTopicsAndVerifyError(mContext, ResultCode.RESULT_RATE_LIMIT_REACHED, request);
    }

    @Test
    public void checkNoPermission() {
        MockContext context =
                new MockContext() {
                    @Override
                    public int checkCallingOrSelfPermission(String permission) {
                        return PackageManager.PERMISSION_DENIED;
                    }

                    @Override
                    public PackageManager getPackageManager() {
                        return mPackageManager;
                    }
                };
        invokeGetTopicsAndVerifyError(context, ResultCode.RESULT_UNAUTHORIZED_CALL);
    }

    @Test
    public void checkSdkNoPermission() {
        when(mPackageManager.checkPermission(
                        PermissionHelper.ACCESS_ADSERVICES_TOPICS_PERMISSION, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        invokeGetTopicsAndVerifyError(mMockSdkContext, ResultCode.RESULT_UNAUTHORIZED_CALL);
    }

    @Test
    public void checkNoUserConsent() {
        MockContext context =
                new MockContext() {
                    @Override
                    public int checkCallingOrSelfPermission(String permission) {
                        return PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public PackageManager getPackageManager() {
                        return mPackageManager;
                    }
                };
        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.REVOKED);

        TopicsServiceImpl topicsService =
                new TopicsServiceImpl(
                        context,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);
        topicsService.getTopics(
                mRequest,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) throws RemoteException {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(int resultCode) {
                        assertThat(resultCode).isEqualTo(ResultCode.RESULT_UNAUTHORIZED_CALL);
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
    }

    @Test
    public void getTopics() throws Exception {
        runGetTopics(
                new TopicsServiceImpl(
                        mContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler));
    }

    @Test
    public void getTopicsSdk() throws Exception {
        // Since this is a mock PackageManager, return the same resources as the app.
        when(mPackageManager.getProperty(
                        AppManifestConfigHelper.AD_SERVICES_CONFIG_PROPERTY, TEST_APP_PACKAGE_NAME))
                .thenReturn(
                        mContext.getPackageManager()
                                .getProperty(
                                        AppManifestConfigHelper.AD_SERVICES_CONFIG_PROPERTY,
                                        TEST_APP_PACKAGE_NAME));
        when(mPackageManager.getResourcesForApplication(TEST_APP_PACKAGE_NAME))
                .thenReturn(
                        mContext.getPackageManager()
                                .getResourcesForApplication(TEST_APP_PACKAGE_NAME));
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0))
                .thenReturn(mContext.getPackageManager().getPackageUid(TEST_APP_PACKAGE_NAME, 0));

        when(mPackageManager.checkPermission(
                        PermissionHelper.ACCESS_ADSERVICES_TOPICS_PERMISSION, SDK_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        runGetTopics(
                new TopicsServiceImpl(
                        mContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler));
    }

    @Test
    public void getTopics_oneTopicBlocked() throws InterruptedException {
        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;
        List<Topic> topics = prepareAndPersistTopics(numberOfLookBackEpochs);

        // block topic1
        mBlockedTopicsManager.blockTopic(topics.get(0));

        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setTaxonomyVersions(Arrays.asList(2L, 3L))
                        .setModelVersions(Arrays.asList(5L, 6L))
                        .setTopics(Arrays.asList(2, 3))
                        .build();

        TopicsServiceImpl topicsServiceImpl =
                new TopicsServiceImpl(
                        mContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);

        // Call init() to load the cache
        topicsServiceImpl.init();

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = getTopicsResults(topicsServiceImpl);

        assertThat(
                        mGetTopicsCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        GetTopicsResult getTopicsResult = capturedResponseParcel[0];
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // Invocations Summary
        // loadCache() : 1, getTopics(): 1 * 2
        verify(mMockEpochManager, Mockito.times(3)).getCurrentEpochId();
        verify(mMockFlags, Mockito.times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void getTopics_allTopicsBlocked() throws InterruptedException {
        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;
        List<Topic> topics = prepareAndPersistTopics(numberOfLookBackEpochs);

        // block all topics
        for (Topic topic : topics) {
            mBlockedTopicsManager.blockTopic(topic);
        }

        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // No topics (empty list) were returned because all topics are blocked
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        TopicsServiceImpl topicsServiceImpl =
                new TopicsServiceImpl(
                        mContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);

        // Call init() to load the cache
        topicsServiceImpl.init();

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = getTopicsResults(topicsServiceImpl);

        assertThat(
                        mGetTopicsCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        GetTopicsResult getTopicsResult = capturedResponseParcel[0];

        assertThat(getTopicsResult).isEqualTo(expectedGetTopicsResult);

        // Invocation Summary:
        // loadCache(): 1, getTopics(): 2
        verify(mMockEpochManager, Mockito.times(3)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, Mockito.times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_emptyTopicsReturned() throws InterruptedException {
        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;

        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // No topics (empty list) were returned.
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        TopicsServiceImpl topicsServiceImpl =
                new TopicsServiceImpl(
                        mContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);

        // Call init() to load the cache
        topicsServiceImpl.init();

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = getTopicsResults(topicsServiceImpl);

        assertThat(
                        mGetTopicsCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        GetTopicsResult getTopicsResult = capturedResponseParcel[0];
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // Invocations Summary
        // loadCache() : 1, getTopics(): 1 * 2
        verify(mMockEpochManager, Mockito.times(3)).getCurrentEpochId();
        verify(mMockFlags, Mockito.times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_LatencyCalculateVerify() throws InterruptedException {
        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;

        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // No topics (empty list) were returned.
        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        TopicsServiceImpl topicsServiceImpl =
                new TopicsServiceImpl(
                        mContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);

        // Call init() to load the cache
        topicsServiceImpl.init();

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];
        mGetTopicsCallbackLatch = new CountDownLatch(1);

        // Topic impl service use a background executor to run the task,
        // use a countdownLatch and set the countdown in the logging call operation
        final CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        Mockito.doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // The method logAPiCallStats is called.
                invocation.callRealMethod();
                logOperationCalledLatch.countDown();
                return null;
            }
        }).when(mAdServicesLogger).logApiCallStats(
                ArgumentMatchers.any(ApiCallStats.class));

        // Setting up the timestamp for latency calculation, we passing in a client side call
        // timestamp as a parameter to the call (100 in the below code), in topic service, it
        // call for timestamp at the method start which will return 150, we get client side to
        // service latency as (start - client) * 2. The second time it calling for timestamp will
        // be at logging time which will return 200, we get service side latency as
        // (logging - start), thus the total latency is logging - start + (start - client) * 2,
        // which is 150 in these numbers
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);

        ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);

        // Send client side timestamp, working with the mock information in
        // service side to calculate the latency
        topicsServiceImpl.getTopics(
                mRequest,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) {
                        capturedResponseParcel[0] = responseParcel;
                        mGetTopicsCallbackLatch.countDown();
                    }

                    @Override
                    public void onFailure(int resultCode) {
                        Assert.fail();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        assertThat(
                        mGetTopicsCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        GetTopicsResult getTopicsResult = capturedResponseParcel[0];
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        assertThat(
                        logOperationCalledLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        verify(mAdServicesLogger).logApiCallStats(argument.capture());
        assertThat(argument.getValue().getResultCode()).isEqualTo(ResultCode.RESULT_OK);
        assertThat(argument.getValue().getAppPackageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(argument.getValue().getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        // The latency calculate result (200 - 150) + (150 - 100) * 2 = 150
        assertThat(argument.getValue().getLatencyMillisecond()).isEqualTo(150);

        // Invocations Summary
        // loadCache() : 1, getTopics(): 1 * 2
        verify(mMockEpochManager, Mockito.times(3)).getCurrentEpochId();
        verify(mMockFlags, Mockito.times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_enforceCallingPackage_invalidPackage() throws InterruptedException {
        MockContext context =
                new MockContext() {
                    @Override
                    public int checkCallingOrSelfPermission(String permission) {
                        return PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public PackageManager getPackageManager() {
                        return mPackageManager;
                    }
                };
        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;

        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        TopicsServiceImpl topicsService =
                new TopicsServiceImpl(
                        context,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);

        // A request with an invalid package name.
        mRequest =
                new GetTopicsParam.Builder()
                        .setAppPackageName(INVALID_PACKAGE_NAME)
                        .setSdkName(SOME_SDK_NAME)
                        .setSdkPackageName(SOME_SDK_NAME)
                        .build();

        mGetTopicsCallbackLatch = new CountDownLatch(1);

        topicsService.getTopics(
                mRequest,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) throws RemoteException {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(int resultCode) {
                        assertThat(resultCode).isEqualTo(ResultCode.RESULT_UNAUTHORIZED_CALL);
                        mGetTopicsCallbackLatch.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        // This ensures that the callback was called.
        assertThat(
                        mGetTopicsCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
    }

    @NonNull
    private void invokeGetTopicsAndVerifyError(Context context, int expectedResultCode) {
        invokeGetTopicsAndVerifyError(context, expectedResultCode, mRequest);
    }

    @NonNull
    private void invokeGetTopicsAndVerifyError(
            Context context, int expectedResultCode, GetTopicsParam request) {
        TopicsServiceImpl topicsService =
                new TopicsServiceImpl(
                        context,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler);
        topicsService.getTopics(
                request,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) throws RemoteException {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(int resultCode) {
                        assertThat(resultCode).isEqualTo(expectedResultCode);
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
    }

    @NonNull
    private void runGetTopics(TopicsServiceImpl topicsServiceImpl) throws Exception {
        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;
        prepareAndPersistTopics(numberOfLookBackEpochs);

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        GetTopicsResult expectedGetTopicsResult =
                new GetTopicsResult.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();

        // Call init() to load the cache
        topicsServiceImpl.init();
        final GetTopicsResult[] capturedResponseParcel = getTopicsResults(topicsServiceImpl);

        assertThat(
                        mGetTopicsCallbackLatch.await(
                                BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        GetTopicsResult getTopicsResult = capturedResponseParcel[0];
        // Since the returned topic list is shuffled, elements have to be verified separately
        assertThat(getTopicsResult.getResultCode())
                .isEqualTo(expectedGetTopicsResult.getResultCode());
        assertThat(getTopicsResult.getTaxonomyVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTaxonomyVersions());
        assertThat(getTopicsResult.getModelVersions())
                .containsExactlyElementsIn(expectedGetTopicsResult.getModelVersions());
        assertThat(getTopicsResult.getTopics())
                .containsExactlyElementsIn(expectedGetTopicsResult.getTopics());

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, Mockito.times(3)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, Mockito.times(3)).getTopicsNumberOfLookBackEpochs();
    }

    @NonNull
    private GetTopicsResult[] getTopicsResults(TopicsServiceImpl topicsServiceImpl) {
        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];
        mGetTopicsCallbackLatch = new CountDownLatch(1);

        topicsServiceImpl.getTopics(
                mRequest,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) {
                        capturedResponseParcel[0] = responseParcel;
                        mGetTopicsCallbackLatch.countDown();
                    }

                    @Override
                    public void onFailure(int resultCode) {
                        Assert.fail();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
        return capturedResponseParcel;
    }

    @NonNull
    private List<Topic> prepareAndPersistTopics(int numberOfLookBackEpochs) {
        final Pair<String, String> appSdkKey = Pair.create(TEST_APP_PACKAGE_NAME, SOME_SDK_NAME);
        Topic topic1 =
                Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L, /* modelVersion = */ 4L);
        Topic topic2 =
                Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L, /* modelVersion = */ 5L);
        Topic topic3 =
                Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L, /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Topic> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic);
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, returnedAppSdkTopicsMap);
        }
        return Arrays.asList(topics);
    }
}
