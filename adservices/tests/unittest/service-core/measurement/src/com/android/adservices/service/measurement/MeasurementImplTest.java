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

import static com.android.compatibility.common.util.VersionCodes.S_V2;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequestInternal;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.annotation.NonNull;
import android.app.adservices.AdServicesManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.view.InputEvent;
import android.view.MotionEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.WebUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.SQLDatastoreManager;
import com.android.adservices.data.measurement.deletion.MeasurementDataDeleter;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.measurement.attribution.TriggerContentProvider;
import com.android.adservices.service.measurement.inputverification.ClickVerifier;
import com.android.adservices.service.measurement.registration.AsyncRegistrationContentProvider;
import com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementWipeoutStats;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Unit tests for {@link MeasurementImpl} */
@SpyStatic(AdServicesManager.class)
@SpyStatic(MeasurementRollbackCompatManager.class)
@SpyStatic(FakeFlagsFactory.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AdServicesLoggerImpl.class)
public final class MeasurementImplTest extends AdServicesExtendedMockitoTestCase {
    private static final Context DEFAULT_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri DEFAULT_URI = Uri.parse("android-app://com.example.abc");
    private static final Uri REGISTRATION_URI_1 = WebUtil.validUri("https://foo.test/bar?ad=134");
    private static final Uri REGISTRATION_URI_2 = WebUtil.validUri("https://foo.test/bar?ad=256");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final String DEFAULT_ENROLLMENT = "enrollment-id";
    private static final Uri INVALID_WEB_DESTINATION = Uri.parse("https://example.not_a_tld");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("https://web-destination.test");
    private static final Uri OTHER_WEB_DESTINATION =
            WebUtil.validUri("https://other-web-destination.test");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app_destination");
    private static final Uri OTHER_APP_DESTINATION =
            Uri.parse("android-app://com.other_app_destination");
    private static final boolean DEFAULT_AD_ID_PERMISSION = false;

    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebSourceParams INPUT_SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();
    private static final WebTriggerParams INPUT_TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();
    private static final long REQUEST_TIME = 10000L;
    private static final String POST_BODY = "{\"ad_location\":\"bottom_right\"}";
    private static final String AD_ID_VALUE = "ad_id_value";

    @Mock private AdServicesErrorLogger mErrorLogger;
    @Mock private ContentProviderClient mMockContentProviderClient;
    @Mock private ContentResolver mContentResolver;
    @Mock private ClickVerifier mClickVerifier;
    @Mock EnrollmentDao mEnrollmentDao;
    @Mock MeasurementDataDeleter mMeasurementDataDeleter;

    @Spy
    private DatastoreManager mDatastoreManager =
            new SQLDatastoreManager(DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger);

    private MeasurementImpl mMeasurementImpl;

    private static EnrollmentData getEnrollment(String enrollmentId) {
        return new EnrollmentData.Builder().setEnrollmentId(enrollmentId).build();
    }

    public static InputEvent getInputEvent() {
        return MotionEvent.obtain(0, 0, ACTION_BUTTON_PRESS, 0, 0, 0);
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
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME)
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
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        REQUEST_TIME)
                .build();
    }

    private static SourceRegistrationRequestInternal createSourceRegistrationRequest() {
        SourceRegistrationRequest request =
                new SourceRegistrationRequest.Builder(
                                Arrays.asList(REGISTRATION_URI_1, REGISTRATION_URI_2))
                        .build();
        return new SourceRegistrationRequestInternal.Builder(
                        request, DEFAULT_CONTEXT.getPackageName(), SDK_PACKAGE_NAME, REQUEST_TIME)
                .build();
    }

    @Before
    public void before() throws Exception {
        Uri triggerUri = TriggerContentProvider.getTriggerUri();
        Uri asyncRegistrationTriggerUri = AsyncRegistrationContentProvider.getTriggerUri();
        when(mContentResolver.acquireContentProviderClient(triggerUri))
                .thenReturn(mMockContentProviderClient);
        when(mContentResolver.acquireContentProviderClient(asyncRegistrationTriggerUri))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(eq(triggerUri), any())).thenReturn(triggerUri);
        when(mMockContentProviderClient.insert(eq(asyncRegistrationTriggerUri), any()))
                .thenReturn(asyncRegistrationTriggerUri);
        mMeasurementImpl =
                spy(
                        new MeasurementImpl(
                                DEFAULT_CONTEXT,
                                FakeFlagsFactory.getFlagsForTest(),
                                mDatastoreManager,
                                mClickVerifier,
                                mMeasurementDataDeleter,
                                mContentResolver));
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        when(mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(any()))
                .thenReturn(getEnrollment(DEFAULT_ENROLLMENT));
    }

    @Test
    public void testDeleteRegistrations_successfulNoOptionalParameters() {
        disableRollbackDeletion();

        MeasurementImpl measurement =
                new MeasurementImpl(
                        DEFAULT_CONTEXT,
                        FakeFlagsFactory.getFlagsForTest(),
                        new SQLDatastoreManager(
                                DbTestUtil.getMeasurementDbHelperForTest(), mErrorLogger),
                        mClickVerifier,
                        mMeasurementDataDeleter,
                        mContentResolver);
        doReturn(true).when(mMeasurementDataDeleter).delete(any());
        final int result =
                measurement.deleteRegistrations(
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.ofEpochMilli(Long.MIN_VALUE),
                                        Instant.ofEpochMilli(Long.MAX_VALUE),
                                        DEFAULT_CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_webDestinationMismatch() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, OTHER_WEB_DESTINATION),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRange() {
        disableRollbackDeletion();

        doReturn(true).when(mMeasurementDataDeleter).delete(any());
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.now().minusSeconds(1),
                                        Instant.now(),
                                        DEFAULT_CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .build());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithOrigin() {
        disableRollbackDeletion();

        DeletionParam deletionParam =
                new DeletionParam.Builder(
                                Collections.singletonList(DEFAULT_URI),
                                Collections.emptyList(),
                                Instant.ofEpochMilli(Long.MIN_VALUE),
                                Instant.ofEpochMilli(Long.MAX_VALUE),
                                DEFAULT_CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                        .build();
        when(mMeasurementDataDeleter.delete(deletionParam)).thenReturn(true);
        final int result = mMeasurementImpl.deleteRegistrations(deletionParam);
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testDeleteRegistrations_internalError() {
        doReturn(false).when(mDatastoreManager).runInTransaction(any());
        doReturn(false).when(mMeasurementDataDeleter).delete(any());
        final int result =
                mMeasurementImpl.deleteRegistrations(
                        new DeletionParam.Builder(
                                        Collections.emptyList(),
                                        Collections.emptyList(),
                                        Instant.MIN,
                                        Instant.MAX,
                                        DEFAULT_CONTEXT.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                                .build());
        assertEquals(STATUS_INTERNAL_ERROR, result);
    }

    @Test
    public void testRegisterWebSource_invalidWebDestination() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(null, INVALID_WEB_DESTINATION, null),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testRegisterWebTrigger_invalidDestination() {
        final int result =
                mMeasurementImpl.registerWebTrigger(
                        createWebTriggerRegistrationRequest(INVALID_WEB_DESTINATION),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testRegisterWebSource_verifiedDestination_appDestinationMismatch() {
        final int result =
                mMeasurementImpl.registerWebSource(
                        createWebSourceRegistrationRequest(
                                APP_DESTINATION, WEB_DESTINATION, OTHER_APP_DESTINATION),
                        DEFAULT_AD_ID_PERMISSION,
                        System.currentTimeMillis());
        assertEquals(STATUS_INVALID_ARGUMENT, result);
    }

    @Test
    public void testRegisterEvent_noOptionalParameters_success() {
        final int result =
                mMeasurementImpl.registerEvent(
                        REGISTRATION_URI_1,
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        false,
                        null,
                        null,
                        null);
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testRegisterEvent_optionalParameters_success() {
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        final int result =
                mMeasurementImpl.registerEvent(
                        REGISTRATION_URI_1,
                        DEFAULT_CONTEXT.getPackageName(),
                        SDK_PACKAGE_NAME,
                        false,
                        POST_BODY,
                        getInputEvent(),
                        AD_ID_VALUE);
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testGetSourceType_verifiedInputEvent_returnsNavigationSourceType() {
        doReturn(true).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        assertEquals(
                Source.SourceType.NAVIGATION,
                mMeasurementImpl.getSourceType(getInputEvent(), 1000L, "app_name"));
    }

    @Test
    public void testGetSourceType_noInputEventGiven() {
        assertEquals(
                Source.SourceType.EVENT, mMeasurementImpl.getSourceType(null, 1000L, "app_name"));
    }

    @Test
    public void testGetSourceType_inputEventNotVerifiable_returnsEventSourceType() {
        doReturn(false).when(mClickVerifier).isInputEventVerifiable(any(), anyLong(), anyString());
        assertEquals(
                Source.SourceType.EVENT,
                mMeasurementImpl.getSourceType(getInputEvent(), 1000L, "app_name"));
    }

    @Test
    public void testGetSourceType_clickVerificationDisabled_returnsNavigationSourceType() {
        ClickVerifier mockClickVerifier = Mockito.mock(ClickVerifier.class);
        doReturn(false)
                .when(mockClickVerifier)
                .isInputEventVerifiable(any(), anyLong(), anyString());
        doReturn(false).when(mMockFlags).getMeasurementIsClickVerificationEnabled();
        ExtendedMockito.doReturn(mMockFlags).when(FakeFlagsFactory::getFlagsForTest);
        MeasurementImpl measurementImpl =
                new MeasurementImpl(
                        DEFAULT_CONTEXT,
                        FakeFlagsFactory.getFlagsForTest(),
                        mDatastoreManager,
                        mockClickVerifier,
                        mMeasurementDataDeleter,
                        mContentResolver);

        // Because click verification is disabled, the SourceType is NAVIGATION even if the
        // input event is not verifiable.
        assertEquals(
                Source.SourceType.NAVIGATION,
                measurementImpl.getSourceType(getInputEvent(), 1000L, "app_name"));
    }

    @Test
    @RequiresSdkLevelAtLeastT()
    public void testDeleteRegistrations_success_recordsDeletionInSystemServer() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doDeleteRegistrations();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    @RequiresSdkRange(atMost = S_V2)
    public void testDeleteRegistrations_success_recordsDeletion_S() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager = doDeleteRegistrationsCompat();

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeleteRegistrations_success_recordsDeletionInSystemServer_flagOff() {
        doReturn(true).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doDeleteRegistrations();

        Mockito.verify(mockAdServicesManager, Mockito.never())
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testDeleteRegistrations_success_recordsDeletionInAppSearch_flagOff_S() {
        mocker.mockSdkLevelS();

        doReturn(true).when(mMockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        MeasurementRollbackCompatManager mockRollbackManager = doDeleteRegistrationsCompat();

        Mockito.verify(mockRollbackManager, Mockito.never()).recordAdServicesDeletionOccurred();
    }

    private MeasurementRollbackCompatManager doDeleteRegistrationsCompat() {
        mocker.mockGetFlags(mMockFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doDeleteRegistrations();
        return mockRollbackManager;
    }

    private void doDeleteRegistrations() {
        MeasurementImpl measurement = createMeasurementImpl();
        doReturn(true).when(mMeasurementDataDeleter).delete(any());
        measurement.deleteRegistrations(
                new DeletionParam.Builder(
                                Collections.emptyList(),
                                Collections.emptyList(),
                                Instant.ofEpochMilli(Long.MIN_VALUE),
                                Instant.ofEpochMilli(Long.MAX_VALUE),
                                DEFAULT_CONTEXT.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build());
    }

    @Test
    @RequiresSdkLevelAtLeastT()
    public void testDeletePackageRecords_success_recordsDeletionInSystemServer_T() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(Collections.singletonList(DEFAULT_URI)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        doDeletePackageRecords();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testDeletePackageRecords_success_recordsDeletionInSystemServer_S() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);
        mocker.mockSdkLevelS();

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doReturn(Optional.of(Collections.singletonList(DEFAULT_URI)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        doDeletePackageRecords();

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    @RequiresSdkRange(atMost = S_V2)
    public void testDeletePackageRecords_success_recordsDeletion_S() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager = doDeletePackageRecordsCompat();

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    public void testDeletePackageRecords_noDeletion_doesNotRecordDeletion() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(false)).when(mDatastoreManager).runInTransactionWithResult(any());
        doReturn(true).when(mDatastoreManager).runInTransaction(any());

        doDeletePackageRecords();

        Mockito.verify(mockAdServicesManager, Mockito.never())
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    private MeasurementRollbackCompatManager doDeletePackageRecordsCompat() {
        mocker.mockGetFlags(mMockFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        doDeletePackageRecords();
        return mockRollbackManager;
    }

    private void doDeletePackageRecords() {
        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deletePackageRecords(DEFAULT_URI, 0);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testDeleteAllMeasurementData_success_recordsDeletionInSystemServer() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doDeleteAllMeasurementData();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    @RequiresSdkRange(atMost = S_V2)
    public void testDeleteAllMeasurementData_success_recordsDeletion_S() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager = doDeleteAllMeasurementDataCompat();

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    private MeasurementRollbackCompatManager doDeleteAllMeasurementDataCompat() {
        mocker.mockGetFlags(mMockFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doDeleteAllMeasurementData();
        return mockRollbackManager;
    }

    private void doDeleteAllMeasurementData() {
        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllMeasurementData(Collections.EMPTY_LIST);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testDeleteAllUninstalledMeasurementData_success_recordsDeletionInSystemServer_T() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(Collections.singletonList(DEFAULT_URI)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();

        Mockito.verify(mockAdServicesManager)
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testDeleteAllUninstalledMeasurementData_success_recordsDeletionInSystemServer_S() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);
        mocker.mockSdkLevelS();

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));

        doReturn(Optional.of(Collections.singletonList(DEFAULT_URI)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    @Test
    @RequiresSdkRange(atMost = S_V2)
    public void testDeleteAllUninstalledMeasurementData_success_recordsDeletion_S() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();

        MeasurementRollbackCompatManager mockRollbackManager =
                doDeleteAllUninstalledMeasurementDataCompat();

        Mockito.verify(mockRollbackManager).recordAdServicesDeletionOccurred();
    }

    private MeasurementRollbackCompatManager doDeleteAllUninstalledMeasurementDataCompat() {
        mocker.mockGetFlags(mMockFlags);

        MeasurementRollbackCompatManager mockRollbackManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockRollbackManager)
                .when(
                        () ->
                                MeasurementRollbackCompatManager.getInstance(
                                        any(), eq(AdServicesManager.MEASUREMENT_DELETION)));
        doReturn(Optional.of(List.of(DEFAULT_URI)))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(true).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();
        return mockRollbackManager;
    }

    @Test
    public void testDeleteAllUninstalledMeasurementData_noDeletion_doesNotRecordDeletion() {
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        AdServicesManager mockAdServicesManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockAdServicesManager)
                .when(() -> AdServicesManager.getInstance(any()));

        doReturn(Optional.of(List.of(Uri.parse("android-app://foo"))))
                .when(mDatastoreManager)
                .runInTransactionWithResult(any());
        doReturn(false).when(mMeasurementDataDeleter).deleteAppUninstalledData(any(), anyLong());

        MeasurementImpl measurement = createMeasurementImpl();
        measurement.deleteAllUninstalledMeasurementData();

        Mockito.verify(mockAdServicesManager, Mockito.never())
                .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testCheckIfNeedsToHandleReconciliation_S() {
        mocker.mockSdkLevelS();

        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        checkIfNeedsToHandleReconciliationCompat(false, true);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testCheckIfNeedsToHandleReconciliation_R() {
        mocker.mockIsAtLeastS(false);

        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        checkIfNeedsToHandleReconciliationCompat(false, false);
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testCheckIfNeedsToHandleReconciliation_clearsData_S() {
        mocker.mockSdkLevelS();

        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        checkIfNeedsToHandleReconciliationCompat(true, true);
    }

    private void checkIfNeedsToHandleReconciliationCompat(
            boolean returnValue, boolean callsManager) {
        mocker.mockGetFlags(mMockFlags);

        MeasurementRollbackCompatManager mockManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);
        ExtendedMockito.doReturn(mockManager)
                .when(() -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()));

        doReturn(returnValue).when(mockManager).needsToHandleRollbackReconciliation();
        MeasurementImpl measurement = createMeasurementImpl();
        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isEqualTo(returnValue);

        if (callsManager) {
            Mockito.verify(mockManager).needsToHandleRollbackReconciliation();
        }
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testCheckIfNeedsToHandleReconciliation_flagOff_S() {
        mocker.mockSdkLevelS();

        doReturn(true).when(mMockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        MeasurementImpl measurement = createMeasurementImpl();
        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isFalse();
        ExtendedMockito.verify(
                () -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()), never());
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testCheckIfNeedsToHandleReconciliation_flagOff_R() {
        mocker.mockIsAtLeastS(false);

        doReturn(true).when(mMockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();
        mocker.mockGetFlags(mMockFlags);

        MeasurementImpl measurement = createMeasurementImpl();
        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isFalse();
        ExtendedMockito.verify(
                () -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()), never());
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testCheckIfNeedsToHandleReconciliation_TPlus() {
        mocker.mockIsAtLeastT(true);

        AdServicesManager mockManager = Mockito.mock(AdServicesManager.class);
        ExtendedMockito.doReturn(mockManager).when(() -> AdServicesManager.getInstance(any()));

        doReturn(true).when(mockManager).needsToHandleRollbackReconciliation(anyInt());

        MeasurementImpl measurement = createMeasurementImpl();

        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isTrue();
        Mockito.verify(mockManager)
                .needsToHandleRollbackReconciliation(eq(AdServicesManager.MEASUREMENT_DELETION));

        // Verify that the code doesn't accidentally fall through into the Android S part.
        ExtendedMockito.verify(FlagsFactory::getFlags, never());
    }

    @Test
    @MockStatic(SdkLevel.class)
    public void testDeleteOnRollback_logsWipeout() {
        AdServicesLoggerImpl mockLogger = Mockito.mock(AdServicesLoggerImpl.class);
        MeasurementRollbackCompatManager mockManager =
                Mockito.mock(MeasurementRollbackCompatManager.class);

        mocker.mockGetFlags(mMockFlags);
        mocker.mockAdServicesLoggerImpl(mockLogger);
        mocker.mockSdkLevelS();
        ExtendedMockito.doReturn(mockManager)
                .when(() -> MeasurementRollbackCompatManager.getInstance(any(), anyInt()));

        doReturn(true).when(mockManager).needsToHandleRollbackReconciliation();
        doReturn(true).when(mDatastoreManager).runInTransaction(any());
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        doReturn(false).when(mMockFlags).getMeasurementRollbackDeletionAppSearchKillSwitch();

        MeasurementImpl measurement = new MeasurementImpl(DEFAULT_CONTEXT);

        assertThat(measurement.checkIfNeedsToHandleReconciliation()).isTrue();
        ArgumentCaptor<MeasurementWipeoutStats> statusArg =
                ArgumentCaptor.forClass(MeasurementWipeoutStats.class);
        Mockito.verify(mockLogger).logMeasurementWipeoutStats(statusArg.capture());
        MeasurementWipeoutStats measurementWipeoutStats = statusArg.getValue();
        assertEquals("", measurementWipeoutStats.getSourceRegistrant());
        assertEquals(
                WipeoutStatus.WipeoutType.ROLLBACK_WIPEOUT_CAUSE.getValue(),
                measurementWipeoutStats.getWipeoutType());
    }

    @Test
    public void testRegisterSources_success() {
        final int result =
                mMeasurementImpl.registerSources(
                        createSourceRegistrationRequest(), System.currentTimeMillis());
        assertEquals(STATUS_SUCCESS, result);
    }

    @Test
    public void testRegisterSources_ioError() {
        doReturn(false).when(mDatastoreManager).runInTransaction(any());
        final int result =
                mMeasurementImpl.registerSources(
                        createSourceRegistrationRequest(), System.currentTimeMillis());
        assertEquals(STATUS_IO_ERROR, result);
    }

    private void disableRollbackDeletion() {
        ExtendedMockito.doReturn(true).when(mMockFlags).getMeasurementRollbackDeletionKillSwitch();
        mocker.mockGetFlags(mMockFlags);
    }

    @NonNull
    private MeasurementImpl createMeasurementImpl() {
        return new MeasurementImpl(
                DEFAULT_CONTEXT,
                FakeFlagsFactory.getFlagsForTest(),
                mDatastoreManager,
                mClickVerifier,
                mMeasurementDataDeleter,
                mContentResolver);
    }
}
