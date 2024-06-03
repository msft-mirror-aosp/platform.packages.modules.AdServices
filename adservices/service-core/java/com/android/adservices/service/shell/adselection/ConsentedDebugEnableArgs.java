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

package com.android.adservices.service.shell.adselection;

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.internal.util.Preconditions.checkArgument;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

class ConsentedDebugEnableArgs {
    public static final String SECRET_DEBUG_TOKEN_ARG_NAME = "--secret-debug-token";
    public static final String EXPIRY_IN_HOURS_ARG_NAME = "--expires-in-hours";
    @VisibleForTesting public static final int SECRET_DEBUG_TOKEN_MIN_LEN = 6;
    @VisibleForTesting public static final int DEFAULT_EXPIRY_IN_HOURS = 24;
    @VisibleForTesting public static final int MAX_EXPIRY_IN_HOURS = 30 * 24;
    private final String mSecretDebugToken;
    private final Instant mExpiryTimestamp;

    private ConsentedDebugEnableArgs(@NonNull String debugToken, @NonNull Instant expiryTimestamp) {
        mSecretDebugToken = Objects.requireNonNull(debugToken);
        mExpiryTimestamp = Objects.requireNonNull(expiryTimestamp);
    }

    public String getSecretDebugToken() {
        return mSecretDebugToken;
    }

    public Instant getExpiryTimestamp() {
        return mExpiryTimestamp;
    }

    @Override
    public String toString() {
        return SECRET_DEBUG_TOKEN_ARG_NAME.substring(2)
                + ": "
                + getSecretDebugToken()
                + ", "
                + EXPIRY_IN_HOURS_ARG_NAME.substring(2)
                + ": "
                + getExpiryTimestamp();
    }

    public static ConsentedDebugEnableArgs parseCliArgs(ImmutableMap<String, String> cliArgs) {
        Log.v(TAG, "Parsing command line arguments: " + cliArgs);
        String secretDebugToken = "";
        String expiryInHours = String.valueOf(DEFAULT_EXPIRY_IN_HOURS);
        for (Map.Entry<String, String> mapEntry : cliArgs.entrySet()) {
            String key = mapEntry.getKey();
            String value = mapEntry.getValue();
            switch (key) {
                case SECRET_DEBUG_TOKEN_ARG_NAME -> secretDebugToken = value;
                case EXPIRY_IN_HOURS_ARG_NAME -> expiryInHours = value;
                default -> throw new IllegalArgumentException("Unknown Argument: " + key);
            }
        }
        return new ConsentedDebugEnableArgs(
                parseDebugToken(secretDebugToken), parseExpiry(expiryInHours));
    }

    private static String parseDebugToken(String secretDebugToken) {
        Log.d(
                TAG,
                String.format(
                        "value %s for arg %s: ", secretDebugToken, SECRET_DEBUG_TOKEN_ARG_NAME));
        checkArgument(
                secretDebugToken != null && secretDebugToken.length() >= SECRET_DEBUG_TOKEN_MIN_LEN,
                "Minimum length for `%s` is '%d'",
                SECRET_DEBUG_TOKEN_ARG_NAME,
                SECRET_DEBUG_TOKEN_MIN_LEN);
        return secretDebugToken;
    }

    private static Instant parseExpiry(String expiryInHours) {
        Log.d(TAG, String.format("value %s for arg %s: ", expiryInHours, EXPIRY_IN_HOURS_ARG_NAME));
        if (expiryInHours == null || expiryInHours.isEmpty()) {
            return Instant.now().plus(Duration.ofHours(DEFAULT_EXPIRY_IN_HOURS));
        }
        int expiryInHoursInt;
        try {
            expiryInHoursInt = Integer.parseInt(expiryInHours);
        } catch (NumberFormatException exception) {
            Log.e(
                    TAG,
                    String.format(
                            "Arg value for %s is not a valid number. value: %s",
                            EXPIRY_IN_HOURS_ARG_NAME, expiryInHours));
            throw new IllegalArgumentException(
                    "Arg value for " + EXPIRY_IN_HOURS_ARG_NAME + " should be a positive number");
        }
        checkArgument(
                expiryInHoursInt > 0 && expiryInHoursInt <= MAX_EXPIRY_IN_HOURS,
                "`%s` should be greater than 0 and less than %d",
                EXPIRY_IN_HOURS_ARG_NAME,
                MAX_EXPIRY_IN_HOURS);
        return Instant.now().plus(Duration.ofHours(expiryInHoursInt));
    }
}
