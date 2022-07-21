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

package com.android.adservices.tests.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class AdServicesCommonManagerTest {
    private static final String TAG = "AdservicesStatusManagerTest";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private AdServicesCommonManager mCommonManager =
            sContext.getSystemService(AdServicesCommonManager.class);

    @Test
    public void testStatusManager() throws Exception {
        overrideAdserviceEnableStatus(false);

        // At beginning, Sdk1 receives a false status.
        Boolean sdk1Response = getAdservicesStatus().get();
        assertThat(sdk1Response).isFalse();

        overrideAdserviceEnableStatus(true);

        Boolean sdk2Response = getAdservicesStatus().get();
        assertThat(sdk2Response).isTrue();
    }

    // Override the Adservice enable status in the test.
    private void overrideAdserviceEnableStatus(boolean overrideStatus) {
        ShellUtils.runShellCommand(
                "device_config put adservices adservice_enable_status " + overrideStatus);
    }

    private ListenableFuture<Boolean> getAdservicesStatus() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.isAdServicesEnabled(
                            CALLBACK_EXECUTOR,
                            new OutcomeReceiver<Boolean, AdServicesException>() {
                                @Override
                                public void onResult(Boolean result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(AdServicesException error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }
}
