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

import static android.adservices.common.AdServicesCommonManager.MODULE_STATE_ENABLED;
import static android.adservices.common.Module.MEASUREMENT;

import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adservicesapi.EnableAdServicesCommand.CMD_ENABLE_ADSERVICES;
import static com.android.adservices.service.shell.adservicesapi.ResetConsentCommand.CMD_RESET_CONSENT_DATA;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.NotificationType;
import android.adservices.common.UpdateAdServicesModuleStatesParams;
import android.util.Log;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.shared.testing.shell.CommandResult;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;

public final class AdServicesEnrollmentTest extends AdServicesCtsTestCase
        implements EnrollmentTestFlags {
    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed. Note that it extends AdServicesCtsTestCase, it is not part of Cts test suite.
     */
    @Test
    public void testEnableAdServices() throws Exception {
        AdServicesShellCommandHelper adServicesShellCommandHelper =
                new AdServicesShellCommandHelper();
        CommandResult commandResult =
                adServicesShellCommandHelper.runCommandRwe(
                        COMMAND_PREFIX + " " + CMD_RESET_CONSENT_DATA);

        Log.i(
                mTag,
                "Invoked reset consent data through cli, output from cli:"
                        + commandResult.getOut());

        AdServicesCommonManager commonManager = AdServicesCommonManager.get(mContext);

        UpdateAdServicesModuleStatesParams params =
                new UpdateAdServicesModuleStatesParams.Builder()
                        .setModuleState(MEASUREMENT, MODULE_STATE_ENABLED)
                        .setNotificationType(NotificationType.NOTIFICATION_ONGOING)
                        .build();
        ListenableFuture<Integer> responseFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            commonManager.requestAdServicesModuleOverrides(
                                    params,
                                    AdServicesExecutors.getLightWeightExecutor(),
                                    new AdServicesOutcomeReceiver<Void, Exception>() {
                                        @Override
                                        public void onResult(Void unused) {
                                            completer.set(null);
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            completer.set(2);
                                        }
                                    });
                            return "enableAdservices";
                        });
        int response = responseFuture.get();
        expect.that(response).isAtLeast(0);

        commandResult =
                adServicesShellCommandHelper.runCommandRwe(
                        COMMAND_PREFIX + " " + CMD_ENABLE_ADSERVICES);
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
