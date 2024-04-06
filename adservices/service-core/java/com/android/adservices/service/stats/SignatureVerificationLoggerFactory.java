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

package com.android.adservices.service.stats;

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;

import java.util.Objects;

/** Logger factory for {@link SignatureVerificationLogger} */
public class SignatureVerificationLoggerFactory {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String NO_OP_LOGGER =
            "FledgeAdSelectionContextualAdsMetricsEnabled flag is off NoOp logger is returned.";
    @NonNull private final AdServicesLogger mAdServicesLogger;
    private final boolean mIsContextualAdsMetricsEnabled;

    public SignatureVerificationLoggerFactory(
            @NonNull AdServicesLogger adServicesLogger, @NonNull Flags flags) {
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);

        mAdServicesLogger = adServicesLogger;
        mIsContextualAdsMetricsEnabled = flags.getFledgeAdSelectionContextualAdsMetricsEnabled();
    }

    /** Returns a {@link SignatureVerificationLogger} implementation. */
    public SignatureVerificationLogger getInstance() {
        if (mIsContextualAdsMetricsEnabled) {
            return new SignatureVerificationLoggerImpl(mAdServicesLogger);
        } else {
            sLogger.v(NO_OP_LOGGER);
            return new SignatureVerificationLoggerNoOp();
        }
    }
}
