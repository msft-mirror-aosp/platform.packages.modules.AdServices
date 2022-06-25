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

package com.android.tests.sdkprovider.restrictionstest;

import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;

import java.util.concurrent.Executor;

public class RestrictionsTestSandboxedSdkProvider extends SandboxedSdkProvider {

    private static final String BUNDLE_KEY_PHASE_NAME = "phase-name";
    private SandboxedSdkContext mSdkContext;

    @Override
    public void initSdk(SandboxedSdkContext sandboxedSdkContext, Bundle params, Executor executor,
            InitSdkCallback callback) {
        mSdkContext = sandboxedSdkContext;
        callback.onInitSdkFinished(new Bundle());
    }

    @Override
    public View getView(Context windowContext, Bundle params) {

        handlePhase(params);
        return new View(windowContext);
    }

    @Override
    public void onDataReceived(Bundle data, DataReceivedCallback callback) {}

    private void handlePhase(Bundle params) {
        String phaseName = params.getString(BUNDLE_KEY_PHASE_NAME, "");
        switch (phaseName) {
            case "testSdkSandboxBroadcastRestrictions":
                testSdkSandboxBroadcastRestrictions();
                break;
            default:
        }
    }

    // Tries to register a broadcast receiver. An exception will be thrown if broadcast restrictions
    // are being enforced.
    void testSdkSandboxBroadcastRestrictions() {
        mSdkContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            }
        }, new IntentFilter(Intent.ACTION_SEND));
    }
}
