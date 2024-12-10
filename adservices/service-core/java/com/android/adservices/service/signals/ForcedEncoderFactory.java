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

import android.content.Context;

public class ForcedEncoderFactory {

    // Flags determining which ForcedEncoder to create.
    private final boolean mFledgeEnableForcedEncodingAfterSignalsUpdate;
    private final long mFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds;
    private final Context mContext;

    /**
     * Constructs and initializes a {@link ForcedEncoderFactory} with configuration values to create
     * a relevant {@link ForcedEncoder}.
     */
    public ForcedEncoderFactory(
            boolean fledgeEnableForcedEncodingAfterSignalsUpdate,
            long fledgeForcedEncodingAfterSignalsUpdateCooldownSeconds,
            Context context) {
        mFledgeEnableForcedEncodingAfterSignalsUpdate =
                fledgeEnableForcedEncodingAfterSignalsUpdate;
        mFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds =
                fledgeForcedEncodingAfterSignalsUpdateCooldownSeconds;
        mContext = context;
    }

    /**
     * Returns a {@link ForcedEncoder} created based on configuration flags.
     *
     * @return a {@link ForcedEncoder}.
     */
    ForcedEncoder createInstance() {
        if (mFledgeEnableForcedEncodingAfterSignalsUpdate) {
            return new ForcedEncoderImpl(
                    mFledgeForcedEncodingAfterSignalsUpdateCooldownSeconds, mContext);
        }
        return new ForcedEncoderNoOpImpl();
    }
}
