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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairSetter;
import com.android.adservices.shared.testing.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FakeNameValuePairSetter implements NameValuePairSetter {

    private final Logger mLog = new Logger(DynamicLogger.getInstance(), getClass());

    private final List<NameValuePair> mCalls = new ArrayList<>();

    private final Map<String, NameValuePair> mMap = new LinkedHashMap<>();
    private final Map<String, RuntimeException> mOnSetExceptions = new LinkedHashMap<>();

    @Override
    public NameValuePair set(NameValuePair nvp) {
        Objects.requireNonNull(nvp, "nvp cannot be null");
        var previous = mMap.get(nvp.name);
        RuntimeException exception = mOnSetExceptions.get(nvp.name);
        mLog.d("set(%s): previous=%s, exception=%s", nvp, previous, exception);
        if (exception != null) {
            throw exception;
        }
        mCalls.add(nvp);
        mMap.put(nvp.name, nvp);
        return previous;
    }

    @Override
    public void remove(String name) {
        var removed = mMap.remove(name);
        mLog.i("remove(%s): removed %s", name, removed);
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
    public ImmutableList<NameValuePair> getAll() {
        return ImmutableList.copyOf(mMap.values());
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
                + "[mCalls="
                + mCalls
                + ", mMap="
                + mMap
                + ", mOnSetExceptions="
                + mOnSetExceptions
                + ']';
    }
}
