/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.adservices.data.measurement.migration;

/**
 * Container class for deprecated Measurement PPAPI table definitions and constants.
 */
public final class MeasurementTablesDeprecated {

    /** Contract for asynchronous Registration. */
    public interface AsyncRegistration {
        String INPUT_EVENT = "input_event";
        String REDIRECT = "redirect";
        String SCHEDULED_TIME = "scheduled_time";
    }

    public interface Source {
        String DEDUP_KEYS = "dedup_keys";
    }

    // Private constructor to prevent instantiation.
    private MeasurementTablesDeprecated() {
    }
}
