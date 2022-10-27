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

import static android.view.MotionEvent.ACTION_BUTTON_PRESS;

import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.registration.AsyncSourceFetcher;
import com.android.adservices.service.measurement.registration.AsyncTriggerFetcher;
import com.android.adservices.service.measurement.util.AsyncFetchStatus;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Unit tests for {@link AsyncRegistrationQueueRunnerTest} */
public class AsyncRegistrationQueueRunnerTest {
    private static final Context sDefaultContext = ApplicationProvider.getApplicationContext();
    private static final String DEFAULT_ENROLLMENT = "enrollment-id";
    private static final Uri TOP_ORIGIN =
            Uri.parse("android-app://" + sDefaultContext.getPackageName());
    private static final Uri REGISTRATION_URI = Uri.parse("https://foo.com/bar?ad=134");
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination.com");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");

    private AsyncSourceFetcher mAsyncSourceFetcher;
    private AsyncTriggerFetcher mAsyncTriggerFetcher;

    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private Source mMockedSource;
    @Mock private Trigger mMockedTrigger;
    @Mock private ITransaction mTransaction;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private ContentResolver mContentResolver;
    @Mock private ContentProviderClient mMockContentProviderClient;

    private MockitoSession mStaticMockSession;

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    class FakeDatastoreManager extends DatastoreManager {

        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }
    }

    @After
    public void cleanup() {
        SQLiteDatabase db = DbHelper.getInstance(sDefaultContext).safeGetWritableDatabase();
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

        mAsyncSourceFetcher = spy(new AsyncSourceFetcher(sDefaultContext));
        mAsyncTriggerFetcher = spy(new AsyncTriggerFetcher(sDefaultContext));
        MockitoAnnotations.initMocks(this);
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT));
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_success() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_noRedirects_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_NetworkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());

        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());

        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appSource_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_noRedirects_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_appTrigger_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_success() throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        WEB_DESTINATION,
                        APP_DESTINATION,
                        Uri.parse("android-app://com.example"),
                        Uri.parse("android-app://com.example"),
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.of(mMockedSource);
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);
        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);
        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, times(1)).insertSource(any(Source.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        WEB_DESTINATION,
                        APP_DESTINATION,
                        Uri.parse("android-app://com.example"),
                        Uri.parse("android-app://com.example"),
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_NetworkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        WEB_DESTINATION,
                        APP_DESTINATION,
                        Uri.parse("android-app://com.example"),
                        Uri.parse("android-app://com.example"),
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webSource_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        REGISTRATION_URI,
                        WEB_DESTINATION,
                        APP_DESTINATION,
                        Uri.parse("android-app://com.example"),
                        Uri.parse("android-app://com.example"),
                        TOP_ORIGIN,
                        AsyncRegistration.RegistrationType.APP_SOURCE,
                        Source.SourceType.EVENT,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncSourceFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncSourceFetcher)
                .when(mAsyncSourceFetcher)
                .fetchSource(any(), any(), any());

        Source.FakeReport sf =
                new Source.FakeReport(
                        new UnsignedLong(1L), 1L, Uri.parse("https://example.com/sF"));
        List<Source.FakeReport> eventReportList = Collections.singletonList(sf);
        when(mMockedSource.assignAttributionModeAndGenerateFakeReports())
                .thenReturn(eventReportList);

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncSourceFetcher, times(1))
                .fetchSource(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertEventReport(any(EventReport.class));
        verify(mMeasurementDao, never()).insertSource(any(Source.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_success()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SUCCESS);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.of(mMockedTrigger);
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, times(1)).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, times(2)).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_adTechUnavailable()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.SERVER_UNAVAILABLE);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_networkError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.NETWORK_ERROR);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        ArgumentCaptor<AsyncRegistration> asyncRegistrationArgumentCaptor =
                ArgumentCaptor.forClass(AsyncRegistration.class);
        verify(mMeasurementDao, times(1))
                .updateRetryCount(asyncRegistrationArgumentCaptor.capture());
        Assert.assertEquals(1, asyncRegistrationArgumentCaptor.getAllValues().size());
        Assert.assertEquals(
                1, asyncRegistrationArgumentCaptor.getAllValues().get(0).getRetryCount());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).deleteAsyncRegistration(any(String.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
    }

    @Test
    public void test_runAsyncRegistrationQueueWorker_webTrigger_parsingError()
            throws DatastoreException {
        // Setup
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        AsyncRegistration validAsyncRegistration =
                createAsyncRegistration(
                        UUID.randomUUID().toString(),
                        "enrollment_id",
                        Uri.parse("android-app://com.example"),
                        null,
                        null,
                        Uri.parse("android-app://com.example"),
                        null,
                        Uri.parse("android-app://com.example"),
                        AsyncRegistration.RegistrationType.APP_TRIGGER,
                        null,
                        System.currentTimeMillis(),
                        0,
                        System.currentTimeMillis(),
                        true,
                        true);

        Answer<?> answerAsyncTriggerFetcher =
                invocation -> {
                    AsyncFetchStatus asyncFetchStatus1 = invocation.getArgument(1);
                    asyncFetchStatus1.setStatus(AsyncFetchStatus.ResponseStatus.PARSING_ERROR);
                    List<Uri> redirectList = invocation.getArgument(2);
                    redirectList.add(Uri.parse("https://example.com/sF1"));
                    redirectList.add(Uri.parse("https://example.com/sF2"));
                    return Optional.empty();
                };
        doAnswer(answerAsyncTriggerFetcher)
                .when(mAsyncTriggerFetcher)
                .fetchTrigger(any(), any(), any());

        when(mMeasurementDao.fetchNextQueuedAsyncRegistration(anyShort(), any()))
                .thenReturn(validAsyncRegistration);

        // Execution
        asyncRegistrationQueueRunner.runAsyncRegistrationQueueWorker(1L, (short) 5);

        // Assertions
        verify(mAsyncTriggerFetcher, times(1))
                .fetchTrigger(any(AsyncRegistration.class), any(AsyncFetchStatus.class), any());
        verify(mMeasurementDao, never()).updateRetryCount(any());
        verify(mMeasurementDao, never()).insertTrigger(any(Trigger.class));
        verify(mMeasurementDao, never()).insertAsyncRegistration(any(AsyncRegistration.class));
        verify(mMeasurementDao, times(1)).deleteAsyncRegistration(any(String.class));
    }

    @Test
    public void insertSource_withFakeReportsFalseAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION);
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getAppDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    @Test
    public void insertSource_withFakeReportsFalseWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(null)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source, fakeReportsCount, SourceFixture.ValidSourceParams.WEB_DESTINATION);
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getWebDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }
    //// HERE
    @Test
    public void insertSource_withFalseAppAndWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build());
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));

        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION);

        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, times(2)).insertEventReport(any());
        verify(mMeasurementDao, times(2))
                .insertAttribution(attributionRateLimitArgCaptor.capture());
        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getAppDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getAllValues().get(0));

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getWebDestination().toString())
                        .setDestinationSite(source.getWebDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getAllValues().get(1));
    }

    @Test
    public void insertSource_withFakeReportsNeverAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .build());
        List<Source.FakeReport> fakeReports = Collections.emptyList();
        AsyncRegistrationQueueRunner asyncRegistrationQueueRunner =
                spy(
                        new AsyncRegistrationQueueRunner(
                                mContentResolver,
                                mAsyncSourceFetcher,
                                mAsyncTriggerFetcher,
                                mEnrollmentDao,
                                new FakeDatastoreManager()));
        Answer<?> neverAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.NEVER);
                    return fakeReports;
                };
        doAnswer(neverAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        asyncRegistrationQueueRunner.insertSourcesFromTransaction(source, mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).insertSource(source);
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mMeasurementDao).insertAttribution(attributionRateLimitArgCaptor.capture());

        assertEquals(
                new Attribution.Builder()
                        .setDestinationOrigin(source.getAppDestination().toString())
                        .setDestinationSite(source.getAppDestination().toString())
                        .setEnrollmentId(source.getEnrollmentId())
                        .setSourceOrigin(source.getPublisher().toString())
                        .setSourceSite(source.getPublisher().toString())
                        .setRegistrant(source.getRegistrant().toString())
                        .setTriggerTime(source.getEventTime())
                        .build(),
                attributionRateLimitArgCaptor.getValue());
    }

    private List<Source.FakeReport> createFakeReports(Source source, int count, Uri destination) {
        return IntStream.range(0, count)
                .mapToObj(
                        x ->
                                new Source.FakeReport(
                                        new UnsignedLong(0L),
                                        source.getReportingTimeForNoising(0),
                                        destination))
                .collect(Collectors.toList());
    }

    public static InputEvent getInputEvent() {
        return MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
    }

    public AsyncRegistration createAsyncRegistration(
            String iD,
            String enrollmentId,
            Uri registrationUri,
            Uri webDestination,
            Uri osDestination,
            Uri registrant,
            Uri verifiedDestination,
            Uri topOrigin,
            AsyncRegistration.RegistrationType registrationType,
            Source.SourceType sourceType,
            long mRequestTime,
            long mRetryCount,
            long mLastProcessingTime,
            boolean redirect,
            boolean debugKeyAllowed) {
        return new AsyncRegistration.Builder()
                .setId(iD)
                .setEnrollmentId(enrollmentId)
                .setRegistrationUri(registrationUri)
                .setWebDestination(webDestination)
                .setOsDestination(osDestination)
                .setRegistrant(registrant)
                .setVerifiedDestination(verifiedDestination)
                .setTopOrigin(topOrigin)
                .setType(registrationType.ordinal())
                .setSourceType(
                        registrationType == AsyncRegistration.RegistrationType.APP_SOURCE
                                        || registrationType
                                                == AsyncRegistration.RegistrationType.WEB_SOURCE
                                ? sourceType
                                : null)
                .setRequestTime(mRequestTime)
                .setRetryCount(mRetryCount)
                .setLastProcessingTime(mLastProcessingTime)
                .setRedirect(redirect)
                .setDebugKeyAllowed(debugKeyAllowed)
                .build();
    }
}
