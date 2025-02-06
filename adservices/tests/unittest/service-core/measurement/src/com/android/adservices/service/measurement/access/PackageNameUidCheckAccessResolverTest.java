/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_UID_CHECK_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.content.pm.PackageManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.service.common.SdkRuntimeUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Locale;

@SpyStatic(SdkRuntimeUtil.class)
public final class PackageNameUidCheckAccessResolverTest extends AdServicesExtendedMockitoTestCase {
    @Mock private PackageManager mMockPackageManager;

    @Before
    public void setup() {
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @Test
    public void testIsAllowed_callerUid_matchesAppUid_returnTrue()
            throws PackageManager.NameNotFoundException {
        // Setup
        doReturn(1).when(() -> SdkRuntimeUtil.getCallingAppUid(anyInt()));
        when(mMockPackageManager.getPackageUid(mPackageName, /* flags= */ 0)).thenReturn(1);
        // Execute
        AccessInfo result =
                new PackageNameUidCheckAccessResolver(
                                mPackageName,
                                /* callingUid= */ 1,
                                /* packageNameUidCheckEnabled= */ true)
                        .getAccessInfo(mMockContext);

        // Validation
        expect.withMessage("result.isAllowedAccess()").that(result.isAllowedAccess()).isTrue();
        expect.withMessage("result.getResponseCode()")
                .that(result.getResponseCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mMockPackageManager).getPackageUid(anyString(), anyInt());
    }

    @Test
    public void testIsAllowed_callerUid_doesntMatchAppUid_returnTrue()
            throws PackageManager.NameNotFoundException {
        // Setup
        doReturn(2).when(() -> SdkRuntimeUtil.getCallingAppUid(anyInt()));
        when(mMockPackageManager.getPackageUid(mPackageName, /* flags= */ 0)).thenReturn(1);

        // Execute
        AccessInfo result =
                new PackageNameUidCheckAccessResolver(
                                mPackageName,
                                /* callingUid= */ 1,
                                /* packageNameUidCheckEnabled= */ true)
                        .getAccessInfo(mMockContext);

        // Validation
        expect.withMessage("result.isAllowedAccess()").that(result.isAllowedAccess()).isFalse();
        expect.withMessage("result.getResponseCode()")
                .that(result.getResponseCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_UID_MISMATCH);
    }

    @Test
    public void testIsAllowed_catchesNameNotFoundException_returnsFalse()
            throws PackageManager.NameNotFoundException {
        // Setup
        doThrow(new PackageManager.NameNotFoundException())
                .when(mMockPackageManager)
                .getPackageUid(anyString(), anyInt());

        // Execute
        AccessInfo result =
                new PackageNameUidCheckAccessResolver(
                                mPackageName,
                                /* callingUid= */ 1,
                                /* packageNameUidCheckEnabled= */ true)
                        .getAccessInfo(mMockContext);

        // Validation
        expect.withMessage("result.isAllowedAccess()").that(result.isAllowedAccess()).isFalse();
        expect.withMessage("result.getResponseCode()")
                .that(result.getResponseCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_UID_MISMATCH);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = Any.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_UID_CHECK_FAILURE,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
    public void testIsAllowed_catchesAllExceptions_returnsFalse()
            throws PackageManager.NameNotFoundException {
        // Setup
        doThrow(new SecurityException())
                .when(mMockPackageManager)
                .getPackageUid(anyString(), anyInt());

        // Execute
        AccessInfo result =
                new PackageNameUidCheckAccessResolver(
                                mPackageName,
                                /* callingUid= */ 1,
                                /* packageNameUidCheckEnabled= */ true)
                        .getAccessInfo(mMockContext);

        // Validation
        expect.withMessage("result.isAllowedAccess()").that(result.isAllowedAccess()).isFalse();
        expect.withMessage("result.getResponseCode()")
                .that(result.getResponseCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED_UID_MISMATCH);
    }

    @Test
    public void testIsAllowed_packageNameUidCheckDisabled_returnTrue()
            throws PackageManager.NameNotFoundException {
        // Execute
        AccessInfo result =
                new PackageNameUidCheckAccessResolver(
                                mPackageName,
                                /* callingUid= */ 1,
                                /* packageNameUidCheckEnabled= */ false)
                        .getAccessInfo(mMockContext);

        // Validation
        expect.withMessage("result.isAllowedAccess()").that(result.isAllowedAccess()).isTrue();
        expect.withMessage("result.getResponseCode()")
                .that(result.getResponseCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_SUCCESS);
        verify(mMockPackageManager, never()).getPackageUid(anyString(), anyInt());
    }

    @Test
    public void testGetErrorMessage() {
        String errorMessage =
                String.format(
                        Locale.ENGLISH, "Package %s does not belong to UID %d.", mPackageName, 1);
        expect.withMessage("errorMessage")
                .that(errorMessage)
                .isEqualTo(
                        new PackageNameUidCheckAccessResolver(
                                        mPackageName,
                                        /* callingUid= */ 1,
                                        /* packageNameUidCheckEnabled= */ true)
                                .getErrorMessage());
    }
}
