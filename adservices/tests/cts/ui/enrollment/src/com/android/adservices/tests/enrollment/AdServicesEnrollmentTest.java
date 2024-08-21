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

import static com.android.adservices.service.shell.common.EnableAdServicesCommand.CMD_ENABLE_ADSERVICES;
import static com.android.adservices.service.shell.common.ResetConsentCommand.CMD_RESET_CONSENT_DATA;

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.shared.testing.shell.CommandResult;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;

import org.junit.Test;

public final class AdServicesEnrollmentTest extends AdServicesCtsTestCase
        implements CtsEnrollmentFlags {
    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed.
     */
    @Test
    public void testEnableAdServices() throws Exception {
        AdServicesShellCommandHelper adServicesShellCommandHelper =
                new AdServicesShellCommandHelper();
        CommandResult commandResult =
                adServicesShellCommandHelper.runCommandRwe(CMD_RESET_CONSENT_DATA);

        Log.i(
                mTag,
                "Invoked reset consent data through cli, output from cli:"
                        + commandResult.getOut());
        commandResult = adServicesShellCommandHelper.runCommandRwe(CMD_ENABLE_ADSERVICES);
        Log.i(
                mTag,
                "Invoked enableAdServices through cli, output from cli:" + commandResult.getOut());

        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // TODO(b/357961246) to fix R No enrollment channel available issue
        AdservicesWorkflows.verifyNotification(
                mContext,
                uiDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                UiConstants.UX.GA_UX);

        uiDevice.pressHome();
    }
}
