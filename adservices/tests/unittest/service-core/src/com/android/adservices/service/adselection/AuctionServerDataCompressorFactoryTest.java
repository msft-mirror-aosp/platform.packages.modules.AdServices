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

import org.junit.Assert;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public class AuctionServerDataCompressorFactoryTest {
    private static final int VALID_VERSION = AuctionServerDataCompressorV0.VERSION;
    private static final int INVALID_VERSION = Integer.MAX_VALUE;

    @Test
    public void testFactory_validVersion_returnImplementationSuccess() {
        AuctionServerDataCompressor compressor =
                AuctionServerDataCompressorFactory.getAuctionServerDataCompressor(VALID_VERSION);
        Assert.assertTrue(compressor instanceof AuctionServerDataCompressorV0);
    }

    @Test
    public void testFactory_invalidVersion_throwsExceptionFailure() {
        ThrowingRunnable runnable =
                () ->
                        AuctionServerDataCompressorFactory.getAuctionServerDataCompressor(
                                INVALID_VERSION);
        Assert.assertThrows(
                String.format(NO_IMPLEMENTATION_FOUND, INVALID_VERSION),
                IllegalArgumentException.class,
                runnable);
    }
}
