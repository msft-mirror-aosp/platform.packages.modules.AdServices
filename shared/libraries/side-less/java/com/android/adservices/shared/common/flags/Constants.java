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
package com.android.adservices.shared.common.flags;

/**
 * Defines constants used by flags in production and testing infra (both device and host side).
 *
 * <p><b>NOTE: </b>cannot have any dependency on Android or other AdServices code.
 */
public final class Constants {

    /** (Default) string used to separate array values on flattened flags. */
    public static final String ARRAY_SPLITTER_COMMA = ",";

    /** Constant used to allow everything (typically all packages) on allow-list flags. */
    public static final String ALLOWLIST_ALL = "*";

    /** Constant used to not allow anything (typically all packages) on allow-list flags. */
    public static final String ALLOWLIST_NONE = "";

    // Maximum possible percentage for percentage variables
    public static final int MAX_PERCENTAGE = 100;

    private Constants() {
        throw new UnsupportedOperationException("Contains only static constants");
    }
}
