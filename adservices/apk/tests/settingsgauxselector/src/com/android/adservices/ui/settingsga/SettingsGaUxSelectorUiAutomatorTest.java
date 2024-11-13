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
package com.android.adservices.ui.settingsga;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.ui.util.AdServicesUiTestCase;
import com.android.adservices.ui.util.SettingsTestUtil;

import org.junit.Rule;
import org.junit.Test;

@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE)
@SetFlagEnabled(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagEnabled(KEY_U18_UX_ENABLED)
@SetFlagEnabled(KEY_GA_UX_FEATURE_ENABLED)
@SetStringFlag(name = KEY_DEBUG_UX, value = "GA_UX")
@SetFlagDisabled(KEY_PAS_UX_ENABLED)
@SetCompatModeFlags
public final class SettingsGaUxSelectorUiAutomatorTest extends AdServicesUiTestCase {
    @Rule(order = 11)
    public final AdServicesFlagsSetterRule flags = AdServicesFlagsSetterRule.newInstance();

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() {
        SettingsTestUtil.settingsRemoveMainToggleAndMeasurementEntryTestUtil(mDevice);
    }

    @Test
    @FlakyTest(bugId = 299829948)
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
    @SetFlagEnabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    public void topicsToggleDialogTest() {
        SettingsTestUtil.topicsToggleDialogTestUtil(mDevice, flags);
    }

    @Test
    @FlakyTest(bugId = 299153376)
    public void appsToggleDialogTest() {
        SettingsTestUtil.appsToggleDialogTestUtil(mDevice, flags);
    }

    @Test
    @FlakyTest(bugId = 301779357)
    public void measurementToggleDialogTest() {
        SettingsTestUtil.measurementToggleDialogTestUtil(mDevice, flags);
    }

    @Test
    @FlakyTest(bugId = 375981099)
    public void fledgeViewTextPasEnabledTest() throws Exception {
        SettingsTestUtil.fledgeViewTextPasEnabledTest(mDevice, flags);
    }
}
