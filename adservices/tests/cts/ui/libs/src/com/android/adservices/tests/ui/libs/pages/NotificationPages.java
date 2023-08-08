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
package com.android.adservices.tests.ui.libs.pages;

import static com.android.adservices.tests.ui.libs.UiConstants.LAUNCH_TIMEOUT_MS;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_NAME;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;
import static com.android.adservices.tests.ui.libs.UiUtils.PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT;
import static com.android.adservices.tests.ui.libs.UiUtils.SCROLL_WAIT_TIME;
import static com.android.adservices.tests.ui.libs.UiUtils.getElement;
import static com.android.adservices.tests.ui.libs.UiUtils.getString;
import static com.android.adservices.tests.ui.libs.UiUtils.getUiElement;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.util.Log;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import com.android.adservices.api.R;
import com.android.adservices.tests.ui.libs.UiConstants;

public class NotificationPages {
    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuTest,
            UiConstants.UX ux)
            throws Exception {
        device.openNotification();
        Thread.sleep(LAUNCH_TIMEOUT_MS);

        int notificationTitle = -1;
        int notificationHeader = -1;
        switch (ux) {
            case GA_UX:
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_ga_title_eu
                                : R.string.notificationUI_notification_ga_title;
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_header_ga_title_eu
                                : R.string.notificationUI_header_ga_title;
                break;
            case BETA_UX:
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_title_eu
                                : R.string.notificationUI_notification_title;
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_header_title_eu
                                : R.string.notificationUI_header_title;
                break;
            case U18_UX:
                notificationTitle = R.string.notificationUI_u18_notification_title;
                notificationHeader = R.string.notificationUI_u18_header_title;
                break;
        }
        Log.d("adservices", "eu test is " + isEuTest);
        Log.d("adservices", "notificationTitle is " + getString(context, notificationTitle));
        UiSelector notificationCardSelector =
                new UiSelector().text(getString(context, notificationTitle));

        UiObject scroller =
                device.findObject(
                        new UiSelector()
                                .packageName(SYSTEM_UI_NAME)
                                .resourceId(SYSTEM_UI_RESOURCE_ID));

        UiObject notificationCard = scroller.getChild(notificationCardSelector);
        if (!isDisplayed) {
            assertThat(notificationCard.exists()).isFalse();
            return;
        }
        assertThat(notificationCard.exists()).isTrue();

        notificationCard.click();
        Thread.sleep(LAUNCH_TIMEOUT_MS);
        UiObject title = getUiElement(device, context, notificationHeader);
        assertThat(title.exists()).isTrue();
    }

    public static void betaNotificationPage(
            Context context,
            UiDevice device,
            boolean isEuDevice,
            boolean isGoSettings,
            boolean isOptin)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId =
                isEuDevice
                        ? R.string.notificationUI_left_control_button_text_eu
                        : R.string.notificationUI_left_control_button_text;

        int rightButtonResId =
                isEuDevice
                        ? R.string.notificationUI_right_control_button_text_eu
                        : R.string.notificationUI_right_control_button_text;
        UiObject leftControlButton = getElement(context, device, leftButtonResId);
        UiObject rightControlButton = getElement(context, device, rightButtonResId);
        UiObject moreButton = getElement(context, device, R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();

        int clickCount = 10;
        while (moreButton.exists() && clickCount-- > 0) {
            moreButton.click();
            Thread.sleep(SCROLL_WAIT_TIME);
        }

        leftControlButton = getElement(context, device, leftButtonResId);
        rightControlButton = getElement(context, device, rightButtonResId);

        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();

        if (isEuDevice) {
            if (!isOptin) {
                leftControlButton.click();
            } else {
                rightControlButton.click();
            }
        } else {
            if (isGoSettings) {
                leftControlButton.click();
            } else {
                rightControlButton.click();
            }
            Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        }
    }

    public static void betaNotificationConfirmPage(
            Context context, UiDevice device, boolean isGoSettings)
            throws UiObjectNotFoundException, InterruptedException {
        int leftButtonResId = R.string.notificationUI_confirmation_left_control_button_text;

        int rightButtonResId = R.string.notificationUI_confirmation_right_control_button_text;

        UiObject leftControlButton = getElement(context, device, leftButtonResId);
        UiObject rightControlButton = getElement(context, device, rightButtonResId);

        if (isGoSettings) {
            leftControlButton.click();
        } else {
            rightControlButton.click();
        }
        Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
    }

    public static void euNotificationLandingPageMsmtAndFledgePage(
            Context context, UiDevice device, boolean isGoSettings)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject leftControlButton =
                getElement(
                        context,
                        device,
                        R.string.notificationUI_confirmation_left_control_button_text);
        UiObject rightControlButton =
                getElement(
                        context,
                        device,
                        R.string.notificationUI_confirmation_right_control_button_text);
        UiObject moreButton = getElement(context, device, R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();

        int clickCount = 10;
        while (moreButton.exists() && clickCount-- > 0) {
            moreButton.click();
            Thread.sleep(SCROLL_WAIT_TIME);
        }
        leftControlButton =
                getElement(
                        context,
                        device,
                        R.string.notificationUI_confirmation_left_control_button_text);
        rightControlButton =
                getElement(
                        context,
                        device,
                        R.string.notificationUI_confirmation_right_control_button_text);
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();
        if (isGoSettings) {
            leftControlButton.click();
        } else {
            rightControlButton.click();
        }
        Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
    }

    public static void euNotificationLandingPageTopicsPage(
            Context context, UiDevice device, boolean isOptin)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject leftControlButton =
                getElement(context, device, R.string.notificationUI_left_control_button_text_eu);
        UiObject rightControlButton =
                getElement(
                        context, device, R.string.notificationUI_right_control_button_ga_text_eu);
        UiObject moreButton = getElement(context, device, R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();
        int clickCount = 10;
        while (moreButton.exists() && clickCount-- > 0) {
            moreButton.click();
            Thread.sleep(SCROLL_WAIT_TIME);
        }
        leftControlButton =
                getElement(context, device, R.string.notificationUI_left_control_button_text_eu);
        rightControlButton =
                getElement(
                        context, device, R.string.notificationUI_right_control_button_ga_text_eu);
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();
        if (isOptin) {
            rightControlButton.click();
        } else {
            leftControlButton.click();
        }
    }

    public static void rowNotificationLandingPage(
            Context context, UiDevice device, boolean isGoSettings)
            throws UiObjectNotFoundException, InterruptedException {
        UiObject leftControlButton =
                getElement(context, device, R.string.notificationUI_left_control_button_text);
        UiObject rightControlButton =
                getElement(context, device, R.string.notificationUI_right_control_button_text);
        UiObject moreButton = getElement(context, device, R.string.notificationUI_more_button_text);
        assertThat(leftControlButton.exists()).isFalse();
        assertThat(rightControlButton.exists()).isFalse();
        assertThat(moreButton.exists()).isTrue();
        int clickCount = 10;
        while (moreButton.exists() && clickCount-- > 0) {
            moreButton.click();
            Thread.sleep(SCROLL_WAIT_TIME);
        }
        leftControlButton =
                getElement(context, device, R.string.notificationUI_left_control_button_text);
        rightControlButton =
                getElement(context, device, R.string.notificationUI_right_control_button_text);
        assertThat(leftControlButton.exists()).isTrue();
        assertThat(rightControlButton.exists()).isTrue();
        assertThat(moreButton.exists()).isFalse();
        if (isGoSettings) {
            leftControlButton.click();
        } else {
            rightControlButton.click();
        }
        Thread.sleep(PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
    }
}
