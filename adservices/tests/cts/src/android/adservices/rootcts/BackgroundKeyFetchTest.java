/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH;
import static com.android.adservices.spe.AdServicesJobInfo.FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.util.Log;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED)
@SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH)
@EnableDebugFlag(KEY_FLEDGE_BACKGROUND_KEY_FETCH_COMPLETE_BROADCAST_ENABLED)
public final class BackgroundKeyFetchTest extends FledgeRootScenarioTest {
    private static final String ACTION_BACKGROUND_KEY_FETCH_COMPLETE =
            "ACTION_BACKGROUND_KEY_FETCH_COMPLETE";
    private BackgroundJobHelper mBackgroundJobHelper;
    private AdSelectionConfig mAdSelectionConfig;

    @Before
    public void setup() throws Exception {
        super.setUp();
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-020.json"));
        mAdSelectionConfig = makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());
        try {
            // We want to start the ad selection service -running any API will start the adservices.
            // We don't care about the result or exception thrown by this API.
            doSelectAds(mAdSelectionClient, mAdSelectionConfig);
        } catch (Exception e) {
            Log.w(LOGCAT_TAG_FLEDGE, "Failed while running selectAds", e);
        }
    }

    @Test
    public void testBackgroundKeyFetch_startingAdServices_schedulesBackgroundJob()
            throws Exception {
        assertWithMessage("BackgroundKeyFetchJobService enabled")
                .that(
                        mBackgroundJobHelper.isJobScheduled(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId()))
                .isTrue();
    }

    @Test
    public void
            testBackgroundKeyFetch_runningBgJobWithFeatureFlagDisabled_shouldCancelScheduledJob()
                    throws Exception {
        assertWithMessage("BackgroundKeyFetchJobService enabled")
                .that(
                        mBackgroundJobHelper.isJobScheduled(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId()))
                .isTrue();

        flags.setFlag(KEY_FLEDGE_AUCTION_SERVER_BACKGROUND_KEY_FETCH_JOB_ENABLED, false);

        mBackgroundJobHelper.runJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId());

        assertWithMessage("BackgroundKeyFetchJobService enabled after disabling feature flag")
                .that(
                        mBackgroundJobHelper.isJobScheduled(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId()))
                .isFalse();
    }

    @Test
    public void testBackgroundKeyFetch_withGlobalKillSwitchEnabled_shouldCancelScheduledJob()
            throws Exception {
        assertWithMessage("BackgroundKeyFetchJobService enabled")
                .that(
                        mBackgroundJobHelper.isJobScheduled(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId()))
                .isTrue();

        enableGlobalKillSwitch(true);

        mBackgroundJobHelper.runJob(FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId());

        assertWithMessage("BackgroundKeyFetchJobService enabled after enabling global kill switch")
                .that(
                        mBackgroundJobHelper.isJobScheduled(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId()))
                .isFalse();
        enableGlobalKillSwitch(false);
    }

    @Test
    public void testBackgroundKeyFetch_forceRunningBackgroundJob_runsTheBackgroundJob()
            throws Exception {
        assertWithMessage("BackgroundKeyFetchJobService enabled")
                .that(
                        mBackgroundJobHelper.isJobScheduled(
                                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId()))
                .isTrue();

        mBackgroundJobHelper.runJobWithBroadcastIntent(
                FLEDGE_AD_SELECTION_ENCRYPTION_KEY_FETCH_JOB.getJobId(),
                ACTION_BACKGROUND_KEY_FETCH_COMPLETE);
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
}
