/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.adservices.test.scenario.adservices.topics;

import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/**
 * A helper that helps to sleep until reached start of an Epoch based on the fetched origin and test
 * epoch job period.
 */
final class EpochSleeper {
    private static final String TAG = "EpochSleeper";
    private static final String ORIGIN_TIMESTAMP_MS = "origin_timestamp_ms";
    private static final String SHARED_PREFERENCE_NAME = "epoch_sync";
    private static final String SETUP_SDK_NAME = "setup_origin";
    private static final String RETRIEVED_EPOCH_ORIGIN_LOG = "retrieved Epoch origin";
    private static final DateTimeFormatter DEFAULT_DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private static EpochSleeper sSleeper;

    private final Instant mFetchOriginStartTime;
    private final Context mContext;
    private final SharedPreferences mSharedPreferences;
    private final long mTestEpochPeriodMs;

    private EpochSleeper(Context context, long testEpochPeriodMs) {
        Preconditions.checkArgument(
                testEpochPeriodMs > 0L, "epoch period should be a positive long.");
        this.mContext = context;

        // A Crystal Ball test runs tests in iterations. Using a SharedPreference can effectively
        // share common data across the processes. The stored data will be cleaned up as test
        // application cycle ends, so it won't affect other tests.
        this.mSharedPreferences =
                context.getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_MULTI_PROCESS);
        this.mFetchOriginStartTime = Instant.now();
        this.mTestEpochPeriodMs = testEpochPeriodMs;
    }

    static EpochSleeper getInstance(Context context, long testEpochPeriodMs) {
        if (sSleeper == null) {
            sSleeper = new EpochSleeper(context, testEpochPeriodMs);
        }
        return sSleeper;
    }

    // Makes a call to getTopics API to trigger a log that tells the stored origin.
    void triggerOriginLog() {
        if (getOrigin() == -1) {
            try {
                AdvertisingTopicsClient client =
                        new AdvertisingTopicsClient.Builder()
                                .setContext(mContext)
                                .setSdkName(SETUP_SDK_NAME)
                                .setExecutor(Executors.newCachedThreadPool())
                                .build();
                client.getTopics().get();
            } catch (InterruptedException | ExecutionException e) {
                Log.i(TAG, "Failed to setup a Topics origin, keep on the test.");
            }
        }
    }

    // Fetches a valid origin and perform sleep till start of an Epoch.
    void sleepUntilNextEpoch() throws Exception {
        long origin = fetchOrigin();
        if (origin != -1) {

            // Uses origin to make sure every test invoke starts at the beginning of an Epoch.
            long calculatedSleepTimeMs =
                    mTestEpochPeriodMs - (System.currentTimeMillis() - origin) % mTestEpochPeriodMs;
            Thread.sleep(calculatedSleepTimeMs);
        }
    }

    private long getOrigin() {
        return mSharedPreferences.getLong(ORIGIN_TIMESTAMP_MS, -1L);
    }

    // Stores and shares the origin with other iterations.
    private void putOrigin(long origin) {
        mSharedPreferences.edit().putLong(ORIGIN_TIMESTAMP_MS, origin).commit();
    }

    // Fetches the origin from the SharedPreference firstly. If it does not exist, fetches again
    // from the log and updates the SharedPreference properly.
    private long fetchOrigin() {
        long origin = getOrigin();
        try {

            // Fetches and stores origin from log if not set.
            if (origin == -1) {
                origin = fetchOriginFromLog();
                putOrigin(origin);
            }
        } catch (IOException e) {
            Log.i(TAG, "Failed to fetch a Topics origin from logs, keep on the test.");
        }
        return origin;
    }

    private long fetchOriginFromLog() throws IOException {
        ProcessBuilder pb =
                new ProcessBuilder(
                        ImmutableList.of(
                                "logcat",
                                "-s",
                                "adservices.topics",
                                "-t",
                                DEFAULT_DATETIME_FORMATTER.format(mFetchOriginStartTime),
                                "|",
                                "grep",
                                RETRIEVED_EPOCH_ORIGIN_LOG));
        BufferedReader bufferedReader =
                new BufferedReader(new InputStreamReader(pb.start().getInputStream()));
        String[] arr =
                bufferedReader
                        .lines()
                        .filter(l -> l.contains(RETRIEVED_EPOCH_ORIGIN_LOG))
                        .findFirst()
                        .orElse(String.format("%s %d", RETRIEVED_EPOCH_ORIGIN_LOG, -1))
                        .split(" ");
        return Long.valueOf(arr[arr.length - 1].trim());
    }
}
