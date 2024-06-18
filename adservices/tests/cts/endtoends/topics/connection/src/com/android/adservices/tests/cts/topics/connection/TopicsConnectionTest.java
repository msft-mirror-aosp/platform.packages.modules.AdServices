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

package com.android.adservices.tests.cts.topics.connection;

import static com.android.adservices.service.DebugFlagsConstants.KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_EPOCH_JOB_PERIOD_MS;
import static com.android.adservices.service.FlagsConstants.KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.topics.GetTopicsResponse;
import android.adservices.topics.Topic;

import com.android.adservices.common.AdServicesSupportHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongFlag;
import com.android.adservices.topics.TopicsTestHelper;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@EnableDebugFlag(KEY_RECORD_TOPICS_COMPLETE_BROADCAST_ENABLED)
@SetLongFlag(
        name = KEY_TOPICS_EPOCH_JOB_PERIOD_MS,
        value = TopicsConnectionTest.TEST_EPOCH_JOB_PERIOD_MS)
@SetIntegerFlag(
        name = KEY_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC,
        value = TopicsConnectionTest.TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC)
public final class TopicsConnectionTest extends CtsAdServicesTopicsConnectionTestCase {
    // The JobId of the Epoch Computation.
    private static final int EPOCH_JOB_ID = 2;

    // Override the Epoch Job Period to this value to speed up the epoch computation.
    static final long TEST_EPOCH_JOB_PERIOD_MS = 5000;

    // Use 0 percent for random topic in the test so that we can verify the returned topic.
    static final int TEST_TOPICS_PERCENTAGE_FOR_RANDOM_TOPIC = 0;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private final String mAdServicesPackageName =
            AdServicesSupportHelper.getInstance().getAdServicesPackageName();

    @Before
    public void setup() throws Exception {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(mAdServicesPackageName);

        // We need to skip 3 epochs so that if there is any usage from other test runs, it will
        // not be used for epoch retrieval.
        Thread.sleep(3 * TEST_EPOCH_JOB_PERIOD_MS);
    }

    @Test
    public void testEnableGlobalKillSwitch() throws Exception {
        // First enable the Global Kill Switch and then connect to the TopicsService.
        // The connection should fail with Exception.
        enableGlobalKillSwitch(/* enabled */ true);

        // Sdk1 calls the Topics git .
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // Due to global kill switch is enabled, we receive the IllegalStateException.
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient1.getTopics().get());
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);

        // Now disable the Global Kill Switch, we should be able to connect to the Service normally.
        enableGlobalKillSwitch(/* enabled */ false);

        // Re-create the client after enabling the kill switch.
        AdvertisingTopicsClient advertisingTopicsClient2 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // At beginning, Sdk1 receives no topic.
        GetTopicsResponse sdk1Result =
                TopicsTestHelper.getTopicsWithBroadcast(sContext, advertisingTopicsClient2);

        assertThat(sdk1Result.getTopics()).isEmpty();

        // Now force the Epoch Computation Job. This should be done in the same epoch for
        // callersCanLearnMap to have the entry for processing.
        forceEpochComputationJob();

        // Wait to the next epoch. We will not need to do this after we implement the fix in
        // go/rb-topics-epoch-scheduling
        Thread.sleep(TEST_EPOCH_JOB_PERIOD_MS);

        // Since the sdk1 called the Topics API in the previous Epoch, it should receive some topic.
        sdk1Result = advertisingTopicsClient2.getTopics().get();
        assertThat(sdk1Result.getTopics()).isNotEmpty();

        // We only have 1 test app which has 5 classification topics: 10147,10253,10175,10254,10333
        // in the precomputed list.
        // These 5 classification topics will become top 5 topics of the epoch since there is
        // no other apps calling Topics API.
        // The app will be assigned one random topic from one of these 5 topics.
        assertThat(sdk1Result.getTopics()).hasSize(1);
        Topic topic = sdk1Result.getTopics().get(0);

        // topic is one of the 5 classification topics of the Test App.
        assertThat(topic.getTopicId()).isIn(Arrays.asList(10147, 10253, 10175, 10254, 10333));

        assertThat(topic.getModelVersion()).isAtLeast(1L);
        assertThat(topic.getTaxonomyVersion()).isAtLeast(1L);
    }

    // Override global_kill_switch to ignore the effect of actual PH values.
    // If enabled = true, override global_kill_switch to ON to turn off Adservices.
    // If enabled = false, the AdServices is enabled.
    // TODO(b/346825347) Inline this method when the bug is fixed
    private void enableGlobalKillSwitch(boolean enabled) {
        if (SdkLevel.isAtLeastT()) {
            flags.setGlobalKillSwitch(enabled);
        } else {
            flags.setEnableBackCompat(!enabled);
        }
    }

    /** Forces JobScheduler to run the Epoch Computation job */
    private void forceEpochComputationJob() {
        ShellUtils.runShellCommand(
                "cmd jobscheduler run -f %s %d", mAdServicesPackageName, EPOCH_JOB_ID);
    }
}
