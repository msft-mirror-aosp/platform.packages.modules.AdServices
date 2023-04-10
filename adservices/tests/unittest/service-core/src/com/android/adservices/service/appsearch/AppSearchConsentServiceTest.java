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
import static org.mockito.Mockito.atMost;
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
import com.android.adservices.service.consent.ConsentConstants;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@SmallTest
public class AppSearchConsentServiceTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private static final String ADSERVICES_PACKAGE_NAME = "com.android.adservices.api";
    private static final String ADEXTSERVICES_PACKAGE_NAME = "com.android.ext.adservices.api";
    private static final String API_TYPE = "CONSENT-TOPICS";
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
                            .strictness(Strictness.LENIENT)
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
            boolean result = AppSearchConsentService.getInstance(mContext).getConsent(API_TYPE);
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
            boolean result2 = AppSearchConsentService.getInstance(mContext).getConsent(API_TYPE);
            assertThat(result2).isTrue();
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testSetContent_failure() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.LENIENT)
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
                                    AppSearchConsentService.getInstance(mContext)
                                            .setConsent(API_TYPE, CONSENTED));
            assertThat(e.getMessage()).isEqualTo(ConsentConstants.ERROR_MESSAGE_APPSEARCH_FAILURE);
        } finally {
            if (staticMockSessionLocal != null) {
                staticMockSessionLocal.finishMocking();
            }
        }
    }

    @Test
    public void testSetContent() {
        MockitoSession staticMockSessionLocal = null;
        try {
            staticMockSessionLocal =
                    ExtendedMockito.mockitoSession()
                            .spyStatic(PlatformStorage.class)
                            .strictness(Strictness.LENIENT)
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
            AppSearchConsentService.getInstance(mContext).setConsent(API_TYPE, CONSENTED);
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
                            .strictness(Strictness.LENIENT)
                            .initMocks(this)
                            .startMocking();
            UserHandle mockUserHandle = Mockito.mock(UserHandle.class);
            Mockito.when(UserHandle.getUserHandleForUid(Binder.getCallingUid()))
                    .thenReturn(mockUserHandle);
            Mockito.when(mockUserHandle.getIdentifier()).thenReturn(UID);
            String result =
                    AppSearchConsentService.getInstance(mContext)
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
                            .strictness(Strictness.LENIENT)
                            .initMocks(this)
                            .startMocking();
            Context context = Mockito.mock(Context.class);
            PackageManager mockPackageManager = Mockito.mock(PackageManager.class);
            Mockito.when(context.getPackageManager()).thenReturn(mockPackageManager);
            Mockito.when(AdServicesCommon.resolveAdServicesService(any(), any())).thenReturn(null);
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () -> AppSearchConsentService.getAdServicesPackageName(context));
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
        Mockito.when(mockPackageManager.queryIntentServicesAsUser(any(), anyInt(), any()))
                .thenReturn(List.of(resolveInfo1, resolveInfo2));
        assertThat(AppSearchConsentService.getAdServicesPackageName(context))
                .isEqualTo(ADSERVICES_PACKAGE_NAME);

        // When the resolveInfo returns AdExtServices package name, the AdServices package name
        // is returned.
        Mockito.when(mockPackageManager.queryIntentServicesAsUser(any(), anyInt(), any()))
                .thenReturn(List.of(resolveInfo2));
        assertThat(AppSearchConsentService.getAdServicesPackageName(context))
                .isEqualTo(ADSERVICES_PACKAGE_NAME);
    }
}
