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

import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.internal.util.Preconditions.checkArgument;

import android.util.Log;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

/** Class to provide utilities to parse arguments passed to {@link ShellCommand} */
public class ShellCommandArgParserHelper {

    /**
     * @return An unmodifiable map of key, value parsing args from the provided start index.
     */
    public static ImmutableMap<String, String> parseCliArguments(String[] args, int startIndex) {
        return parseCliArguments(args, startIndex, false);
    }

    /**
     * @return An unmodifiable map of key, value parsing args from the provided start index.
     */
    public static ImmutableMap<String, String> parseCliArguments(
            String[] args, int startIndex, boolean removeKeyPrefix) {
        Map<String, String> argsMap = new HashMap<>();
        for (int i = startIndex; i < args.length; i += 2) {
            String key = args[i];
            checkArgument(
                    !argsMap.containsKey(key),
                    "Command line argument with key `%s` is defined multiple times",
                    key);
            Log.d(TAG, String.format("Parsing arg value for arg: %s ", key));
            checkArgument(
                    i + 1 < args.length, "Required value for argument `%s` is not present", key);
            String value = args[i + 1];
            checkArgument(
                    key.startsWith("--") && !value.contains("--"),
                    "Command line arguments `%s %s` must use the syntax `--key value`",
                    key,
                    value);
            Log.d(TAG, String.format("arg value for arg %s is %s", key, value));
            if (removeKeyPrefix) {
                key = key.replace("--", "");
            }
            argsMap.put(key, value);
        }
        return ImmutableMap.copyOf(argsMap);
    }
}
