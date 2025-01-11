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

import com.android.adservices.service.FakeFlags;
import com.android.adservices.service.Flags;

import com.google.common.annotations.VisibleForTesting;

/** {@code FlagsSetterRule} that uses a fake flags implementation. */
public final class AdServicesFakeFlagsSetterRule
        extends AdServicesFlagsSetterRuleForUnitTests<AdServicesFakeFlagsSetterRule> {

    public AdServicesFakeFlagsSetterRule() {
        this(FakeFlags.createFakeFlagsForFlagSetterRulePurposesOnly());
    }

    private AdServicesFakeFlagsSetterRule(FakeFlags fakeFlags) {
        super(fakeFlags, fakeFlags.getFlagsSetter());
    }

    private FakeFlags getFakeFlags() {
        return (FakeFlags) getFlags();
    }

    @Override
    public AdServicesFakeFlagsSetterRule setMissingFlagBehavior(MissingFlagBehavior behavior) {
        mLog.i("setMissingFlagBehavior(): from %s to %s", getMissingFlagBehavior(), behavior);
        getFakeFlags().setMissingFlagBehavior(behavior);
        return getThis();
    }

    @Override
    public Flags getFlagsSnapshot() {
        mLog.i("getFlagsSnapshot(): clonning %s", getFlags());
        if (!isRunning()) {
            throw new IllegalStateException("getFlagsSnapshot() can only be called inside a test");
        }
        return getFakeFlags().getSnapshot();
    }

    @VisibleForTesting
    MissingFlagBehavior getMissingFlagBehavior() {
        return getFakeFlags().getMissingFlagBehavior();
    }
}
