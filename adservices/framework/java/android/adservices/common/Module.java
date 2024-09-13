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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.SystemApi;

import com.android.adservices.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents AdServices Module type.
 *
 * <p>This class is used to identify the adservices feature that we want to turn on/off or set user
 * consent.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_ADSERVICES_ENABLE_PER_MODULE_OVERRIDES_API)
public final class Module {
    /** Unknown module. */
    public static final int UNKNOWN = 0;

    /** Measurement module. */
    public static final int MEASUREMENT = 1;

    /** Privacy Sandbox module. */
    public static final int PROTECTED_AUDIENCE = 2;

    /** Privacy Sandbox Attribution module. */
    public static final int PROTECTED_APP_SIGNALS = 3;

    /** Topics module. */
    public static final int TOPICS = 4;

    /** On-device Personalization(ODP) module. */
    public static final int ON_DEVICE_PERSONALIZATION = 5;

    /** ADID module. */
    public static final int ADID = 6;

    /** Default Contractor, make it private so that it won't show in the system-current.txt */
    private Module() {}

    /**
     * ModuleCode IntDef.
     *
     * @hide
     */
    @IntDef(
            value = {
                UNKNOWN,
                MEASUREMENT,
                PROTECTED_AUDIENCE,
                PROTECTED_APP_SIGNALS,
                TOPICS,
                ON_DEVICE_PERSONALIZATION,
                ADID
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ModuleCode {}
}
