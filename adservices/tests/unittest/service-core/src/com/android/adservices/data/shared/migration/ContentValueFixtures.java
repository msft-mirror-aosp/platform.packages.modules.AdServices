/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.shared.migration;

import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_REPORTING_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_SOURCE_REGISTRATION_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ATTRIBUTION_TRIGGER_REGISTRATION_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.COMPANY_ID;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENCRYPTION_KEY_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.ENROLLMENT_ID;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.REMARKETING_RESPONSE_BASED_REGISTRATION_URL;
import static com.android.adservices.data.shared.migration.ContentValueFixtures.EnrollmentValues.SDK_NAMES;

import android.content.ContentValues;

import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.service.measurement.WebUtil;

public class ContentValueFixtures {

    public static class EnrollmentValues {
        public static final String ENROLLMENT_ID = "enrollment_id";
        public static final String COMPANY_ID = "COMPANY_ID";
        public static final String SDK_NAMES = "SDK_NAMES";
        public static final String ATTRIBUTION_SOURCE_REGISTRATION_URL =
                WebUtil.validUrl("https://subdomain.example.test/source");
        public static final String ATTRIBUTION_TRIGGER_REGISTRATION_URL =
                WebUtil.validUrl("https://subdomain.example.test/trigger");
        public static final String ATTRIBUTION_REPORTING_URL =
                WebUtil.validUrl("https://subdomain.example.test/report");
        public static final String REMARKETING_RESPONSE_BASED_REGISTRATION_URL =
                WebUtil.validUrl("https://subdomain.example.test/remarket");
        public static final String ENCRYPTION_KEY_URL =
                WebUtil.validUrl("https://subdomain.example.test/encryption");
    }

    public static ContentValues generateEnrollmentContentValuesV1() {
        ContentValues enrollmentContentValues = new ContentValues();
        enrollmentContentValues.put(
                EnrollmentTables.EnrollmentDataContract.ENROLLMENT_ID, ENROLLMENT_ID);
        enrollmentContentValues.put(EnrollmentTables.EnrollmentDataContract.COMPANY_ID, COMPANY_ID);
        enrollmentContentValues.put(EnrollmentTables.EnrollmentDataContract.SDK_NAMES, SDK_NAMES);
        enrollmentContentValues.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                ATTRIBUTION_SOURCE_REGISTRATION_URL);
        enrollmentContentValues.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                ATTRIBUTION_TRIGGER_REGISTRATION_URL);
        enrollmentContentValues.put(
                EnrollmentTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                ATTRIBUTION_REPORTING_URL);
        enrollmentContentValues.put(
                EnrollmentTables.EnrollmentDataContract.REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                REMARKETING_RESPONSE_BASED_REGISTRATION_URL);
        enrollmentContentValues.put(
                EnrollmentTables.EnrollmentDataContract.ENCRYPTION_KEY_URL, ENCRYPTION_KEY_URL);

        return enrollmentContentValues;
    }
}
