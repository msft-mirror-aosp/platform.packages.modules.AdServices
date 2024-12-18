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

package com.android.tests.sandbox.measurement;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFIED_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetMsmtApiAppAllowList;
import com.android.adservices.common.annotations.SetMsmtWebContextClientAppAllowList;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetSdkSandboxStateEnabled;

@SetSdkSandboxStateEnabled
@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@EnableDebugFlag(KEY_CONSENT_NOTIFIED_DEBUG_MODE)
@SetAllLogcatTags
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
@SetMsmtApiAppAllowList
@SetMsmtWebContextClientAppAllowList
public abstract class CtsSandboxedMeasurementManagerTestCase extends AdServicesCtsTestCase {}
