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
package com.android.adservices.flags;

import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.FakeNameValuePairContainer;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePairContainer;
import com.android.adservices.shared.testing.flags.FakeFlagsBackend;

import com.google.common.annotations.VisibleForTesting;

/** {@code DebugFlagsSetterRule} that uses a fake {@link FakeDebugFlags} implementation. */
public final class AdServicesFakeDebugFlagsSetterRule
        extends AbstractAdServicesDebugFlagsSetterRule<
                AdServicesFakeDebugFlagsSetterRule, FakeDebugFlags> {

    private static final Logger sLogger =
            new Logger(AndroidLogger.getInstance(), AdServicesFakeDebugFlagsSetterRule.class);

    private final FakeDebugFlags mDebugFlags;

    public AdServicesFakeDebugFlagsSetterRule() {
        this(new FakeNameValuePairContainer(FakeDebugFlags.TAG));
    }

    @VisibleForTesting
    protected AdServicesFakeDebugFlagsSetterRule(NameValuePairContainer container) {
        this(new FakeDebugFlags(new FakeFlagsBackend(sLogger, container)), container);
    }

    private AdServicesFakeDebugFlagsSetterRule(
            FakeDebugFlags debugFlags, NameValuePairContainer container) {
        super(AndroidLogger.getInstance(), container);
        mDebugFlags = debugFlags;
    }

    @Override
    public FakeDebugFlags getDebugFlags() {
        return mDebugFlags;
    }
}
