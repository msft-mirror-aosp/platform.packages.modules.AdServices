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

package com.android.adservices.service.profiling;

/** Metric, class and method names used to name tracing points. */
public final class TracingNames {
    public static final String CLASS_NAME_AD_ID_SERVICE = "AdIdService";
    public static final String CLASS_NAME_ATTRIBUTION_JOB_SERVICE = "AttributionJobService";
    public static final String CLASS_NAME_ATTRIBUTION_FALLBACK_JOB_SERVICE =
            "AttributionFallbackJobService";
    public static final String CLASS_NAME_TOPICS_SERVICE = "TopicsService";
    public static final String CLASS_NAME_CONSENT_MANAGER = "ConsentManager";

    public static final String METHOD_NAME_CONSTRUCTOR = "getInstance";
    public static final String METHOD_NAME_GET_AD_ID = "getAdId";
    public static final String METHOD_NAME_ON_START_JOB = "onStartJob";
    public static final String METHOD_NAME_GET_TOPICS = "getTopics";

    // Method names for ConsentManager.
    public static final String METHOD_NAME_CREATE_AND_INIT_DATASTORE = "createAndInitDataStore";
    public static final String METHOD_NAME_GET_AD_SERVICES_MANAGER = "getAdServicesManager";
    public static final String METHOD_NAME_MIGRATION_FROM_APP_SEARCH = "migrationFromAppSearch";
    public static final String METHOD_NAME_MIGRATION_FROM_PPAPI = "migrationFromPpapi";
    public static final String METHOD_NAME_MIGRATION_ENROLLMENT_DATA = "migrationEnrollmentData";
    public static final String METHOD_NAME_SET_CONSENT = "setConsent";
    public static final String METHOD_NAME_GET_CONSENT = "getConsent";
}
