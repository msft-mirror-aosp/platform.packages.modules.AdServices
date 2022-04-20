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
 */
public class SystemHealthParams {
    /*
     * Max number of sources an app can register.
     */
    public static final int MAX_SOURCE_REGISTERS_PER_REGISTRANT = 1600; // placeholder value

    /*
     * Max number of triggers an app can register.
     */
    public static final int MAX_TRIGGER_REGISTERS_PER_REGISTRANT = 1000; // placeholder value

    /**
     * Delay for attribution job triggering.
     */
    public static final long ATTRIBUTION_JOB_TRIGGERING_DELAY_MS =
            TimeUnit.MINUTES.toMillis(2);

    /**
     * Max number of {@link Trigger} to process per job for {@link AttributionJobService}
     */
    public static final int MAX_ATTRIBUTIONS_PER_INVOCATION = 100;

    /**
     * Maximum event report upload retry window.
     */
    public static final long MAX_EVENT_REPORT_UPLOAD_RETRY_WINDOW_MS =
            TimeUnit.DAYS.toMillis(28);

    private SystemHealthParams() {
    }
}
