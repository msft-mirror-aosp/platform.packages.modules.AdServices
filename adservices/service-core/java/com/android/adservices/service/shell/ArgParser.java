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

package com.android.adservices.service.shell;

import static com.android.internal.util.Preconditions.checkArgument;

import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Map;

// TODO(b/322395593): Refactor ArgParser to work better after M-04 release.
final class ArgParser {
    private static final String TAG = ArgParser.class.getSimpleName();

    private final ImmutableList<String> mRequiredArgs;
    private final Map<String, String> mParsedArgs = new ArrayMap<>();

    ArgParser() {
        mRequiredArgs = ImmutableList.of();
    }

    ArgParser(String... requiredArgs) {
        mRequiredArgs = ImmutableList.copyOf(requiredArgs);
    }

    /**
     * Parses command line arguments with the `--key=value` format.
     *
     * @param args list of command line args, where the first element is the invoked command.
     * @return map containing arguments.
     * @throws IllegalArgumentException if any command line argument doesn't match expected format.
     * @throws IllegalArgumentException if any required command line argument is missing.
     */
    Map<String, String> parse(String... args) {
        checkArgument(args.length > 0, "No argument was passed to ArgParser.");
        Log.d(TAG, "Parsing command line arguments: " + Arrays.toString(args));
        for (int i = 1; i < args.length; i++) {
            parseArgument(args[i]);
        }
        verifyRequiredArgsArePresent();
        return mParsedArgs;
    }

    /**
     * Gets the value of a given command line argument by key.
     *
     * @param key Key of the argument, e.g. `--key=value`
     * @return Value of the argument e.g. `--key=value`
     */
    String getValue(@NonNull String key) {
        checkArgument(
                mParsedArgs.containsKey(key),
                String.format("Required command line argument `%s` is not present", key));
        return mParsedArgs.get(key);
    }

    private void parseArgument(String keyValueArgument) {
        checkArgument(
                keyValueArgument.startsWith("--") && keyValueArgument.contains("="),
                String.format(
                        "Command line arguments %s must use the syntax `--key=value`",
                        keyValueArgument));

        String[] parts = keyValueArgument.substring(2).split("=");
        checkArgument(
                parts.length == 2, "Command line arguments %s must use the syntax `--key=value`");

        String key = parts[0];
        String value = parts[1];
        checkArgument(
                !Strings.isNullOrEmpty(key) && !Strings.isNullOrEmpty(value),
                String.format(
                        "Command line arguments %s must use the syntax `--key=value`",
                        keyValueArgument));
        checkArgument(
                !mParsedArgs.containsKey(key),
                String.format(
                        "Command line argument as key `%s` is defined multiple times",
                        keyValueArgument));
        mParsedArgs.put(key, value);
    }

    private void verifyRequiredArgsArePresent() {
        for (String arg : mRequiredArgs) {
            checkArgument(
                    mParsedArgs.containsKey(arg),
                    String.format("Required command line argument `%s` is not present", arg));
        }
    }
}
