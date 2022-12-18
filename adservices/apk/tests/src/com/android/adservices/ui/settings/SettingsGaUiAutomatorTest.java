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
package com.android.adservices.ui.settings;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsGaUiAutomatorTest {
    private static final String PRIVACY_SANDBOX_TEST_PACKAGE = "android.adservices.ui.SETTINGS";
    private static final int LAUNCH_TIMEOUT = 5000;
    private static UiDevice sDevice;

    @Before
    public void setup() {
        // Initialize UiDevice instance
        sDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Start from the home screen
        sDevice.pressHome();

        // Wait for launcher
        final String launcherPackage = sDevice.getLauncherPackageName();
        assertThat(launcherPackage).isNotNull();
        sDevice.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), LAUNCH_TIMEOUT);
    }

    @After
    public void teardown() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
    }

    @Test
    public void settingsRemoveMainToggleAndMeasurementEntryTest() throws UiObjectNotFoundException {
        ShellUtils.runShellCommand("device_config put adservices ga_ux_enabled true");
        // launch app
        Context context = ApplicationProvider.getApplicationContext();
        Intent intent = new Intent(PRIVACY_SANDBOX_TEST_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);

        // Wait for the app to appear
        sDevice.wait(
                Until.hasObject(By.pkg(PRIVACY_SANDBOX_TEST_PACKAGE).depth(0)), LAUNCH_TIMEOUT);

        // no main switch any more
        UiObject mainSwitch =
                sDevice.findObject(new UiSelector().className("android.widget.Switch"));
        assertThat(mainSwitch.exists()).isFalse();

        // make sure we are on the main settings page
        UiObject appButton = getElement(R.string.settingsUI_apps_title);
        assertThat(appButton.exists()).isTrue();
        UiObject topicsButton = getElement(R.string.settingsUI_topics_title);
        assertThat(topicsButton.exists()).isTrue();

        // click measurement page
        scrollToAndClick(R.string.settingsUI_measurement_view_title);

        // verify have entered to measurement page
        UiObject resetButton = getElement(R.string.settingsUI_measurement_view_reset_title);
        assertThat(resetButton.exists()).isTrue();

        sDevice.pressBack();
        // verify back to the main page
        assertThat(appButton.exists()).isTrue();
    }

    private UiObject getElement(int resId) {
        return sDevice.findObject(new UiSelector().text(getString(resId)));
    }

    private String getString(int resourceId) {
        return ApplicationProvider.getApplicationContext().getResources().getString(resourceId);
    }

    private void scrollToAndClick(int resId) throws UiObjectNotFoundException {
        UiScrollable scrollView =
                new UiScrollable(
                        new UiSelector().scrollable(true).className("android.widget.ScrollView"));
        UiObject element =
                sDevice.findObject(
                        new UiSelector().childSelector(new UiSelector().text(getString(resId))));
        scrollView.scrollIntoView(element);
        element.click();
    }
}
