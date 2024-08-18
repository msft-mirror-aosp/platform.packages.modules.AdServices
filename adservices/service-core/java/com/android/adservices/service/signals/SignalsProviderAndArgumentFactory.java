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

import com.android.adservices.data.signals.ProtectedSignalsDao;

/** Factory for the appropriate {@link SignalsProvider} and {@link ProtectedSignalsArgument} */
public class SignalsProviderAndArgumentFactory {

    private final ProtectedSignalsDao mProtectedSignalsDao;
    private final boolean mPasEncodingJobImprovementsEnabled;

    public SignalsProviderAndArgumentFactory(
            ProtectedSignalsDao protectedSignalsDao, boolean pasEncodingJobImprovementsEnabled) {
        this.mProtectedSignalsDao = protectedSignalsDao;
        this.mPasEncodingJobImprovementsEnabled = pasEncodingJobImprovementsEnabled;
    }

    /** Gets the {@link SignalsProvider} */
    public SignalsProvider getSignalsProvider() {
        if (mPasEncodingJobImprovementsEnabled) {
            return new SignalsProviderFastImpl(mProtectedSignalsDao);
        }
        return new SignalsProviderImpl(mProtectedSignalsDao);
    }

    /** Gets the {@link ProtectedSignalsArgument} */
    public ProtectedSignalsArgument getProtectedSignalsArgument() {
        if (mPasEncodingJobImprovementsEnabled) {
            return new ProtectedSignalsArgumentFastImpl();
        }
        return new ProtectedSignalsArgumentImpl();
    }
}
