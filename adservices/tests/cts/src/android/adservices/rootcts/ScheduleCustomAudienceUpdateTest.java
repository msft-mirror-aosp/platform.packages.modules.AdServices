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

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_FLEDGE_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;
import static com.android.adservices.spe.AdServicesJobInfo.SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.AdSelectionSignals;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;
import android.adservices.utils.Scenarios;
import android.net.Uri;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@SetFlagEnabled(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@EnableDebugFlag(KEY_FLEDGE_SCHEDULE_CA_COMPLETE_BROADCAST_ENABLED)
@SetIntegerFlag(
        name = KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
        value = 30)
public final class ScheduleCustomAudienceUpdateTest extends FledgeRootScenarioTest {
    private static final String CA_NAME = "delayed_updated_ca";
    private static final String ACTION_SCHEDULE_CA_COMPLETE_INTENT =
            "ACTION_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_FINISHED";
    private static final int MIN_ALLOWED_DELAY_TEST_OVERRIDE = -100;
    private BackgroundJobHelper mBackgroundJobHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);
    }

    @After
    public void teardown() {
        clearAlDebuggableUpdates();
    }

    @Test
    public void testScheduleCustomAudienceUpdate_badUpdateUri_failure() {
        Uri updateUri = Uri.parse("");
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                updateUri, Duration.of(60, ChronoUnit.MINUTES), List.of())
                        .build();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class, () -> doScheduleCustomAudienceUpdate(request));
        assertEquals("SecurityException", e.getCause().getClass().getSimpleName());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_DelayExceedsLimit_failure() {
        Uri updateUri = Uri.parse("http://localhost/update/ca");
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                updateUri, Duration.of(20, ChronoUnit.DAYS), List.of())
                        .build();

        ExecutionException e =
                assertThrows(
                        ExecutionException.class, () -> doScheduleCustomAudienceUpdate(request));
        assertEquals("IllegalArgumentException", e.getCause().getClass().getSimpleName());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_DelayLowerLimit_failure() {
        Uri updateUri = Uri.parse("http://localhost/update/ca");
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                updateUri, Duration.of(1, ChronoUnit.MILLIS), List.of())
                        .build();

        Exception e =
                assertThrows(
                        ExecutionException.class, () -> doScheduleCustomAudienceUpdate(request));
        expect.withMessage("Thrown exception for duration below limit")
                .that(e)
                .hasCauseThat()
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testScheduleCustomAudienceUpdate_DownloadedCaWinsAdSelection_success()
            throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/scheduleupdates/remarketing-cuj-scheduled-update.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        // Set min allowed delay in past for easier testing
        flags.setFlag(
                KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
                MIN_ALLOWED_DELAY_TEST_OVERRIDE);

        Uri updateUri =
                Uri.parse(
                        dispatcher.getBaseAddressWithPrefix().toString()
                                + Scenarios.UPDATE_CA_PATH);
        CustomAudience customAudience = makeCustomAudience(CA_NAME).build();
        PartialCustomAudience partialCustomAudience =
                new PartialCustomAudience.Builder(CA_NAME)
                        .setActivationTime(customAudience.getActivationTime())
                        .setExpirationTime(customAudience.getExpirationTime())
                        .setUserBiddingSignals(AdSelectionSignals.fromString("{\"a\":\"b\"}"))
                        .build();
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                updateUri,
                                Duration.of(0, ChronoUnit.MINUTES),
                                List.of(partialCustomAudience))
                        .build();

        try {
            doScheduleCustomAudienceUpdate(request);
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJobWithBroadcastIntent(
                    SCHEDULE_CUSTOM_AUDIENCE_UPDATE_BACKGROUND_JOB.getJobId(),
                    ACTION_SCHEDULE_CA_COMPLETE_INTENT);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            leaveCustomAudience(CA_NAME);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_Disabled_failure() throws Exception {
        flags.setFlag(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED, false);

        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/scheduleupdates/remarketing-cuj-scheduled-update.json"));
        Uri updateUri = Uri.parse(dispatcher.getBaseAddressWithPrefix() + Scenarios.UPDATE_CA_PATH);
        ScheduleCustomAudienceUpdateRequest request =
                new ScheduleCustomAudienceUpdateRequest.Builder(
                                updateUri,
                                Duration.of(60, ChronoUnit.MINUTES),
                                Collections.emptyList())
                        .build();
        ExecutionException e =
                assertThrows(
                        ExecutionException.class, () -> doScheduleCustomAudienceUpdate(request));
        assertEquals("IllegalStateException", e.getCause().getClass().getSimpleName());
    }

    private void clearAlDebuggableUpdates() {
        ShellUtils.runShellCommand(
                "sqlite3 /data/data/com.google.android.adservices"
                        + ".api/databases/customaudience.db \\\"DELETE FROM "
                        + "scheduled_custom_audience_update WHERE is_debuggable = true ;\\\" \\\""
                        + ".exit\\\";");
    }
}
