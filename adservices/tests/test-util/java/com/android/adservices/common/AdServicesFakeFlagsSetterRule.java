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

import static com.android.adservices.common.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;

import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.RawFlags;
import com.android.adservices.shared.flags.FlagsBackend;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** {@code FlagsSetterRule} that uses a fake flags implementation. */
public final class AdServicesFakeFlagsSetterRule
        extends AdServicesFlagsSetterRuleForUnitTests<AdServicesFakeFlagsSetterRule> {

    private final FakeFlagsBackend mFakeFlagsBackend;

    public AdServicesFakeFlagsSetterRule() {
        this(new FakeFlags());
    }

    private AdServicesFakeFlagsSetterRule(FakeFlags fakeFlags) {
        super(fakeFlags, fakeFlags.getFakeFlagsBackend());
        mFakeFlagsBackend = fakeFlags.getFakeFlagsBackend();
    }

    @Override
    public AdServicesFakeFlagsSetterRule setMissingFlagBehavior(MissingFlagBehavior behavior) {
        mLog.i("setMissingFlagBehavior(): from %s to %s", mFakeFlagsBackend.mBehavior, behavior);
        mFakeFlagsBackend.mBehavior = Objects.requireNonNull(behavior, "behavior cannot be null");
        return getThis();
    }

    @Override
    public Flags getFlagsSnapshot() {
        var flags = mFakeFlagsBackend.mFlags;
        mLog.v("getFlagsSnapshot(): cloning %s", flags);
        if (!isRunning()) {
            throw new IllegalStateException("getFlagsSnapshot() can only be called inside a test");
        }
        return new FakeFlags(new FakeFlagsBackend(new HashMap<>(mFakeFlagsBackend.mFlags)));
    }

    @VisibleForTesting
    MissingFlagBehavior getMissingFlagBehavior() {
        return mFakeFlagsBackend.mBehavior;
    }

    // NOTE: this class is internal on purpose, so tests use the rule approach. But we could make it
    // standalone if needed (it must be public because it's used in the constructor that takes a
    // Consumer<NameValuePair> lambda, but it's constructor is private)
    public static final class FakeFlags extends RawFlags {

        // TODO(b/384798806): make it package protected once FakeFlagsFactory doesn't use it anymore
        // (need to refactor tests to use AdServicesFakeFlagsSetterRule first)
        public FakeFlags() {
            this(new FakeFlagsBackend());
        }

        private FakeFlags(FlagsBackend backend) {
            super(backend);
        }

        private FakeFlagsBackend getFakeFlagsBackend() {
            return (FakeFlagsBackend) mBackend;
        }

        // NOTE: public because it's used by FakeFlagsFactory.getFlagsForTest()
        /**
         * Set flags that used to be set by {@code FakeFlagsFactory.TestFlags}.
         *
         * @deprecated tests should use {@link FakeFlagsFactory.SetFakeFlagsFactoryFlags} instead.
         */
        @Deprecated
        public FakeFlags setFakeFlagsFactoryFlags() {
            var backend = getFakeFlagsBackend();
            AdServicesFlagsSetterRuleForUnitTests.setFakeFlagsFactoryFlags(
                    (name, value) -> backend.setFlag(name, value));
            return this;
        }

        @Override
        public String toString() {
            var flags = getFakeFlagsBackend().mFlags;
            if (flags.isEmpty()) {
                return "FakeFlags{empty}";
            }
            return flags.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // sort by key
                    .map(entry -> entry.getValue().toString())
                    .collect(Collectors.joining(", ", "FakeFlags{", "}"));
        }
    }

    private static class FakeFlagsBackend implements FlagsBackend, Consumer<NameValuePair> {
        private final Map<String, NameValuePair> mFlags;

        private final Logger mLog = new Logger(AndroidLogger.getInstance(), "FakeFlags");

        private MissingFlagBehavior mBehavior = USES_EXPLICIT_DEFAULT;

        private FakeFlagsBackend() {
            this(new HashMap<>());
        }

        private FakeFlagsBackend(Map<String, NameValuePair> flags) {
            mFlags = flags;
        }

        @Override
        public String getFlag(String name) {
            throw new UnsupportedOperationException(
                    "INTERNAL ERROR: getFlag("
                            + name
                            + ") called when all methods should have been overridden!");
        }

        @Override
        public boolean getFlag(String name, boolean defaultValue) {
            var flag = getFlagChecked(name);
            if (flag == null) {
                var value = isMockingMode(name) ? false : defaultValue;
                mLog.w("getFlag(%s, %b): returning %b for missing flag", name, defaultValue, value);
                return value;
            }
            var value = Boolean.parseBoolean(flag.value);
            mLog.v("getFlag(%s, %b): returning %b", name, defaultValue, value);
            return value;
        }

        @Override
        public String getFlag(String name, String defaultValue) {
            var flag = getFlagChecked(name);
            if (flag == null) {
                var value = isMockingMode(name) ? null : defaultValue;
                mLog.w("getFlag(%s, %s): returning %s for missing flag", name, defaultValue, value);
                return value;
            }
            mLog.v("getFlag(%s, %s): returning %s", name, defaultValue, flag.value);
            return flag.value;
        }

        @Override
        public int getFlag(String name, int defaultValue) {
            var flag = getFlagChecked(name);
            if (flag == null) {
                var value = isMockingMode(name) ? 0 : defaultValue;
                mLog.w("getFlag(%s, %d): returning %d for missing flag", name, defaultValue, value);
                return value;
            }
            var value = Integer.parseInt(flag.value);
            mLog.v("getFlag(%s, %d): returning %d", name, defaultValue, value);
            return value;
        }

        @Override
        public long getFlag(String name, long defaultValue) {
            var flag = getFlagChecked(name);
            if (flag == null) {
                var value = isMockingMode(name) ? 0 : defaultValue;
                mLog.w("getFlag(%s, %d): returning %d for missing flag", name, defaultValue, value);
                return value;
            }
            var value = Long.parseLong(flag.value);
            mLog.v("getFlag(%s, %d): returning %d", name, defaultValue, value);
            return value;
        }

        @Override
        public float getFlag(String name, float defaultValue) {
            var flag = getFlagChecked(name);
            if (flag == null) {
                var value = isMockingMode(name) ? 0 : defaultValue;
                mLog.w("getFlag(%s, %f): returning %f for missing flag", name, defaultValue, value);
                return value;
            }
            var value = Float.parseFloat(flag.value);
            mLog.v("getFlag(%s, %f): returning %f", name, defaultValue, value);
            return value;
        }

        private boolean isMockingMode(String name) {
            switch (mBehavior) {
                case THROWS_EXCEPTION:
                    throw new IllegalStateException("Value of flag " + name + " not set");
                case USES_EXPLICIT_DEFAULT:
                    return false;
                case USES_JAVA_LANGUAGE_DEFAULT:
                    return true;
                default:
                    throw new UnsupportedOperationException("Unexpected behavior: " + mBehavior);
            }
        }

        private NameValuePair getFlagChecked(String name) {
            Objects.requireNonNull(name, "name cannot be null");
            var value = mFlags.get(name);
            return value == null ? null : value;
        }

        @Override
        public void accept(NameValuePair flag) {
            mLog.v("setFlag(%s)", flag);
            Objects.requireNonNull(flag, "internal error: NameValuePair cannot be null");
            mFlags.put(flag.name, flag);
        }

        private void setFlag(String name, String value) {
            accept(new NameValuePair(name, value));
        }
    }
}
