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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AuctionServerDataCompressorFactory.NO_IMPLEMENTATION_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_PARSING_RESPONSE_DATA_COMPRESSION_NOT_FOUND;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

@ExtendedMockitoRule.MockStatic(FlagsFactory.class)
public class AuctionServerDataCompressorFactoryTest extends AdServicesExtendedMockitoTestCase {
    private static final int VALID_VERSION = AuctionServerDataCompressorGzip.VERSION;
    private static final int INVALID_VERSION = Integer.MAX_VALUE;

    @Before
    public void setup() {
        mocker.mockGetFlags(mFakeFlags);
    }

    @Test
    public void testFactory_validVersion_returnImplementationSuccess() {
        AuctionServerDataCompressor compressor =
                AuctionServerDataCompressorFactory.getDataCompressor(VALID_VERSION);
        Assert.assertTrue(compressor instanceof AuctionServerDataCompressorGzip);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_PARSING_RESPONSE_DATA_COMPRESSION_NOT_FOUND,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE,
            throwable = IllegalArgumentException.class)
    public void testFactory_invalidVersion_throwsExceptionFailure() {
        ThrowingRunnable runnable =
                () -> AuctionServerDataCompressorFactory.getDataCompressor(INVALID_VERSION);
        Assert.assertThrows(
                String.format(NO_IMPLEMENTATION_FOUND, INVALID_VERSION),
                IllegalArgumentException.class,
                runnable);
    }
}
