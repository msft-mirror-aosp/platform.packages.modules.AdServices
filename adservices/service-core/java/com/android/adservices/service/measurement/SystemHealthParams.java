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

package com.android.adservices.service.measurement;

import java.util.concurrent.TimeUnit;

/**
 * Class for holding system health related parameters.
 * All values in this class are temporary and subject to change based on feedback and testing.
 */
public class SystemHealthParams {

    private SystemHealthParams() {
    }

    /**
     * Max number of triggers an app can register.
     */
    public static final int MAX_TRIGGER_REGISTERS_PER_REGISTRANT = 1000; // placeholder value

    /** Max number of sources per publisher. */
    public static final long MAX_SOURCES_PER_PUBLISHER = 1024L;

    /** Delay for attribution job triggering. */
    public static final long ATTRIBUTION_JOB_TRIGGERING_DELAY_MS = TimeUnit.MINUTES.toMillis(2);

    /**
     * Max number of {@link Trigger} to process per job for {@link AttributionJobService}
     */
    public static final int MAX_ATTRIBUTIONS_PER_INVOCATION = 100;

    /**
     * Maximum event report upload retry window.
     */
    public static final long MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS =
            TimeUnit.DAYS.toMillis(28);

    /**
     * Maximum aggregate report upload retry window.
     */
    public static final long MAX_AGGREGATE_REPORT_UPLOAD_RETRY_WINDOW_MS =
            TimeUnit.DAYS.toMillis(28);

    /**
     * Maximum number of bytes allowed in an attribution filter string.
     */
    public static final int MAX_BYTES_PER_ATTRIBUTION_FILTER_STRING = 25;

    /**
     * Maximum number of values allowed in an attribution filter.
     */
    public static final int MAX_VALUES_PER_ATTRIBUTION_FILTER = 50;

    /**
     * Maximum number of attribution filters allowed for a source.
     */
    public static final int MAX_ATTRIBUTION_FILTERS = 50;

    /**
     * Maximum number of bytes allowed in an aggregate key ID.
     */
    public static final int MAX_BYTES_PER_ATTRIBUTION_AGGREGATE_KEY_ID = 25;

    /**
     * Maximum number of aggregation keys allowed during source or trigger registration.
     */
    public static final int MAX_AGGREGATE_KEYS_PER_REGISTRATION = 50;

    /**
     * Maximum number of aggregatable trigger data allowed in a trigger registration.
     */
    public static final int MAX_AGGREGATABLE_TRIGGER_DATA = 50;

    /**
     * Maximum number of event trigger data allowed in a trigger registration.
     */
    public static final int MAX_ATTRIBUTION_EVENT_TRIGGER_DATA = 10;
}
