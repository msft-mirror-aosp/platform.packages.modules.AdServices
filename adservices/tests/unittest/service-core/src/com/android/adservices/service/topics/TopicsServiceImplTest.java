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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.GetTopicsResult;
import android.adservices.topics.IGetTopicsCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.ResultCode;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.topics.Topic;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;

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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link com.android.adservices.service.topics.TopicsServiceImpl}.
 */
public class TopicsServiceImplTest {
    private static final String SOME_PACKAGE_NAME = "SomePackageName";
    private static final String SOME_ATTRIBUTION_TAG = "SomeAttributionTag";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final int SOME_UID = 11;
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());

    private TopicsServiceImpl mTopicsServiceImpl;
    private CountDownLatch mGetTopicsCallbackLatch;
    private CallerMetadata mCallerMetadata;
    private TopicsDao mTopicsDao;

    @Mock private EpochManager mMockEpochManager;
    @Mock private Flags mMockFlags;
    @Mock private Clock mClock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Clean DB before each test
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
        CacheManager cacheManager = new CacheManager(mMockEpochManager,
                mTopicsDao,
                mMockFlags);
        TopicsWorker mTopicsWorker = new TopicsWorker(mMockEpochManager,
                cacheManager,
                mMockFlags);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        mTopicsServiceImpl = new TopicsServiceImpl(mContext,
                mTopicsWorker, mAdServicesLogger, mClock);
        mCallerMetadata = new CallerMetadata.Builder()
                .setBinderElapsedTimestamp(100L)
                .build();
    }

    @Test
    public void getTopics() throws InterruptedException {
        AttributionSource source =
                new AttributionSource.Builder(SOME_UID)
                        .setPackageName(SOME_PACKAGE_NAME)
                        .setAttributionTag(SOME_ATTRIBUTION_TAG)
                        .build();
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAttributionSource(source)
                        .setSdkName(SOME_SDK_NAME)
                        .build();

        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;
        final Pair<String, String> appSdkKey = Pair.create(SOME_PACKAGE_NAME, SOME_SDK_NAME);
        Topic topic1 = Topic.create(/* topic */ 1, /* taxonomyVersion = */ 1L,
                /* modelVersion = */ 4L);
        Topic topic2 = Topic.create(/* topic */ 2, /* taxonomyVersion = */ 2L,
                /* modelVersion = */ 5L);
        Topic topic3 = Topic.create(/* topic */ 3, /* taxonomyVersion = */ 3L,
                /* modelVersion = */ 6L);
        Topic[] topics = {topic1, topic2, topic3};
        // persist returned topics into DB
        for (int numEpoch = 1; numEpoch <= numberOfLookBackEpochs; numEpoch++) {
            Topic currentTopic = topics[numberOfLookBackEpochs - numEpoch];
            Map<Pair<String, String>, Integer> returnedAppSdkTopicsMap = new HashMap<>();
            returnedAppSdkTopicsMap.put(appSdkKey, currentTopic.getTopic());
            mTopicsDao.persistReturnedAppTopicsMap(numEpoch, currentTopic.getTaxonomyVersion(),
                    currentTopic.getModelVersion(), returnedAppSdkTopicsMap);
        }

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        GetTopicsResult getTopicsResult =
                new GetTopicsResult.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L, 2L, 3L))
                        .setModelVersions(Arrays.asList(4L, 5L, 6L))
                        .setTopics(Arrays.asList(1, 2, 3))
                        .build();

        // Call init() to load the cache
        mTopicsServiceImpl.init();

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];
        mGetTopicsCallbackLatch = new CountDownLatch(1);

        mTopicsServiceImpl.getTopics(
                request,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) throws RemoteException {
                        capturedResponseParcel[0] = responseParcel;
                        mGetTopicsCallbackLatch.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        assertThat(mGetTopicsCallbackLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(capturedResponseParcel[0]).isEqualTo(getTopicsResult);

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, Mockito.times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, Mockito.times(2)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_emptyTopicsReturned() throws InterruptedException {
        AttributionSource source =
                new AttributionSource.Builder(SOME_UID)
                        .setPackageName(SOME_PACKAGE_NAME)
                        .setAttributionTag(SOME_ATTRIBUTION_TAG)
                        .build();
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAttributionSource(source)
                        .setSdkName(SOME_SDK_NAME)
                        .build();

        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // No topics (empty list) were returned.
        GetTopicsResult getTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        // Call init() to load the cache
        mTopicsServiceImpl.init();

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];
        mGetTopicsCallbackLatch = new CountDownLatch(1);

        mTopicsServiceImpl.getTopics(
                request,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) throws RemoteException {
                        capturedResponseParcel[0] = responseParcel;
                        mGetTopicsCallbackLatch.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        assertThat(mGetTopicsCallbackLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        assertThat(capturedResponseParcel[0]).isEqualTo(getTopicsResult);

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, Mockito.times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, Mockito.times(2)).getTopicsNumberOfLookBackEpochs();
    }

    @Test
    public void testGetTopics_LatencyCalculateVerify() throws InterruptedException {
        AttributionSource source =
                new AttributionSource.Builder(SOME_UID)
                        .setPackageName(SOME_PACKAGE_NAME)
                        .setAttributionTag(SOME_ATTRIBUTION_TAG)
                        .build();
        GetTopicsParam request =
                new GetTopicsParam.Builder()
                        .setAttributionSource(source)
                        .setSdkName(SOME_SDK_NAME)
                        .build();

        final long currentEpochId = 4L;
        final int numberOfLookBackEpochs = 3;

        when(mMockEpochManager.getCurrentEpochId()).thenReturn(currentEpochId);
        when(mMockFlags.getTopicsNumberOfLookBackEpochs()).thenReturn(numberOfLookBackEpochs);

        // No topics (empty list) were returned.
        GetTopicsResult getTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Collections.emptyList())
                        .setModelVersions(Collections.emptyList())
                        .setTopics(Collections.emptyList())
                        .build();

        // Call init() to load the cache
        mTopicsServiceImpl.init();

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
        mTopicsServiceImpl.getTopics(
                request,
                mCallerMetadata,
                new IGetTopicsCallback() {
                    @Override
                    public void onResult(GetTopicsResult responseParcel) throws RemoteException {
                        capturedResponseParcel[0] = responseParcel;
                        mGetTopicsCallbackLatch.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        assertThat(mGetTopicsCallbackLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(capturedResponseParcel[0]).isEqualTo(getTopicsResult);

        assertThat(logOperationCalledLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        verify(mAdServicesLogger).logApiCallStats(argument.capture());
        assertThat(argument.getValue().getResultCode()).isEqualTo(ResultCode.RESULT_OK);
        assertThat(argument.getValue().getAppPackageName()).isEqualTo(SOME_PACKAGE_NAME);
        assertThat(argument.getValue().getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        // The latency calculate result (200 - 150) + (150 - 100) * 2 = 150
        assertThat(argument.getValue().getLatencyMillisecond()).isEqualTo(150);

        // loadcache() and getTopics() in CacheManager calls this mock
        verify(mMockEpochManager, Mockito.times(2)).getCurrentEpochId();
        // getTopics in CacheManager and TopicsWorker calls this mock
        verify(mMockFlags, Mockito.times(2)).getTopicsNumberOfLookBackEpochs();
    }
}
