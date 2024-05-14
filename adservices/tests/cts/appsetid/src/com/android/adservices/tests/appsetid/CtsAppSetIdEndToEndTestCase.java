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
package com.android.adservices.tests.appsetid;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;

@SetCompatModeFlags
@SetPpapiAppAllowList
@DisableGlobalKillSwitch
@SetFlagDisabled(FlagsConstants.KEY_APPSETID_KILL_SWITCH)
abstract class CtsAppSetIdEndToEndTestCase extends AdServicesCtsTestCase {

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.withDefaultLogcatTags();
    }
}
