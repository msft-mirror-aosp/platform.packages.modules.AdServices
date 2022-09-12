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

package com.android.adservices.service.measurement.reporting;

import android.net.Uri;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Class to construct the full reporting url specific to event reports.
 */
public class EventReportSender extends MeasurementReportSender {

    private static final String EVENT_ATTRIBUTION_REPORT_URI_PATH =
            ".well-known/attribution-reporting/report-event-attribution";

    /**
     * Creates URL to send the POST request to.
     */
    URL createReportingFullUrl(Uri adTechDomain)
            throws MalformedURLException {
        Uri reportingFullUrl = Uri.withAppendedPath(adTechDomain,
                EVENT_ATTRIBUTION_REPORT_URI_PATH);
        return new URL(reportingFullUrl.toString());
    }
}
