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
 * Class for holding privacy related parameters.
 */
public final class PrivacyParams {

    /**
     * Max reports for 'Navigation' {@link Source}.
     */
    public static final int NAVIGATION_SOURCE_MAX_REPORTS = 3;

    /**
     * Max reports for 'Event' {@link Source}.
     */
    public static final int EVENT_SOURCE_MAX_REPORTS = 1;

    /**
     * Max reports for Install Attributed 'Navigation' {@link Source}.
     */
    public static final int INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS = 3;

    /**
     * Max reports for Install Attributed 'Event' {@link Source}.
     */
    public static final int INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS = 2;

    /**
     * Maximum attributions per rate limit window.
     * Rate limit unit: (Source Site, Destination Site, Reporting Site, Window).
     */
    public static final int MAX_ATTRIBUTION_PER_RATE_LIMIT_WINDOW = 100;

    /**
     * Rate limit window for (Source Site, Destination Site, Reporting Site, Window) privacy unit.
     * 28 days.
     */
    public static final long RATE_LIMIT_WINDOW_MILLISECONDS = TimeUnit.DAYS.toMillis(28);

    /**
     * Early reporting window for 'Navigation' {@link Source}.
     * 2 days and 7 days.
     */
    public static final long[] NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS = new long[]{
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7)
    };

    /**
     * Early reporting window for 'Event' {@link Source}.
     * No windows.
     */
    public static final long[] EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS = new long[]{ };

    /**
     * Early reporting window for Install Attributed 'Navigation' {@link Source}.
     * 2 days and 7 days.
     */
    public static final long[] INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS =
            new long[]{ TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7) };

    /**
     * Early reporting window for Install Attributed 'Event' {@link Source}.
     * 2 days.
     */
    public static final long[] INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS =
            new long[]{ TimeUnit.DAYS.toMillis(2) };

    /**
     * {@link Source} attribution state selection randomness probability for 'Event'
     */
    public static final double EVENT_RANDOM_ATTRIBUTION_STATE_PROBABILITY = 0.0000017D;

    /**
     * {@link Source} attribution state selection randomness probability for 'Navigation'
     */
    public static final double NAVIGATION_RANDOM_ATTRIBUTION_STATE_PROBABILITY = 0.0024255D;

    /**
     * Trigger data noise probability for 'Event' {@link Source} attribution.
     */
    public static final double EVENT_RANDOM_TRIGGER_DATA_NOISE = 0.0000025D;

    /**
     * Trigger data noise probability for 'Navigation' {@link Source} attribution.
     */
    public static final double NAVIGATION_RANDOM_TRIGGER_DATA_NOISE = 0.0024263D;

    /**
     * Trigger data cardinality for 'Event' {@link Source} attribution.
     */
    public static final int EVENT_TRIGGER_DATA_CARDINALITY = 2;

    /**
     * Trigger data cardinality for 'Navigation' {@link Source} attribution.
     */
    public static final int NAVIGATION_TRIGGER_DATA_CARDINALITY = 8;

    private PrivacyParams() {
    }
}
