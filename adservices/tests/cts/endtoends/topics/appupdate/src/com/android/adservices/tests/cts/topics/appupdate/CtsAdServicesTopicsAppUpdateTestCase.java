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
package com.android.adservices.tests.cts.topics.appupdate;

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_TOPICS_ENROLLMENT_CHECK;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

@EnableAllApis
@EnableDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE)
@SetAllLogcatTags
@SetCompatModeFlags
@SetFlagEnabled(KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED)
@SetFlagEnabled(KEY_CLASSIFIER_FORCE_USE_BUNDLED_FILES)
@SetFlagEnabled(KEY_DISABLE_TOPICS_ENROLLMENT_CHECK)
abstract class CtsAdServicesTopicsAppUpdateTestCase extends AdServicesCtsTestCase {}
