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

import static com.android.adservices.data.measurement.DatastoreManager.ThrowingCheckedConsumer;
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.RegistrationRequest;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.measurement.attribution.BaseUriExtractor;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.SourceRegistration;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.registration.TriggerRegistration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link MeasurementImpl} */
@SmallTest
public final class MeasurementImplTest {

    private static final Context DEFAULT_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri URI_WITHOUT_APP_SCHEME = Uri.parse("com.example.abc");
    private static final Uri DEFAULT_URI = Uri.parse("android-app://com.example.abc");
    private static final Uri DEFAULT_REGISTRATION_URI = Uri.parse("https://foo.com/bar?ad=134");
    private static final String ANDROID_APP_SCHEME = "android-app://";
    private static final RegistrationRequest SOURCE_REGISTRATION_REQUEST =
            createRegistrationRequest(RegistrationRequest.REGISTER_SOURCE);
    private static final RegistrationRequest TRIGGER_REGISTRATION_REQUEST =
            createRegistrationRequest(RegistrationRequest.REGISTER_TRIGGER);
    private static final String TOP_LEVEL_FILTERS_JSON_STRING =
            "{\n"
                    + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                    + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                    + "}\n";
    private static final long TRIGGER_PRIORITY = 345678L;
    private static final Long TRIGGER_DEDUP_KEY = 2345678L;
    private static final Long TRIGGER_DATA = 1L;
    private static final String EVENT_TRIGGERS =
            "[\n"
                    + "{\n"
                    + "  \"trigger_data\": \""
                    + TRIGGER_DATA
                    + "\",\n"
                    + "  \"priority\": \""
                    + TRIGGER_PRIORITY
                    + "\",\n"
                    + "  \"deduplication_key\": \""
                    + TRIGGER_DEDUP_KEY
                    + "\",\n"
                    + "  \"filters\": {\n"
                    + "    \"source_type\": [\"navigation\"],\n"
                    + "    \"key_1\": [\"value_1\"] \n"
                    + "   }\n"
                    + "}"
                    + "]\n";

    private static final SourceRegistration VALID_SOURCE_REGISTRATION_1 =
            new com.android.adservices.service.measurement.registration.SourceRegistration
                    .Builder()
                    .setSourceEventId(1L)//
                    .setSourcePriority(100L)//
                    .setDestination(Uri.parse("android-app://com.destination"))
                    .setExpiry(8640000010L)//
                    .setInstallAttributionWindow(841839879274L)//
                    .setInstallCooldownWindow(8418398274L)//
                    .setReportingOrigin(Uri.parse("https://com.example"))//
                    .setTopOrigin(Uri.parse("android-app://com.source"))
                    .build();

    private static final SourceRegistration VALID_SOURCE_REGISTRATION_2 =
            new com.android.adservices.service.measurement.registration.SourceRegistration
                    .Builder()
                    .setSourceEventId(2)//
                    .setSourcePriority(200L)//
                    .setDestination(Uri.parse("android-app://com.destination2"))
                    .setExpiry(865000010L)//
                    .setInstallAttributionWindow(841839879275L)//
                    .setInstallCooldownWindow(7418398274L)//
                    .setReportingOrigin(Uri.parse("https://com.example2"))//
                    .setTopOrigin(Uri.parse("android-app://com.source2"))
                    .build();

    private static final TriggerRegistration VALID_TRIGGER_REGISTRATION =
            new TriggerRegistration.Builder()
                    .setTopOrigin(Uri.parse("https://foo.com"))
                    .setReportingOrigin(Uri.parse("https://bar.com"))
                    .setEventTriggers(EVENT_TRIGGERS)
                    .setAggregateTriggerData(
                            "[{\"key_piece\":\"0x400\",\"source_keys\":[\"campaignCounts\"],"
                                    + "\"not_filters\":{\"product\":[\"1\"]}},"
                                    + "{\"key_piece\":\"0xA80\",\"source_keys\":[\"geoValue\"]}]")
                    .setAggregateValues("{\"campaignCounts\":32768,\"geoValue\":1644}")
                    .setFilters(TOP_LEVEL_FILTERS_JSON_STRING)
                    .build();

    @Mock
    private DatastoreManager mDatastoreManager;
    @Mock
    private ContentProviderClient mMockContentProviderClient;
    @Mock
    private ContentResolver mContentResolver;
    @Mock
    private SourceFetcher mSourceFetcher;
    @Mock
    private TriggerFetcher mTriggerFetcher;
    @Mock
    private IMeasurementDao mMeasurementDao;

    public static InputEvent getInputEvent() {
        return MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
    }

    private static RegistrationRequest createRegistrationRequest(int type) {
        return new RegistrationRequest.Builder()
                .setRegistrationUri(DEFAULT_REGISTRATION_URI)
                .setTopOriginUri(DEFAULT_URI)
                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                .setRegistrationType(type)
                .build();
    }

    @Before
    public void before() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchSuccess()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        RegistrationRequest registrationRequest = SOURCE_REGISTRATION_REQUEST;
        when(mSourceFetcher.fetchSource(any(), any())).thenAnswer(
                (invocation) -> {
                    List<SourceRegistration> argument = invocation.getArgument(1);
                    argument.addAll(sourceRegistrationsOut);
                    return true;
                });
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Test
        MeasurementImpl measurement = spy(new MeasurementImpl(
                mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher));
        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).getSourceEventReports(any());
        final int result = measurement.register(registrationRequest, eventTime);

        // Assert
        assertEquals(IMeasurementCallback.RESULT_OK, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchSource(any(), any());
        verify(mDatastoreManager, times(2))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mTriggerFetcher, never()).fetchTrigger(ArgumentMatchers.any(), any());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        // Verify that the executors do data insertion
        insertionLogicExecutor.get(0).accept(mMeasurementDao);
        verifyInsertSource(registrationRequest, VALID_SOURCE_REGISTRATION_1,
                eventTime, VALID_SOURCE_REGISTRATION_1.getDestination());
        insertionLogicExecutor.get(1).accept(mMeasurementDao);
        verifyInsertSource(registrationRequest, VALID_SOURCE_REGISTRATION_2,
                eventTime, VALID_SOURCE_REGISTRATION_1.getDestination());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchFailure() {
        when(mSourceFetcher.fetchSource(any(), any())).thenReturn(false);
        MeasurementImpl measurement = spy(new MeasurementImpl(
                mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher));
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).getSourceEventReports(any());
        final int result = measurement.register(SOURCE_REGISTRATION_REQUEST,
                System.currentTimeMillis());
        assertEquals(IMeasurementCallback.RESULT_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchSource(any(), any());
        verify(mTriggerFetcher, never()).fetchTrigger(ArgumentMatchers.any(), any());
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchSuccess() throws Exception {
        // Setup
        when(mTriggerFetcher.fetchTrigger(any(), any())).thenReturn(true);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        final long triggerTime = System.currentTimeMillis();
        List<TriggerRegistration> triggerRegistrations = new ArrayList<>();
        Answer<Boolean> populateTriggerRegistrations =
                invocation -> {
                    List<TriggerRegistration> triggerRegs = invocation.getArgument(1);
                    triggerRegs.add(VALID_TRIGGER_REGISTRATION);
                    return true;
                };
        doAnswer(populateTriggerRegistrations)
                .when(mTriggerFetcher)
                .fetchTrigger(TRIGGER_REGISTRATION_REQUEST, triggerRegistrations);

        // Execution
        final int result = measurement.register(TRIGGER_REGISTRATION_REQUEST, triggerTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(IMeasurementCallback.RESULT_OK, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any(), any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(any(), any());
        verify(mMeasurementDao)
                .insertTrigger(
                        DEFAULT_URI,
                        VALID_TRIGGER_REGISTRATION.getReportingOrigin(),
                        Uri.parse(
                                ANDROID_APP_SCHEME
                                        + DEFAULT_CONTEXT.getAttributionSource().getPackageName()),
                        triggerTime,
                        EVENT_TRIGGERS,
                        VALID_TRIGGER_REGISTRATION.getAggregateTriggerData(),
                        VALID_TRIGGER_REGISTRATION.getAggregateValues(),
                        VALID_TRIGGER_REGISTRATION.getFilters());
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchFailure() throws RemoteException {
        when(mTriggerFetcher.fetchTrigger(any(), any())).thenReturn(false);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        final int result = measurement.register(TRIGGER_REGISTRATION_REQUEST,
                System.currentTimeMillis());
        assertEquals(IMeasurementCallback.RESULT_IO_ERROR, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any(), any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(ArgumentMatchers.any(), any());
    }

    @Test
    public void testDeleteRegistrations_successfulNoOptionalParameters() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRange() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setStart(Instant.now().minusSeconds(1))
                        .setEnd(Instant.now())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithOrigin() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setOriginUri(DEFAULT_URI)
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRangeAndOrigin() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setStart(Instant.now().minusSeconds(1))
                        .setEnd(Instant.now())
                        .setOriginUri(DEFAULT_URI)
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_invalidParameterStartButNoEnd() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setStart(Instant.now())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_invalidParameterEndButNoStart() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setEnd(Instant.now())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_internalError() {
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mDatastoreManager, new SourceFetcher(), new TriggerFetcher());
        Mockito.when(mDatastoreManager.runInTransaction(ArgumentMatchers.any()))
                .thenReturn(false);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_INTERNAL_ERROR, result);
    }

    @Test
    public void testSourceRegistration_callsImpressionNoiseCreator() throws DatastoreException {
        long eventTime = System.currentTimeMillis();
        long expiry = TimeUnit.DAYS.toSeconds(20);
        // Creating source for easy comparison
        Source sampleSource = new Source.Builder()
                .setAdTechDomain(BaseUriExtractor.getBaseUri(DEFAULT_REGISTRATION_URI))
                .setSourceType(Source.SourceType.NAVIGATION)
                .setExpiryTime(eventTime + TimeUnit.SECONDS.toMillis(expiry))
                .setEventTime(eventTime)
                .setPublisher(DEFAULT_URI)
                .setAttributionDestination(Uri.parse("android-app://com.example.abc"))
                .setEventId(123L)
                .build();
        // Mocking fetchSource call to populate source registrations.
        doAnswer(invocation -> {
            List<SourceRegistration> sourceReg = invocation.getArgument(1);
            sourceReg.add(new SourceRegistration.Builder()
                    .setSourceEventId(sampleSource.getEventId())
                    .setDestination(sampleSource.getAttributionDestination())
                    .setTopOrigin(sampleSource.getPublisher())
                    .setExpiry(expiry)
                    .setReportingOrigin(sampleSource.getAdTechDomain())
                    .build()
            );
            return true;
        }).when(mSourceFetcher).fetchSource(any(), any());
        MeasurementImpl measurement = spy(new MeasurementImpl(
                mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher));
        InputEvent inputEvent = getInputEvent();
        final int result = measurement.register(
                new RegistrationRequest.Builder()
                        .setRegistrationUri(DEFAULT_REGISTRATION_URI)
                        .setTopOriginUri(DEFAULT_URI)
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setInputEvent(inputEvent)
                        .build(),
                eventTime
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
        ArgumentCaptor<Source> sourceArgs = ArgumentCaptor.forClass(Source.class);
        verify(measurement).getSourceEventReports(sourceArgs.capture());
        Source capturedSource = sourceArgs.getValue();
        assertEquals(sampleSource.getSourceType(), capturedSource.getSourceType());
        assertEquals(sampleSource.getEventId(), capturedSource.getEventId());
        assertEquals(sampleSource.getEventTime(), capturedSource.getEventTime());
        assertEquals(sampleSource.getAggregateSource(), capturedSource.getAggregateSource());
        assertEquals(sampleSource.getAttributionDestination(),
                capturedSource.getAttributionDestination());
        assertEquals(sampleSource.getAdTechDomain(), capturedSource.getAdTechDomain());
        assertEquals(sampleSource.getPublisher(), capturedSource.getPublisher());
        assertEquals(sampleSource.getPriority(), capturedSource.getPriority());

        // Check Attribution Mode assignment
        assertNotEquals(Source.AttributionMode.UNASSIGNED, capturedSource.getAttributionMode());
    }

    @Test
    public void testGetSourceEventReports() {
        long eventTime = System.currentTimeMillis();
        Source source = spy(new Source.Builder()
                .setEventId(123L)
                .setEventTime(eventTime)
                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
                .setSourceType(Source.SourceType.NAVIGATION)
                .setAdTechDomain(BaseUriExtractor.getBaseUri(DEFAULT_REGISTRATION_URI))
                .setAttributionDestination(DEFAULT_URI)
                .setPublisher(DEFAULT_URI)
                .build());
        when(source.getRandomAttributionProbability()).thenReturn(1.1D);
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mockDatastoreManager, mockSourceFetcher, mockTriggerFetcher);
        List<EventReport> fakeEventReports =  measurement.getSourceEventReports(source);

        // Generate valid report times
        Set<Long> reportingTimes = new HashSet<>();
        reportingTimes.add(source.getReportingTime(eventTime + TimeUnit.DAYS.toMillis(1)));
        reportingTimes.add(source.getReportingTime(eventTime + TimeUnit.DAYS.toMillis(3)));
        reportingTimes.add(source.getReportingTime(eventTime + TimeUnit.DAYS.toMillis(8)));

        for (EventReport report : fakeEventReports) {
            Assert.assertEquals(source.getEventId(), report.getSourceId());
            Assert.assertTrue(
                    reportingTimes.stream().anyMatch(x -> x == report.getReportTime()));
            Assert.assertEquals(0, report.getTriggerTime());
            Assert.assertEquals(0, report.getTriggerPriority());
            Assert.assertEquals(source.getAttributionDestination(),
                    report.getAttributionDestination());
            Assert.assertEquals(source.getAdTechDomain(), report.getAdTechDomain());
            Assert.assertTrue(report.getTriggerData()
                    < source.getTriggerDataCardinality());
            Assert.assertNull(report.getTriggerDedupKey());
            Assert.assertEquals(EventReport.Status.PENDING, report.getStatus());
            Assert.assertEquals(source.getSourceType(), report.getSourceType());
            Assert.assertEquals(source.getRandomAttributionProbability(),
                    report.getRandomizedTriggerRate(), /* delta= */ 0.00001D);
        }
    }

    @Test
    public void testInstallAttribution() throws DatastoreException {
        // Setup
        long systemTime = System.currentTimeMillis();
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Execution
        measurement.doInstallAttribution(URI_WITHOUT_APP_SCHEME, systemTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());

        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).doInstallAttribution(DEFAULT_URI, systemTime);
    }

    private void verifyInsertSource(RegistrationRequest registrationRequest,
            SourceRegistration sourceRegistration,
            long eventTime,
            Uri firstSourceDestination)
            throws DatastoreException {
        verify(mMeasurementDao).insertSource(
                sourceRegistration.getSourceEventId(),
                registrationRequest.getTopOriginUri(),
                firstSourceDestination,
                sourceRegistration.getReportingOrigin(),
                Uri.parse("android-app://"
                        + registrationRequest.getAttributionSource().getPackageName()),
                eventTime,
                eventTime + TimeUnit.SECONDS.toMillis(sourceRegistration.getExpiry()),
                sourceRegistration.getSourcePriority(),
                Source.SourceType.EVENT,
                TimeUnit.SECONDS.toMillis(sourceRegistration.getInstallAttributionWindow()),
                TimeUnit.SECONDS.toMillis(sourceRegistration.getInstallCooldownWindow()),
                Source.AttributionMode.TRUTHFULLY,
                sourceRegistration.getAggregateSource(),
                sourceRegistration.getAggregateFilterData()
        );
    }
}
