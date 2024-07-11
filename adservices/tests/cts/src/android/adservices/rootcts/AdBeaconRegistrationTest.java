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

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;

import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import org.junit.Before;
import org.junit.Test;

public class AdBeaconRegistrationTest extends FledgeRootScenarioTest {
    private BackgroundJobHelper mBackgroundJobHelper;

    @Before
    public void setup() {
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);
    }

    @Test
    @SetFlagDisabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void testAdBeaconRegistration_BeaconCannotRegister_reportEventNotHappen()
            throws Exception {
        runAdSelectionAndReporting("scenarios/remarketing-cuj-187.json", false);
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void testAdBeaconRegistration_RegisteringEventDisabled_eventShouldOnlyBeReported()
            throws Exception {
        runAdSelectionAndReporting("scenarios/remarketing-cuj-188.json", false);
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
    @SetFlagDisabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void
            testAdBeaconRegistration_RegisteringEventEnabled_eventShouldBeReportedInCombinedCall()
                    throws Exception {
        runAdSelectionAndReporting("scenarios/remarketing-cuj-188.json", true);
    }

    @Test
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_ENABLED)
    @SetFlagEnabled(KEY_FLEDGE_MEASUREMENT_REPORT_AND_REGISTER_EVENT_API_FALLBACK_ENABLED)
    public void testAdBeaconRegistration_useBothMeasurementAndEventReporting() throws Exception {
        runAdSelectionAndReporting("scenarios/remarketing-cuj-188.json", true);
    }

    private void runAdSelectionAndReporting(String scenarioJson, boolean shouldRunBackgroundJob)
            throws Exception {
        ScenarioDispatcher scenarioDispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                scenarioJson));
        joinCustomAudience(SHOES_CA);
        AdSelectionOutcome outcome =
                doSelectAds(makeAdSelectionConfig(scenarioDispatcher.getBaseAddressWithPrefix()));
        doReportImpression(
                outcome.getAdSelectionId(),
                makeAdSelectionConfig(scenarioDispatcher.getBaseAddressWithPrefix()));
        try {
            doReportEvent(outcome.getAdSelectionId(), "view");
        } catch (Exception e) {
            // Pass through.
        }

        if (shouldRunBackgroundJob) {
            mBackgroundJobHelper.runJob(20);
        }

        assertThat(scenarioDispatcher.getCalledPaths())
                .containsAtLeastElementsIn(scenarioDispatcher.getVerifyCalledPaths());
        assertThat(scenarioDispatcher.getCalledPaths())
                .containsNoneIn(scenarioDispatcher.getVerifyNotCalledPaths());
    }
}
