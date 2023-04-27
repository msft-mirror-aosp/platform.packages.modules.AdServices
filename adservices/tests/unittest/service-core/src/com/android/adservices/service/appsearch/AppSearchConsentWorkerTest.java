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

package com.android.adservices.service.appsearch;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.UserHandle;

import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchResult;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.app.SetSchemaResponse;
import androidx.appsearch.platformstorage.PlatformStorage;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.AdServicesCommon;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentConstants;
import com.android.adservices.service.consent.ConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.ExecutionException;

@SmallTest
public class AppSearchConsentWorkerTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private static final String ADSERVICES_PACKAGE_NAME = "com.android.adservices.api";
    private static final String ADEXTSERVICES_PACKAGE_NAME = "com.android.ext.adservices.api";
    private static final String API_TYPE = AdServicesApiType.TOPICS.toPpApiDatastoreKey();
    private static final Boolean CONSENTED = true;
    private static final String TEST = "test";
    private static final int UID = 55;

    @Test
    public void testGetConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .mockStatic(AppSearchConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            ExtendedMockito.doReturn(false)
                    .when(
                            () ->
                                    AppSearchConsentDao.readConsentData(
                                            /* globalSearchSession= */ any(ListenableFuture.class),
                                            /* executor= */ any(),
                                            /* userId= */ any(),
                                            eq(API_TYPE)));
            boolean result = AppSearchConsentWorker.getInstance(mContext).getConsent(API_TYPE);
            assertThat(result).isFalse();

            // Confirm that the right value is returned even when it is true.
            ExtendedMockito.doReturn(true)
                    .when(
                            () ->
                                    AppSearchConsentDao.readConsentData(
                                            /* globalSearchSession= */ any(ListenableFuture.class),
                                            /* executor= */ any(),
                                            /* userId= */ any(),
                                            eq(API_TYPE)));
            boolean result2 = AppSearchConsentWorker.getInstance(mContext).getConsent(API_TYPE);
            assertThat(result2).isTrue();
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testSetConsent_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(
                            () ->
                                    PlatformStorage.createSearchSessionAsync(
                                            any(PlatformStorage.SearchContext.class)));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));

            AppSearchResult mockResult = Mockito.mock(AppSearchResult.class);
            SetSchemaResponse.MigrationFailure failure =
                    new SetSchemaResponse.MigrationFailure(
                            /* namespace= */ TEST,
                            /* id= */ TEST,
                            /* schemaType= */ TEST,
                            /* appSearchResult= */ mockResult);
            when(mockResponse.getMigrationFailures()).thenReturn(List.of(failure));
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    AppSearchConsentWorker.getInstance(mContext)
                                            .setConsent(API_TYPE, CONSENTED));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testSetConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(
                            () ->
                                    PlatformStorage.createSearchSessionAsync(
                                            any(PlatformStorage.SearchContext.class)));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            when(mockSession.putAsync(any())).thenReturn(Futures.immediateFuture(result));

            verify(mockResponse, atMost(1)).getMigrationFailures();
            when(mockResponse.getMigrationFailures()).thenReturn(List.of());
            // Verify that no exception is thrown.
            AppSearchConsentWorker.getInstance(mContext).setConsent(API_TYPE, CONSENTED);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testGetUserIdentifierFromBinderCallingUid() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(UserHandle.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
            Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                    .thenReturn(mockUserHandle);
            Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
            String result =
                    AppSearchConsentWorker.getInstance(mContext)
                            .getUserIdentifierFromBinderCallingUid();
            assertThat(result).isEqualTo("" + UID);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testGetAdServicesPackageName_null() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AdServicesCommon.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            Context context = Mockito.mock(Context.class);
            PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
            Mockito.when(context.getPackageManager()).thenReturn(mockPackageManager);
            Mockito.when(AdServicesCommon.resolveAdServicesService(any(), any())).thenReturn(null);
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () -> AppSearchConsentWorker.getAdServicesPackageName(context));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testGetAdServicesPackageName() {
        Context context = Mockito.mock(Context.class);
        PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
        // When the resolveInfo returns AdServices package name, that is returned.
        Mockito.when(context.getPackageManager()).thenReturn(mockPackageManager);

        ServiceInfo serviceInfo1 = new ServiceInfo();
        serviceInfo1.packageName = ADSERVICES_PACKAGE_NAME;
        ResolveInfo resolveInfo1 = new ResolveInfo();
        resolveInfo1.serviceInfo = serviceInfo1;

        ServiceInfo serviceInfo2 = new ServiceInfo();
        serviceInfo2.packageName = ADEXTSERVICES_PACKAGE_NAME;
        ResolveInfo resolveInfo2 = new ResolveInfo();
        resolveInfo2.serviceInfo = serviceInfo2;
        Mockito.when(mockPackageManager.queryIntentServices(any(), anyInt()))
                .thenReturn(List.of(resolveInfo1, resolveInfo2));
        assertThat(AppSearchConsentWorker.getAdServicesPackageName(context))
                .isEqualTo(ADSERVICES_PACKAGE_NAME);

        // When the resolveInfo returns AdExtServices package name, the AdServices package name
        // is returned.
        Mockito.when(mockPackageManager.queryIntentServices(any(), anyInt()))
                .thenReturn(List.of(resolveInfo2));
        assertThat(AppSearchConsentWorker.getAdServicesPackageName(context))
                .isEqualTo(ADSERVICES_PACKAGE_NAME);
    }

    @Test
    public void testGetAppsWithConsent_nullOrEmpty() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            // Null dao is returned.
            ExtendedMockito.doReturn(null)
                    .when(() -> AppSearchAppConsentDao.readConsentData(any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEmpty();

            // Dao is returned, but list is null.
            AppSearchAppConsentDao mockDao = Mockito.mock(AppSearchAppConsentDao.class);
            ExtendedMockito.doReturn(mockDao)
                    .when(() -> AppSearchAppConsentDao.readConsentData(any(), any(), any(), any()));
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEmpty();
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testGetAppsWithConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            // Null dao is returned.
            ExtendedMockito.doReturn(null)
                    .when(() -> AppSearchAppConsentDao.readConsentData(any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEmpty();

            // Dao is returned, but list is null.
            AppSearchAppConsentDao mockDao = Mockito.mock(AppSearchAppConsentDao.class);
            List<String> apps = ImmutableList.of(TEST);
            when(mockDao.getApps()).thenReturn(apps);
            ExtendedMockito.doReturn(mockDao)
                    .when(() -> AppSearchAppConsentDao.readConsentData(any(), any(), any(), any()));
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isNotNull();
            assertThat(appSearchConsentWorker.getAppsWithConsent(TEST)).isEqualTo(apps);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testClearAppsWithConsent_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            FluentFuture future =
                    FluentFuture.from(
                            Futures.immediateFailedFuture(new ExecutionException("test", null)));
            ExtendedMockito.doReturn(future)
                    .when(() -> AppSearchDao.deleteConsentData(any(), any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () -> appSearchConsentWorker.clearAppsWithConsent(TEST));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testClearAppsWithConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            FluentFuture future = FluentFuture.from(Futures.immediateFuture(result));
            ExtendedMockito.doReturn(future)
                    .when(() -> AppSearchDao.deleteConsentData(any(), any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            // No exceptions are thrown.
            appSearchConsentWorker.clearAppsWithConsent(TEST);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testAddAppWithConsent_null() {
        MockitoSession staticMockSessionLocal = null;
        try {
            String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            ExtendedMockito.doReturn(null)
                    .when(() -> AppSearchAppConsentDao.readConsentData(any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            // No exceptions are thrown.
            assertThat(appSearchConsentWorker.addAppWithConsent(consentType, TEST)).isTrue();
            ExtendedMockito.verify(
                    () -> AppSearchAppConsentDao.getRowId(any(), eq(consentType)), atLeastOnce());
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testAddAppWithConsent_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);
            ExtendedMockito.doReturn(dao)
                    .when(
                            () ->
                                    AppSearchAppConsentDao.readConsentData(
                                            any(), any(), any(), eq(consentType)));
            when(dao.getApps()).thenReturn(List.of());
            FluentFuture future =
                    FluentFuture.from(
                            Futures.immediateFailedFuture(new ExecutionException("test", null)));
            when(dao.writeConsentData(any(), any(), any())).thenReturn(future);

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.addAppWithConsent(consentType, TEST)).isFalse();
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testAddAppWithConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);
            ExtendedMockito.doReturn(dao)
                    .when(
                            () ->
                                    AppSearchAppConsentDao.readConsentData(
                                            any(), any(), any(), eq(consentType)));
            when(dao.getApps()).thenReturn(List.of());
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            FluentFuture future = FluentFuture.from(Futures.immediateFuture(result));
            when(dao.writeConsentData(any(), any(), any())).thenReturn(future);

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            // No exceptions are thrown.
            assertThat(appSearchConsentWorker.addAppWithConsent(consentType, TEST)).isTrue();
            verify(dao, atLeastOnce()).getApps();
            verify(dao, atLeastOnce()).writeConsentData(any(), any(), any());
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRemoveAppWithConsent_null() {
        MockitoSession staticMockSessionLocal = null;
        try {
            String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            ExtendedMockito.doReturn(null)
                    .when(() -> AppSearchAppConsentDao.readConsentData(any(), any(), any(), any()));
            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            // No exceptions are thrown.
            appSearchConsentWorker.removeAppWithConsent(consentType, TEST);
            ExtendedMockito.verify(
                    () -> AppSearchAppConsentDao.getRowId(any(), eq(consentType)), never());
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRemoveAppWithConsent_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);
            ExtendedMockito.doReturn(dao)
                    .when(
                            () ->
                                    AppSearchAppConsentDao.readConsentData(
                                            any(), any(), any(), eq(consentType)));
            when(dao.getApps()).thenReturn(List.of(TEST));
            FluentFuture future =
                    FluentFuture.from(
                            Futures.immediateFailedFuture(new ExecutionException("test", null)));
            when(dao.writeConsentData(any(), any(), any())).thenReturn(future);

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () -> appSearchConsentWorker.removeAppWithConsent(consentType, TEST));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRemoveAppWithConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchAppConsentDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);
            ExtendedMockito.doReturn(dao)
                    .when(
                            () ->
                                    AppSearchAppConsentDao.readConsentData(
                                            any(), any(), any(), eq(consentType)));

            when(dao.getApps()).thenReturn(List.of(TEST));
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            FluentFuture future = FluentFuture.from(Futures.immediateFuture(result));
            when(dao.writeConsentData(any(), any(), any())).thenReturn(future);

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            // No exceptions are thrown.
            appSearchConsentWorker.removeAppWithConsent(consentType, TEST);
            verify(dao, atLeastOnce()).getApps();
            verify(dao, atLeastOnce()).writeConsentData(any(), any(), any());
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testWasNotificationDisplayed() {
        runNotificationDisplayedTest(true);
    }

    @Test
    public void testNotificationNotDisplayed() {
        runNotificationDisplayedTest(false);
    }

    private void runNotificationDisplayedTest(boolean displayed) {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchNotificationDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            ExtendedMockito.doReturn(displayed)
                    .when(
                            () ->
                                    AppSearchNotificationDao.wasNotificationDisplayed(
                                            any(), any(), any()));

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.wasNotificationDisplayed()).isEqualTo(displayed);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testWasGaUxNotificationDisplayed() {
        runGaUxNotificationDisplayedTest(true);
    }

    @Test
    public void testGaUxNotificationNotDisplayed() {
        runGaUxNotificationDisplayedTest(false);
    }

    private void runGaUxNotificationDisplayedTest(boolean displayed) {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchNotificationDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            ExtendedMockito.doReturn(displayed)
                    .when(
                            () ->
                                    AppSearchNotificationDao.wasGaUxNotificationDisplayed(
                                            any(), any(), any()));

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.wasGaUxNotificationDisplayed()).isEqualTo(displayed);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRecordNotificationDisplayed_failure() {
        runRecordNotificationDisplayedTestFailure(/* isBetaUx= */ true);
    }

    @Test
    public void testRecordGaUxNotificationDisplayed_faiure() {
        runRecordNotificationDisplayedTestFailure(/* isBetaUx= */ false);
    }

    private void runRecordNotificationDisplayedTestFailure(boolean isBetaUx) {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(() -> PlatformStorage.createSearchSessionAsync(any()));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));

            AppSearchResult mockResult = Mockito.mock(AppSearchResult.class);
            SetSchemaResponse.MigrationFailure failure =
                    new SetSchemaResponse.MigrationFailure(
                            /* namespace= */ TEST,
                            /* id= */ TEST,
                            /* schemaType= */ TEST,
                            /* appSearchResult= */ mockResult);
            when(mockResponse.getMigrationFailures()).thenReturn(List.of(failure));
            AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance(mContext);
            if (isBetaUx) {
                RuntimeException e =
                        assertThrows(
                                RuntimeException.class, () -> worker.recordNotificationDisplayed());
                assertThat(e.getMessage())
                        .isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
            } else {
                RuntimeException e =
                        assertThrows(
                                RuntimeException.class,
                                () -> worker.recordGaUxNotificationDisplayed());
                assertThat(e.getMessage())
                        .isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
            }
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRecordNotificationDisplayed() {
        runRecordNotificationDisplayed(/* isBetaUx= */ true);
    }

    @Test
    public void testRecordGaUxNotificationDisplayed() {
        runRecordNotificationDisplayed(/* isBetaUx= */ false);
    }

    private void runRecordNotificationDisplayed(boolean isBetaUx) {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .mockStatic(AppSearchNotificationDao.class)
                            .spyStatic(UserHandle.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
            Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                    .thenReturn(mockUserHandle);
            Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(() -> PlatformStorage.createSearchSessionAsync(any()));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            when(mockSession.putAsync(any())).thenReturn(Futures.immediateFuture(result));

            verify(mockResponse, atMost(1)).getMigrationFailures();
            when(mockResponse.getMigrationFailures()).thenReturn(List.of());
            when(AppSearchNotificationDao.getRowId(eq("" + UID))).thenReturn("" + UID);
            // Verify that no exception is thrown.
            if (isBetaUx) {
                when(AppSearchNotificationDao.wasGaUxNotificationDisplayed(any(), any(), any()))
                        .thenReturn(false);
                AppSearchConsentWorker.getInstance(mContext).recordNotificationDisplayed();
            } else {
                when(AppSearchNotificationDao.wasNotificationDisplayed(any(), any(), any()))
                        .thenReturn(false);
                AppSearchConsentWorker.getInstance(mContext).recordGaUxNotificationDisplayed();
            }
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testGetPrivacySandboxFeature() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchInteractionsDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            PrivacySandboxFeatureType feature = PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT;
            ExtendedMockito.doReturn(feature)
                    .when(
                            () ->
                                    AppSearchInteractionsDao.getPrivacySandboxFeatureType(
                                            any(), any(), any()));

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.getPrivacySandboxFeature()).isEqualTo(feature);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(() -> PlatformStorage.createSearchSessionAsync(any()));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));

            AppSearchResult mockResult = Mockito.mock(AppSearchResult.class);
            SetSchemaResponse.MigrationFailure failure =
                    new SetSchemaResponse.MigrationFailure(
                            /* namespace= */ TEST,
                            /* id= */ TEST,
                            /* schemaType= */ TEST,
                            /* appSearchResult= */ mockResult);
            when(mockResponse.getMigrationFailures()).thenReturn(List.of(failure));
            AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance(mContext);
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    worker.setCurrentPrivacySandboxFeature(
                                            PrivacySandboxFeatureType.PRIVACY_SANDBOX_RECONSENT));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testSetCurrentPrivacySandboxFeature() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .spyStatic(UserHandle.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
            Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                    .thenReturn(mockUserHandle);
            Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(() -> PlatformStorage.createSearchSessionAsync(any()));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            when(mockSession.putAsync(any())).thenReturn(Futures.immediateFuture(result));

            verify(mockResponse, atMost(1)).getMigrationFailures();
            when(mockResponse.getMigrationFailures()).thenReturn(List.of());
            // Verify that no exception is thrown.
            PrivacySandboxFeatureType feature =
                    PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT;
            AppSearchConsentWorker.getInstance(mContext).setCurrentPrivacySandboxFeature(feature);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testGetInteractions() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(AppSearchInteractionsDao.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            int umi = ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
            ExtendedMockito.doReturn(umi)
                    .when(
                            () ->
                                    AppSearchInteractionsDao.getManualInteractions(
                                            any(), any(), any()));

            AppSearchConsentWorker appSearchConsentWorker =
                    AppSearchConsentWorker.getInstance(mContext);
            assertThat(appSearchConsentWorker.getUserManualInteractionWithConsent()).isEqualTo(umi);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRecordUserManualInteractionWithConsent_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(() -> PlatformStorage.createSearchSessionAsync(any()));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));

            AppSearchResult mockResult = Mockito.mock(AppSearchResult.class);
            SetSchemaResponse.MigrationFailure failure =
                    new SetSchemaResponse.MigrationFailure(
                            /* namespace= */ TEST,
                            /* id= */ TEST,
                            /* schemaType= */ TEST,
                            /* appSearchResult= */ mockResult);
            when(mockResponse.getMigrationFailures()).thenReturn(List.of(failure));
            AppSearchConsentWorker worker = AppSearchConsentWorker.getInstance(mContext);
            int interactions = ConsentManager.MANUAL_INTERACTIONS_RECORDED;
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () -> worker.recordUserManualInteractionWithConsent(interactions));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testRecordUserManualInteractionWithConsent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .mockStatic(AppSearchInteractionsDao.class)
                            .spyStatic(UserHandle.class)
                            .strictness(Strictness.WARN)
                            .initMocks(this)
                            .startMocking();
            AppSearchSession mockSession = Mockito.mock(AppSearchSession.class);
            UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
            Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                    .thenReturn(mockUserHandle);
            Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
            ExtendedMockito.doReturn(Futures.immediateFuture(mockSession))
                    .when(() -> PlatformStorage.createSearchSessionAsync(any()));
            verify(mockSession, atMost(1)).setSchemaAsync(any(SetSchemaRequest.class));

            SetSchemaResponse mockResponse = Mockito.mock(SetSchemaResponse.class);
            when(mockSession.setSchemaAsync(any(SetSchemaRequest.class)))
                    .thenReturn(Futures.immediateFuture(mockResponse));
            AppSearchBatchResult<String, Void> result = Mockito.mock(AppSearchBatchResult.class);
            when(mockSession.putAsync(any())).thenReturn(Futures.immediateFuture(result));

            verify(mockResponse, atMost(1)).getMigrationFailures();
            when(mockResponse.getMigrationFailures()).thenReturn(List.of());
            when(AppSearchInteractionsDao.getRowId(any(), any())).thenReturn("" + UID);
            // Verify that no exception is thrown.
            int interactions = ConsentManager.MANUAL_INTERACTIONS_RECORDED;
            AppSearchConsentWorker.getInstance(mContext)
                    .recordUserManualInteractionWithConsent(interactions);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }
}
