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

import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_FOREGROUND_UNKNOWN_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.compat.ProcessCompatUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(ProcessCompatUtils.class)
@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
public final class ForegroundEnforcementAccessResolverTest
        extends AdServicesExtendedMockitoTestCase {
    private static final String ERROR_MESSAGE = "Measurement API was not called from foreground.";

    @Mock private AppImportanceFilter mAppImportanceFilter;

    @Test
    public void testIsAllowed_flagEnforced_assertCallerForeground() {
        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                .getAccessInfo(mContext);

        // Validation
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_flagNotEnforced_dontAssertCallerForeground() {
        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* not enforced */ false)
                .getAccessInfo(mContext);

        // Validation
        verify(mAppImportanceFilter, never()).assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_callerNotSandbox_assertCallerForeground() {
        // Not Sandbox
        ExtendedMockito.doReturn(false).when(() -> ProcessCompatUtils.isSdkSandboxUid(anyInt()));

        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                .getAccessInfo(mContext);

        // Validation
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_callerIsSandbox_dontAssertCallerForeground() {
        // Is Sandbox
        ExtendedMockito.doReturn(true).when(() -> ProcessCompatUtils.isSdkSandboxUid(anyInt()));

        // Execute
        new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                .getAccessInfo(mContext);

        // Validation
        verify(mAppImportanceFilter, never()).assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_isForeground_returnTrue() {
        // Setup
        doNothing()
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        // Execute
        AccessInfo result =
                new ForegroundEnforcementAccessResolver(
                                1, 1, mAppImportanceFilter, () -> /* enforced */ true)
                        .getAccessInfo(mContext);

        // Validation
        assertThat(result.isAllowedAccess()).isTrue();
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testIsAllowed_isNotForeground_returnFalse() {
        // Setup
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        // Execute
        ForegroundEnforcementAccessResolver foregroundEnforcementAccessResolver =
                new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true);
        AccessInfo result = foregroundEnforcementAccessResolver.getAccessInfo(mContext);

        // Validation
        assertThat(result.isAllowedAccess()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo(STATUS_BACKGROUND_CALLER);
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = SecurityException.class,
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_FOREGROUND_UNKNOWN_FAILURE)
    public void testIsAllowed_CatchesAllExceptions_returnsFalse() {
        // Setup
        doThrow(new SecurityException())
                .when(mAppImportanceFilter)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        // Execute
        ForegroundEnforcementAccessResolver foregroundEnforcementAccessResolver =
                new ForegroundEnforcementAccessResolver(
                        1, 1, mAppImportanceFilter, () -> /* enforced */ true);
        AccessInfo result = foregroundEnforcementAccessResolver.getAccessInfo(mContext);

        // Validation
        assertThat(result.isAllowedAccess()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo(STATUS_BACKGROUND_CALLER);
        verify(mAppImportanceFilter, times(1))
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
    }

    @Test
    public void testGetErrorMessage() {
        assertThat(
                        new ForegroundEnforcementAccessResolver(
                                        1, 1, mAppImportanceFilter, () -> true)
                                .getErrorMessage())
                .isEqualTo(ERROR_MESSAGE);
    }
}
