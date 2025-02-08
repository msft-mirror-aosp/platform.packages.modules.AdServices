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

package com.android.adservices.metriclogger;

import static com.android.adservices.service.FlagsConstants.KEY_AD_SERVICES_JOB_EXECUTION_SAMPLING_CONFIG;

import android.util.Base64;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.proto.LogSamplingConfig;

import com.google.protobuf.MessageLite;

import org.junit.Test;
import org.mockito.Mock;

public final class SamplingConfigFlagReaderTest extends AdServicesExtendedMockitoTestCase {

    @Mock private AdServicesErrorLogger mMockAdServicesErrorLogger;

    @Test
    public void testGetSamplingConfigOrDefault_defaultProto() {
        flags.setFlag(KEY_AD_SERVICES_JOB_EXECUTION_SAMPLING_CONFIG, "");

        expect.that(
                        SamplingConfigFlagReader.getSamplingConfigOrDefault(
                                mFakeFlags::getAdServicesJobExecutionSamplingConfig,
                                mMockAdServicesErrorLogger))
                .isEqualTo(LogSamplingConfig.getDefaultInstance());
    }

    @Test
    public void testGetSamplingConfigOrDefault() {
        LogSamplingConfig configProto =
                LogSamplingConfig.newBuilder()
                        .setPerEventSampling(
                                LogSamplingConfig.PerEventSampling.newBuilder()
                                        .setSamplingRate(0.5)
                                        .build())
                        .build();
        flags.setFlag(
                KEY_AD_SERVICES_JOB_EXECUTION_SAMPLING_CONFIG,
                getBase64EncodedString(configProto, Base64.DEFAULT));

        expect.that(
                        SamplingConfigFlagReader.getSamplingConfigOrDefault(
                                mFakeFlags::getAdServicesJobExecutionSamplingConfig,
                                mMockAdServicesErrorLogger))
                .isEqualTo(configProto);
    }

    @Test
    public void testGetSamplingConfigOrDefault_invalidInput() {
        flags.setFlag(KEY_AD_SERVICES_JOB_EXECUTION_SAMPLING_CONFIG, "xyz");

        expect.that(
                        SamplingConfigFlagReader.getSamplingConfigOrDefault(
                                mFakeFlags::getAdServicesJobExecutionSamplingConfig,
                                mMockAdServicesErrorLogger))
                .isEqualTo(LogSamplingConfig.getDefaultInstance());
    }

    // Converts proto to a Base64 encoded string.
    private static <T extends MessageLite> String getBase64EncodedString(T value, int flag) {
        return Base64.encodeToString(value.toByteArray(), flag);
    }
}
