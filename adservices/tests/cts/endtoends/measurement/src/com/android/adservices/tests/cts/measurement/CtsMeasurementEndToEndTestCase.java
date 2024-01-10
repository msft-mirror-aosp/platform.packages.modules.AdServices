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
package com.android.adservices.tests.cts.measurement;

import static com.android.adservices.service.FlagsConstants.KEY_GLOBAL_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;

import android.util.Log;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;

abstract class CtsMeasurementEndToEndTestCase extends AdServicesCtsTestCase {

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        String packageName = sContext.getPackageName();
        Log.d(mTag, "getAdServicesFlagsSetterRule(): allow-listing for " + packageName);
        return AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                .setLogcatTag(LOGCAT_TAG_MEASUREMENT, LOGCAT_LEVEL_VERBOSE)
                .setCompatModeFlags()
                .setMsmtApiAppAllowList(packageName)
                .setMsmtWebContextClientAllowList(packageName)
                .setConsentManagerDebugMode(true)
                .setConsentNotifiedDebugMode(true)
                .setSystemProperty(KEY_GLOBAL_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH, false)
                .setSystemProperty(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH, false)
                .setFlag(KEY_MEASUREMENT_ENABLE_SESSION_STABLE_KILL_SWITCHES, false)
                .setAdIdKillSwitchForTests(false);
    }
}
