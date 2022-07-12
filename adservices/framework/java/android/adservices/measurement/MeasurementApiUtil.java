/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.adservices.measurement;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 *  This class encapsulates possible states of the APIs exposed by MeasurementApi apk.
 */
public class MeasurementApiUtil {

    private MeasurementApiUtil() {
    }

    /**
     * This state indicates that Measurement APIs are unavailable.
     * Invoking them will result in an {@link UnsupportedOperationException}.
     */
    public static final int MEASUREMENT_API_STATE_DISABLED = 0;

    /**
     * This state indicates that Measurement APIs are enabled.
     */
    public static final int MEASUREMENT_API_STATE_ENABLED = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "MEASUREMENT_API_STATE_", value = {
            MEASUREMENT_API_STATE_DISABLED,
            MEASUREMENT_API_STATE_ENABLED,
    })
    public @interface MeasurementApiState {}
}

