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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ReportingWithDestinationPerformedStats {
    /** Returns the status of the report impression performed */
    @AdsRelevanceStatusUtils.ReportingCallStatsStatus
    public abstract int getStatus();

    /** Returns the destination of the report impression */
    @AdsRelevanceStatusUtils.ReportingCallStatsDestination
    public abstract int getDestination();

    /** Returns the type of reporting for this logs (report event/report impression). */
    @AdsRelevanceStatusUtils.ReportingApiType
    public abstract int getReportingType();

    public static Builder builder() {
        return new AutoValue_ReportingWithDestinationPerformedStats.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the status for the report impression performed. */
        public abstract Builder setStatus(
                @AdsRelevanceStatusUtils.ReportingCallStatsStatus int status);

        /** Sets the type of destination. */
        public abstract Builder setDestination(
                @AdsRelevanceStatusUtils.ReportingCallStatsDestination int destination);

        /** Sets the type of reporting performed. */
        public abstract Builder setReportingType(
                @AdsRelevanceStatusUtils.ReportingApiType int reportingType);

        /**
         * Returns a new instance of {@link ReportingWithDestinationPerformedStats} built from the
         * values set in this builder
         */
        public abstract ReportingWithDestinationPerformedStats build();
    }
}
