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

package com.android.adservices.service.shell.attributionreporting;

import com.android.adservices.service.shell.ShellCommandArgParserHelper;

import com.google.common.collect.ImmutableMap;

import java.io.PrintWriter;

public class AttributionReportingUtil {
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_FULL = "full";

    /**
     * Parses schema argument from the command line.
     *
     * @param args The command line arguments.
     * @return The schema value, defaulting to "partial" if not specified.
     * @throws IllegalArgumentException If the schema value is missing or invalid.
     */
    public static String parseAttributionReportingSchema(
            String[] args, int startIndex, PrintWriter out) throws IllegalArgumentException {
        ImmutableMap<String, String> parsedArgs =
                ShellCommandArgParserHelper.parseCliArguments(args, startIndex);
        String schema = parsedArgs.getOrDefault("--schema", SCHEMA_PARTIAL);
        if (!schema.equals(SCHEMA_PARTIAL) && !schema.equals(SCHEMA_FULL)) {
            throw new IllegalArgumentException(
                    "Invalid schema. The 'schema' parameter must be either 'partial' or 'full'."
                            + " Check for typos.");
        }
        return schema;
    }
}
