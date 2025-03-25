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

package com.android.adservices.service.measurement.ondevicepersonalization;

import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationSystemEventManager;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SpyStatic(OdpDelegationWrapperImpl.class)
public class OdpDelegationWrapperFactoryTest extends AdServicesExtendedMockitoTestCase {
    @Mock private AdServicesLogger mLogger;
    @Mock private OnDevicePersonalizationSystemEventManager mOdpSystemEventManager;
    private OdpDelegationWrapperImpl mOdpDelegationWrapperImpl;

    @Before
    public void setup() {
        mOdpDelegationWrapperImpl =
                OdpDelegationWrapperImpl.createInstanceForTest(
                        mOdpSystemEventManager, mLogger, mMockFlags);
    }

    @Test
    public void getOdpDelegationWrapperImpl_nullInstance_returnNoOdpDelegationWrapper() {
        ExtendedMockito.doReturn(null).when(OdpDelegationWrapperImpl::getInstance);
        OdpDelegationWrapperFactory factory = new OdpDelegationWrapperFactory();
        // Execution
        IOdpDelegationWrapper wrapper = factory.getOdpDelegationWrapperImpl();
        // Assertion
        assertTrue(wrapper instanceof NoOdpDelegationWrapper);
    }

    @Test
    public void getOdpDelegationWrapperImpl_nonNullInstance_returnOdpDelegationWrapperImpl() {
        ExtendedMockito.doReturn(mOdpDelegationWrapperImpl)
                .when(OdpDelegationWrapperImpl::getInstance);
        OdpDelegationWrapperFactory factory = new OdpDelegationWrapperFactory();
        // Execution
        IOdpDelegationWrapper wrapper = factory.getOdpDelegationWrapperImpl();
        // Assertion
        assertTrue(wrapper instanceof OdpDelegationWrapperImpl);
    }
}
