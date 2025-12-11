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

package com.android.adservices.tests.cts.topics;

import static com.android.adservices.service.DebugFlagsConstants.KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.shared.common.exception.AdServicesDeprecationConstants.TOPICS_SERVICE_DEPRECATION_MESSAGE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsRequest;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.TopicsManager;

import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@EnableDebugFlag(KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED)
@RequiresSdkLevelAtLeastS
public final class TopicsManagerTest extends CtsTopicsEndToEndTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    @Test
    public void testTopicsManager_shouldThrowDeprecatedException() {
        flags.setTopicsKillSwitch(false);
        AdvertisingTopicsClient advertisingTopicsClient =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient.getTopics().get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage()).isEqualTo(TOPICS_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testGetTopics_lowRamDevice() throws Exception {
        TopicsManager manager = TopicsManager.get(sContext);
        assertWithMessage("manager").that(manager).isNotNull();
        OutcomeReceiverForTests<GetTopicsResponse> receiver = new OutcomeReceiverForTests<>();

        manager.getTopics(new GetTopicsRequest.Builder().build(), CALLBACK_EXECUTOR, receiver);

        IllegalStateException exception = receiver.assertFailure(IllegalStateException.class);
        assertWithMessage("Checking exception message.")
                .that(exception.getMessage())
                .contains(TOPICS_SERVICE_DEPRECATION_MESSAGE);
    }
}
