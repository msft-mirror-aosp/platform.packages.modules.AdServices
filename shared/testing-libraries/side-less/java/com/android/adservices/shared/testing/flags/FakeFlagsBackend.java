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
package com.android.adservices.shared.testing.flags;

import static com.android.adservices.shared.testing.flags.MissingFlagBehavior.USES_EXPLICIT_DEFAULT;

import com.android.adservices.shared.flags.FlagsBackend;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairSetter;
import com.android.adservices.shared.testing.Nullable;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** In-memory container for flag-related backends. */
public final class FakeFlagsBackend implements FlagsBackend, NameValuePairSetter {

    private final Map<String, NameValuePair> mFlags;
    private final Logger mLog;

    private MissingFlagBehavior mBehavior = USES_EXPLICIT_DEFAULT;

    /**
     * Default constructor.
     *
     * @param tagName tag used to log messages.
     */
    public FakeFlagsBackend(String tagName) {
        this(tagName, new HashMap<>());
    }

    /**
     * Custom constructor.
     *
     * @param tagName tag used to log messages.
     * @param initialFlags initial value of the flags.
     */
    public FakeFlagsBackend(String tagName, Map<String, NameValuePair> initialFlags) {
        Objects.requireNonNull(tagName, "tagName cannot be null");
        Objects.requireNonNull(initialFlags, "flags cannot be null");

        mFlags = new LinkedHashMap<>(initialFlags);
        mLog = new Logger(DynamicLogger.getInstance(), tagName);
    }

    /** Gets a snapshot of the current flags. */
    public ImmutableMap<String, NameValuePair> getSnapshot() {
        return ImmutableMap.copyOf(mFlags);
    }

    /** Gets the getters behaviors when the flag is missing. */
    public MissingFlagBehavior getMissingFlagBehavior() {
        return mBehavior;
    }

    /** Sets the getters behaviors when the flag is missing. */
    public void setMissingFlagBehavior(MissingFlagBehavior behavior) {
        mBehavior = Objects.requireNonNull(behavior, "behavior cannot be null");
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
    public NameValuePair set(NameValuePair flag) {
        Objects.requireNonNull(flag, "internal error: NameValuePair cannot be null");
        NameValuePair previous;
        if (flag.value == null) {
            previous = mFlags.remove(flag.name);
        } else {
            previous = mFlags.put(flag.name, flag);
        }
        mLog.v("set(%s): returning %s", flag, previous);
        return previous;
    }

    /** Sets the flag to the given value, or removes it if the value is {@code null}. */
    public void setFlag(String name, @Nullable String value) {
        mLog.v("set(%s, %s)", name, value);
        if (value == null) {
            mFlags.remove(name);
            return;
        }
        set(new NameValuePair(name, value));
    }
}
