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

import com.android.adservices.common.AdServicesFakeFlagsSetterRule.FakeFlags;
import com.android.adservices.service.RawFlags;
import com.android.adservices.shared.flags.FlagsBackend;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** {@code FlagsSetterRule} that uses a fake flags implementation. */
public final class AdServicesFakeFlagsSetterRule
        extends AdServicesFlagsSetterRuleForUnitTests<AdServicesFakeFlagsSetterRule, FakeFlags> {

    public AdServicesFakeFlagsSetterRule() {
        this(new FakeFlags());
    }

    private AdServicesFakeFlagsSetterRule(FakeFlags fakeFlags) {
        super(fakeFlags, fakeFlags.getFakeFlagsBackend());
    }

    // NOTE: this class is internal on purpose, so tests use the rule approach. But we could make it
    // standalone if needed (it must be public because it's used in the constructor that takes a
    // Consumer<NameValuePair> lambda, but it's constructor is private)
    public static final class FakeFlags extends RawFlags {

        private FakeFlags() {
            super(new FakeFlagsBackend());
        }

        private FakeFlagsBackend getFakeFlagsBackend() {
            return (FakeFlagsBackend) mBackend;
        }
    }

    private static class FakeFlagsBackend implements FlagsBackend, Consumer<NameValuePair> {

        private final Logger mLog =
                new Logger(AndroidLogger.getInstance(), AdServicesFakeFlagsSetterRule.class);

        private final Map<String, NameValuePair> mFlags = new HashMap<>();

        @Override
        public String getFlag(String name) {
            var flag = mFlags.get(name);
            return flag == null ? null : flag.value;
        }

        @Override
        public void accept(NameValuePair flag) {
            mLog.v("setFlag(%s)", flag);
            Objects.requireNonNull(flag, "internal error: NameValuePair cannot be null");
            mFlags.put(flag.name, flag);
        }
    }
}
