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

import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.ui.util.AdservicesSettingsUiTestCase;
import com.android.adservices.ui.util.SettingsTestUtil;

import org.junit.Test;

@SetFlagEnabled(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagEnabled(KEY_ADSERVICES_ENABLED)
public final class SettingsGaUiAutomatorTest extends AdservicesSettingsUiTestCase {

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() {
        SettingsTestUtil.settingsRemoveMainToggleAndMeasurementEntryTestUtil(mDevice);
    }

    @Test
    public void measurementDialogTest() throws Exception {
        SettingsTestUtil.measurementDialogTestUtil(mDevice, realFlags);
    }

    @Test
    public void topicsToggleTest() throws Exception {
        SettingsTestUtil.topicsToggleTestUtil(mDevice, realFlags);
    }

    @Test
    public void fledgeToggleTest() throws Exception {
        SettingsTestUtil.fledgeToggleTestUtil(mDevice, realFlags);
    }

    @Test
    public void measurementToggleTest() throws Exception {
        SettingsTestUtil.measurementToggleTestUtil(mDevice, realFlags);
    }

    @Test
    public void topicsSubtitleTest() {
        SettingsTestUtil.topicsSubtitleTestUtil(mDevice, realFlags);
    }

    @Test
    public void appsSubtitleTest() {
        SettingsTestUtil.appsSubtitleTestUtil(mDevice, realFlags);
    }

    @Test
    public void measurementSubtitleTest() {
        SettingsTestUtil.measurementSubtitleTestUtil(mDevice, realFlags);
    }

    @Test
    public void topicsToggleDialogTest() {
        SettingsTestUtil.topicsToggleDialogTestUtil(mDevice, realFlags);
    }

    @Test
    public void appsToggleDialogTest() {
        SettingsTestUtil.appsToggleDialogTestUtil(mDevice, realFlags);
    }

    @Test
    public void measurementToggleDialogTest() {
        SettingsTestUtil.measurementToggleDialogTestUtil(mDevice, realFlags);
    }
}
