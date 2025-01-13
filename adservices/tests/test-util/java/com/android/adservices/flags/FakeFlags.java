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
package com.android.adservices.flags;

import static com.android.adservices.flags.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.flags.FlagsBackend;
import com.android.adservices.shared.testing.AndroidLogger;
import com.android.adservices.shared.testing.Identifiable;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * TODO(b/384798806): this class should be package-protected and used by only by the flags-setter
 * rule, but it's currently public because the rule is located in a different package - we should
 * move all related classes to a common .something.flags package instead...
 */
public final class FakeFlags extends RawFlags implements Identifiable {

    private static int sNextId;

    private final String mId = String.valueOf(++sNextId);
    private final boolean mCalledByRule;

    private FakeFlags(boolean calledByRule) {
        this(new FakeFlagsBackend(), calledByRule);
    }

    private FakeFlags(FlagsBackend backend, boolean calledByRule) {
        super(backend);
        mCalledByRule = calledByRule;
    }

    private FakeFlagsBackend getFakeFlagsBackend() {
        return (FakeFlagsBackend) mBackend;
    }

    /** TODO(b/384798806): make it package protected. */
    public static FakeFlags createFakeFlagsForFlagSetterRulePurposesOnly() {
        return new FakeFlags(/* calledByRule= */ true);
    }

    /** TODO(b/384798806): make it package protected. */
    public static FakeFlags createFakeFlagsForFakeFlagsFactoryPurposesOnly() {
        return new FakeFlags(/* calledByRule= */ false).setFakeFlagsFactoryFlags();
    }

    /** Should only be called by the rule */
    public Consumer<NameValuePair> getFlagsSetter() {
        assertCalledByRule();
        return getFakeFlagsBackend();
    }

    /** Should only be called by the rule */
    public void setFlag(String name, String value) {
        assertCalledByRule();
        getFakeFlagsBackend().setFlag(name, value);
    }

    /** Should only be called by the rule */
    public void setMissingFlagBehavior(MissingFlagBehavior behavior) {
        assertCalledByRule();
        getFakeFlagsBackend().mBehavior =
                Objects.requireNonNull(behavior, "behavior cannot be null");
    }

    /** Should only be called by the rule */
    public MissingFlagBehavior getMissingFlagBehavior() {
        assertCalledByRule();
        // TODO Auto-generated method stub
        return getFakeFlagsBackend().mBehavior;
    }

    /** Should only be called by the rule */
    public Flags getSnapshot() {
        assertCalledByRule();
        Map<String, NameValuePair> flags = getFakeFlagsBackend().mFlags;
        return new FakeFlags(new FakeFlagsBackend(new HashMap<>(flags)), /* calledByRule= */ true);
    }

    private FakeFlags setFakeFlagsFactoryFlags() {
        var backend = getFakeFlagsBackend();
        AdServicesFlagsSetterRuleForUnitTests.setFakeFlagsFactoryFlags(
                (name, value) -> backend.setFlag(name, value));
        return this;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String toString() {
        var prefix = "FakeFlags#" + mId + "{";
        var flags = getFakeFlagsBackend().mFlags;
        if (flags.isEmpty()) {
            return prefix + "empty}";
        }
        return flags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // sort by key
                .map(entry -> entry.getValue().toString())
                .collect(Collectors.joining(", ", prefix, "}"));
    }

    // TODO(b/384798806): remove this method (and callers) when stuff moved to the same package. */
    private void assertCalledByRule() {
        if (!mCalledByRule) {
            throw new UnsupportedOperationException(
                    "Can only be called when used by a flag setter rule");
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

        void setFlag(String name, String value) {
            accept(new NameValuePair(name, value));
        }
    }
}
