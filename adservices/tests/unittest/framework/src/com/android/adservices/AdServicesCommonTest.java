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

package com.android.adservices;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SmallTest
public class AdServicesCommonTest extends AdServicesUnitTestCase {
    private ResolveInfo mResolveInfo1, mResolveInfo2, mResolveInfo3, mResolveInfo4;
    private ServiceInfo mServiceInfo1, mServiceInfo2;
    private static final String ADSERVICES_PACKAGE_NAME = "com.android.adservices.api";
    private static final String ADEXTSERVICES_PACKAGE_NAME = "com.android.ext.services";
    private static final String ADSERVICES_PACKAGE_NAME_NOT_SUFFIX = "com.android.adservices.api.x";

    @Before
    public void setUp() {
        mServiceInfo1 = new ServiceInfo();
        mServiceInfo1.packageName = ADSERVICES_PACKAGE_NAME;
        mServiceInfo1.name = mServiceInfo1.packageName;
        mResolveInfo1 = new ResolveInfo();
        mResolveInfo1.serviceInfo = mServiceInfo1;

        mServiceInfo2 = new ServiceInfo();
        mServiceInfo2.packageName = ADEXTSERVICES_PACKAGE_NAME;
        mServiceInfo2.name = mServiceInfo2.packageName;
        mResolveInfo2 = new ResolveInfo();
        mResolveInfo2.serviceInfo = mServiceInfo2;

        ServiceInfo serviceInfo3 = new ServiceInfo();
        serviceInfo3.packageName = "foobar";
        serviceInfo3.name = serviceInfo3.packageName;
        mResolveInfo3 = new ResolveInfo();
        mResolveInfo3.serviceInfo = serviceInfo3;

        mResolveInfo4 = new ResolveInfo();
        ServiceInfo serviceInfo4 = new ServiceInfo();
        serviceInfo4.packageName = ADSERVICES_PACKAGE_NAME_NOT_SUFFIX;
        serviceInfo4.name = serviceInfo4.packageName;
        mResolveInfo4.serviceInfo = serviceInfo4;
    }

    @Test
    public void testResolveAdServicesService_empty() {
        assertThat(AdServicesCommon.resolveAdServicesService(List.of(), "test")).isNull();
    }

    @Test
    public void testResolveAdServicesService_moreThan2() {
        assertThat(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo1, mResolveInfo2, mResolveInfo3), "test"))
                .isNull();
    }

    @Test
    public void testResolveAdServicesService_single() {
        expect.withMessage("Single item matching AdServices package")
                .that(AdServicesCommon.resolveAdServicesService(List.of(mResolveInfo1), ""))
                .isEqualTo(mServiceInfo1);

        expect.withMessage("Single item not matching AdServices package name")
                .that(AdServicesCommon.resolveAdServicesService(List.of(mResolveInfo2), ""))
                .isEqualTo(mServiceInfo2);
    }

    @Test
    public void testResolveAdServicesService() {
        expect.withMessage("List with AdServices and ExtServices packages")
                .that(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo1, mResolveInfo2), ""))
                .isEqualTo(mServiceInfo1);
        expect.withMessage("List with ExtServices and AdServices packages")
                .that(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo2, mResolveInfo1), ""))
                .isEqualTo(mServiceInfo1);
        expect.withMessage("List with AdServices and non-AdServices-suffix packages")
                .that(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo1, mResolveInfo4), ""))
                .isEqualTo(mServiceInfo1);
        expect.withMessage("List with non-AdServices-suffix and AdServices packages")
                .that(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo4, mResolveInfo1), ""))
                .isEqualTo(mServiceInfo1);
        expect.withMessage("List with ExtServices and non-AdServices-suffix packages")
                .that(
                        AdServicesCommon.resolveAdServicesService(
                                List.of(mResolveInfo2, mResolveInfo4), ""))
                .isNull();
    }
}
