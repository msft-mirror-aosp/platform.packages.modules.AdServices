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
package android.adservices.test.scenario.adservices.fledge;

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import android.Manifest;

import androidx.annotation.CallSuper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsPreparerClassRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetSyncDisabledModeForTest;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

/** Base class for all Fledge Perf tests. */
@SetSyncDisabledModeForTest
@SetCompatModeFlags
@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH)
@SetFlagDisabled(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH)
@SetFlagDisabled(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH)
@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED)
@SetFlagEnabled(KEY_ENABLE_BACK_COMPAT)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS, value = 120000)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS, value = 120000)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS, value = 120000)
@SetIntegerFlag(name = KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS, value = 120000)
@SetIntegerFlag(name = KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(
        name = KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
        value = 1000)
@SetIntegerFlag(
        name = KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
        value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(
        name = KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
        value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(name = KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND, value = 1000)
@SetIntegerFlag(
        name = KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
        value = 1000)
@SetIntegerFlag(
        name = KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND,
        value = 1000)
@SetIntegerFlag(name = KEY_SDK_REQUEST_PERMITS_PER_SECOND, value = 100000)
public abstract class FledgePerfTestCase {
    @ClassRule
    public static final AdServicesFlagsPreparerClassRule sFlagsPreparer =
            new AdServicesFlagsPreparerClassRule();

    @Rule(order = 5)
    public final AdServicesFlagsSetterRule flags = getAdServicesFlagsSetterRule();

    /** Gets the {@link AdServicesFlagsSetterRule} for this test. */
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.newInstance();
    }

    @Before
    @CallSuper
    public void setupFlags() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);
        // Disable backoff since we will be killing the process between tests
        disableBackoff();
    }

    private static void disableBackoff() {
        String packageName =
                AdservicesTestHelper.getAdServicesPackageName(
                        ApplicationProvider.getApplicationContext());
        ShellUtils.runShellCommand("am service-restart-backoff disable " + packageName);
    }
}
