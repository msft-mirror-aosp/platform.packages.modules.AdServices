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

package com.android.tests.providers.sdk1;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.common.collect.ImmutableSet;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class Sdk1 extends SandboxedSdkProvider {
    private static final String TAG = "Sdk1";
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    private static final long TEST_EPOCH_JOB_PERIOD_MS = 4000;

    // Set of classification topics for the Test App. The returned topic should be one of these
    // Topics.
    private static final ImmutableSet<Integer> TOPIC_ID_SET =
            ImmutableSet.of(20, 183, 96, 6, 13, 286, 112, 194, 242, 17);

    private Executor mExecutor;
    private OnLoadSdkCallback mCallback;

    @Override
    public void onLoadSdk(Bundle params, Executor executor, OnLoadSdkCallback callback) {
        mExecutor = executor;
        mCallback = callback;
        executeTest();
    }

    private void executeTest() {
        boolean success = true;

        try {
            // The first call to the Topics API. This will record the usage of the test app to
            // the Topics API.
            GetTopicsResponse unUsedResponse = callTopicsApi();

            // Trigger the success callback to tell the Test App that the first Topics API call
            // finished successfully.
            mExecutor.execute(() -> mCallback.onLoadSdkFinished(null));
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());

            // Trigger the error callback to tell the Test App that the first Topics API call
            // finished with an error.
            mExecutor.execute(() -> mCallback.onLoadSdkError(e.getMessage()));
            success = false;
        }

        if (success) {
            try {
                // Wait until the next epoch.
                Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }
            try {
                // Make the second call to the Topics API. Since we called the Topics API in the
                // previous epoch, we should have some returned topic now.
                GetTopicsResponse response = callTopicsApi();
                if (response.getTopics().isEmpty()) {
                    // Trigger the error callback to tell the Test App that the we did not receive
                    // any topic. This will tell the Test App to fail the test.
                    Log.e(TAG, "Failed. No topics!");
                    mExecutor.execute(() -> mCallback.onLoadSdkError("Failed. No topics!"));
                } else {
                    // Verify the returned Topic.
                    Topic topic = response.getTopics().get(0);
                    boolean correctResult =
                            TOPIC_ID_SET.contains(topic.getTopicId())
                                    && topic.getTaxonomyVersion() == 1L
                                    && topic.getModelVersion() == 1L;
                    if (correctResult) {
                        // Trigger the success callback to tell the Test App that the second Topics
                        // API call got back some topic which is expected. This will tell the Test
                        // App to pass the test.
                        Log.i(TAG, "Get correct returned topic: " + topic.getTopicId());
                        mExecutor.execute(() -> mCallback.onLoadSdkFinished(null));
                    } else {
                        // Trigger the error callback to tell the Test App that the we received
                        // a wrong topic. This will tell the Test App to fail the test.
                        Log.e(TAG, "Get wrong returned topic: " + topic.getTopicId());
                        mExecutor.execute(() -> mCallback.onLoadSdkError("Failed. Wrong topics!"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
                // Trigger the error callback to tell the Test App that some errors occurred so
                // that it will fail the test.
                mExecutor.execute(() -> mCallback.onLoadSdkError(e.getMessage()));
            }
        }
    }

    @Override
    public View getView(Context windowContext, Bundle params) {
        return null;
    }

    @Override
    public void onDataReceived(Bundle data, DataReceivedCallback callback) {}

    private GetTopicsResponse callTopicsApi() throws Exception {
        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(getBaseContext())
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        return advertisingTopicsClient.getTopics().get();
    }
}
