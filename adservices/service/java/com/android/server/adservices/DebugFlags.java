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

package com.android.server.adservices;

import com.android.adservices.service.CommonDebugFlags;

/**
 * Flags that are only used for development / testing purposes.
 *
 * <p>{@link DebugFlags} extends {@link CommonDebugFlags} to share common debug flags between system
 * server and service. This class contains system server only debug flags.
 *
 * @hide
 */
public final class DebugFlags extends CommonDebugFlags {

    private static final DebugFlags sInstance = new DebugFlags();

    public static DebugFlags getInstance() {
        return sInstance;
    }
}
