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

import static com.android.adservices.service.FlagsConstants.KEY_DEBUG_UX;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.ui.util.ApkTestUtil.getString;
import static com.android.adservices.ui.util.NotificationActivityTestUtil.WINDOW_LAUNCH_TIMEOUT;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.api.R;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.ui.util.AdservicesNotificationUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;
import com.android.adservices.ui.util.NotificationActivityTestUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SetFlagTrue(KEY_ENABLE_AD_SERVICES_SYSTEM_API)
@SetFlagTrue(KEY_U18_UX_ENABLED)
@SetStringFlag(name = KEY_DEBUG_UX, value = "U18_UX")
public final class NotificationActivityU18UxSelectorUiAutomatorTest
        extends AdservicesNotificationUiTestCase {

    @Test
    public void acceptFlowTest() throws Exception {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);

        UiObject2 u18NotificationTitle =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_u18_notification_title);
        assertWithMessage("notification title should show").that(u18NotificationTitle).isNotNull();

        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_u18_left_control_button_text);
        assertWithMessage("notification left button should show")
                .that(leftControlButton)
                .isNotNull();
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_u18_right_control_button_text);
        rightControlButton.click();
        mDevice.wait(
                Until.gone(
                        By.text(getString(R.string.notificationUI_u18_right_control_button_text))),
                WINDOW_LAUNCH_TIMEOUT);

        // Retrieve a new instance to avoid android.support.test.uiautomator.StaleObjectException.
        u18NotificationTitle =
                ApkTestUtil.getElement(mDevice, R.string.notificationUI_u18_notification_title);
        assertWithMessage("second page should not show").that(u18NotificationTitle).isNull();
    }

    @Test
    @FlakyTest(bugId = 302607350)
    public void clickSettingsTest() throws InterruptedException {
        NotificationActivityTestUtil.startActivity(/* isEuActivity= */ false, mDevice);
        NotificationActivityTestUtil.clickMoreToBottom(mDevice);

        UiObject2 leftControlButton =
                ApkTestUtil.getElement(
                        mDevice, R.string.notificationUI_u18_left_control_button_text);
        UiObject2 rightControlButton =
                ApkTestUtil.getElement(
                        mDevice,
                        R.string.notificationUI_u18_right_control_button_text);
        assertWithMessage("notification right button should show")
                .that(rightControlButton)
                .isNotNull();

        leftControlButton.click();
        mDevice.wait(
                Until.gone(
                        By.text(getString(R.string.notificationUI_u18_left_control_button_text))),
                WINDOW_LAUNCH_TIMEOUT);

        // make sure it goes to u18 page rather than GA page
        UiObject2 topicTitle = ApkTestUtil.getElement(mDevice, R.string.settingsUI_topics_ga_title);
        assertWithMessage(
                        "notification enter settings should see topics title %s ",
                        getString(R.string.settingsUI_topics_ga_title))
                .that(topicTitle)
                .isNull();
        UiObject2 measurementTitle =
                ApkTestUtil.getElement(mDevice, R.string.settingsUI_u18_measurement_view_title);
        assertWithMessage(
                        "notification enter settings should see measurement title %s ",
                        getString(R.string.settingsUI_u18_measurement_view_title))
                .that(measurementTitle)
                .isNotNull();
    }
}
