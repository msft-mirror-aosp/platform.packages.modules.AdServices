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
package com.android.adservices.tests.ui.libs;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import static com.android.adservices.AdServicesCommon.BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_MANAGER_OTA_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_NOTIFICATION_RESET_TOKEN;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_AD_SERVICES_SYSTEM_API;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_GET_ADSERVICES_COMMON_STATES_ALLOW_LIST;
import static com.android.adservices.service.FlagsConstants.KEY_IS_EEA_DEVICE;
import static com.android.adservices.service.FlagsConstants.KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PAS_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_U18_UX_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_OTA_STRINGS_FEATURE_ENABLED;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_NAME;
import static com.android.adservices.tests.ui.libs.UiConstants.SYSTEM_UI_RESOURCE_ID;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesStates;
import android.content.Context;
import android.graphics.Point;
import android.os.OutcomeReceiver;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.SearchCondition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.adservices.LogUtil;
import com.android.adservices.api.R;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.shared.testing.common.FileHelper;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class UiUtils {
    public static final int LAUNCH_TIMEOUT = 5000;
    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT = 500;
    public static final int SCROLL_WAIT_TIME = 1000;

    private static final int DEFAULT_BINDER_CONNECTION_TIMEOUT_MS = 10000;

    private static final String ANDROID_WIDGET_SCROLLVIEW = "android.widget.ScrollView";

    /** Refreshes the consent reset token to a new randomly-generated value */
    public static void refreshConsentResetToken(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_CONSENT_NOTIFICATION_RESET_TOKEN, UUID.randomUUID().toString());
    }

    /** Enables consent manager debug mode */
    public static void enableConsentDebugMode(AdServicesFlagsSetterRule flags) {
        flags.setDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, true);
    }

    /** Disables consent manager debug mode */
    public static void disableConsentDebugMode(AdServicesFlagsSetterRule flags) {
        flags.setDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE, false);
    }

    /** Sets the flag to mimic the behavior of an EEA device */
    public static void setAsEuDevice(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_IS_EEA_DEVICE, true);
    }

    /** Sets the flag to mimic the behavior of a non-EEA device */
    public static void setAsRowDevice(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_IS_EEA_DEVICE, false);
    }

    /** Enables Under-18 UX */
    public static void enableU18(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_U18_UX_ENABLED, true);
    }

    /** Sets the flag to enable GA UX */
    public static void enableGa(AdServicesFlagsSetterRule flags) throws Exception {
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, true);
    }

    /** Enables the enableAdServices system API. */
    public static void turnOnAdServicesSystemApi(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_ENABLE_AD_SERVICES_SYSTEM_API, true);
    }

    /** Sets the flag to enable Beta UX */
    public static void enableBeta(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_GA_UX_FEATURE_ENABLED, false);
    }

    /** Sets the flag to disable OTA String download feature */
    public static void disableOtaStrings(AdServicesFlagsSetterRule flags) throws Exception {
        flags.setFlag(KEY_UI_OTA_STRINGS_FEATURE_ENABLED, false);
    }

    /** Restarts the AdServices process */
    public static void restartAdservices() {
        ShellUtils.runShellCommand("am force-stop com.google.android.adservices.api");
        ShellUtils.runShellCommand("am force-stop com.android.adservices.api");
    }

    /** Sets flag consent_manager_debug_mode to true in tests */
    public static void setConsentManagerDebugMode(AdServicesFlagsSetterRule flags) {
        flags.setDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE, true);
    }

    /** Sets flag consent_manager_ota_debug_mode to true in tests */
    public static void setConsentManagerOtaDebugMode(AdServicesFlagsSetterRule flags) {
        flags.setDebugFlag(KEY_CONSENT_MANAGER_OTA_DEBUG_MODE, true);
    }

    /** Sets flag consent_manager_debug_mode to false in tests */
    public static void resetConsentManagerDebugMode(AdServicesFlagsSetterRule flags) {
        flags.setDebugFlag(KEY_CONSENT_MANAGER_DEBUG_MODE, false);
    }

    public static void enableNotificationPermission() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .grantRuntimePermission("com.android.adservices.api", POST_NOTIFICATIONS);
    }

    /** Sets the flag to disable the V2 notification flow */
    public static void disableNotificationFlowV2(AdServicesFlagsSetterRule flags) throws Exception {
        flags.setFlag("eu_notif_flow_change_enabled", false);
    }

    /** Sets the binder time for cts test */
    public static void setBinderTimeout(AdServicesFlagsSetterRule flags) {
        flags.setDebugFlag(
                BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME, DEFAULT_BINDER_CONNECTION_TIMEOUT_MS);
    }

    /** Enables pas */
    public static void enablePas(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_PAS_UX_ENABLED, true);
    }

    /** Disables pas */
    public static void disablePas(AdServicesFlagsSetterRule flags) {
        flags.setFlag(KEY_PAS_UX_ENABLED, false);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            boolean isEuTest,
            UiConstants.UX ux)
            throws Exception {
        int notificationTitle = -1;
        int notificationHeader = -1;
        switch (ux) {
            case GA_UX:
                // Should match the contentTitle string in ConsentNotificationTrigger.java.
                notificationTitle =
                        isEuTest
                                ? R.string.notificationUI_notification_ga_title_eu_v2
                                : R.string.notificationUI_notification_ga_title_v2;
                // Should match the text in consent_notification_screen_1_ga_v2_eu.xml and
                // consent_notification_screen_1_ga_v2_row.xml, respectively.
                notificationHeader =
                        isEuTest
                                ? R.string.notificationUI_fledge_measurement_title_v2
                                : R.string.notificationUI_header_ga_title_v2;
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

        verifyNotification(context, device, isDisplayed, notificationTitle, notificationHeader);
    }

    public static void verifyNotification(
            Context context,
            UiDevice device,
            boolean isDisplayed,
            int notificationTitle,
            int notificationHeader)
            throws Exception {
        device.openNotification();
        device.waitForIdle(LAUNCH_TIMEOUT);
        // Wait few seconds for Adservices notification to show, waitForIdle is not enough.
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject2 scroller = device.findObject(By.res(SYSTEM_UI_RESOURCE_ID));

        BySelector notificationTitleSelector = By.text(getString(context, notificationTitle));
        if (!isDisplayed) {
            assertThat(scroller.hasObject(notificationTitleSelector)).isFalse();
            return;
        }
        assertThat(scroller.hasObject(notificationTitleSelector)).isTrue();
        UiObject2 notificationCard =
                scroller.findObject(By.text(getString(context, notificationTitle)));

        notificationCard.click();
        device.waitForIdle(LAUNCH_TIMEOUT);
        Thread.sleep(LAUNCH_TIMEOUT);
        UiObject2 title = getElement(context, device, notificationHeader);
        assertThat(title).isNotNull();
    }

    /**
     * Swipes through the screen to show elements on the button of the page but hidden by the
     * navigation bar.
     *
     * @param device the UiDevice to manipulate
     */
    public static void gentleSwipe(UiDevice device) {
        UiObject2 scrollView =
                device.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW));
        if (scrollView != null) {
                scrollView.scroll(Direction.DOWN, /* percent */ 0.25F);
        }
    }

    /** Sets the flag to enable or disable the flip flow */
    public static void setFlipFlow(AdServicesFlagsSetterRule flags, boolean isFlip) {
        flags.setFlag("eu_notif_flow_change_enabled", isFlip);
    }

    /** Sets get adservices common states services enabled */
    public static void setGetAdservicesCommonStatesServiceEnable(
            AdServicesFlagsSetterRule flags, boolean enable) {
        flags.setFlag(KEY_IS_GET_ADSERVICES_COMMON_STATES_API_ENABLED, enable);
    }

    /** Sets get adservices common states services enabled */
    public static void setGetAdservicesCommonStatesAllowList(
            AdServicesFlagsSetterRule flags, String list) {
        flags.setFlag(KEY_GET_ADSERVICES_COMMON_STATES_ALLOW_LIST, list);
    }

    public static String getString(Context context, int resourceId) {
        return context.getResources().getString(resourceId);
    }

    public static void scrollToAndClick(Context context, UiDevice device, int resId) {
        scrollTo(context, device, resId);
        UiObject2 consentPageButton =
                device.wait(
                        getSearchCondByResId(context, resId), PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT);
        clickTopLeft(consentPageButton);
    }

    public static SearchCondition<UiObject2> getSearchCondByResId(Context context, int resId) {
        String targetStr = getString(context, resId);
        return Until.findObject(By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE)));
    }

    public static UiObject2 getPageElement(Context context, UiDevice device, int resId) {
        return device.findObject(By.text(getString(context, resId)));
    }

    public static UiObject2 scrollTo(Context context, UiDevice device, int resId) {
        UiObject2 scrollView =
                device.findObject(By.scrollable(true).clazz(ANDROID_WIDGET_SCROLLVIEW));
        if (scrollView != null) {
            String targetStr = getString(context, resId);
            scrollView.scrollUntil(
                    Direction.DOWN,
                    Until.findObject(
                            By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE))));
        }
        return getElement(context, device, resId);
    }

    public static UiObject2 getElement(Context context, UiDevice device, int resId) {
        return getElement(context, device, resId, 0);
    }

    public static UiObject2 getElement(Context context, UiDevice device, int resId, int index) {
        String targetStr = getString(context, resId);
        List<UiObject2> objList =
                device.findObjects(By.text(Pattern.compile(targetStr, Pattern.CASE_INSENSITIVE)));
        if (objList.size() <= index) {
            return null;
        }
        return objList.get(index);
    }

    public static void clickTopLeft(UiObject2 obj) {
        assertThat(obj).isNotNull();
        obj.click(new Point(obj.getVisibleBounds().top, obj.getVisibleBounds().left));
    }

    public static void takeScreenshot(UiDevice device, String methodName) {
        try {
            String timeStamp =
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                            .format(Date.from(Instant.now()));

            File screenshotFile =
                    new File(
                            FileHelper.getAdServicesTestsOutputDir(),
                            methodName + timeStamp + ".png");
            device.takeScreenshot(screenshotFile);
        } catch (RuntimeException e) {
            LogUtil.e("Failed to take screenshot: " + e.getMessage());
        }
    }

    /** Resets AdServices consent data */
    public static void resetAdServicesConsentData(Context context, AdServicesFlagsSetterRule flags)
            throws Exception {
        // Neeed to disable debug mode since it takes precedence over reset channel.
        disableConsentDebugMode(flags);
        turnOnAdServicesSystemApi(flags);

        AdServicesCommonManager mCommonManager = AdServicesCommonManager.get(context);

        // Reset consent and thereby AdServices data before each test.
        UiUtils.refreshConsentResetToken(flags);

        SettableFuture<Boolean> responseFuture = SettableFuture.create();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setU18Account(true)
                        .setPrivacySandboxUiEnabled(true)
                        .setPrivacySandboxUiRequest(false)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        responseFuture.set(result);
                    }

                    @Override
                    public void onError(Exception exception) {
                        responseFuture.setException(exception);
                    }
                });

        Boolean response = responseFuture.get();
        assertThat(response).isTrue();
    }

    /** Returns a [BySelector] of a resource in sysui package. */
    public static BySelector sysuiResSelector(String resourceId) {
        return By.pkg(SYSTEM_UI_NAME).res(SYSTEM_UI_NAME, resourceId);
    }
}
