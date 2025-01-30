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

package com.android.adservices.service.measurement.reporting;

import android.net.Uri;

import com.android.adservices.common.WebUtil;

public final class DebugReportFixture {
    private DebugReportFixture() {}

    public static class ValidDebugReportParams {
        public static final String TYPE = "trigger-event-deduplicated";
        public static final String BODY =
                " {\n"
                        + "      \"attribution_destination\":"
                        + " \"https://destination.example\",\n"
                        + "      \"source_event_id\": \"45623\"\n"
                        + "    }";
        public static final String ENROLLMENT_ID = "foo";
        public static final Uri REGISTRATION_ORIGIN =
                WebUtil.validUri("https://subdomain.example.test");
        public static final Long INSERTION_TIME = 1617297798L;
        public static final Uri REGISTRANT = Uri.parse("android-app://com.example1.sample");
    }
}
