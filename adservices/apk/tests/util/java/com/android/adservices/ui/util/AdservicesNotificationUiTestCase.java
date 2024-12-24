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

package com.android.adservices.ui.util;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@DisableGlobalKillSwitch
@SetAllLogcatTags
@SetCompatModeFlags
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE)
@SetFlagTrue(KEY_GA_UX_FEATURE_ENABLED)
public abstract class AdservicesNotificationUiTestCase extends AdServicesUiTestCase {

    // TODO(b/384798806): called realFlags because it's "really" changing the Flags using
    // DeviceConfig (and superclass will eventually provide a flags object that uses
    // AdServicesFakeFlagsSetterRule). Ideally this class should use that same flags, but it doesn't
    // support DebugFlags (we'll need to wait until the DebugFlags logic is moved to its own rule).
    @Rule(order = 11)
    public final AdServicesFlagsSetterRule realFlags = AdServicesFlagsSetterRule.newInstance();

    @BeforeClass
    public static void classSetup() throws Exception {
        NotificationActivityTestUtil.setupBeforeTests();
    }

    @Before
    public void setup() throws Exception {
        if (!sdkLevel.isAtLeastT()) {
            Assume.assumeTrue(
                    "Notification intent does not have enabled activity.",
                    NotificationActivityTestUtil.isNotificationIntentInstalled(true));
        }
    }
}
