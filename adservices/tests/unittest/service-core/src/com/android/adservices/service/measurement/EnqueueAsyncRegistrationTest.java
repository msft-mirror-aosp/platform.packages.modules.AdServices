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

import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.SqliteObjectMapper;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.registration.EnqueueAsyncRegistration;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnqueueAsyncRegistrationTest {

    private static Context sDefaultContext = ApplicationProvider.getApplicationContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.test/bar?ad=134");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.test/bar?ad=256");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final boolean DEFAULT_AD_ID_PERMISSION = false;
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

    private MockitoSession mStaticMockSession;

    private static final WebSourceRegistrationRequest
            VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT =
                    new WebSourceRegistrationRequest.Builder(
                                    sSourceParamsList, Uri.parse("android-app://example.test/aD1"))
                            .setWebDestination(Uri.parse("android-app://example.test/aD1"))
                            .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                            .setVerifiedDestination(Uri.parse("android-app://example.test/aD1"))
                            .build();

    private static final WebTriggerRegistrationRequest VALID_WEB_TRIGGER_REGISTRATION =
            new WebTriggerRegistrationRequest.Builder(
                            sTriggerParamsList, Uri.parse("android-app://test.e.abc"))
                    .build();

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbTestUtil.getMeasurementDbHelperForTest().safeGetWritableDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }
        mStaticMockSession.finishMocking();
    }

    @Before
    public void before() throws RemoteException {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
        MockitoAnnotations.initMocks(this);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT));
    }

    @Test
    public void testAppSourceRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_SOURCE,
                                Uri.parse("http://baz.test"),
                                sDefaultContext.getAttributionSource().getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.EVENT,
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
        }
    }

    @Test
    public void testAppSourceRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_SOURCE,
                                Uri.parse("http://baz.test"),
                                sDefaultContext.getAttributionSource().getPackageName(),
                                SDK_PACKAGE_NAME)
                        .setInputEvent(mInputEvent)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.NAVIGATION,
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
        }
    }

    @Test
    public void testAppTriggerRegistrationRequest_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_TRIGGER,
                                Uri.parse("http://baz.test"),
                                sDefaultContext.getAttributionSource().getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();

        Assert.assertTrue(
                EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                        registrationRequest,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        null,
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNull(asyncRegistration.getSourceType());
        }
    }

    @Test
    public void testWebSourceRegistrationRequest_event_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        Assert.assertTrue(
                EnqueueAsyncRegistration.webSourceRegistrationRequest(
                        VALID_WEB_SOURCE_REGISTRATION_NULL_INPUT_EVENT,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.EVENT,
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistration.getRedirectType());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE, asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);

                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistrationTwo.getRedirectType());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistration.getRedirectType());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistrationTwo.getRedirectType());
                Assert.assertEquals(Source.SourceType.EVENT, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
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
    public void testWebSourceRegistrationRequest_navigation_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        List<WebSourceParams> sourceParamsList = new ArrayList<>();
        sourceParamsList.add(INPUT_SOURCE_REGISTRATION_1);
        sourceParamsList.add(INPUT_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequest validWebSourceRegistration =
                new WebSourceRegistrationRequest.Builder(
                                sourceParamsList, Uri.parse("android-app://example.test/aD1"))
                        .setWebDestination(Uri.parse("android-app://example.test/aD1"))
                        .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                        .setVerifiedDestination(Uri.parse("android-app://example.test/aD1"))
                        .setInputEvent(mInputEvent)
                        .build();
        Assert.assertTrue(
                EnqueueAsyncRegistration.webSourceRegistrationRequest(
                        validWebSourceRegistration,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        Source.SourceType.NAVIGATION,
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistration.getRedirectType());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getVerifiedDestination());
                Assert.assertNotNull(asyncRegistration.getType());
                Assert.assertEquals(
                        AsyncRegistration.RegistrationType.WEB_SOURCE, asyncRegistration.getType());

                Assert.assertTrue(cursor.moveToNext());
                AsyncRegistration asyncRegistrationTwo =
                        SqliteObjectMapper.constructAsyncRegistration(cursor);

                Assert.assertNotNull(asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(REGISTRATION_URI_2, asyncRegistrationTwo.getRegistrationUri());
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistrationTwo.getRedirectType());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistration.getRedirectType());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistration.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistration.getRegistrant());
                Assert.assertNotNull(asyncRegistration.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getWebDestination());
                Assert.assertNotNull(asyncRegistration.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistration.getOsDestination());
                Assert.assertNotNull(asyncRegistration.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistrationTwo.getRedirectType());
                Assert.assertEquals(
                        Source.SourceType.NAVIGATION, asyncRegistrationTwo.getSourceType());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
                        asyncRegistrationTwo.getRegistrant());
                Assert.assertNotNull(asyncRegistrationTwo.getWebDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getWebDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getOsDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
                        asyncRegistrationTwo.getOsDestination());
                Assert.assertNotNull(asyncRegistrationTwo.getVerifiedDestination());
                Assert.assertEquals(
                        Uri.parse("android-app://example.test/aD1"),
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
    public void testWebTriggerRegistrationRequest_isValid() {
        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        Assert.assertTrue(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        datastoreManager));

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistration.getRedirectType());
                Assert.assertEquals(null, asyncRegistration.getSourceType());
                Assert.assertNotNull(asyncRegistration.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistrationTwo.getRedirectType());
                Assert.assertEquals(null, asyncRegistrationTwo.getSourceType());
                Assert.assertNotNull(asyncRegistration.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistration.getRedirectType());
                Assert.assertEquals(null, asyncRegistration.getSourceType());
                Assert.assertNotNull(asyncRegistration.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
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
                Assert.assertEquals(
                        AsyncRegistration.RedirectType.NONE,
                        asyncRegistrationTwo.getRedirectType());
                Assert.assertEquals(null, asyncRegistrationTwo.getSourceType());
                Assert.assertNotNull(asyncRegistrationTwo.getRegistrant());
                Assert.assertEquals(
                        Uri.parse("android-app://test.destination"),
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
    public void testRunInTransactionFail_inValid() {
        when(mDatastoreManagerMock.runInTransaction(any())).thenReturn(false);
        Assert.assertFalse(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        mDatastoreManagerMock));
    }

    @Test
    public void testMissingEnrollmentData_inValid() {
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any())).thenReturn(null);
        Assert.assertFalse(
                EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                        VALID_WEB_TRIGGER_REGISTRATION,
                        DEFAULT_AD_ID_PERMISSION,
                        Uri.parse("android-app://test.destination"),
                        System.currentTimeMillis(),
                        mEnrollmentDao,
                        mDatastoreManagerMock));
    }

    /** Test that the AsyncRegistration is inserted correctly. */
    @Test
    public void testVerifyAsyncRegistrationStoredCorrectly() {
        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_SOURCE,
                                Uri.parse("http://baz.test"),
                                sDefaultContext.getAttributionSource().getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();

        DatastoreManager datastoreManager =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                registrationRequest,
                DEFAULT_AD_ID_PERMISSION,
                Uri.parse("android-app://test.destination"),
                System.currentTimeMillis(),
                Source.SourceType.EVENT,
                mEnrollmentDao,
                datastoreManager);

        try (Cursor cursor =
                DbTestUtil.getMeasurementDbHelperForTest()
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
                    Uri.parse("android-app://test.destination"), asyncRegistration.getTopOrigin());
            Assert.assertNotNull(asyncRegistration.getRegistrationUri());
            Assert.assertNotNull(asyncRegistration.getRegistrant());
            Assert.assertEquals(
                    Uri.parse("android-app://test.destination"), asyncRegistration.getRegistrant());
            Assert.assertNotNull(asyncRegistration.getSourceType());
            Assert.assertEquals(Source.SourceType.EVENT, asyncRegistration.getSourceType());
            Assert.assertNotNull(asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RegistrationType.APP_SOURCE, asyncRegistration.getType());
            Assert.assertEquals(
                    AsyncRegistration.RedirectType.ANY,
                    asyncRegistration.getRedirectType());
        }
    }


    private void enqueueSourceRegistrationRequestWithDebugRelatedKeys(
            final boolean adIdPermission, final boolean debugKeyAllowed) {
        final DatastoreManager datastore =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        final List<WebSourceParams> params =
                Collections.singletonList(
                        new WebSourceParams.Builder(REGISTRATION_URI_1)
                                .setDebugKeyAllowed(debugKeyAllowed)
                                .build());
        final WebSourceRegistrationRequest request =
                new WebSourceRegistrationRequest.Builder(
                                params, Uri.parse("android-app://example.test/aD1"))
                        .setWebDestination(Uri.parse("android-app://example.test/aD1"))
                        .setAppDestination(Uri.parse("android-app://example.test/aD1"))
                        .setVerifiedDestination(Uri.parse("android-app://example.test/aD1"))
                        .setInputEvent(mInputEvent)
                        .build();

        EnqueueAsyncRegistration.webSourceRegistrationRequest(
                request,
                adIdPermission,
                Uri.parse("android-app://test.destination"),
                System.currentTimeMillis(),
                Source.SourceType.NAVIGATION,
                mEnrollmentDao,
                datastore);
    }

    private void enqueueTriggerRegistrationRequestWithDebugRelatedKeys(
            final boolean adIdPermission, final boolean debugKeyAllowed) {
        final DatastoreManager datastore =
                new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
        final List<WebTriggerParams> params =
                Collections.singletonList(
                        new WebTriggerParams.Builder(REGISTRATION_URI_1)
                                .setDebugKeyAllowed(debugKeyAllowed)
                                .build());
        final WebTriggerRegistrationRequest request =
                new WebTriggerRegistrationRequest.Builder(
                                params, Uri.parse("android-app://test.e.abc"))
                        .build();

        EnqueueAsyncRegistration.webTriggerRegistrationRequest(
                request,
                adIdPermission,
                Uri.parse("android-app://test.destination"),
                System.currentTimeMillis(),
                mEnrollmentDao,
                datastore);
    }
}
