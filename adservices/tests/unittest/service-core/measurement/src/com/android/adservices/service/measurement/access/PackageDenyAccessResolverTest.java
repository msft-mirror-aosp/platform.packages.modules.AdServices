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

package com.android.adservices.service.measurement.access;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.AdPackageDenyResolver;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.mockito.Mock;

import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class PackageDenyAccessResolverTest extends AdServicesExtendedMockitoTestCase {
    private final Set<String> mApiGroups = Set.of("Measurement");

    @Mock private AdPackageDenyResolver mMockAdPackageDenyResolver;

    @Test
    public void testEnablePackageDeny_Allowed() {
        String appName = "testApp";
        String sdkName = "testSdk";
        ListenableFuture<Boolean> future = Futures.immediateFuture(false);
        when(mMockAdPackageDenyResolver.shouldDenyPackage(appName, sdkName, mApiGroups))
                .thenReturn(future);

        PackageDenyAccessResolver resolver =
                new PackageDenyAccessResolver(
                        true, mMockAdPackageDenyResolver, appName, sdkName, mApiGroups);

        assertThat(resolver.getAccessInfo(mContext).isAllowedAccess()).isTrue();
    }

    @Test
    public void testEnablePackageDeny_Denied() {
        String appName = "testApp";
        String sdkName = "testSdk";
        ListenableFuture<Boolean> future = Futures.immediateFuture(true);
        when(mMockAdPackageDenyResolver.shouldDenyPackage(appName, sdkName, mApiGroups))
                .thenReturn(future);

        PackageDenyAccessResolver resolver =
                new PackageDenyAccessResolver(
                        true, mMockAdPackageDenyResolver, appName, sdkName, mApiGroups);

        assertThat(resolver.getAccessInfo(mContext).isAllowedAccess()).isFalse();
    }

    @Test
    public void testEnablePackageDeny_Exception() {
        String appName = "testApp";
        String sdkName = "testSdk";
        ListenableFuture<Boolean> future =
                Futures.immediateFailedFuture(new Exception("test exception"));
        when(mMockAdPackageDenyResolver.shouldDenyPackage(appName, sdkName, mApiGroups))
                .thenReturn(future);

        PackageDenyAccessResolver resolver =
                new PackageDenyAccessResolver(
                        true, mMockAdPackageDenyResolver, appName, sdkName, mApiGroups);

        assertThat(resolver.getAccessInfo(mContext).isAllowedAccess()).isFalse();
        assertThat(resolver.getErrorMessage())
                .isEqualTo("Package app testApp for sdk testSdk is denied for measurement");
    }

    @Test
    public void testDisablePackageDeny() {
        String appName = "testApp";
        String sdkName = "testSdk";

        PackageDenyAccessResolver resolver =
                new PackageDenyAccessResolver(
                        false, mMockAdPackageDenyResolver, appName, sdkName, mApiGroups);

        assertThat(resolver.getAccessInfo(mContext).isAllowedAccess()).isTrue();
        verify(mMockAdPackageDenyResolver, never())
                .shouldDenyPackage(anyString(), anyString(), anySet());
    }

    @Test
    public void testTimeout() throws Exception {
        String appName = "testApp";
        String sdkName = "testSdk";
        ListenableFuture<Boolean> future =
                Futures.submit(
                        () -> {
                            TimeUnit.SECONDS.sleep(10);
                            return true;
                        },
                        AdServicesExecutors.getLightWeightExecutor());
        when(mMockAdPackageDenyResolver.shouldDenyPackage(appName, sdkName, mApiGroups))
                .thenReturn(future);

        PackageDenyAccessResolver resolver =
                new PackageDenyAccessResolver(
                        true, mMockAdPackageDenyResolver, appName, sdkName, mApiGroups);
        verify(mMockAdPackageDenyResolver).shouldDenyPackage(anyString(), anyString(), anySet());
        assertThat(resolver.getAccessInfo(mContext).isAllowedAccess()).isFalse();
        assertThat(resolver.getErrorMessage())
                .isEqualTo("Package app testApp for sdk testSdk is denied for measurement");
    }
}
