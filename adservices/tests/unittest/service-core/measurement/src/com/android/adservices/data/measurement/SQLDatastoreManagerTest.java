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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(LoggerFactory.class)
@SpyStatic(FlagsFactory.class)
public final class SQLDatastoreManagerTest extends AdServicesExtendedMockitoTestCase {

    @Mock private DatastoreManager.ThrowingCheckedFunction<Void> mFunction;
    @Mock private DatastoreManager.ThrowingCheckedConsumer mConsumer;
    @Mock private Flags mMockFlags;
    @Mock private AdServicesErrorLogger mErrorLogger;
    @Mock private LoggerFactory.Logger mLogger;
    private SQLDatastoreManager mSQLDatastoreManager;

    @Before
    public void setUp() {
        mSQLDatastoreManager =
                ExtendedMockito.spy(
                        new SQLDatastoreManager(
                                DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger));
        ExtendedMockito.doReturn(mLogger).when(LoggerFactory::getMeasurementLogger);
    }

    @Test
    public void runInTransactionWithResult_throwsException_logsDbVersion() throws Exception {
        // Setup
        doThrow(new IllegalArgumentException()).when(mFunction).apply(any());
        // Execution & assertion
        assertThrows(
                IllegalArgumentException.class,
                () -> mSQLDatastoreManager.runInTransactionWithResult(mFunction));

        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));

        int errorCode =
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_UNKNOWN_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }

    @Test
    public void runInTransactionWithResult_throwsDataStoreExceptionExceptionDisable_logsDbVersion()
            throws Exception {
        // Setup
        doThrow(new DatastoreException(null)).when(mFunction).apply(any());
        mocker.mockGetFlags(mMockFlags);
        doReturn(false)
                .when(mMockFlags)
                .getMeasurementEnableDatastoreManagerThrowDatastoreException();

        // Execution
        mSQLDatastoreManager.runInTransactionWithResult(mFunction);

        // Execution & assertion
        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }

    @Test
    public void runInTransactionWithResult_throwsDataStoreExceptionExceptionEnable_rethrows()
            throws Exception {
        // Setup
        doThrow(new DatastoreException(null)).when(mFunction).apply(any());
        mocker.mockGetFlags(mMockFlags);
        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableDatastoreManagerThrowDatastoreException();
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        // Execution
        try {
            mSQLDatastoreManager.runInTransactionWithResult(mFunction);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(DatastoreException.class, e.getCause().getClass());
        }
        // Execution & assertion
        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }

    @Test
    public void runInTransaction_throwsException_logsDbVersion() throws Exception {
        // Setup
        doThrow(new IllegalArgumentException()).when(mConsumer).accept(any());

        // Execution & assertion
        assertThrows(
                IllegalArgumentException.class,
                () -> mSQLDatastoreManager.runInTransaction(mConsumer));
        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));
        int errorCode =
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_UNKNOWN_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }

    @Test
    public void runInTransaction_throwsDataStoreExceptionExceptionDisable_logsDbVersion()
            throws Exception {
        // Setup
        doThrow(new DatastoreException(null)).when(mConsumer).accept(any());
        mocker.mockGetFlags(mMockFlags);
        doReturn(false)
                .when(mMockFlags)
                .getMeasurementEnableDatastoreManagerThrowDatastoreException();

        // Execution
        mSQLDatastoreManager.runInTransaction(mConsumer);

        // Execution & assertion
        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }

    @Test
    public void runInTransaction_throwsDataStoreExceptionSamplingDisabled_logsDbVersion()
            throws Exception {
        // Setup
        doThrow(new DatastoreException(null)).when(mConsumer).accept(any());
        mocker.mockGetFlags(mMockFlags);
        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableDatastoreManagerThrowDatastoreException();
        doReturn(0.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        // Execution
        mSQLDatastoreManager.runInTransaction(mConsumer);

        // Execution & assertion
        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }

    @Test
    public void runInTransaction_throwsDataStoreExceptionExceptionEnable_logsDbVersion()
            throws Exception {
        // Setup
        doThrow(new DatastoreException(null)).when(mConsumer).accept(any());
        mocker.mockGetFlags(mMockFlags);
        doReturn(true)
                .when(mMockFlags)
                .getMeasurementEnableDatastoreManagerThrowDatastoreException();
        doReturn(1.0f).when(mMockFlags).getMeasurementThrowUnknownExceptionSamplingRate();

        // Execution
        try {
            mSQLDatastoreManager.runInTransaction(mConsumer);
            fail();
        } catch (IllegalStateException e) {
            assertEquals(DatastoreException.class, e.getCause().getClass());
        }

        // Execution & assertion
        verify(mLogger)
                .w(
                        eq(
                                "Underlying datastore version: "
                                        + MeasurementDbHelper.CURRENT_DATABASE_VERSION));
        int errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_DATASTORE_FAILURE;
        verify(mErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(errorCode),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT));
    }
}
