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
package android.adservices.cts;

import static com.android.adservices.AdServicesCommon.BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;
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
import static com.android.adservices.service.FlagsConstants.KEY_MDD_BACKGROUND_TASK_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetLongDebugFlag;

// TODO (b/330324133): Short-term solution to allow test to extend binder timeout to
// resolve the test flakiness.
@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@EnableDebugFlag(KEY_CONSENT_NOTIFIED_DEBUG_MODE)
@SetAllLogcatTags
@SetCompatModeFlags
@SetFlagDisabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagDisabled(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH)
@SetFlagDisabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
@SetFlagDisabled(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH)
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
@SetFlagEnabled(KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_ENABLED)
@SetFlagEnabled(KEY_MDD_BACKGROUND_TASK_KILL_SWITCH)
@SetIntegerFlag(name = KEY_SDK_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(
        name = KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND,
        value = 100000)
@SetIntegerFlag(
        name = KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND,
        value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(
        name = KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND,
        value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(
        name = KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND,
        value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(name = KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND, value = 100000)
@SetIntegerFlag(
        name = KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND,
        value = 100000)
@SetIntegerFlag(
        name = KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND,
        value = 100000)
@SetLongDebugFlag(name = BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME, value = 10_000)
abstract class CtsAdServicesDeviceTestCase extends AdServicesCtsTestCase {}
