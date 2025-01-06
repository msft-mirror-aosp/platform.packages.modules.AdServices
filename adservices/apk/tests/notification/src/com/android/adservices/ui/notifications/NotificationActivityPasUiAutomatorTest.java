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
package com.android.adservices.ui.notifications;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_TOGGLE_SPEED_BUMP_ENABLED;
import static com.android.adservices.ui.util.NotificationActivityTestUtil.WINDOW_LAUNCH_TIMEOUT_MS;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.ui.util.AdservicesNotificationUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;

import org.junit.Test;

@RequiresSdkLevelAtLeastT(reason = "PAS UX is currently only available on T+ devices")
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagTrue(KEY_U18_UX_ENABLED)
@SetStringFlag(name = KEY_DEBUG_UX, value = "GA_UX")
@SetFlagTrue(KEY_PAS_UX_ENABLED)
@SetFlagTrue(KEY_IS_EEA_DEVICE_FEATURE_ENABLED)
@SetFlagFalse(KEY_IS_EEA_DEVICE)
@SetFlagFalse(KEY_UI_TOGGLE_SPEED_BUMP_ENABLED)
public final class NotificationActivityPasUiAutomatorTest extends AdservicesNotificationUiTestCase {

    private static final String ANDROID_WIDGET_SWITCH = "android.widget.Switch";
    private static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS = 2_000;

    @Test
    @FlakyTest(bugId = 374129459)
    public void renotifyClickSettingsTest() throws Exception {
        // enable at least one of Fledge or Mesurement API
        ApkTestUtil.launchSettingView(mDevice, LAUNCH_TIMEOUT_MS);
        mDevice.waitForIdle();
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_apps_ga_title);
        UiObject2 appsToggle =
                mDevice.wait(
                        Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (!appsToggle.isChecked()) {
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT_MS);
        }
        mDevice.waitForIdle();

        // start renotify notice
        NotificationActivityTestUtil.startRenotifyPasActivity(/* isEuActivity= */ false, mDevice);

        UiObject2 pasNotificationHeader =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_pas_renotify_header_title);
        assertWithMessage("pas head should show").that(pasNotificationHeader).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        assertWithMessage("left button should show").that(leftControlButton).isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        assertWithMessage("right button should show").that(rightControlButton).isNotNull();

        // check manage settings button works
        leftControlButton.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT_MS);
        UiObject2 topicsTitle =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_topics_ga_title);
        assertWithMessage("pas notification enter settings page should see topics title")
                .that(topicsTitle)
                .isNotNull();
        UiObject2 appsTitle = ApkTestUtil.getElement(mDevice, R.string.settingsUI_apps_ga_title);
        ApkTestUtil.scrollTo(mDevice, R.string.settingsUI_apps_ga_title);
        assertWithMessage("pas notification enter settings page should see apps title")
                .that(appsTitle)
                .isNotNull();
    }

    @Test
    @FlakyTest(bugId = 353988743)
    public void firstTimeRowCombinedTextShownTest() throws Exception {
        // disable both Fledge and Measurement
        ApkTestUtil.launchSettingView(mDevice, LAUNCH_TIMEOUT_MS);
        mDevice.waitForIdle();
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_apps_ga_title);
        UiObject2 appsToggle =
                mDevice.wait(
                        Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (appsToggle.isChecked()) {
            appsToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT_MS);
        }
        mDevice.waitForIdle();
        mDevice.pressBack();
        mDevice.waitForIdle();
        ApkTestUtil.scrollToAndClick(mDevice, R.string.settingsUI_measurement_ga_title);
        UiObject2 measurementToggle =
                mDevice.wait(
                        Until.findObject(By.clazz(ANDROID_WIDGET_SWITCH)),
                        PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS);
        if (measurementToggle.isChecked()) {
            measurementToggle.clickAndWait(Until.newWindow(), WINDOW_LAUNCH_TIMEOUT_MS);
        }
        mDevice.waitForIdle();

        // start combined notice activity
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);

        UiObject2 pasNotificationHeader =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_pas_combined_header_title);
        assertWithMessage("pas notification header should show")
                .that(pasNotificationHeader)
                .isNotNull();
        UiObject2 pasNotificationBody =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_pas_combined_body_2);
        assertWithMessage("pas notification body should show")
                .that(pasNotificationBody)
                .isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_left_control_button_text);
        assertWithMessage("pas notification left button should show")
                .that(leftControlButton)
                .isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_right_control_button_text);
        assertWithMessage("pas notification right button should show")
                .that(rightControlButton)
                .isNotNull();
    }
}
