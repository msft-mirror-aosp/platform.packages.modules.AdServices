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
package com.android.adservices.tests.enrollment;

import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_DISABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_ENABLED;
import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_UNKNOWN;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_NONE;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_ONGOING;
import static android.adservices.common.AdServicesCommonManager.NOTIFICATION_REGULAR;
import static android.adservices.common.Module.ADID;
import static android.adservices.common.Module.MEASUREMENT;
import static android.adservices.common.Module.ON_DEVICE_PERSONALIZATION;
import static android.adservices.common.Module.PROTECTED_APP_SIGNALS;
import static android.adservices.common.Module.PROTECTED_AUDIENCE;
import static android.adservices.common.Module.TOPICS;

import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adservicesapi.ResetConsentCommand.CMD_RESET_CONSENT_DATA;
import static com.android.adservices.service.shell.adservicesapi.SetModuleStatesCommand.CMD_SET_MODULE_STATES;
import static com.android.adservices.service.shell.adservicesapi.SetUserChoicesCommand.CMD_SET_USER_CHOICES;

import android.adservices.common.NotificationType;
import android.adservices.common.UpdateAdServicesModuleStatesParams;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.shared.testing.shell.CommandResult;
import com.android.adservices.tests.ui.libs.UiConstants;
import com.android.adservices.tests.ui.libs.pages.NotificationPages;

import org.junit.Test;

import java.util.Map;

public final class AdServicesEnrollmentTest extends AdServicesCtsTestCase
        implements EnrollmentTestFlags {

    private static final Map<Integer, String> MODULE_CODE_MAPPING =
            Map.of(
                    MEASUREMENT, "msmt",
                    PROTECTED_AUDIENCE, "pa",
                    PROTECTED_APP_SIGNALS, "pas",
                    TOPICS, "topics",
                    ON_DEVICE_PERSONALIZATION, "odp",
                    ADID, "adid");

    private static final Map<Integer, String> MODULE_STATE_MAPPING =
            Map.of(
                    MODULE_STATE_UNKNOWN, "unknown",
                    MODULE_STATE_ENABLED, "enabled",
                    MODULE_STATE_DISABLED, "disabled");

    private static final Map<Integer, String> NOTIFICATION_TYPE_MAPPING =
            Map.of(
                    NOTIFICATION_NONE, "none",
                    NOTIFICATION_ONGOING, "ongoing",
                    NOTIFICATION_REGULAR, "regular");

    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();

    public static final int COMMAND_LINE_EXECUTION_WAIT_TIME_MS = 500;

    /**
     * Verify that for GA, the personalized notification is displayed. Even it extends
     * AdServicesCtsTestCase it is not config as part of CTS suite.
     */
    @Test
    public void testAdServicesSetModuleStates_happyPath() throws Exception {

        resetConsent();
        UpdateAdServicesModuleStatesParams params =
                new UpdateAdServicesModuleStatesParams.Builder()
                        .setModuleState(MEASUREMENT, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_AUDIENCE, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_APP_SIGNALS, MODULE_STATE_DISABLED)
                        .setModuleState(TOPICS, MODULE_STATE_ENABLED)
                        .setModuleState(ON_DEVICE_PERSONALIZATION, MODULE_STATE_DISABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_ONGOING)
                        .build();
        setModuleStates(params);

        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        NotificationPages.verifyNotificationV2(
                mContext, uiDevice, UiConstants.NotificationMode.UPDATED_FIRST_TIME);

        uiDevice.pressHome();
    }

    @Test
    public void testAdServicesSetModuleStates_nonPersonalizedAds() throws Exception {

        resetConsent();
        UpdateAdServicesModuleStatesParams params =
                new UpdateAdServicesModuleStatesParams.Builder()
                        .setModuleState(MEASUREMENT, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_AUDIENCE, MODULE_STATE_DISABLED)
                        .setModuleState(PROTECTED_APP_SIGNALS, MODULE_STATE_DISABLED)
                        .setModuleState(TOPICS, MODULE_STATE_DISABLED)
                        .setModuleState(ON_DEVICE_PERSONALIZATION, MODULE_STATE_DISABLED)
                        .setModuleState(ADID, MODULE_STATE_DISABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_REGULAR)
                        .build();
        setModuleStates(params);

        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        NotificationPages.verifyNotificationV2(
                mContext, uiDevice, UiConstants.NotificationMode.LIMITED);

        uiDevice.pressHome();
    }

    @Test
    public void testAdServicesSetModuleStates_renotify() throws Exception {

        resetConsent();
        UpdateAdServicesModuleStatesParams params =
                new UpdateAdServicesModuleStatesParams.Builder()
                        .setModuleState(MEASUREMENT, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_AUDIENCE, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_APP_SIGNALS, MODULE_STATE_DISABLED)
                        .setModuleState(TOPICS, MODULE_STATE_ENABLED)
                        .setModuleState(ON_DEVICE_PERSONALIZATION, MODULE_STATE_DISABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_NONE)
                        .build();
        setModuleStates(params);
        // need set user choices so that re-notification will be triggered
        setUserChoices();

        UpdateAdServicesModuleStatesParams paramsRenotify =
                new UpdateAdServicesModuleStatesParams.Builder()
                        .setModuleState(MEASUREMENT, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_AUDIENCE, MODULE_STATE_ENABLED)
                        .setModuleState(PROTECTED_APP_SIGNALS, MODULE_STATE_ENABLED)
                        .setModuleState(TOPICS, MODULE_STATE_ENABLED)
                        .setModuleState(ON_DEVICE_PERSONALIZATION, MODULE_STATE_ENABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_ONGOING)
                        .build();
        setModuleStates(paramsRenotify);

        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        NotificationPages.verifyNotificationV2(
                mContext, uiDevice, UiConstants.NotificationMode.UPDATED_RENOTIFY);

        uiDevice.pressHome();
    }

    private void setUserChoices() {
        SystemClock.sleep(COMMAND_LINE_EXECUTION_WAIT_TIME_MS);
        String userChoiceParam = " --topics opted-in --msmt opted-in --pa opted-in";
        String setUserChoicesStateCmdFmt = "%s %s %s";
        CommandResult commandResult =
                mAdServicesShellCommandHelper.runCommandRwe(
                        setUserChoicesStateCmdFmt,
                        COMMAND_PREFIX,
                        CMD_SET_USER_CHOICES,
                        userChoiceParam);
        Log.i(
                mTag,
                "Invoked set user choices through cli, output from cli:" + commandResult.getOut());
    }

    private void setModuleStates(UpdateAdServicesModuleStatesParams params) {

        SystemClock.sleep(COMMAND_LINE_EXECUTION_WAIT_TIME_MS);
        String setModuleStateCmdFmt = "%s %s %s";
        CommandResult commandResult =
                mAdServicesShellCommandHelper.runCommandRwe(
                        setModuleStateCmdFmt,
                        COMMAND_PREFIX,
                        CMD_SET_MODULE_STATES,
                        covertUpdateAdServicesModuleStatesParamsToCmdStr(params));
        Log.i(
                mTag,
                "Invoked set module state  through cli, output from cli:" + commandResult.getOut());
    }

    private void resetConsent() {
        CommandResult commandResult =
                mAdServicesShellCommandHelper.runCommandRwe(
                        COMMAND_PREFIX + " " + CMD_RESET_CONSENT_DATA);

        Log.i(
                mTag,
                "Invoked reset consent data through cli, output from cli:"
                        + commandResult.getOut());
    }

    private String covertUpdateAdServicesModuleStatesParamsToCmdStr(
            UpdateAdServicesModuleStatesParams params) {
        StringBuilder sb = new StringBuilder();
        for (int key : MODULE_CODE_MAPPING.keySet()) {
            String moduleName = MODULE_CODE_MAPPING.get(key);
            int moduleStateVal = params.getModuleState(key);
            String moduleStateStr = MODULE_STATE_MAPPING.getOrDefault(moduleStateVal, "unknown");
            sb.append(String.format(" --%s %s", moduleName, moduleStateStr));
        }
        sb.append(
                " --notification-type "
                        + NOTIFICATION_TYPE_MAPPING.getOrDefault(
                                params.getNotificationType(), "ongoing"));
        return sb.toString();
    }
}
