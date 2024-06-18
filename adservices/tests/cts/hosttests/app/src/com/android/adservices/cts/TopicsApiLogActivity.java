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
package com.android.adservices.cts;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TopicsApiLogActivity extends AppCompatActivity {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final String SDK_NAME = "AdServicesCtsSdk";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(this)
                        .setSdkName(SDK_NAME)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ListenableFuture<GetTopicsResponse> getTopicsResponseFuture =
                advertisingTopicsClient.getTopics();

        // Block the getTopics() call until it finishes.
        try {
            GetTopicsResponse response = getTopicsResponseFuture.get();
            Preconditions.checkState(
                    response.getTopics().isEmpty(), "Incorrect getTopicsResponse!");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
