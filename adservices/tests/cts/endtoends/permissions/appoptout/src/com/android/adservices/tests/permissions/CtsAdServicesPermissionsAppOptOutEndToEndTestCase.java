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

package com.android.adservices.tests.permissions;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_SDK_REQUEST_PERMITS_PER_SECOND;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

@EnableAllApis
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@SetAllLogcatTags
@SetCompatModeFlags
@SetFlagDisabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
@SetFlagEnabled(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_FREQUENCY_CAP_FILTERING_ENABLED)
@SetIntegerFlag(name = KEY_SDK_REQUEST_PERMITS_PER_SECOND, value = 1000)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetPpapiAppAllowList
public abstract class CtsAdServicesPermissionsAppOptOutEndToEndTestCase
        extends AdServicesCtsTestCase {}
