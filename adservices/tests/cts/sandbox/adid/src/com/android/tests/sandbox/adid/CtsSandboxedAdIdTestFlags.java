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

package com.android.tests.sandbox.adid;

import static com.android.adservices.service.FlagsConstants.KEY_ADID_KILL_SWITCH;

import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetSdkSandboxStateEnabled;

@SetAllLogcatTags
@SetSdkSandboxStateEnabled
@SetFlagDisabled(KEY_ADID_KILL_SWITCH)
interface CtsSandboxedAdIdTestFlags {}
