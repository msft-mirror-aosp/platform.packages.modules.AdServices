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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SqliteObjectMapper;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.registration.EnqueueAsyncRegistration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class EnqueueAsyncRegistrationTest {

    private static Context sDefaultContext = ApplicationProvider.getApplicationContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.com/bar?ad=134");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.com/bar?ad=256");
    private static final String DEFAULT_ENROLLMENT = "enrollment-id";
    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static List<WebSourceParams> sSourceParamsList = new ArrayList<>();

    private static List<WebTriggerParams> sTriggerParamsList = new ArrayList<>();

    static {
        sSourceParamsList.add(INPUT_SOURCE_REGISTRATION_1);
        sSourceParamsList.add(INPUT_SOURCE_REGISTRATION_2);
        sTriggerParamsList.add(INPUT_TRIGGER_REGISTRATION_1);
        sTriggerParamsList.add(INPUT_TRIGGER_REGISTRATION_2);
    }

    @Mock private DatastoreManager mDatastoreManagerMock;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private InputEvent mInputEvent;

    private static final WebSourceRegistrationRequest
            VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT =
                    new WebSourceRegistrationRequest.Builder(
                                    sSourceParamsList, Uri.parse("android-app://example.com/aD1"))
                            .setWebDestination(Uri.parse("android-app://example.com/aD1"))
                            .setAppDestination(Uri.parse("android-app://example.com/aD1"))
                            .setVerifiedDestination(Uri.parse("android-app://example.com/aD1"))
                            .build();

    private static final WebTriggerRegistrationRequest VALID_WEB_TRIGGER_REGISTRATION =
            new WebTriggerRegistrationRequest.Builder(
                            sTriggerParamsList, Uri.parse("android-app://com.e.abc"))
                    .build();

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbHelper.getInstance(sDefaultContext).safeGetWritableDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }
    }

    @Before
    public void before() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT));
    }

    @Test
    public void test_appSourceRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("http://baz.com"))
                        .setPackageName(sDefaultContext.getAttributionSource().getPackageName())
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    "android-app://" + registrationRequest.getPackageName(),
                    asyncRegistration.getTopOrigin().toString());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://com.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
        }
    }

    @Test
    public void test_appSourceRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("http://baz.com"))
                        .setPackageName(sDefaultContext.getAttributionSource().getPackageName())
                        .setInputEvent(mInputEvent)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    "android-app://" + registrationRequest.getPackageName(),
                    asyncRegistration.getTopOrigin().toString());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://com.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
        }
    }

    @Test
    public void test_appTriggerRegistrationRequest_isValid() {
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                        .setRegistrationUri(Uri.parse("http://baz.com"))
                        .setPackageName(sDefaultContext.getAttributionSource().getPackageName())
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    "android-app://" + registrationRequest.getPackageName(),
                    asyncRegistration.getTopOrigin().toString());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://com.destination"), asyncRegistration.getRegistrant());
            Assert.assertNull(asyncRegistration.getSourceType());
        }
    }

    @Test
    public void test_webSourceRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        Assert.assertTrue(
                EnqueueAsyncRegistration.webSourceRegistrationRequest(
                        VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            if (asyncRegistration
                    .getRegistrationUri()
                    .equals(
                            VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT
                                    .getSourceParams()
                                    .get(0)
                                    .getRegistrationUri())) {
                Assert.assertNotNull(asyncRegistration.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistration.getRedirect());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE, asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);

                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistrationTwo.getRedirect());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT.getTopOriginUri(),
                        asyncRegistrationTwo.getTopOrigin());
                Assert.assertNotNull(asyncRegistrationTwo.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE,
                        asyncRegistrationTwo.getType());
            } else if (asyncRegistration
                    .getRegistrationUri()
                    .equals(
                            VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT
                                    .getSourceParams()
                                    .get(1)
                                    .getRegistrationUri())) {
                Assert.assertNotNull(asyncRegistration.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistration.getRedirect());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistration.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT.getTopOriginUri(),
                        asyncRegistration.getTopOrigin());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE, asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);
                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_1, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistrationTwo.getRedirect());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT.getTopOriginUri(),
                        asyncRegistrationTwo.getTopOrigin());
                Assert.assertNotNull(asyncRegistrationTwo.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE,
                        asyncRegistrationTwo.getType());
            } else {
                Assert.fail();
            }
        }
    }

    @Test
    public void test_webSourceRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        List<WebSourceParams> sourceParamsList = new ArrayList<>();
        sourceParamsList.add(INPUT_SOURCE_REGISTRATION_1);
        sourceParamsList.add(INPUT_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequest validWebSourceRegistration =
                new WebSourceRegistrationRequest.Builder(
                                sourceParamsList, Uri.parse("android-app://example.com/aD1"))
                        .setWebDestination(Uri.parse("android-app://example.com/aD1"))
                        .setAppDestination(Uri.parse("android-app://example.com/aD1"))
                        .setVerifiedDestination(Uri.parse("android-app://example.com/aD1"))
                        .setInputEvent(mInputEvent)
                        .build();
        Assert.assertTrue(
                EnqueueAsyncRegistration.webSourceRegistrationRequest(
                        validWebSourceRegistration,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            if (asyncRegistration
                    .getRegistrationUri()
                    .equals(
                            validWebSourceRegistration
                                    .getSourceParams()
                                    .get(0)
                                    .getRegistrationUri())) {
                Assert.assertNotNull(asyncRegistration.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistration.getRedirect());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE, asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);

                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistrationTwo.getRedirect());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getTopOrigin());
                Assert.assertEquals(
                        validWebSourceRegistration.getTopOriginUri(),
                        asyncRegistrationTwo.getTopOrigin());
                Assert.assertNotNull(asyncRegistrationTwo.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE,
                        asyncRegistrationTwo.getType());
            } else if (asyncRegistration
                    .getRegistrationUri()
                    .equals(
                            validWebSourceRegistration
                                    .getSourceParams()
                                    .get(1)
                                    .getRegistrationUri())) {
                Assert.assertNotNull(asyncRegistration.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistration.getRedirect());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistration.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistration.getTopOrigin());
                Assert.assertEquals(
                        validWebSourceRegistration.getTopOriginUri(),
                        asyncRegistration.getTopOrigin());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE, asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);
                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_1, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistrationTwo.getRedirect());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.com/aD1"),
                        asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getTopOrigin());
                Assert.assertEquals(
                        validWebSourceRegistration.getTopOriginUri(),
                        asyncRegistrationTwo.getTopOrigin());
                Assert.assertNotNull(asyncRegistrationTwo.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE,
                        asyncRegistrationTwo.getType());
            } else {
                Assert.fail();
            }
        }
    }

    @Test
    public void test_webTriggerRegistrationRequest_isValid() {
        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        Assert.assertTrue(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            if (asyncRegistration
                    .getRegistrationUri()
                    .equals(
                            VALID_WEB_TRIGGER_REGISTRATION
                                    .getTriggerParams()
                                    .get(0)
                                    .getRegistrationUri())) {
                Assert.assertNotNull(asyncRegistration.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_1, asyncRegistration.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistration.getRedirect());
                Assert.assertEquals(null, asyncRegistration.getSourceType());
                Assert.assertNotNull(asyncRegistration.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_TRIGGER_REGISTRATION.getDestination(),
                        asyncRegistration.getTopOrigin());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_TRIGGER,
                        asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);

                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistrationTwo.getRedirect());
                Assert.assertEquals(null, asyncRegistrationTwo.getSourceType());
                Assert.assertNotNull(asyncRegistration.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_TRIGGER_REGISTRATION.getDestination(),
                        asyncRegistrationTwo.getTopOrigin());
                Assert.assertNotNull(asyncRegistrationTwo.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_TRIGGER,
                        asyncRegistrationTwo.getType());
            } else if (asyncRegistration
                    .getRegistrationUri()
                    .equals(
                            VALID_WEB_TRIGGER_REGISTRATION
                                    .getTriggerParams()
                                    .get(1)
                                    .getRegistrationUri())) {
                Assert.assertNotNull(asyncRegistration.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistration.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistration.getRedirect());
                Assert.assertEquals(null, asyncRegistration.getSourceType());
                Assert.assertNotNull(asyncRegistration.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_TRIGGER_REGISTRATION.getDestination(),
                        asyncRegistration.getTopOrigin());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_TRIGGER,
                        asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);
                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_1, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(false, asyncRegistrationTwo.getRedirect());
                Assert.assertEquals(null, asyncRegistrationTwo.getSourceType());
                Assert.assertNotNull(asyncRegistrationTwo.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://com.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getTopOrigin());
                Assert.assertEquals(
                        VALID_WEB_TRIGGER_REGISTRATION.getDestination(),
                        asyncRegistrationTwo.getTopOrigin());
                Assert.assertNotNull(asyncRegistrationTwo.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_TRIGGER,
                        asyncRegistrationTwo.getType());
            } else {
                Assert.fail();
            }
        }
    }

    @Test
    public void test_runInTransactionFail_inValid() {
        when(mDatastoreManagerMock.runInTransaction(any())).thenReturn(false);
        Assert.assertFalse(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        mDatastoreManagerMock));
    }

    @Test
    public void test_MissingEnrollmentData_inValid() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(null);
        Assert.assertFalse(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        Uri.parse("android-app://com.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        mDatastoreManagerMock));
    }

    /** Test that the AsyncRegistration is inserted correctly. */
    @Test
    public void test_verifyAsyncRegistrationStoredCorrectly() {
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder()
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setRegistrationUri(Uri.parse("http://baz.com"))
                        .setPackageName(sDefaultContext.getAttributionSource().getPackageName())
                        .build();

        DatastoreManager datastoreManager =
                DatastoreManagerFactory.getDatastoreManager(sDefaultContext);
        EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                registrationRequest,
                Uri.parse("android-app://com.destination"),
                System.currentTimeMillis(),
                mEnrollmentDao,
                datastoreManager);

        try (Cursor cursor =
                DbHelper.getInstance(sDefaultContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AsyncRegistrationContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {

            Assert.assertTrue(cursor.moveToNext());
            AsyncRegistration asyncRegistration =
                    SqliteObjectMapper.constructAsyncRegistration(cursor);
            Assert.assertNotNull(asyncRegistration);
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    registrationRequest.getRegistrationUri(),
                    asyncRegistration.getRegistrationUri());
            Assert.assertEquals(
                    "android-app://" + registrationRequest.getPackageName(),
                    asyncRegistration.getTopOrigin().toString());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://com.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
            Assert.assertEquals(true, asyncRegistration.getRedirect());
        }
    }
}
