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

package com.android.adservices.data.measurement;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_UNKNOWN_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

public class SQLDatastoreManagerTest {

    protected static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    @Mock private DatastoreManager.ThrowingCheckedFunction<Void> mFunction;
    @Mock private DatastoreManager.ThrowingCheckedConsumer mConsumer;
    @Mock private Flags mFlags;
    private MockitoSession mMockitoSession;
    private SQLDatastoreManager mSQLDatastoreManager;

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(LogUtil.class)
                        .spyStatic(ErrorLogUtil.class)
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .startMocking();
        mSQLDatastoreManager = new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest());
    }

    @Test
    public void runInTransactionWithResult_throwsException_logsDbVersion()
            throws DatastoreException {
        // Setup
        doThrow(new IllegalArgumentException()).when(mFunction).apply(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        // Execution & assertion
        assertThrows(
                IllegalArgumentException.class,
                () -> mSQLDatastoreManager.runInTransactionWithResult(mFunction));
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode =
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_UNKNOWN_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @Test
    public void runInTransactionWithResult_throwsDataStoreExceptionExceptionDisable_logsDbVersion()
            throws DatastoreException {
        // Setup
        doThrow(new DatastoreException(null)).when(mFunction).apply(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(false).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();

        // Execution
        mSQLDatastoreManager.runInTransactionWithResult(mFunction);

        // Execution & assertion
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @Test
    public void runInTransactionWithResult_throwsDataStoreExceptionExceptionEnable_rethrows()
            throws DatastoreException {
        // Setup
        doThrow(new DatastoreException(null)).when(mFunction).apply(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(true).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();
        doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        // Execution
        try {
            mSQLDatastoreManager.runInTransactionWithResult(mFunction);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(DatastoreException.class, e.getCause().getClass());
        }

        // Execution & assertion
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @Test
    public void runInTransaction_throwsException_logsDbVersion() throws DatastoreException {
        // Setup
        doThrow(new IllegalArgumentException()).when(mConsumer).accept(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));

        // Execution & assertion
        assertThrows(
                IllegalArgumentException.class,
                () -> mSQLDatastoreManager.runInTransaction(mConsumer));
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode =
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_UNKNOWN_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @Test
    public void runInTransaction_throwsDataStoreExceptionExceptionDisable_logsDbVersion()
            throws DatastoreException {
        // Setup
        doThrow(new DatastoreException(null)).when(mConsumer).accept(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(false).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();

        // Execution
        mSQLDatastoreManager.runInTransaction(mConsumer);

        // Execution & assertion
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @Test
    public void runInTransaction_throwsDataStoreExceptionSamplingDisabled_logsDbVersion()
            throws DatastoreException {
        // Setup
        doThrow(new DatastoreException(null)).when(mConsumer).accept(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(true).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();
        doReturn(0.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        // Execution
        mSQLDatastoreManager.runInTransaction(mConsumer);

        // Execution & assertion
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @Test
    public void runInTransaction_throwsDataStoreExceptionExceptionEnable_logsDbVersion()
            throws DatastoreException {
        // Setup
        doThrow(new DatastoreException(null)).when(mConsumer).accept(any());
        ExtendedMockito.doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        ExtendedMockito.doReturn(mFlags).when(FlagsFactory::getFlags);
        doReturn(true).when(mFlags).getMeasurementEnableDatastoreManagerThrowDatastoreException();
        doReturn(1.0f).when(mFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        // Execution
        try {
            mSQLDatastoreManager.runInTransaction(mConsumer);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(DatastoreException.class, e.getCause().getClass());
        }

        // Execution & assertion
        ExtendedMockito.verify(
                () ->
                        LogUtil.w(
                                eq(
                                        "Underlying datastore version: "
                                                + MeasurementDbHelper.CURRENT_DATABASE_VERSION)));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        ExtendedMockito.verify(
                () ->
                        ErrorLogUtil.e(
                                any(),
                                eq(errorCode),
                                eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)));
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }
}
