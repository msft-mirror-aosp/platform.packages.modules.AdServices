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

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.LoggerFactory;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

public class ForcedEncoderNoOpImpl implements ForcedEncoder {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    // TODO(b/380936203): Split into individual methods when we have individual implementations for
    //   raw signals encoding and encoders update.
    /**
     * Forces encoding and updaters encoders.
     *
     * @return a {@link FluentFuture} that completes when the encoding and encoder updates are
     *     complete. The future's result is {@code null}.
     */
    public FluentFuture<Boolean> forceEncodingAndUpdateEncoderForBuyer(AdTechIdentifier buyer) {
        sLogger.v("Forced encoding is disabled.");

        // No-op.
        return FluentFuture.from(Futures.immediateFuture(false));
    }
}
