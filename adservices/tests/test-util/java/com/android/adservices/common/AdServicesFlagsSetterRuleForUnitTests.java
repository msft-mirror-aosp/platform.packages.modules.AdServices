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

package com.android.adservices.common;

import android.os.Build;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.NameValuePair;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base class of {@code FlagsSetterRule} to be used by unit tests.
 *
 * <p>It won't use {@code DeviceConfig} directly, but rather stub it (using mocks or fake flags).
 *
 * @param <R> concrete rule implementation
 * @param <F> type of flags implementation used by the rule.
 */
abstract class AdServicesFlagsSetterRuleForUnitTests<
                R extends AdServicesFlagsSetterRuleForUnitTests<R, F>, F extends Flags>
        extends AbstractAdServicesFlagsSetterRule<R> {

    protected final F mFlags;

    protected AdServicesFlagsSetterRuleForUnitTests(F flags, Consumer<NameValuePair> flagsSetter) {
        super(AndroidLogger.getInstance(), flagsSetter);
        mFlags = Objects.requireNonNull(flags, "flags cannot be null");
        mLog.d("Constructed for %s", flags);
    }

    /**
     * Gets the flags implementation.
     *
     * <p>Typically used by test classes to pass to the object under test.
     */
    public final F getFlags() {
        return mFlags;
    }

    @Override
    protected final int getDeviceSdk() {
        return Build.VERSION.SDK_INT;
    }
}
