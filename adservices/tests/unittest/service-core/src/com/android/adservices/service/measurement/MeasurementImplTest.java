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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.view.MotionEvent.ACTION_BUTTON_PRESS;

import static com.android.adservices.data.measurement.DatastoreManager.ThrowingCheckedConsumer;
import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.SourceRegistration;
import com.android.adservices.service.measurement.registration.TriggerFetcher;
import com.android.adservices.service.measurement.registration.TriggerRegistration;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Unit tests for {@link MeasurementImpl} */
@SmallTest
public final class MeasurementImplTest {
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    private static final Context DEFAULT_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri URI_WITHOUT_APP_SCHEME = Uri.parse("com.example.abc");
    private static final Uri DEFAULT_URI = Uri.parse("android-app://com.example.abc");
    private static final Uri TOP_ORIGIN =
            Uri.parse("android-app://" + DEFAULT_CONTEXT.getPackageName());
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo.com/bar?ad=134");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.com/bar?ad=256");
    private static final String DEFAULT_ENROLLMENT = "enrollment-id";
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination.com");
    private static final Uri WEB_DESTINATION_WITH_SUBDOMAIN =
            Uri.parse("https://subdomain.web-destination.com");
    private static final Uri OTHER_WEB_DESTINATION = Uri.parse("https://other-web-destination.com");
    private static final Uri INVALID_WEB_DESTINATION = Uri.parse("https://example.not_a_tld");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");
    private static final Uri OTHER_APP_DESTINATION =
            Uri.parse("android-app://com.other_app_destination");
    private static final String ANDROID_APP_SCHEME = "android-app://";
    private static final String VENDING_PREFIX = "https://play.google.com/store/apps/details?id=";
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
                    .setEnrollmentId(DEFAULT_ENROLLMENT)
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
                    .setSourceEventId(new UnsignedLong(1L))
                    .setSourcePriority(100L)
                    .setAppDestination(Uri.parse("android-app://com.destination"))
                    .setWebDestination(Uri.parse("https://web-destination.com"))
                    .setExpiry(8640000010L)
                    .setInstallAttributionWindow(841839879274L)
                    .setInstallCooldownWindow(8418398274L)
                    .setEnrollmentId(DEFAULT_ENROLLMENT)
                    .setDebugKey(new UnsignedLong(47823478789L))
                    .build();
    private static final SourceRegistration VALID_SOURCE_REGISTRATION_2 =
            new com.android.adservices.service.measurement.registration.SourceRegistration.Builder()
                    .setSourceEventId(new UnsignedLong(2L))
                    .setSourcePriority(200L)
                    .setAppDestination(Uri.parse("android-app://com.destination2"))
                    .setWebDestination(Uri.parse("https://web-destination2.com"))
                    .setExpiry(865000010L)
                    .setInstallAttributionWindow(841839879275L)
                    .setInstallCooldownWindow(7418398274L)
                    .setEnrollmentId(DEFAULT_ENROLLMENT)
                    .setDebugKey(new UnsignedLong(37348783483L))
                    .build();
    private static final SourceRegistration VALID_SOURCE_REGISTRATION_3 =
            new com.android.adservices.service.measurement.registration.SourceRegistration.Builder()
                    .setSourceEventId(new UnsignedLong(3L))
                    .setSourcePriority(300L)
                    .setAppDestination(Uri.parse("android-app://com.destination3"))
                    .setWebDestination(Uri.parse("https://web-destination3.com"))
                    .setExpiry(863000010L)
                    .setInstallAttributionWindow(841839879275L)
                    .setInstallCooldownWindow(7418398274L)
                    .setEnrollmentId(DEFAULT_ENROLLMENT)
                    .build();
    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final long REQUEST_TIME = 10000L;

    @Spy
    private DatastoreManager mDatastoreManager =
            DatastoreManagerFactory.getDatastoreManager(DEFAULT_CONTEXT);

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
    @Mock private ClickVerifier mClickVerifier;
    private MeasurementImpl mMeasurementImpl;
    @Mock
    ITransaction mTransaction;
    @Mock
    EnrollmentDao mEnrollmentDao;

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

    private interface AppVendorPackages {
        String PLAY_STORE = "com.android.vending";
    }

    public static InputEvent getInputEvent() {
        return MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
    }

    private static RegistrationRequest createRegistrationRequest(int type) {
        return new RegistrationRequest.Builder()
                .setRegistrationUri(REGISTRATION_URI_1)
                .setPackageName(DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                .setRegistrationType(type)
                .setAdIdPermissionGranted(true)
                .build();
    }

    private static RegistrationRequest createRegistrationRequestWithoutAdIdPermission(int type) {
        return new RegistrationRequest.Builder()
                .setRegistrationUri(REGISTRATION_URI_1)
                .setPackageName(DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                .setRegistrationType(type)
                .setAdIdPermissionGranted(false)
                .build();
    }

    private static WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest(
            Uri destination) {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Arrays.asList(
                                        INPUT_TRIGGER_REGISTRATION_1, INPUT_TRIGGER_REGISTRATION_2),
                                destination)
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder(
                        webTriggerRegistrationRequest,
                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(true)
                .build();
    }

    private static WebTriggerRegistrationRequestInternal
            createWebTriggerRegistrationRequestWithoutAdIdPermission(Uri destination) {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Arrays.asList(
                                        INPUT_TRIGGER_REGISTRATION_1, INPUT_TRIGGER_REGISTRATION_2),
                                destination)
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder(
                        webTriggerRegistrationRequest,
                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                .setAdIdPermissionGranted(false)
                .build();
    }

    private static WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest(
            Uri appDestination, Uri webDestination, Uri verifiedDestination) {
        return createWebSourceRegistrationRequest(
                appDestination, webDestination, verifiedDestination, DEFAULT_URI);
    }

    private static WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest(
            Uri appDestination, Uri webDestination, Uri verifiedDestination, Uri topOriginUri) {

        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Arrays.asList(
                                        INPUT_SOURCE_REGISTRATION_1, INPUT_SOURCE_REGISTRATION_2),
                                topOriginUri)
                        .setAppDestination(appDestination)
                        .setWebDestination(webDestination)
                        .setVerifiedDestination(verifiedDestination)
                        .build();

        return new WebSourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest,
                        DEFAULT_CONTEXT.getAttributionSource().getPackageName(),
                        REQUEST_TIME)
                .setAdIdPermissionGranted(true)
                .build();
    }

    private static WebSourceRegistrationRequestInternal
            createWebSourceRegistrationRequestWithoutAdIdPermission(
                    Uri appDestination, Uri webDestination, Uri verifiedDestination) {

        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Arrays.asList(
                                        INPUT_SOURCE_REGISTRATION_1, INPUT_SOURCE_REGISTRATION_2),
                                DEFAULT_URI)
                        .setAppDestination(appDestination)
                        .setWebDestination(webDestination)
                        .setVerifiedDestination(verifiedDestination)
                        .build();

        return new WebSourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest,
                        DEFAULT_CONTEXT.getAttributionSource().getPackageName(),
                        REQUEST_TIME)
                .setAdIdPermissionGranted(false)
                .build();
    }

    @Before
    public void before() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
        mMeasurementImpl =
                spy(
                        new MeasurementImpl(
                                DEFAULT_CONTEXT,
                                mContentResolver,
                                mDatastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong());
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT));
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

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurementImpl).generateFakeEventReports(any());
        final int result = measurementImpl.register(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(datastoreManager, times(2))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(4))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(4)).countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchTrigger(any());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_1,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchSuccess_withoutAdIdPermission()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        RegistrationRequest registrationRequest =
                createRegistrationRequestWithoutAdIdPermission(RegistrationRequest.REGISTER_SOURCE);
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchSource(any());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurementImpl).generateFakeEventReports(any());
        final int result = measurementImpl.register(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(datastoreManager, times(2)).runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(4))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(4))
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchTrigger(any());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_1,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchFailure() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(mMeasurementImpl).generateFakeEventReports(any());
        final int result =
                mMeasurementImpl.register(SOURCE_REGISTRATION_REQUEST, System.currentTimeMillis());
        // STATUS_IO_ERROR is expected when fetchSource returns Optional.empty()
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(mTriggerFetcher, never()).fetchTrigger(any());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsPrivacyParam_destination()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        RegistrationRequest registrationRequest = SOURCE_REGISTRATION_REQUEST;
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchSource(any());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).generateFakeEventReports(any());
        final int result = measurement.register(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(datastoreManager, never()).runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never())
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchTrigger(any());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsMaxSourcesLimit()
            throws DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchSource(any());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).generateFakeEventReports(any());
        final int result = measurement.register(SOURCE_REGISTRATION_REQUEST, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(mMeasurementDao, never()).insertSource(any());
        verify(datastoreManager, never()).runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(1)).getNumSourcesPerPublisher(any(), anyInt());
        verify(mTriggerFetcher, never()).fetchTrigger(any());
    }

    @Test
    public void testRegister_registrationTypeSource_partiallyInsertSources()
            throws DatastoreException {
        // setup
        int remainingBudget = 2;
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(
                        VALID_SOURCE_REGISTRATION_1,
                        VALID_SOURCE_REGISTRATION_2,
                        VALID_SOURCE_REGISTRATION_3);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchSource(SOURCE_REGISTRATION_REQUEST);

        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER - remainingBudget)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());
        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).generateFakeEventReports(any());
        final int result = measurement.register(SOURCE_REGISTRATION_REQUEST, eventTime);
        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        verify(mSourceFetcher).fetchSource(SOURCE_REGISTRATION_REQUEST);
        verify(datastoreManager, times(remainingBudget))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(remainingBudget, insertionLogicExecutor.size());
        assertTrue(remainingBudget < sourceRegistrationsOut.size());
    }

    @Test
    public void testRegister_registrationTypeSource_exceedsPrivacyParam_adTech()
            throws RemoteException, DatastoreException {
        // Setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        RegistrationRequest registrationRequest = SOURCE_REGISTRATION_REQUEST;
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchSource(any());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(100))
                        .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).generateFakeEventReports(any());
        final int result = measurement.register(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchSource(any());
        verify(datastoreManager, times(1))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(4))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(3)).countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchTrigger(any());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(1, insertionLogicExecutor.size());

        // First registration was removed for exceeding the privacy bound.
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchSuccess() throws Exception {
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
        final int result = mMeasurementImpl.register(TRIGGER_REGISTRATION_REQUEST, triggerTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(any());
        Trigger trigger =
                createTrigger(
                        triggerTime,
                        DEFAULT_CONTEXT.getAttributionSource(),
                        TOP_ORIGIN,
                        EventSurfaceType.APP);
        verify(mMeasurementDao).insertTrigger(trigger);
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchSuccess_withoutAdIdPermission()
            throws Exception {
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        final long triggerTime = System.currentTimeMillis();
        Answer<Optional<List<TriggerRegistration>>> populateTriggerRegistrations =
                invocation -> {
                    List<TriggerRegistration> triggerRegs = new ArrayList<>();
                    triggerRegs.add(VALID_TRIGGER_REGISTRATION);
                    return Optional.of(triggerRegs);
                };
        RegistrationRequest registrationRequest =
                createRegistrationRequestWithoutAdIdPermission(
                        RegistrationRequest.REGISTER_TRIGGER);

        doAnswer(populateTriggerRegistrations)
                .when(mTriggerFetcher)
                .fetchTrigger(registrationRequest);

        // Execution
        final int result = mMeasurementImpl.register(registrationRequest, triggerTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(any());
        Trigger trigger =
                createTrigger(
                        triggerTime,
                        DEFAULT_CONTEXT.getAttributionSource(),
                        TOP_ORIGIN,
                        EventSurfaceType.APP);
        verify(mMeasurementDao).insertTrigger(trigger);
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchFailure() throws RemoteException {
        when(mTriggerFetcher.fetchTrigger(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.register(TRIGGER_REGISTRATION_REQUEST, System.currentTimeMillis());
        assertEquals(STATUS_IO_ERROR, result);

        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchTrigger(any());
    }

    @Test
    public void testDeleteRegistrations_successfulNoOptionalParameters() {
        MeasurementImpl measurement =
                new MeasurementImpl(
                        DEFAULT_CONTEXT,
                        mContentResolver,
                        DatastoreManagerFactory.getDatastoreManager(DEFAULT_CONTEXT),
                        mSourceFetcher,
                        mTriggerFetcher,
                        mClickVerifier);
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setPackageName(
                                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setStart(Instant.ofEpochMilli(Long.MIN_VALUE))
                                .setEnd(Instant.ofEpochMilli(Long.MAX_VALUE))
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRange() {
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setPackageName(
                                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .setStart(Instant.now().minusSeconds(1))
                                .setEnd(Instant.now())
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithOrigin() {
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setPackageName(
                                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.singletonList(DEFAULT_URI))
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .setStart(Instant.ofEpochMilli(Long.MIN_VALUE))
                                .setEnd(Instant.ofEpochMilli(Long.MAX_VALUE))
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_internalError() {
        doReturn(false).when(mDatastoreManager).runInTransaction(any());
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder()
                                .setPackageName(
                                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                                .setDomainUris(Collections.emptyList())
                                .setOriginUris(Collections.emptyList())
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .setStart(Instant.MIN)
                                .setEnd(Instant.MAX)
                                .build());
        assertEquals(STATUS_INTERNAL_ERROR, result);
    }

    @Test
    public void testSourceRegistration_callsImpressionNoiseCreator() throws DatastoreException {
        long eventTime = System.currentTimeMillis();
        long expiry = TimeUnit.DAYS.toSeconds(20);
        // Creating source for easy comparison
        Source sampleSource =
                SourceFixture.getValidSourceBuilder()
                        .setSourceType(Source.SourceType.NAVIGATION)
                        .setExpiryTime(eventTime + TimeUnit.SECONDS.toMillis(expiry))
                        .setEventTime(eventTime)
                        .setPublisher(TOP_ORIGIN)
                        .setAppDestination(Uri.parse("android-app://com.example.abc"))
                        .setEventId(new UnsignedLong(123L))
                        .build();
        // Mocking fetchSource call to populate source registrations.
        List<SourceRegistration> sourceRegistrations =
                Collections.singletonList(
                        new SourceRegistration.Builder()
                                .setSourceEventId(sampleSource.getEventId())
                                .setAppDestination(sampleSource.getAppDestination())
                                .setExpiry(expiry)
                                .setEnrollmentId(DEFAULT_ENROLLMENT)
                                .build());
        doReturn(Optional.of(sourceRegistrations)).when(mSourceFetcher).fetchSource(any());

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));

        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                new FakeDatastoreManager(),
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        InputEvent inputEvent = getInputEvent();
        final int result =
                measurementImpl.register(
                        new RegistrationRequest.Builder()
                                .setRegistrationUri(REGISTRATION_URI_1)
                                .setPackageName(
                                        DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                                .setInputEvent(inputEvent)
                                .build(),
                        eventTime);
        assertEquals(STATUS_SUCCESS, result);
        ArgumentCaptor<Source> sourceArgs = ArgumentCaptor.forClass(Source.class);
        verify(measurementImpl).generateFakeEventReports(sourceArgs.capture());
        Source capturedSource = sourceArgs.getValue();
        assertEquals(sampleSource.getSourceType(), capturedSource.getSourceType());
        assertEquals(sampleSource.getEventId(), capturedSource.getEventId());
        assertEquals(sampleSource.getEventTime(), capturedSource.getEventTime());
        assertEquals(sampleSource.getAggregateSource(), capturedSource.getAggregateSource());
        assertEquals(sampleSource.getAppDestination(), capturedSource.getAppDestination());
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
                                .setEventId(new UnsignedLong(123L))
                                .setEventTime(eventTime)
                                .setExpiryTime(eventTime + TimeUnit.DAYS.toMillis(20))
                                .setSourceType(Source.SourceType.NAVIGATION)
                                .setAppDestination(DEFAULT_URI)
                                .setPublisher(TOP_ORIGIN)
                                .build());
        when(source.getRandomAttributionProbability()).thenReturn(1.1D);
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);

        List<EventReport> fakeEventReports = mMeasurementImpl.generateFakeEventReports(source);

        // Generate valid report times
        Set<Long> reportingTimes = new HashSet<>();
        reportingTimes.add(
                source.getReportingTime(
                        eventTime + TimeUnit.DAYS.toMillis(1), EventSurfaceType.APP));
        reportingTimes.add(
                source.getReportingTime(
                        eventTime + TimeUnit.DAYS.toMillis(3), EventSurfaceType.APP));
        reportingTimes.add(
                source.getReportingTime(
                        eventTime + TimeUnit.DAYS.toMillis(8), EventSurfaceType.APP));

        for (EventReport report : fakeEventReports) {
            Assert.assertEquals(source.getEventId(), report.getSourceId());
            Assert.assertTrue(reportingTimes.stream().anyMatch(x -> x == report.getReportTime()));
            Assert.assertEquals(source.getEventTime(), report.getTriggerTime());
            Assert.assertEquals(0, report.getTriggerPriority());
            Assert.assertEquals(source.getAppDestination(), report.getAttributionDestination());
            Long triggerData = report.getTriggerData().getValue();
            Assert.assertTrue(0 <= triggerData && triggerData
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

        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Execution
        mMeasurementImpl.doInstallAttribution(URI_WITHOUT_APP_SCHEME, systemTime);
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());

        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertion
        verify(mMeasurementDao).doInstallAttribution(DEFAULT_URI, systemTime);
    }

    @Test
    public void testGetMeasurementApiStatus_enabled() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(AdServicesApiConsent.GIVEN)
                    .when(mConsentManager)
                    .getConsent(any());
            ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));
            final int result = mMeasurementImpl.getMeasurementApiStatus();
            assertEquals(MeasurementManager.MEASUREMENT_API_STATE_ENABLED, result);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testGetMeasurementApiStatus_disabled() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(AdServicesApiConsent.REVOKED)
                    .when(mConsentManager)
                    .getConsent(any());
            ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));
            final int result = mMeasurementImpl.getMeasurementApiStatus();
            assertEquals(MeasurementManager.MEASUREMENT_API_STATE_DISABLED, result);
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testDeleteAllMeasurementData() throws DatastoreException {
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        // Execution
        mMeasurementImpl.deleteAllMeasurementData(Collections.emptyList());
        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());

        consumerArgumentCaptor.getValue().accept(mMeasurementDao);
        verify(mMeasurementDao, times(1)).deleteAllMeasurementData(any());
    }

    @Test
    public void testRegisterWebSource_sourceFetchSuccess()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest(APP_DESTINATION, WEB_DESTINATION, null);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), anyBoolean());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurementImpl).generateFakeEventReports(any());
        final int result = measurementImpl.registerWebSource(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
        verify(datastoreManager, times(2))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(4))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(4)).countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any(), anyBoolean());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_1,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void registerWebSource_sourceFetchSuccess_withoutAdIdPermission()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequestWithoutAdIdPermission(
                        APP_DESTINATION, WEB_DESTINATION, null);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), eq(false));
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong()))
                .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurementImpl).generateFakeEventReports(any());
        final int result = measurementImpl.registerWebSource(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
        verify(datastoreManager, times(2)).runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(4))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(4))
                .countDistinctEnrollmentsPerPublisherXDestinationInSource(
                        any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any(), anyBoolean());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(2, insertionLogicExecutor.size());

        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_1,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void testRegisterWebSource_sourceFetchFailure() {
        when(mSourceFetcher.fetchWebSources(any(), anyBoolean())).thenReturn(Optional.empty());

        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(mMeasurementImpl).generateFakeEventReports(any());
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(APP_DESTINATION, WEB_DESTINATION, null),
                        System.currentTimeMillis());
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_invalidWebDestination() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(null, INVALID_WEB_DESTINATION, null),
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
        verify(mSourceFetcher, never()).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_exceedsPrivacyParam_destination()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest(APP_DESTINATION, WEB_DESTINATION, null);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), anyBoolean());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(100));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).generateFakeEventReports(any());
        final int result = measurement.registerWebSource(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
        verify(datastoreManager, never())
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(2))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, never()).countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_exceedsPrivacyParam_adTech()
            throws RemoteException, DatastoreException {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest(APP_DESTINATION, WEB_DESTINATION, null);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), anyBoolean());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);

        when(mMeasurementDao.countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(0));
        when(mMeasurementDao.countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong()))
                        .thenReturn(Integer.valueOf(100))
                        .thenReturn(Integer.valueOf(0));
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());

        // Test
        MeasurementImpl measurement =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));

        long eventTime = System.currentTimeMillis();
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurement).generateFakeEventReports(any());
        final int result = measurement.registerWebSource(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
        verify(datastoreManager, times(1))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        verify(mMeasurementDao, times(4))
                .countDistinctDestinationsPerPublisherXEnrollmentInActiveSource(
                        any(), anyInt(), any(), any(), anyInt(), anyLong(), anyLong());
        verify(mMeasurementDao, times(3)).countDistinctEnrollmentsPerPublisherXDestinationInSource(
                any(), anyInt(), any(), any(), anyLong(), anyLong());
        verify(mTriggerFetcher, never()).fetchWebTriggers(any(), anyBoolean());

        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(1, insertionLogicExecutor.size());

        // First registration was removed for exceeding the privacy bound.
        verifyInsertSource(
                registrationRequest,
                VALID_SOURCE_REGISTRATION_2,
                eventTime,
                VALID_SOURCE_REGISTRATION_1.getAppDestination(),
                VALID_SOURCE_REGISTRATION_1.getWebDestination());
    }

    @Test
    public void testRegisterWebSource_webDestinationIsValidWhenNull() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(APP_DESTINATION, null, null),
                        System.currentTimeMillis());
        // STATUS_IO_ERROR is expected when fetchSource returns Optional.empty();
        // it means validation passed and the procedure called the fetcher.
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_exactWebDestinationMatch() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, WEB_DESTINATION),
                        System.currentTimeMillis());
        // STATUS_IO_ERROR is expected when fetchSource returns Optional.empty();
        // it means validation passed and the procedure called the fetcher.
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testrRegisterWebSource_verifiedDestination_topPrivateDomainMatch() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, WEB_DESTINATION_WITH_SUBDOMAIN),
                        System.currentTimeMillis());
        // STATUS_IO_ERROR is expected when fetchSource returns Optional.empty();
        // it means validation passed and the procedure called the fetcher.
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_webDestinationMismatch() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, OTHER_WEB_DESTINATION),
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
        verify(mSourceFetcher, times(0)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_appDestinationMatch() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, APP_DESTINATION),
                        System.currentTimeMillis());
        // STATUS_IO_ERROR is expected when fetchSource returns Optional.empty();
        // it means validation passed and the procedure called the fetcher.
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_appDestinationMismatch() {
        when(mSourceFetcher.fetchSource(any())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, OTHER_APP_DESTINATION),
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
        verify(mSourceFetcher, times(0)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_vendingMatch() {
        when(mSourceFetcher.fetchWebSources(any(), anyBoolean())).thenReturn(Optional.empty());
        Uri vendingUri = Uri.parse(VENDING_PREFIX + APP_DESTINATION.getHost());
        MeasurementImpl measurementImpl = getMeasurementImplWithMockedIntentResolution();
        final int result =
                measurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, vendingUri),
                        System.currentTimeMillis());
        // STATUS_IO_ERROR is expected when fetchSource returns Optional.empty();
        // it means validation passed and the procedure called the fetcher.
        assertEquals(STATUS_IO_ERROR, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_vendingMismatch() {
        when(mSourceFetcher.fetchWebSources(any(), anyBoolean())).thenReturn(Optional.empty());
        Uri vendingUri = Uri.parse(VENDING_PREFIX + OTHER_APP_DESTINATION.getHost());
        MeasurementImpl measurementImpl = getMeasurementImplWithMockedIntentResolution();
        final int result =
                measurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, vendingUri),
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
        verify(mSourceFetcher, times(0)).fetchWebSources(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebSource_exceedsMaxSourcesLimit() throws DatastoreException {
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), anyBoolean());
        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest(APP_DESTINATION, WEB_DESTINATION, null);
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));
        long eventTime = System.currentTimeMillis();

        final int result = measurementImpl.registerWebSource(registrationRequest, eventTime);
        assertEquals(STATUS_SUCCESS, result);
        verify(mMeasurementDao, never()).insertSource(any());
        verify(datastoreManager, never()).runInTransaction(insertionLogicExecutorCaptor.capture());
    }

    @Test
    public void testRegisterWebSource_partiallyInsertSources() throws DatastoreException {
        // Setup
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());
        int remainingBudget = 2;
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(
                        VALID_SOURCE_REGISTRATION_1,
                        VALID_SOURCE_REGISTRATION_2,
                        VALID_SOURCE_REGISTRATION_3);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), anyBoolean());
        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER - remainingBudget)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(any(), anyInt());
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest(APP_DESTINATION, WEB_DESTINATION, null);
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));
        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(measurementImpl).generateFakeEventReports(any());
        long eventTime = System.currentTimeMillis();
        // Test
        final int result = measurementImpl.registerWebSource(registrationRequest, eventTime);
        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        verify(mSourceFetcher, times(1)).fetchWebSources(any(), anyBoolean());
        verify(datastoreManager, times(remainingBudget))
                .runInTransaction(insertionLogicExecutorCaptor.capture());
        List<ThrowingCheckedConsumer> insertionLogicExecutor =
                insertionLogicExecutorCaptor.getAllValues();
        assertEquals(remainingBudget, insertionLogicExecutor.size());
        assertTrue(remainingBudget < sourceRegistrationsOut.size());
    }

    @Test
    public void testRegisterWebSource_LimitsMaxSources_ForWebPublisher_WitheTLDMatch()
            throws DatastoreException {
        // Setup
        DatastoreManager datastoreManager = spy(new FakeDatastoreManager());
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1);
        doReturn(Optional.of(sourceRegistrationsOut))
                .when(mSourceFetcher)
                .fetchWebSources(any(), anyBoolean());
        Uri publisherUri = Uri.parse("https://store.example.com");
        Uri topLevelUri = Uri.parse("https://example.com");
        doReturn(SystemHealthParams.MAX_SOURCES_PER_PUBLISHER)
                .when(mMeasurementDao)
                .getNumSourcesPerPublisher(eq(topLevelUri), eq(EventSurfaceType.WEB));
        ArgumentCaptor<ThrowingCheckedConsumer> insertionLogicExecutorCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        WebSourceRegistrationRequestInternal registrationRequest =
                createWebSourceRegistrationRequest(
                        APP_DESTINATION, WEB_DESTINATION, null, publisherUri);
        MeasurementImpl measurementImpl =
                spy(
                        new MeasurementImpl(
                                null,
                                mContentResolver,
                                datastoreManager,
                                mSourceFetcher,
                                mTriggerFetcher,
                                mClickVerifier));
        long eventTime = System.currentTimeMillis();
        // Test
        final int result = measurementImpl.registerWebSource(registrationRequest, eventTime);
        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        assertEquals(STATUS_SUCCESS, result);
        verify(mMeasurementDao, never()).insertSource(any());
        verify(datastoreManager, never()).runInTransaction(insertionLogicExecutorCaptor.capture());
    }

    @Test
    public void testRegisterWebTrigger_triggerFetchSuccess() throws Exception {
        // Setup
        when(mTriggerFetcher.fetchWebTriggers(any(), anyBoolean()))
                .thenReturn(Optional.of(Collections.singletonList(VALID_TRIGGER_REGISTRATION)));

        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        final long triggerTime = System.currentTimeMillis();

        // Execution
        final int result =
                mMeasurementImpl.registerWebTrigger(
                        createWebTriggerRegistrationRequest(WEB_DESTINATION), triggerTime);

        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchWebTriggers(any(), anyBoolean());
        verify(mMeasurementDao)
                .insertTrigger(
                        eq(
                                createTrigger(
                                        triggerTime,
                                        DEFAULT_CONTEXT.getAttributionSource(),
                                        WEB_DESTINATION,
                                        EventSurfaceType.WEB)));
    }

    @Test
    public void registerWebTrigger_triggerFetchSuccess_withoutAdIdPermission() throws Exception {
        // Setup
        when(mTriggerFetcher.fetchWebTriggers(any(), eq(false)))
                .thenReturn(Optional.of(Collections.singletonList(VALID_TRIGGER_REGISTRATION)));

        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgumentCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        final long triggerTime = System.currentTimeMillis();

        // Execution
        final int result =
                mMeasurementImpl.registerWebTrigger(
                        createWebTriggerRegistrationRequestWithoutAdIdPermission(WEB_DESTINATION),
                        triggerTime);

        verify(mDatastoreManager).runInTransaction(consumerArgumentCaptor.capture());
        consumerArgumentCaptor.getValue().accept(mMeasurementDao);

        // Assertions
        assertEquals(STATUS_SUCCESS, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mSourceFetcher, never()).fetchSource(any());
        verify(mTriggerFetcher, times(1)).fetchWebTriggers(any(), eq(false));
        verify(mMeasurementDao)
                .insertTrigger(
                        eq(
                                createTrigger(
                                        triggerTime,
                                        DEFAULT_CONTEXT.getAttributionSource(),
                                        WEB_DESTINATION,
                                        EventSurfaceType.WEB)));
    }

    @Test
    public void testRegisterWebTrigger_triggerFetchFailure() throws RemoteException {
        when(mTriggerFetcher.fetchWebTriggers(any(), anyBoolean())).thenReturn(Optional.empty());

        final int result =
                mMeasurementImpl.registerWebTrigger(
                        createWebTriggerRegistrationRequest(WEB_DESTINATION),
                        System.currentTimeMillis());

        assertEquals(STATUS_IO_ERROR, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mSourceFetcher, never()).fetchWebSources(any(), anyBoolean());
        verify(mTriggerFetcher, times(1)).fetchWebTriggers(any(), anyBoolean());
    }

    @Test
    public void testRegisterWebTrigger_invalidDestination() throws RemoteException {
        final int result =
                mMeasurementImpl.registerWebTrigger(
                        createWebTriggerRegistrationRequest(INVALID_WEB_DESTINATION),
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
        verify(mTriggerFetcher, never()).fetchWebTriggers(any(), anyBoolean());
    }

    @Test
    public void testRegister_registrationTypeSource_clickNotVerifiedFailure() {
        // setup
        List<SourceRegistration> sourceRegistrationsOut =
                Arrays.asList(VALID_SOURCE_REGISTRATION_1, VALID_SOURCE_REGISTRATION_2);
        doReturn(Optional.of(sourceRegistrationsOut)).when(mSourceFetcher).fetchSource(any());

        // Disable Impression Noise
        doReturn(Collections.emptyList()).when(mMeasurementImpl).generateFakeEventReports(any());

        doReturn(false).when(mClickVerifier).isInputEventVerifiable(any(), anyLong());

        RegistrationRequest registrationRequest =
                new RegistrationRequest.Builder()
                        .setRegistrationUri(REGISTRATION_URI_1)
                        .setPackageName(DEFAULT_CONTEXT.getAttributionSource().getPackageName())
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .setInputEvent(getInputEvent())
                        .build();

        long eventTime = System.currentTimeMillis();
        final int result = mMeasurementImpl.register(registrationRequest, eventTime);

        // Assert
        assertEquals(STATUS_SUCCESS, result);
        verify(mSourceFetcher, times(1)).fetchSource(any());
    }

    @Test
    public void testGetSourceType_verifiedInputEvent_returnsNavigationSourceType() {
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong());
        assertEquals(
                Source.SourceType.NAVIGATION,
                mMeasurementImpl.getSourceType(getInputEvent(), 1000L));
    }

    @Test
    public void testGetSourceType_noInputEventGiven() {
        assertEquals(Source.SourceType.EVENT, mMeasurementImpl.getSourceType(null, 1000L));
    }

    @Test
    public void testGetSourceType_inputEventNotVerifiable_returnsEventSourceType() {
        doReturn(false).when(mClickVerifier).isInputEventVerifiable(any(), anyLong());
        assertEquals(
                Source.SourceType.EVENT, mMeasurementImpl.getSourceType(getInputEvent(), 1000L));
    }

    @Test
    public void testGetSourceType_clickVerificationDisabled_returnsNavigationSourceType() {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        try {
            Flags mockFlags = Mockito.mock(Flags.class);
            ClickVerifier mockClickVerifier = Mockito.mock(ClickVerifier.class);
            doReturn(false).when(mockClickVerifier).isInputEventVerifiable(any(), anyLong());
            doReturn(false).when(mockFlags).getMeasurementIsClickVerificationEnabled();
            ExtendedMockito.doReturn(mockFlags).when(() -> FlagsFactory.getFlagsForTest());
            MeasurementImpl measurementImpl =
                    new MeasurementImpl(
                            DEFAULT_CONTEXT,
                            mContentResolver,
                            mDatastoreManager,
                            mSourceFetcher,
                            mTriggerFetcher,
                            mockClickVerifier);

            // Because click verification is disabled, the SourceType is NAVIGATION even if the
            // input event is not verifiable.
            assertEquals(
                    Source.SourceType.NAVIGATION,
                    measurementImpl.getSourceType(getInputEvent(), 1000L));
        } catch (Exception e) {
            Assert.fail();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void insertSource_withFakeReportsFalseAppAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(0L))
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION);
        MeasurementImpl measurementImpl =
                new MeasurementImpl(
                        null,
                        mContentResolver,
                        mDatastoreManager,
                        mSourceFetcher,
                        mTriggerFetcher,
                        mClickVerifier);
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        measurementImpl.insertSource(source);

        // Assertion
        verify(mDatastoreManager).runInTransaction(consumerArgCaptor.capture());

        consumerArgCaptor.getValue().accept(mMeasurementDao);

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
                                .setEventId(new UnsignedLong(0L))
                                .setAppDestination(null)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source, fakeReportsCount, SourceFixture.ValidSourceParams.WEB_DESTINATION);
        MeasurementImpl measurementImpl =
                new MeasurementImpl(
                        null,
                        mContentResolver,
                        mDatastoreManager,
                        mSourceFetcher,
                        mTriggerFetcher,
                        mClickVerifier);
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        measurementImpl.insertSource(source);

        // Assertion
        verify(mDatastoreManager).runInTransaction(consumerArgCaptor.capture());

        consumerArgCaptor.getValue().accept(mMeasurementDao);

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

    @Test
    public void insertSource_withFalseAppAndWebAttribution_accountsForFakeReportAttribution()
            throws DatastoreException {
        // Setup
        int fakeReportsCount = 2;
        Source source =
                spy(
                        SourceFixture.getValidSourceBuilder()
                                .setEventId(new UnsignedLong(0L))
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(SourceFixture.ValidSourceParams.WEB_DESTINATION)
                                .build());
        List<Source.FakeReport> fakeReports =
                createFakeReports(
                        source,
                        fakeReportsCount,
                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION);
        MeasurementImpl measurementImpl =
                new MeasurementImpl(
                        null,
                        mContentResolver,
                        mDatastoreManager,
                        mSourceFetcher,
                        mTriggerFetcher,
                        mClickVerifier);
        Answer<?> falseAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.FALSELY);
                    return fakeReports;
                };
        doAnswer(falseAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        measurementImpl.insertSource(source);

        // Assertion
        verify(mDatastoreManager).runInTransaction(consumerArgCaptor.capture());

        consumerArgCaptor.getValue().accept(mMeasurementDao);

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
                                .setEventId(new UnsignedLong(0L))
                                .setAppDestination(
                                        SourceFixture.ValidSourceParams.ATTRIBUTION_DESTINATION)
                                .setWebDestination(null)
                                .build());
        List<Source.FakeReport> fakeReports = Collections.emptyList();
        MeasurementImpl measurementImpl =
                new MeasurementImpl(
                        null,
                        mContentResolver,
                        mDatastoreManager,
                        mSourceFetcher,
                        mTriggerFetcher,
                        mClickVerifier);
        Answer<?> neverAttributionAnswer =
                (arg) -> {
                    source.setAttributionMode(Source.AttributionMode.NEVER);
                    return fakeReports;
                };
        doAnswer(neverAttributionAnswer).when(source).assignAttributionModeAndGenerateFakeReports();
        ArgumentCaptor<ThrowingCheckedConsumer> consumerArgCaptor =
                ArgumentCaptor.forClass(ThrowingCheckedConsumer.class);
        ArgumentCaptor<Attribution> attributionRateLimitArgCaptor =
                ArgumentCaptor.forClass(Attribution.class);

        // Execution
        measurementImpl.insertSource(source);

        // Assertion
        verify(mDatastoreManager).runInTransaction(consumerArgCaptor.capture());

        consumerArgCaptor.getValue().accept(mMeasurementDao);

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
                        Uri.parse(ANDROID_APP_SCHEME + registrationRequest.getPackageName()),
                        EventSurfaceType.APP,
                        registrationRequest.getPackageName());
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
                        EventSurfaceType.WEB,
                        registrationRequest.getPackageName());
        verify(mMeasurementDao).insertSource(source);
    }

    private Source createSource(
            SourceRegistration sourceRegistration,
            long eventTime,
            Uri firstSourceDestination,
            Uri firstSourceWebDestination,
            Uri topOrigin,
            @EventSurfaceType int publisherType,
            String packageName) {
        return SourceFixture.getValidSourceBuilder()
                .setEventId(sourceRegistration.getSourceEventId())
                .setPublisher(topOrigin)
                .setPublisherType(publisherType)
                .setAppDestination(firstSourceDestination)
                .setWebDestination(firstSourceWebDestination)
                .setEnrollmentId(DEFAULT_ENROLLMENT)
                .setRegistrant(Uri.parse("android-app://" + packageName))
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
                .setDebugKey(sourceRegistration.getDebugKey())
                .build();
    }

    private Trigger createTrigger(long triggerTime, AttributionSource attributionSource,
            Uri destination, @EventSurfaceType int destinationType) {
        return TriggerFixture.getValidTriggerBuilder()
                .setAttributionDestination(destination)
                .setDestinationType(destinationType)
                .setEnrollmentId(DEFAULT_ENROLLMENT)
                .setRegistrant(Uri.parse(ANDROID_APP_SCHEME + attributionSource.getPackageName()))
                .setTriggerTime(triggerTime)
                .setEventTriggers(EVENT_TRIGGERS)
                .setAggregateTriggerData(
                        MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getAggregateTriggerData())
                .setAggregateValues(
                        MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getAggregateValues())
                .setFilters(MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getFilters())
                .setDebugKey(MeasurementImplTest.VALID_TRIGGER_REGISTRATION.getDebugKey())
                .build();
    }

    private MeasurementImpl getMeasurementImplWithMockedIntentResolution() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = AppVendorPackages.PLAY_STORE;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = applicationInfo;
        activityInfo.name = "non null String";
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;
        MockPackageManager mockPackageManager =
                new MockPackageManager() {
                    @Override
                    public ResolveInfo resolveActivity(Intent intent, int flags) {
                        return resolveInfo;
                    }
                };
        MockContext mockContext =
                new MockContext() {
                    @Override
                    public PackageManager getPackageManager() {
                        return mockPackageManager;
                    }
                };
        return new MeasurementImpl(
                mockContext,
                mContentResolver,
                mDatastoreManager,
                mSourceFetcher,
                mTriggerFetcher,
                mClickVerifier);
    }
}
