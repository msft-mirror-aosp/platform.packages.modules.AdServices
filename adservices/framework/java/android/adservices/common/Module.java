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

package android.adservices.common;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility class containing module type.
 *
 * @hide
 */
public class Module {
    public static final int ADID = 0;
    public static final int MEASUREMENT = 1;
    public static final int PA = 2;
    public static final int PAS = 3;
    public static final int TOPIC = 4;
    public static final int ODP = 5;

    /**
     * Result codes that are common across various modules.
     *
     * @hide
     */
    @IntDef(
            prefix = {""},
            value = {ADID, MEASUREMENT, PA, PAS, TOPIC, ODP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ModuleCode {}
}
