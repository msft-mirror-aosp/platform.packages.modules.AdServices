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

package com.android.adservices.errorlogging;

import android.annotation.Nullable;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.errorlogging.AbstractAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.ErrorCodeSampler;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** AdServices implementation of {@link AbstractAdServicesErrorLogger}. */
public final class AdServicesErrorLoggerImpl extends AbstractAdServicesErrorLogger {
    private static final Object SINGLETON_LOCK = new Object();
    private static volatile AdServicesErrorLoggerImpl sSingleton;
    private final Flags mFlags;

    @Nullable private final ErrorCodeSampler mErrorCodeSampler;

    public static AdServicesErrorLoggerImpl getInstance() {
        if (sSingleton == null) {
            synchronized (SINGLETON_LOCK) {
                if (sSingleton == null) {
                    sSingleton =
                            new AdServicesErrorLoggerImpl(
                                    FlagsFactory.getFlags(),
                                    StatsdAdServicesErrorLoggerImpl.getInstance());
                }
            }
        }
        return sSingleton;
    }

    @VisibleForTesting
    AdServicesErrorLoggerImpl(
            Flags flags, StatsdAdServicesErrorLogger statsdAdServicesErrorLogger) {
        this(
                flags,
                statsdAdServicesErrorLogger,
                flags.getCustomErrorCodeSamplingEnabled() ? new ErrorCodeSampler(flags) : null);
    }

    @VisibleForTesting
    AdServicesErrorLoggerImpl(
            Flags flags,
            StatsdAdServicesErrorLogger statsdAdServicesErrorLogger,
            @Nullable ErrorCodeSampler errorCodeSampler) {
        super(statsdAdServicesErrorLogger);
        mFlags = Objects.requireNonNull(flags);
        mErrorCodeSampler = errorCodeSampler;
    }

    /**
     * Returns {@code true} if error code is not part of deny list and one of these conditions is
     * met.
     *
     * <ul>
     *   <li>Error code sampler is disabled and initialized to {@code null}.
     *   <li>Error code sampler is enabled and {@code ErrorCodeSampler#shouldLog} returns {@code
     *       true}.
     * </ul>
     */
    @Override
    protected boolean isEnabled(int errorCode) {
        boolean logBasedOnCustomSampleInterval =
                mErrorCodeSampler == null || mErrorCodeSampler.shouldLog(errorCode);
        // TODO(b/332599638): Deprecate error code deny list once custom sampling is launched.
        return !mFlags.getErrorCodeLoggingDenyList().contains(errorCode)
                && logBasedOnCustomSampleInterval;
    }
}
