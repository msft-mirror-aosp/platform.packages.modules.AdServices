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
package com.android.adservices.shared.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** In-memory implementation of {@link NameValuePairContainer}. */
public final class FakeNameValuePairContainer implements NameValuePairContainer {

    private final Logger mLog;

    private final List<NameValuePair> mCalls = new ArrayList<>();

    private final Map<String, NameValuePair> mMap = new LinkedHashMap<>();
    private final Map<String, RuntimeException> mOnSetExceptions = new LinkedHashMap<>();

    /** Default constructor. */
    public FakeNameValuePairContainer() {
        this(FakeNameValuePairContainer.class.getSimpleName());
    }

    /**
     * Custom constructor.
     *
     * @param tagName name of the tag used for logging purposes
     */
    public FakeNameValuePairContainer(String tagName) {
        mLog = new Logger(DynamicLogger.getInstance(), tagName);
    }

    @Override
    public NameValuePair set(NameValuePair nvp) {
        Objects.requireNonNull(nvp, "nvp cannot be null");
        String name = nvp.name;
        var previous = mMap.get(name);
        RuntimeException exception = mOnSetExceptions.get(name);
        mLog.d("set(%s): previous=%s, exception=%s", nvp, previous, exception);
        if (exception != null) {
            throw exception;
        }
        mCalls.add(nvp);
        if (nvp.value == null) {
            mLog.i("Removing %s", name);
            mMap.remove(name);
        } else {
            mLog.i("Adding %s -> %s", name, nvp);
            mMap.put(name, nvp);
        }
        return previous;
    }

    /**
     * Sets {@link #set(NameValuePair)} to throw {@code exception} when called with the given name.
     */
    public void onSetThrows(String name, RuntimeException exception) {
        mLog.d("onSetThrows(%s. %s)", name, exception);
        mOnSetExceptions.put(name, exception);
    }

    /** Gets the value for the given name. */
    @Nullable
    public NameValuePair get(String name) {
        return mMap.get(name);
    }

    /** Gets all values. */
    public ImmutableMap<String, NameValuePair> getAll() {
        return ImmutableMap.copyOf(mMap);
    }

    /** Gets all calls receive so far. */
    public ImmutableList<NameValuePair> getCalls() {
        return ImmutableList.copyOf(mCalls);
    }

    /** Gets all calls receive so far, and resets them internally. */
    public ImmutableList<NameValuePair> getAndResetCalls() {
        var calls = getCalls();
        mCalls.clear();
        return calls;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[mTag="
                + mLog.getTag()
                + ", mCalls="
                + getCalls()
                + ", mMap="
                + getAll()
                + ", mOnSetExceptions="
                + mOnSetExceptions
                + ']';
    }
}
