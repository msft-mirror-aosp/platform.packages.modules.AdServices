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
package com.android.adservices.shared.testing;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** {@link NameValuePairContainer} implementation for snapshots. */
public final class ImmutableNameValuePairContainer implements NameValuePairContainer {

    private final ImmutableMap<String, NameValuePair> mNvps;

    /**
     * Default constructor.
     *
     * @param source source of values, they'll be copied over
     */
    public ImmutableNameValuePairContainer(Map<String, NameValuePair> source) {
        Objects.requireNonNull(source, "source cannot be null");
        mNvps = ImmutableMap.copyOf(source);
    }

    @Override
    public NameValuePair set(NameValuePair nvp) {
        throw new UnsupportedOperationException("cannot set " + nvp + " on immutable container");
    }

    @Override
    public ImmutableMap<String, NameValuePair> getAll() {
        return mNvps;
    }

    @Override
    public NameValuePair get(String name) {
        return mNvps.get(name);
    }

    @Override
    public String toString() {
        // NOTE: logic below was copied from FakeFlags, it might be worth to add to a helper
        var prefix = "ImmutableNameValuePairContainer{";

        if (mNvps.isEmpty()) {
            return prefix + "empty}";
        }
        return mNvps.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // sort by key
                .map(entry -> entry.getValue().toString())
                .collect(Collectors.joining(", ", prefix, "}"));
    }
}
