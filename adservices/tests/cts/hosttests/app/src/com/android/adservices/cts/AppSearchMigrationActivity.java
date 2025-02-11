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
package com.android.adservices.cts;

import android.adservices.measurement.MeasurementManager;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AppSearchMigrationActivity extends AppCompatActivity {
    private static final String TAG = "AppSearchMigrationActivity";
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Starting AppSearchMigrationActivity");
        callMeasurementApi();
    }

    private void callMeasurementApi() {
        Log.i(TAG, "Calling Measurement api");
        MeasurementManager mgr = MeasurementManager.get(this);
        mgr.getMeasurementApiStatus(EXECUTOR, getOutcomeReceiver("GetMeasurementStatus"));
    }

    private <T> OutcomeReceiver<T, Exception> getOutcomeReceiver(String prefix) {
        return new OutcomeReceiver<>() {
            @Override
            public void onResult(T result) {
                Log.d(TAG, prefix + " API call succeeded");
            }

            @Override
            public void onError(@NonNull Exception e) {
                Log.e(TAG, prefix + " API call failed", e);
            }
        };
    }
}
