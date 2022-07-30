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

package com.android.adservices.service.common;

import static com.android.adservices.stats.FledgeApiCallStatsMatcher.aCallStatForFledgeApiWithStatus;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.content.pm.PackageManager;

import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class FledgeAuthorizationFilterTest {
    private static final int UID = 111;
    private static final int API_NAME_LOGGING_ID =
            AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
    private static final String PACKAGE_NAME = "pkg_name";
    private static final String PACKAGE_NAME_OTHER = "other_pkg_name";

    @Mock private PackageManager mPackageManager;
    @Spy private final AdServicesLogger mAdServicesLoggerSpy = AdServicesLoggerImpl.getInstance();

    private FledgeAuthorizationFilter mChecker;

    @Before
    public void setup() {
        mChecker = new FledgeAuthorizationFilter(mPackageManager, mAdServicesLoggerSpy);
    }

    @Test
    public void testIsCallingPackageName() {
        when(mPackageManager.getPackagesForUid(UID))
                .thenReturn(new String[] {PACKAGE_NAME, PACKAGE_NAME_OTHER});

        mChecker.assertCallingPackageName(PACKAGE_NAME, UID, API_NAME_LOGGING_ID);

        verify(mPackageManager).getPackagesForUid(UID);
        verifyNoMoreInteractions(mPackageManager);
        verifyZeroInteractions(mAdServicesLoggerSpy);
    }

    @Test
    public void testNullPackageName_throwNpe() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.assertCallingPackageName(null, UID, API_NAME_LOGGING_ID));

        verifyZeroInteractions(mPackageManager, mAdServicesLoggerSpy);
    }

    @Test
    public void testIsNotCallingPackageName_throwSecurityException() {
        when(mPackageManager.getPackagesForUid(UID)).thenReturn(new String[] {PACKAGE_NAME_OTHER});

        assertThrows(
                SecurityException.class,
                () -> mChecker.assertCallingPackageName(PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        verify(mPackageManager).getPackagesForUid(UID);
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED));
        verifyNoMoreInteractions(mPackageManager, mAdServicesLoggerSpy);
    }

    @Test
    public void testPackageNotExist_throwSecurityException() {
        when(mPackageManager.getPackagesForUid(UID)).thenReturn(new String[] {});

        assertThrows(
                SecurityException.class,
                () -> mChecker.assertCallingPackageName(PACKAGE_NAME, UID, API_NAME_LOGGING_ID));

        verify(mPackageManager).getPackagesForUid(UID);
        verify(mAdServicesLoggerSpy)
                .logFledgeApiCallStats(
                        API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED);
        verify(mAdServicesLoggerSpy)
                .logApiCallStats(
                        aCallStatForFledgeApiWithStatus(
                                API_NAME_LOGGING_ID, AdServicesStatusUtils.STATUS_UNAUTHORIZED));
        verifyNoMoreInteractions(mPackageManager);
    }
}
