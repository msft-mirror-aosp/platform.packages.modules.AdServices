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

package com.android.server.adservices.errorlogging;

import com.android.adservices.shared.errorlogging.AbstractAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLoggerImpl;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.Flags;
import com.android.server.adservices.FlagsFactory;

/**
 * AdServices System server error logger implementation of {@link AbstractAdServicesErrorLogger}.
 *
 * @hide
 */
public final class AdServicesErrorLoggerImpl extends AbstractAdServicesErrorLogger {
    private static final AdServicesErrorLogger INSTANCE =
            new AdServicesErrorLoggerImpl(
                    FlagsFactory.getFlags(), StatsdAdServicesErrorLoggerImpl.getInstance());

    private final Flags mFlags;

    @VisibleForTesting
    AdServicesErrorLoggerImpl(
            Flags flags, StatsdAdServicesErrorLogger statsdAdServicesErrorLogger) {
        super(statsdAdServicesErrorLogger);
        mFlags = flags;
    }

    public static AdServicesErrorLogger getInstance() {
        return INSTANCE;
    }

    @Override
    protected boolean isEnabled(int errorCode) {
        return mFlags.getEnableCelForSystemServer();
    }
}
