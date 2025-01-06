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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;

import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE)
public abstract class AdservicesNotificationUiTestCase extends AdServicesUiTestCase {

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
