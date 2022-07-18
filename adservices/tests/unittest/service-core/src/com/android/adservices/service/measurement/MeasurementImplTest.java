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

import static com.android.adservices.ResultCode.RESULT_INTERNAL_ERROR;
import static com.android.adservices.ResultCode.RESULT_INVALID_ARGUMENT;
import static com.android.adservices.ResultCode.RESULT_IO_ERROR;
import static com.android.adservices.ResultCode.RESULT_OK;
import static com.android.adservices.data.measurement.DatastoreManager.ThrowingCheckedConsumer;
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.content.AttributionSource;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.RemoteException;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link MeasurementImpl} */
@SmallTest
public final class MeasurementImplTest {

    private static final Context DEFAULT_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri URI_WITHOUT_APP_SCHEME = Uri.parse("com.example.abc");
    private static final Uri DEFAULT_URI = Uri.parse("android-app://com.example.abc");
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.com/bar?ad=134");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.com/bar?ad=256");
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination-uri.com");
    private static final Uri VERIFIED_DESTINATION =
            Uri.parse("https://verified-destination-uri.com");
    private static final Uri OS_DESTINATION = Uri.parse("https://os-destination-uri.com");
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
    private static final SourceRegistration VALID_SOURCE_REGISTRATION_1 =
            new com.android.adservices.service.measurement.registration.SourceRegistration.Builder()
                    .setSourceEventId(1L) //
                    .setSourcePriority(100L) //
                    .setDestination(Uri.parse("android-app://com.destination"))
                    .setWebDestination(Uri.parse("https://com.web.destination"))
                    .setExpiry(8640000010L) //
                    .setInstallAttributionWindow(841839879274L) //
                    .setInstallCooldownWindow(8418398274L) //
                    .setReportingOrigin(Uri.parse("https://com.example")) //
                    .setTopOrigin(Uri.parse("android-app://com.source"))
                    .build();
    private static final SourceRegistration VALID_SOURCE_REGISTRATION_2 =
            new com.android.adservices.service.measurement.registration.SourceRegistration.Builder()
                    .setSourceEventId(2) //
                    .setSourcePriority(200L) //
                    .setDestination(Uri.parse("android-app://com.destination2"))
                    .setWebDestination(Uri.parse("https://com.web.destination2"))
                    .setExpiry(865000010L) //
                    .setInstallAttributionWindow(841839879275L) //
                    .setInstallCooldownWindow(7418398274L) //
                    .setReportingOrigin(Uri.parse("https://com.example2")) //
                    .setTopOrigin(Uri.parse("android-app://com.source2"))
                    .build();
    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_1)
                    .setAllowDebugKey(true)
                    .build();

    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_2)
                    .setAllowDebugKey(false)
                    .build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_1)
                    .setAllowDebugKey(true)
                    .build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_2)
                    .setAllowDebugKey(false)
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
    @Mock
    private ConsentManager mConsentManager;

    public static InputEvent getInputEvent() {
        return MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
    }

    private static RegistrationRequest createRegistrationRequest(int type) {
        return new RegistrationRequest.Builder()
                .setRegistrationUri(REGISTRATION_URI_1)
                .setTopOriginUri(DEFAULT_URI)
                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                .setRegistrationType(type)
                .build();
    }

    private static WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest() {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(
                                Arrays.asList(
                                        INPUT_TRIGGER_REGISTRATION_1, INPUT_TRIGGER_REGISTRATION_2))
                        .setDestination(DEFAULT_URI)
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder()
                .setTriggerRegistrationRequest(webTriggerRegistrationRequest)
                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                .build();
    }

    private static WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest() {

        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(
                                Arrays.asList(
                                        INPUT_SOURCE_REGISTRATION_1, INPUT_SOURCE_REGISTRATION_2))
                        .setTopOriginUri(DEFAULT_URI)
                        .setOsDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(VERIFIED_DESTINATION)
                        .build();

        return new WebSourceRegistrationRequestInternal.Builder()
                .setSourceRegistrationRequest(sourceRegistrationRequest)
                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
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
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchSource(any());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Test
        MeasurementImpl measurement = spy(new MeasurementImpl(
                null, null, mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher));
        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).getSourceEventReports(any());
        final int result = measurement.register(registrationRequest, eventTime);

        // Assert
        assertEquals(RESULT_OK, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(mDatastoreManager, times(2))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mTriggerFetcher, never()).fetchTrigger(any());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        // Verify that the executors do data insertion
        insertionLogicExecutor.get(0).accept(mMeasurementDao);
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_1,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
        insertionLogicExecutor.get(1).accept(mMeasurementDao);
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchFailure() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());
        MeasurementImpl measurement = spy(new MeasurementImpl(
                null, null, mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher));
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).getSourceEventReports(any());
        final int result = measurement.register(SOURCE_REGISTRATION_REQUEST,
                System.currentTimeMillis());
        assertEquals(RESULT_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(mTriggerFetcher, never()).fetchTrigger(any());
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchSuccess() throws Exception {
        // Setup
        MeasurementImpl measurement = new MeasurementImpl(
                null, null, mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        final long triggerTime = System.currentTimeMillis();
        Answer<Optional<List<TriggerRegistration>>> populateTriggerRegistrations =
                invocation -> {
                    List<TriggerRegistration> triggerRegs = new ArrayList<>();
                    triggerRegs.add(VALID_TRIGGER_REGISTRATION);
                    return Optional.of(triggerRegs);
                };
        doAnswer(populateTriggerRegistrations)
                .when(mTriggerFetcher)
                .fetchTrigger(TRIGGER_REGISTRATION_REQUEST);

        // Execution
        final int result = measurement.register(TRIGGER_REGISTRATION_REQUEST, triggerTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(RESULT_OK, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(any());
        Trigger trigger =
                createTrigger(
                        triggerTime,
                        DEFAULT_CONTEXT.getAttributionSource());
        verify(mMeasurementDao).insertTrigger(trigger);
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchFailure() throws RemoteException {
        when(mTriggerFetcher.fetchTrigger(any())).thenReturn(Optional.empty());
        MeasurementImpl measurement = new MeasurementImpl(
                null, null, mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        final int result = measurement.register(TRIGGER_REGISTRATION_REQUEST,
                System.currentTimeMillis());
        assertEquals(RESULT_IO_ERROR, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(any());
    }

    @Test
    public void testDeleteRegistrations_successfulNoOptionalParameters() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .build());
        assertEquals(RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRange() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .setStart(Instant.now().minusSeconds(1))
                                .setEnd(Instant.now())
                                .build());
        assertEquals(RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithOrigin() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.singletonList(DEFAULT_URI))
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .build());
        assertEquals(RESULT_OK, result);
    }
    @Test
    public void testDeleteRegistrations_invalidParameterStartButNoEnd() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .setStart(Instant.now())
                                .build());
        assertEquals(RESULT_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_invalidParameterEndButNoStart() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .setEnd(Instant.now())
                                .build());
        assertEquals(RESULT_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_internalError() {
        MeasurementImpl measurement = new MeasurementImpl(null, null, mContentResolver,
                mDatastoreManager, new SourceFetcher(), new TriggerFetcher());
        Mockito.when(mDatastoreManager.runInTransaction(ArgumentMatchers.any()))
                .thenReturn(false);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .build());
        assertEquals(RESULT_INTERNAL_ERROR, result);
    }

    @Test
    public void testSourceRegistration_callsImpressionNoiseCreator() {
        long eventTime = System.currentTimeMillis();
        long expiry = TimeUnit.DAYS.toSeconds(20);
        // Creating source for easy comparison
        Source sampleSource =
                SourceFixture.getValidSourceBuilder()
                        .setAdTechDomain(BaseUriExtractor.getBaseUri(REGISTRATION_URI_1))
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(eventTime + TimeUnit.SECONDS.toMillis(expiry))
                        .setEventTime(eventTime)
                        .setPublisher(DEFAULT_URI)
                        .setAttributionDestination(Uri.parse("android-app://com.example.abc"))
                        .setEventId(123L)
                        .build();
        // Mocking fetchSource call to populate source registrations.
        List<SourceRegistration> sourceRegistrations =
                Collections.singletonList(
                        new SourceRegistration.Builder()
                                .setSourceEventId(sampleSource.getEventId())
                                .setDestination(sampleSource.getAttributionDestination())
                                .setTopOrigin(sampleSource.getPublisher())
                                .setExpiry(expiry)
                                .setReportingOrigin(sampleSource.getAdTechDomain())
                                .build());
        doReturn(Optional.of(sourceRegistrations)).when(mSourceFetcher).fetchSource(any());
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                null,
                                mContentResolver,
                                mDatastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher));
        InputEvent inputEvent = getInputEvent();
        final int result =
                measurement.register(
                        new RegistrationRequest.Builder()
                                .setRegistrationUri(REGISTRATION_URI_1)
                                .setTopOriginUri(DEFAULT_URI)
                                .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                                .setInputEvent(inputEvent)
                                .build(),
                        eventTime);
        assertEquals(RESULT_OK, result);
        ArgumentCaptor<Source> sourceArgs = ArgumentCaptor.forClass(Source.class);
        verify(measurement).getSourceEventReports(sourceArgs.capture());
        Source capturedSource = sourceArgs.getValue();
        assertEquals(sampleSource.getSourceType(), capturedSource.getSourceType());
        assertEquals(sampleSource.getEventId(), capturedSource.getEventId());
        assertEquals(sampleSource.getEventTime(), capturedSource.getEventTime());
        assertEquals(sampleSource.getAggregateSource(), capturedSource.getAggregateSource());
        assertEquals(
                sampleSource.getAttributionDestination(),
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
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(123L)
                                .setEventTime(eventTime)
                                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
                                .setSourceType(Source.SourceType.NAVIGATION)
                                .setAdTechDomain(BaseUriExtractor.getBaseUri(REGISTRATION_URI_1))
                                .setAttributionDestination(DEFAULT_URI)
                                .setPublisher(DEFAULT_URI)
                                .build());
        when(source.getRandomAttributionProbability()).thenReturn(1.1D);
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);
        MeasurementImpl measurement =
                new MeasurementImpl(
                        null,
                        null,
                        mContentResolver,
                        mockDatastoreManager,
                        mockSourceFetcher,
                        mockTriggerFetcher);
        List<EventReport> fakeEventReports = measurement.getSourceEventReports(source);

        // Generate valid report times
        Set<Long> reportingTimes = new HashSet<>();
        reportingTimes.add(
                source.getReportingTime(
                        eventTime + TimeUnit.DAYS.toMillis(1), DestinationType.APP));
        reportingTimes.add(
                source.getReportingTime(
                        eventTime + TimeUnit.DAYS.toMillis(3), DestinationType.APP));
        reportingTimes.add(
                source.getReportingTime(
                        eventTime + TimeUnit.DAYS.toMillis(8), DestinationType.APP));

        for (EventReport report : fakeEventReports) {
            Assert.assertEquals(source.getEventId(), report.getSourceId());
            Assert.assertTrue(reportingTimes.stream().anyMatch(x -> x == report.getReportTime()));
            Assert.assertEquals(0, report.getTriggerTime());
            Assert.assertEquals(0, report.getTriggerPriority());
            Assert.assertEquals(
                    source.getAttributionDestination(), report.getAttributionDestination());
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
                null, null, mContentResolver, mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Execution
        measurement.doInstallAttribution(URI_WITHOUT_APP_SCHEME, systemTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());

        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).doInstallAttribution(DEFAULT_URI, systemTime);
    }

    @Test
    public void testGetMeasurementApiStatus_enabled() {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.getMeasurementApiStatus();
        assertEquals(MeasurementManager.MEASUREMENT_API_STATE_ENABLED, result);
    }

    @Test
    public void testGetMeasurementApiStatus_disabled() {
        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.REVOKED);
        MeasurementImpl measurement =
                new MeasurementImpl(DEFAULT_CONTEXT, mConsentManager, null, null, null, null);
        final int result = measurement.getMeasurementApiStatus();
        assertEquals(MeasurementManager.MEASUREMENT_API_STATE_DISABLED, result);
    }

    @Test
    public void testDeleteAllMeasurementData() throws DatastoreException {
        MeasurementImpl measurement = new MeasurementImpl(null, null, mContentResolver,
                mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Execution
        measurement.deleteAllMeasurementData(Collections.emptyList());
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());

        consumerArgumentCaptor.getValue().accept(mMeasurementDao);
        verify(mMeasurementDao, times(1)).deleteAllMeasurementData(any());
    }

    @Test
    public void registerWebSource_sourceFetchSuccess() throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest();
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchWebSources(any());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                null,
                                mContentResolver,
                                mDatastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher));
        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).getSourceEventReports(any());
        final int result = measurement.registerWebSource(registrationRequest, eventTime);

        // Assert
        assertEquals(RESULT_OK, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchWebSources(any());
        verify(mDatastoreManager, times(2))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        // Verify that the executors do data insertion
        insertionLogicExecutor.get(0).accept(mMeasurementDao);
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_1,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
        insertionLogicExecutor.get(1).accept(mMeasurementDao);
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void registerWebSource_sourceFetchFailure() {
        when(mSourceFetcher.fetchWebSources(any())).thenReturn(Optional.empty());
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                null,
                                mContentResolver,
                                mDatastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher));
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).getSourceEventReports(any());
        final int result =
                measurement.registerWebSource(
                        createWebSourceRegistrationRequest(), System.currentTimeMillis());
        assertEquals(RESULT_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any());
    }

    @Test
    public void registerWebTrigger_triggerFetchSuccess() throws Exception {
        // Setup
        when(mTriggerFetcher.fetchWebTriggers(any()))
                .thenReturn(Optional.of(Collections.singletonList(VALID_TRIGGER_REGISTRATION)));
        MeasurementImpl measurement = new MeasurementImpl(null, null, mContentResolver,
                mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        final long triggerTime = System.currentTimeMillis();

        // Execution
        final int result =
                measurement.registerWebTrigger(createWebTriggerRegistrationRequest(), triggerTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(RESULT_OK, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchWebTriggers(any());
        verify(mMeasurementDao)
                .insertTrigger(
                        eq(
                                createTrigger(
                                        triggerTime,
                                        DEFAULT_CONTEXT.getAttributionSource())));
    }

    @Test
    public void registerWebTrigger_triggerFetchFailure() throws RemoteException {
        when(mTriggerFetcher.fetchWebTriggers(any())).thenReturn(Optional.empty());
        MeasurementImpl measurement = new MeasurementImpl(null, null, mContentResolver,
                mDatastoreManager, mSourceFetcher, mTriggerFetcher);
        final int result =
                measurement.registerWebTrigger(
                        createWebTriggerRegistrationRequest(), System.currentTimeMillis());
        assertEquals(RESULT_IO_ERROR, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, never()).fetchWebSources(any());
        verify(mTriggerFetcher, times(1)).fetchWebTriggers(any());
    }

    private void verifyInsertSource(
            RegistrationRequest registrationRequest,
            SourceRegistration sourceRegistration,
            long eventTime,
            Uri firstSourceDestination,
            Uri firstSourceWebDestination)
            throws DatastoreException {
        Source source =
                createSource(
                        sourceRegistration,
                        eventTime,
                        firstSourceDestination,
                        firstSourceWebDestination,
                        registrationRequest.getTopOriginUri(),
                        registrationRequest.getAttributionSource());
        verify(mMeasurementDao).insertSource(source);
    }

    private void verifyInsertSource(
            WebSourceRegistrationRequestInternal registrationRequest,
            SourceRegistration sourceRegistration,
            long eventTime,
            Uri firstSourceDestination,
            Uri firstSourceWebDestination)
            throws DatastoreException {
        Source source =
                createSource(
                        sourceRegistration,
                        eventTime,
                        firstSourceDestination,
                        firstSourceWebDestination,
                        registrationRequest.getSourceRegistrationRequest().getTopOriginUri(),
                        registrationRequest.getAttributionSource());
        verify(mMeasurementDao).insertSource(source);
    }

    private Source createSource(
            SourceRegistration sourceRegistration,
            long eventTime,
            Uri firstSourceDestination,
            Uri firstSourceWebDestination,
            Uri topOrigin,
            AttributionSource attributionSource) {
        return SourceFixture.getValidSourceBuilder()
                .setEventId(sourceRegistration.getSourceEventId())
                .setPublisher(topOrigin)
                .setAttributionDestination(firstSourceDestination)
                .setWebDestination(firstSourceWebDestination)
                .setAdTechDomain(sourceRegistration.getReportingOrigin())
                .setRegistrant(Uri.parse("android-app://" + attributionSource.getPackageName()))
                .setEventTime(eventTime)
                .setExpiryTime(
                        eventTime + TimeUnit.SECONDS.toMillis(sourceRegistration.getExpiry()))
                .setPriority(sourceRegistration.getSourcePriority())
                .setSourceType(Source.SourceType.EVENT)
                .setInstallAttributionWindow(
                        TimeUnit.SECONDS.toMillis(sourceRegistration.getInstallAttributionWindow()))
                .setInstallCooldownWindow(
                        TimeUnit.SECONDS.toMillis(sourceRegistration.getInstallCooldownWindow()))
                .setAttributionMode(Source.AttributionMode.TRUTHFULLY)
                .setAggregateSource(sourceRegistration.getAggregateSource())
                .setAggregateFilterData(sourceRegistration.getAggregateFilterData())
                .build();
    }

    private Trigger createTrigger(long triggerTime, AttributionSource attributionSource) {
        return TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(MeasurementImplTest.DEFAULT_URI)
                .setAdTechDomain(
                        MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getReportingOrigin())
                .setRegistrant(Uri.parse(ANDROID_APP_SCHEME + attributionSource.getPackageName()))
                .setTriggerTime(triggerTime)
                .setEventTriggers(EVENT_TRIGGERS)
                .setAggregateTriggerData(
                        MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getAggregateTriggerData())
                .setAggregateValues(
                        MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getAggregateValues())
                .setFilters(MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getFilters())
                .build();
    }
}
