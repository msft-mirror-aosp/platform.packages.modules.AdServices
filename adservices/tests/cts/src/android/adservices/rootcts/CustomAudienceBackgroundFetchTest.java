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

package android.adservices.rootcts;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@EnableDebugFlag(KEY_FLEDGE_BACKGROUND_FETCH_COMPLETE_BROADCAST_ENABLED)
public final class CustomAudienceBackgroundFetchTest extends FledgeRootScenarioTest {

    private static final String CA_NAME = "shoes";
    private static final String ACTION_BACKGROUND_FETCH_JOB_FINISHED =
            "ACTION_BACKGROUND_FETCH_JOB_FINISHED";
    private BackgroundJobHelper mBackgroundJobHelper;
    private static final int HIGH_LATENCY_TIMEOUT = 31_000;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);
    }

    /**
     * Test to ensure that trusted signals are updated (including ads list) during the daily update.
     */
    @Test
    public void testAdSelection_withInvalidFields_backgroundJobUpdatesSuccessfully()
            throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-020.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(makeCustomAudience(CA_NAME).setAds(List.of()).build());
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJobWithBroadcastIntent(
                    FLEDGE_BACKGROUND_FETCH_JOB.getJobId(), ACTION_BACKGROUND_FETCH_JOB_FINISHED);
            assertThat(doSelectAds(adSelectionConfig).hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(CA_NAME);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test to ensure that trusted signals are not updated during the daily update if the ads are
     * not syntactically valid.
     */
    @Test
    public void testAdSelection_withInvalidAds_backgroundJobUpdateFails() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-034.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(makeCustomAudience(CA_NAME).setAds(List.of()).build());
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJobWithBroadcastIntent(
                    FLEDGE_BACKGROUND_FETCH_JOB.getJobId(), ACTION_BACKGROUND_FETCH_JOB_FINISHED);
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
        } finally {
            leaveCustomAudience(CA_NAME);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test to ensure that trusted signals are not updated if a daily update server response exceeds
     * the 30-second timeout.
     */
    @Test
    public void testAdSelection_withHighLatencyBackend_backgroundJobFails() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-030-032.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(makeCustomAudience(CA_NAME).setAds(List.of()).build());
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJobWithBroadcastIntentWithTimeout(
                    FLEDGE_BACKGROUND_FETCH_JOB.getJobId(),
                    ACTION_BACKGROUND_FETCH_JOB_FINISHED,
                    HIGH_LATENCY_TIMEOUT);
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
        } finally {
            leaveCustomAudience(CA_NAME);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test to ensure that trusted signals are not updated if a daily update server response returns
     * an excessive amount of data.
     */
    @Test
    public void testAdSelection_withOverlyLargeDailyUpdate_backgroundJobFails() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-033.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(makeCustomAudience(CA_NAME).setAds(List.of()).build());
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJobWithBroadcastIntent(
                    FLEDGE_BACKGROUND_FETCH_JOB.getJobId(), ACTION_BACKGROUND_FETCH_JOB_FINISHED);
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
        } finally {
            leaveCustomAudience(CA_NAME);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test to ensure that trusted signals are not updated if the daily update job exceeds the
     * default timeout.
     */
    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_BACKGROUND_FETCH_JOB_MAX_RUNTIME_MS, value = 50)
    public void testAdSelection_withLongRunningJob_backgroundJobFails() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-020.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());
        int overrideBackgroundFetchTimeoutMs = 50; // Matches value in annotation

        try {
            joinCustomAudience(makeCustomAudience(CA_NAME).setAds(List.of()).build());
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJobWithBroadcastIntent(
                    FLEDGE_BACKGROUND_FETCH_JOB.getJobId(), ACTION_BACKGROUND_FETCH_JOB_FINISHED);
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
        } finally {
            leaveCustomAudience(CA_NAME);
        }
    }
}
