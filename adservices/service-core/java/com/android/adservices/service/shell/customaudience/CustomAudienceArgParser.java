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

package com.android.adservices.service.shell.customaudience;

import static com.android.internal.util.Preconditions.checkArgument;

import android.util.ArrayMap;

import com.android.adservices.service.shell.ShellCommandArgParserHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

// TODO(b/322395593): Refactor ArgParser to work better after M-04 release.
final class CustomAudienceArgParser {

    private static final int ARG_PARSE_START_INDEX = 2;
    private final ImmutableList<String> mRequiredArgs;
    private final Map<String, String> mParsedArgs = new ArrayMap<>();

    CustomAudienceArgParser() {
        mRequiredArgs = ImmutableList.of();
    }

    CustomAudienceArgParser(String... requiredArgs) {
        mRequiredArgs = ImmutableList.copyOf(requiredArgs);
    }

    /**
     * Parses command line arguments with the {@code --key value} format.
     *
     * @param args list of command line args, where the first element is the invoked command.
     * @return map containing arguments.
     * @throws IllegalArgumentException if any command line argument doesn't match expected format.
     * @throws IllegalArgumentException if any required command line argument is missing.
     */
    Map<String, String> parse(String[] args) {
        checkArgument(args.length > 0, "No argument was passed to CustomAudienceArgParser.");
        mParsedArgs.clear();
        ImmutableMap<String, String> cliArgs =
                ShellCommandArgParserHelper.parseCliArguments(args, ARG_PARSE_START_INDEX);
        mParsedArgs.putAll(cliArgs);
        verifyRequiredArgsArePresent();
        return mParsedArgs;
    }

    /**
     * Gets the value of a given command line argument by key.
     *
     * @param key Key of the argument, e.g. {@code --key value}
     * @return Value of the argument e.g. {@code --key value}
     */
    String getValue(String key) {
        checkArgument(
                mParsedArgs.containsKey(key),
                "Required command line argument `%s` is not present",
                key);
        return mParsedArgs.get(key);
    }

    private void verifyRequiredArgsArePresent() {
        for (String arg : mRequiredArgs) {
            checkArgument(
                    mParsedArgs.containsKey(arg),
                    "Required command line argument `%s` is not present",
                    arg);
        }
    }
}
