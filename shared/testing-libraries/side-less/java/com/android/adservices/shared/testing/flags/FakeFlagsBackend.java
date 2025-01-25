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

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.FakeNameValuePairContainer;
import com.android.adservices.shared.testing.ImmutableNameValuePairContainer;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairContainer;
import com.android.adservices.shared.testing.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** In-memory container for flag-related backends. */
public final class FakeFlagsBackend implements TestableFlagsBackend {

    private final NameValuePairContainer mContainer;
    private final Logger mLog;

    private MissingFlagBehavior mBehavior = USES_EXPLICIT_DEFAULT;

    private final Map<String, String> mThrowReasons = new HashMap<>();

    /**
     * Default constructor.
     *
     * @param tagName tag used to log messages.
     */
    public FakeFlagsBackend(String tagName) {
        this(
                new Logger(DynamicLogger.getInstance(), tagName),
                new FakeNameValuePairContainer(tagName));
    }

    /**
     * Default constructor.
     *
     * @param clazz used to derive the tag name
     */
    public FakeFlagsBackend(Class<?> clazz) {
        this(Objects.requireNonNull(clazz, "clazz cannot be null").getSimpleName());
    }

    /**
     * Custom constructor.
     *
     * @param logger used to log messages.
     * @param container used to manage the flags.
     */
    public FakeFlagsBackend(Logger logger, NameValuePairContainer container) {
        mContainer = Objects.requireNonNull(container, "container cannot be null");
        mLog = Objects.requireNonNull(logger, "logger cannot be null");
    }

    // Constructor used to create a snapshot
    private FakeFlagsBackend(FakeFlagsBackend source) {
        this(source.mLog, new ImmutableNameValuePairContainer(source.mContainer.getAll()));
    }

    @VisibleForTesting
    String getTagName() {
        return mLog.getTag();
    }

    // TODO(b/373446366): ideally it should be @Visible for testing, but it's used by
    // AdServicesFakeFlagsSetterRule's constructor and it would require changing too many classes to
    // encapsulate that, so it's not worth the effort (at least not for now)
    public NameValuePairContainer getContainer() {
        return mContainer;
    }

    /** Gets a {@link FakeFlagsBackend} that has the same flags, but it's immutable. */
    public FakeFlagsBackend cloneForSnapshot() {
        return new FakeFlagsBackend(this);
    }

    /** Gets a snapshot of the current flags. */
    public ImmutableMap<String, NameValuePair> getFlags() {
        return mContainer.getAll();
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

    @Override
    public void onGetFlagThrows(String name, String reason) {
        mLog.v("onGetFlagThrows(%s, %s)", name, reason);
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");

        mThrowReasons.put(name, reason);
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

    @Nullable
    private NameValuePair getFlagChecked(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        String throwReason = mThrowReasons.get(name);
        if (throwReason != null) {
            throw new UnsupportedOperationException(
                    String.format(Locale.ENGLISH, UNSUPPORTED_TEMPLATE, name, throwReason));
        }
        return mContainer.get(name);
    }

    @Override
    public void setFlag(String name, @Nullable String value) {
        mLog.v("setFlag(%s, %s)", name, value);
        mContainer.set(new NameValuePair(name, value));
    }
}
