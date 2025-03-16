/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.ui.settingsga;

import static com.android.adservices.service.FlagsConstants.KEY_ADSERVICES_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_BACK_COMPAT;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.ui.util.AdservicesSettingsUiTestCase;
import com.android.adservices.ui.util.SettingsTestUtil;

import org.junit.Rule;
import org.junit.Test;

@DisableGlobalKillSwitch
@SetAllLogcatTags
@SetCompatModeFlags
@SetFlagEnabled(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagEnabled(KEY_GA_UX_FEATURE_ENABLED)
@SetFlagEnabled(KEY_ADSERVICES_ENABLED)
@SetFlagEnabled(KEY_ENABLE_BACK_COMPAT)
public final class SettingsGaUiAutomatorTest extends AdservicesSettingsUiTestCase {
    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.newInstance();

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() {
        SettingsTestUtil.settingsRemoveMainToggleAndMeasurementEntryTestUtil(mDevice);
    }

    @Test
    public void measurementDialogTest() throws Exception {
        SettingsTestUtil.measurementDialogTestUtil(mDevice, flags);
    }

    @Test
    public void topicsToggleTest() throws Exception {
        SettingsTestUtil.topicsToggleTestUtil(mDevice, flags);
    }

    @Test
    public void fledgeToggleTest() throws Exception {
        SettingsTestUtil.fledgeToggleTestUtil(mDevice, flags);
    }

    @Test
    public void measurementToggleTest() throws Exception {
        SettingsTestUtil.measurementToggleTestUtil(mDevice, flags);
    }

    @Test
    public void topicsSubtitleTest() {
        SettingsTestUtil.topicsSubtitleTestUtil(mDevice, flags);
    }

    @Test
    public void appsSubtitleTest() {
        SettingsTestUtil.appsSubtitleTestUtil(mDevice, flags);
    }

    @Test
    public void measurementSubtitleTest() {
        SettingsTestUtil.measurementSubtitleTestUtil(mDevice, flags);
    }

    @Test
    public void topicsToggleDialogTest() {
        SettingsTestUtil.topicsToggleDialogTestUtil(mDevice, flags);
    }

    @Test
    public void appsToggleDialogTest() {
        SettingsTestUtil.appsToggleDialogTestUtil(mDevice, flags);
    }

    @Test
    public void measurementToggleDialogTest() {
        SettingsTestUtil.measurementToggleDialogTestUtil(mDevice, flags);
    }
}
