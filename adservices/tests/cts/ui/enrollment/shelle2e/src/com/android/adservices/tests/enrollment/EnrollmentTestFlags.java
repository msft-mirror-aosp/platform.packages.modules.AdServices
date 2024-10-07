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

package com.android.adservices.tests.enrollment;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_INTERVAL_END_MS;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED;

import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.shared.testing.annotations.DisableDebugFlag;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetLongFlag;

@DisableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@DisableGlobalKillSwitch
@SetAllLogcatTags
@SetFlagDisabled(KEY_UI_OTA_STRINGS_FEATURE_ENABLED)
@SetFlagEnabled(KEY_ADSERVICES_ENABLED)
@SetFlagEnabled(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagEnabled(KEY_IS_EEA_DEVICE_FEATURE_ENABLED)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@SetLongFlag(name = KEY_CONSENT_NOTIFICATION_INTERVAL_BEGIN_MS, value = 0)
@SetLongFlag(name = KEY_CONSENT_NOTIFICATION_INTERVAL_END_MS, value = 86400000)
@SetLongFlag(name = KEY_CONSENT_NOTIFICATION_MINIMAL_DELAY_BEFORE_INTERVAL_ENDS, value = 0)
interface EnrollmentTestFlags {}
