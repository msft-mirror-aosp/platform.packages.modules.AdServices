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

package com.android.adservices.service.measurement.countunique;

import static com.android.adservices.data.measurement.MeasurementTables.ALL_MSMT_TABLES;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.SqliteObjectMapper;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.AsyncRegistrationFixture;
import com.android.adservices.service.measurement.CountUniqueReport;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.registration.AsyncRegistration;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.math.BigInteger;
import java.util.List;

@ExtendedMockitoRule.SpyStatic(FlagsFactory.class)
public class CountUniqueRegistrarTest extends AdServicesExtendedMockitoTestCase {

    private static final BigInteger KEY = BigInteger.valueOf(56);
    private static final Integer VALUE = 1;
    private static final UnsignedLong FILTERING_ID = new UnsignedLong(32L);
    private static final String CONTEXT_ID = "context-id-test";
    private static final String FILTERING_ID_MAX_BYTES = "6";
    private static final String DEBUG_KEY = "shdau231sbchsc=";
    private static final Uri REGISTRATION_URI = Uri.parse("https://subdomain.private-domain.com");

    private CountUniqueRegistrar mCountUniqueRegistrar;

    @Mock private AdServicesErrorLogger mErrorLogger;

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
        DatastoreManager mDatastoreManager =
                spy(
                        new SQLDatastoreManager(
                                DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger));

        mCountUniqueRegistrar = new CountUniqueRegistrar(mDatastoreManager);
        when(mMockFlags.getMeasurementEnableFlexibleContributionFiltering()).thenReturn(true);
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().safeGetWritableDatabase();
        for (String table : ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }
    }

    @Test
    public void registerCountUniqueEvent_forValidHeaders_storesReport() throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setRegistrationUri(REGISTRATION_URI)
                        .setAdIdPermission(true)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration,
                createEventHeader(KEY, VALUE, FILTERING_ID, CONTEXT_ID, DEBUG_KEY));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();
            CountUniqueReport r = SqliteObjectMapper.constructCountUniqueReport(cursor);
            assertWithMessage("r.getReportId()").that(r.getReportId()).isNotNull();
            assertWithMessage("r.getPayload()")
                    .that(r.getPayload())
                    .isEqualTo(getPayload(KEY, VALUE, FILTERING_ID));
            assertWithMessage("r.getReportingOrigin()")
                    .that(r.getReportingOrigin())
                    .isEqualTo(REGISTRATION_URI);

            assertWithMessage("r.getStatus()")
                    .that(r.getStatus())
                    .isEqualTo(AggregateReport.Status.PENDING);

            assertWithMessage("r.getScheduledReportTime()")
                    .that(r.getScheduledReportTime())
                    .isEqualTo(asyncRegistration.getRequestTime());

            assertWithMessage("r.getApiVersion()").that(r.getApiVersion()).isEqualTo("1.0");

            assertWithMessage("r.getDebugKey()").that(r.getDebugKey()).isEqualTo(DEBUG_KEY);

            assertWithMessage("r.getContextId()").that(r.getContextId()).isEqualTo(CONTEXT_ID);
        }
    }

    @Test
    public void registerCountUniqueEvent_forNullKey_doesNotStoreReport() throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setAdIdPermission(true)
                        .setRegistrationUri(REGISTRATION_URI)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration,
                createEventHeader(null, VALUE, FILTERING_ID, CONTEXT_ID, DEBUG_KEY));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void registerCountUniqueEvent_forNullContextId_StoresReport() throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setAdIdPermission(true)
                        .setRegistrationUri(REGISTRATION_URI)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration, createEventHeader(KEY, VALUE, FILTERING_ID, null, DEBUG_KEY));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();
            CountUniqueReport r = SqliteObjectMapper.constructCountUniqueReport(cursor);
            assertWithMessage("r.getReportId()").that(r.getReportId()).isNotNull();
            assertWithMessage("r.getPayload()")
                    .that(r.getPayload())
                    .isEqualTo(getPayload(KEY, VALUE, FILTERING_ID));
            assertWithMessage("r.getReportingOrigin()")
                    .that(r.getReportingOrigin())
                    .isEqualTo(REGISTRATION_URI);

            assertWithMessage("r.getStatus()")
                    .that(r.getStatus())
                    .isEqualTo(AggregateReport.Status.PENDING);

            assertWithMessage("r.getScheduledReportTime()")
                    .that(r.getScheduledReportTime())
                    .isEqualTo(asyncRegistration.getRequestTime());

            assertWithMessage("r.getApiVersion()").that(r.getApiVersion()).isEqualTo("1.0");

            assertWithMessage("r.getDebugKey()").that(r.getDebugKey()).isEqualTo(DEBUG_KEY);

            assertWithMessage("r.getContextId()").that(r.getContextId()).isNull();
        }
    }

    @Test
    public void registerCountUniqueEvent_forNullValue_doesNotStoreReport() throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setAdIdPermission(true)
                        .setRegistrationUri(REGISTRATION_URI)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration,
                createEventHeader(KEY, null, FILTERING_ID, CONTEXT_ID, DEBUG_KEY));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(0);
        }
    }

    @Test
    public void registerCountUniqueEvent_forNullFilteringId_StoresReport() throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setRegistrationUri(REGISTRATION_URI)
                        .setAdIdPermission(true)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration, createEventHeader(KEY, VALUE, null, CONTEXT_ID, DEBUG_KEY));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();
            CountUniqueReport r = SqliteObjectMapper.constructCountUniqueReport(cursor);
            assertWithMessage("r.getReportId()").that(r.getReportId()).isNotNull();
            assertWithMessage("r.getPayload()")
                    .that(r.getPayload())
                    .isEqualTo(getPayload(KEY, VALUE, null));
            assertWithMessage("r.getReportingOrigin()")
                    .that(r.getReportingOrigin())
                    .isEqualTo(REGISTRATION_URI);

            assertWithMessage("r.getStatus()")
                    .that(r.getStatus())
                    .isEqualTo(AggregateReport.Status.PENDING);

            assertWithMessage("r.getScheduledReportTime()")
                    .that(r.getScheduledReportTime())
                    .isEqualTo(asyncRegistration.getRequestTime());

            assertWithMessage("r.getApiVersion()").that(r.getApiVersion()).isEqualTo("1.0");

            assertWithMessage("r.getDebugKey()").that(r.getDebugKey()).isEqualTo(DEBUG_KEY);

            assertWithMessage("r.getContextId()").that(r.getContextId()).isEqualTo(CONTEXT_ID);
        }
    }

    @Test
    public void registerCountUniqueEvent_forNoAdidPermission_DoesNotStoreDebugKey()
            throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setRegistrationUri(REGISTRATION_URI)
                        .setAdIdPermission(false)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration, createEventHeader(KEY, VALUE, null, CONTEXT_ID, DEBUG_KEY));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();
            CountUniqueReport r = SqliteObjectMapper.constructCountUniqueReport(cursor);
            assertWithMessage("r.getReportId()").that(r.getReportId()).isNotNull();
            assertWithMessage("r.getPayload()")
                    .that(r.getPayload())
                    .isEqualTo(getPayload(KEY, VALUE, null));
            assertWithMessage("r.getReportingOrigin()")
                    .that(r.getReportingOrigin())
                    .isEqualTo(REGISTRATION_URI);

            assertWithMessage("r.getStatus()")
                    .that(r.getStatus())
                    .isEqualTo(AggregateReport.Status.PENDING);

            assertWithMessage("r.getScheduledReportTime()")
                    .that(r.getScheduledReportTime())
                    .isEqualTo(asyncRegistration.getRequestTime());

            assertWithMessage("r.getApiVersion()").that(r.getApiVersion()).isEqualTo("1.0");

            assertWithMessage("r.getDebugKey()").that(r.getDebugKey()).isNull();

            assertWithMessage("r.getContextId()").that(r.getContextId()).isEqualTo(CONTEXT_ID);
        }
    }

    @Test
    public void registerCountUniqueEvent_forNulLDebugKey_StoresReport() throws JSONException {
        // Setup
        AsyncRegistration asyncRegistration =
                AsyncRegistrationFixture.getValidAsyncRegistrationBuilder()
                        .setRegistrationUri(REGISTRATION_URI)
                        .setAdIdPermission(false)
                        .build();

        // Test
        mCountUniqueRegistrar.registerCountUniqueEvent(
                asyncRegistration, createEventHeader(KEY, VALUE, FILTERING_ID, CONTEXT_ID, null));

        // Assert in db
        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            assertWithMessage("cursor.getCount()").that(cursor.getCount()).isEqualTo(1);
            cursor.moveToNext();
            CountUniqueReport r = SqliteObjectMapper.constructCountUniqueReport(cursor);
            assertWithMessage("r.getReportId()").that(r.getReportId()).isNotNull();
            assertWithMessage("r.getPayload()")
                    .that(r.getPayload())
                    .isEqualTo(getPayload(KEY, VALUE, FILTERING_ID));
            assertWithMessage("r.getReportingOrigin()")
                    .that(r.getReportingOrigin())
                    .isEqualTo(REGISTRATION_URI);

            assertWithMessage("r.getStatus()")
                    .that(r.getStatus())
                    .isEqualTo(AggregateReport.Status.PENDING);

            assertWithMessage("r.getScheduledReportTime()")
                    .that(r.getScheduledReportTime())
                    .isEqualTo(asyncRegistration.getRequestTime());

            assertWithMessage("r.getApiVersion()").that(r.getApiVersion()).isEqualTo("1.0");

            assertWithMessage("r.getDebugKey()").that(r.getDebugKey()).isNull();

            assertWithMessage("r.getContextId()").that(r.getContextId()).isEqualTo(CONTEXT_ID);
        }
    }

    private List<String> createEventHeader(
            BigInteger key,
            Integer value,
            UnsignedLong filteringId,
            String contextId,
            String debugKey) {
        StringBuilder header = new StringBuilder("{\n");

        if (key != null) {
            header.append("\"key\": \"").append(key).append("\",\n");
        }

        if (value != null) {
            header.append("\"value\": \"").append(value).append("\",\n");
        }

        if (filteringId != null) {
            header.append("\"filteringId\": \"").append(filteringId).append("\",\n");
        }

        if (contextId != null) {
            header.append("\"contextId\": \"").append(contextId).append("\",\n");
        }

        if (debugKey != null) {
            header.append("\"debug_key\": \"").append(DEBUG_KEY).append("\",\n");
        }

        header.append("\"filteringIdMaxBytes\": \"")
                .append(FILTERING_ID_MAX_BYTES)
                .append("\"\n")
                .append("}\n");

        return List.of(header.toString());
    }

    private String getPayload(BigInteger bucket, Integer value, UnsignedLong filteringId)
            throws JSONException {
        JSONObject contribution = new JSONObject();
        contribution.put("bucket", bucket.toString());
        contribution.put("value", value);
        if (filteringId != null) {
            contribution.put("id", filteringId);
        }
        return contribution.toString();
    }
}
