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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.ResultCode;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link com.android.adservices.service.topics.TopicsServiceImpl}.
 */
public class TopicsServiceImplTest {
    private static final String SOME_PACKAGE_NAME = "SomePackageName";
    private static final String SOME_ATTRIBUTION_TAG = "SomeAttributionTag";
    private static final int SOME_UID = 11;
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private TopicsServiceImpl mTopicsServiceImpl;
    private CountDownLatch mGetTopicsCallbackLatch;
    private CallerMetadata mCallerMetadata;

    @Mock private TopicsWorker mMockTopicsWorker;

    @Mock private Clock mClock;

    private AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        mTopicsServiceImpl = new TopicsServiceImpl(mContext,
                mMockTopicsWorker, mAdServicesLogger, mClock);
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

        GetTopicsResult getTopicsResult =
                new GetTopicsResult.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L, 2L))
                        .setModelVersions(Arrays.asList(3L, 4L))
                        .setTopics(Arrays.asList("topic1", "topic2"))
                        .build();

        when(mMockTopicsWorker.getTopics(/* app = */ anyString(), /* sdk = */ anyString()))
                .thenReturn(getTopicsResult);

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];
        mGetTopicsCallbackLatch = new CountDownLatch(1);

        final CountDownLatch recordUsageCalledLatch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            // The method TopicsWorker.recordUsage is called.
            recordUsageCalledLatch.countDown();
            return null;
        }).when(mMockTopicsWorker).recordUsage(eq(SOME_PACKAGE_NAME), eq(SOME_SDK_NAME));

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

        assertThat(recordUsageCalledLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
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

        // No topics (empty list) were returned.
        GetTopicsResult getTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Arrays.asList())
                        .setModelVersions(Arrays.asList())
                        .setTopics(Arrays.asList())
                        .build();

        when(mMockTopicsWorker.getTopics(/* app = */ anyString(), /* sdk = */ anyString()))
                .thenReturn(getTopicsResult);

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];
        mGetTopicsCallbackLatch = new CountDownLatch(1);

        final CountDownLatch recordUsageCalledLatch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            // The method TopicsWorker.recordUsage is called.
            recordUsageCalledLatch.countDown();
            return null;
        }).when(mMockTopicsWorker).recordUsage(eq(SOME_PACKAGE_NAME), eq(SOME_SDK_NAME));

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

        assertThat(recordUsageCalledLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
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

        // No topics (empty list) were returned.
        GetTopicsResult getTopicsResult =
                new GetTopicsResult.Builder()
                        .setResultCode(RESULT_OK)
                        .setTaxonomyVersions(Arrays.asList())
                        .setModelVersions(Arrays.asList())
                        .setTopics(Arrays.asList())
                        .build();

        when(mMockTopicsWorker.getTopics(/* app = */ anyString(), /* sdk = */ anyString()))
                .thenReturn(getTopicsResult);

        // To capture result in inner class, we have to declare final.
        final GetTopicsResult[] capturedResponseParcel = new GetTopicsResult[1];

        mGetTopicsCallbackLatch = new CountDownLatch(1);

        final CountDownLatch recordUsageCalledLatch = new CountDownLatch(1);
        Mockito.doAnswer(invocation -> {
            // The method TopicsWorker.recordUsage is called.
            recordUsageCalledLatch.countDown();
            return null;
        }).when(mMockTopicsWorker).recordUsage(eq(SOME_PACKAGE_NAME), eq(SOME_SDK_NAME));

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
        mTopicsServiceImpl.getTopics(request,
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

        assertThat(recordUsageCalledLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(logOperationCalledLatch
                .await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();

        verify(mAdServicesLogger).logApiCallStats(argument.capture());
        assertThat(argument.getValue().getResultCode()).isEqualTo(ResultCode.RESULT_OK);
        assertThat(argument.getValue().getAppPackageName()).isEqualTo(SOME_PACKAGE_NAME);
        assertThat(argument.getValue().getSdkPackageName()).isEqualTo(SOME_SDK_NAME);
        // The latency calculate result (200 - 150) + (150 - 100) * 2 = 150
        assertThat(argument.getValue().getLatencyMillisecond()).isEqualTo(150);
    }
}
