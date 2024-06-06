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

package android.adservices.test.scenario.adservices.fledge.utils;

import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.signals.UpdateSignalsRequest;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Helps calling PAS APIs. */
public class ProtectedAppSignalsTestFixture {
    private static final String TAG = ProtectedAppSignalsTestFixture.class.getName();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    protected static final int API_RESPONSE_TIMEOUT_SECONDS = 100;

    protected static final ProtectedSignalsClient PAS_CLIENT =
            new ProtectedSignalsClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

    /** Runs updateSignals with given uri */
    public static boolean updateSignals(String signalsUriString) {
        Uri signalsUri = Uri.parse(signalsUriString);
        UpdateSignalsRequest request = new UpdateSignalsRequest.Builder(signalsUri).build();
        try {
            PAS_CLIENT.updateSignals(request).get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Update Signals failed!", e);
            return false;
        }
        return true;
    }
}
