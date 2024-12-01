/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.signals;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class ForcedEncoderFactoryTest extends AdServicesExtendedMockitoTestCase {

    @Test
    public void test_createInstance_forcedEncodingEnabled_returnsImpl() {
        ForcedEncoder forcedEncoder =
                new ForcedEncoderFactory(/* fledgeEnableForcedEncodingAfterSignalsUpdate= */ true)
                        .createInstance();

        assertThat(forcedEncoder).isInstanceOf(ForcedEncoderImpl.class);
    }

    @Test
    public void test_createInstance_forcedEncodingDisabled_returnsNoOpImpl() {
        ForcedEncoder forcedEncoder =
                new ForcedEncoderFactory(/* fledgeEnableForcedEncodingAfterSignalsUpdate= */ false)
                        .createInstance();

        assertThat(forcedEncoder).isInstanceOf(ForcedEncoderNoOpImpl.class);
    }
}
