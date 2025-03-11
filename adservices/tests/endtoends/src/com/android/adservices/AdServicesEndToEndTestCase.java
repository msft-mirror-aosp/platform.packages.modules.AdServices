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

package com.android.adservices;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_AND_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_GET_AD_SELECTION_DATA_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_JOIN_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_LEAVE_CUSTOM_AUDIENCE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_PERSIST_AD_SELECTION_RESULT_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_IMPRESSION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REPORT_INTERACTION_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SELECT_ADS_WITH_OUTCOMES_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_SET_APP_INSTALL_ADVERTISERS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_UPDATE_AD_COUNTER_HISTOGRAM_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_UPDATE_SIGNALS_REQUEST_PERMITS_PER_SECOND;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@EnableDebugFlag(KEY_CONSENT_NOTIFIED_DEBUG_MODE)
@SetCompatModeFlags
@SetFlagDisabled(KEY_ADID_KILL_SWITCH)
@SetFlagDisabled(KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH)
@SetFlagDisabled(KEY_FLEDGE_SELECT_ADS_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_KILL_SWITCH)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetIntegerFlag(name = KEY_SDK_REQUEST_PERMITS_PER_SECOND, value = 1000)
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
public abstract class AdServicesEndToEndTestCase extends AdServicesCtsTestCase {}
