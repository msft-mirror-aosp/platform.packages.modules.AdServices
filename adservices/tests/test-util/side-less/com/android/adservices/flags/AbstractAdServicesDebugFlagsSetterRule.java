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

import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairSetter;
import com.android.adservices.shared.testing.flags.AbstractDebugFlagsSetterRule;

/**
 * Base class for AdServices-specific rules - will be extended by CTS and Unit Test rules.
 *
 * @param <R> concrete rule class
 * @param <DF> {@code DebugFlags} implementation class (only available on device-side tests)
 */
abstract class AbstractAdServicesDebugFlagsSetterRule<
                R extends AbstractAdServicesDebugFlagsSetterRule<R, DF>, DF>
        extends AbstractDebugFlagsSetterRule<R> {

    protected AbstractAdServicesDebugFlagsSetterRule(
            RealLogger logger, NameValuePairSetter setter) {
        super(logger, setter);
    }

    /**
     * Gets the {@code DebugFlags} object associated managed by the rule.
     *
     * @throws UnsupportedOperationException on host-side tests.
     */
    public abstract DF getDebugFlags();

    /** Sets the value of a {@code DebugFlag} */
    public final void setDebugFlag(String name, boolean value) {
        mSetter.set(new NameValuePair(name, Boolean.toString(value)));
    }
}
