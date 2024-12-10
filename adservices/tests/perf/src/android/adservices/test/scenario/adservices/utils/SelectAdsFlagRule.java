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

package android.adservices.test.scenario.adservices.utils;

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
import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import android.Manifest;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class SelectAdsFlagRule implements TestRule {

    public SelectAdsFlagRule() {
    }

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forAllApisEnabledTests().setCompatModeFlags();

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                setupFlags();
                base.evaluate();
            }
        };
    }

    private void setupFlags() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG);
        enableAdservicesApi();
        disableApiThrottling();
        extendAuctionTimeouts();
        // Disable backoff since we will be killing the process between tests
        disableBackoff();
        modifyServerAuctionFlags();
    }

    private void modifyServerAuctionFlags() {
        flags.setFlag(KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED, true);
        flags.setFlag(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH, false);
        flags.setFlag(KEY_FLEDGE_AUCTION_SERVER_ENABLED, true);
    }

    private static void disableBackoff() {
        String packageName =
                AdservicesTestHelper.getAdServicesPackageName(
                        ApplicationProvider.getApplicationContext());
        ShellUtils.runShellCommand("am service-restart-backoff disable " + packageName);
    }

    private void extendAuctionTimeouts() {
        flags.setFlag(KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS, 120000);
        flags.setFlag(KEY_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS, 120000);
        flags.setFlag(KEY_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS, 120000);
        flags.setFlag(KEY_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_BUYER_MS, 120000);
    }

    private void disableApiThrottling() {
        flags.setFlag(KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND, 1000);
        flags.setFlag(KEY_SDK_REQUEST_PERMITS_PER_SECOND, 100000);
    }

    private void enableAdservicesApi() {
        flags.setFlag(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK, true);
        flags.setFlag(KEY_CONSENT_MANAGER_DEBUG_MODE, true);
        flags.setFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, true);
        flags.setFlag(KEY_GLOBAL_KILL_SWITCH, false);
        flags.setFlag(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED, true);
        flags.setFlag(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, false);
        flags.setFlag(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH, false);
        flags.setFlag(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED, true);
        flags.setFlag(KEY_ENABLE_BACK_COMPAT, true);
    }
}
