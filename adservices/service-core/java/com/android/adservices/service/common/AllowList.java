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

package com.android.adservices.service.common;

import android.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class that uses both an allow list and deny list for a feature's enablement.
 *
 * <p>The default condition to determine a feature is allowed is if it is NOT in the deny list and
 * is in the allow list.
 */
public final class AllowList {
    @VisibleForTesting public static final String DEFAULT_DELIMITER = ",";
    @VisibleForTesting public static final String DEFAULT_SET_ALL_STRING = "*";

    private final Set<String> mAllowSet;
    private final Set<String> mDenySet;
    private final boolean mIsAllowAll;
    private final boolean mIsDenyAll;

    /** Creates an {@link AllowList}. */
    public AllowList(
            @Nullable String allowList,
            @Nullable String denyList,
            String delimiter,
            String setAllString) {
        if (allowList == null) {
            allowList = "";
        }
        if (denyList == null) {
            denyList = "";
        }
        Objects.requireNonNull(delimiter);
        Objects.requireNonNull(setAllString);

        mIsAllowAll = allowList.equals(setAllString);
        mIsDenyAll = denyList.equals(setAllString);

        mAllowSet =
                Arrays.stream(allowList.split(delimiter))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
        mDenySet =
                Arrays.stream(denyList.split(delimiter))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
    }

    /**
     * Creates an {@link AllowList} with {@link #DEFAULT_DELIMITER} and {@link
     * #DEFAULT_SET_ALL_STRING}.
     */
    public AllowList(String allowList, String denyList) {
        this(allowList, denyList, DEFAULT_DELIMITER, DEFAULT_SET_ALL_STRING);
    }

    /**
     * Checks if the feature is allowed with the default condition: A feature is allowed only if it
     * isn't denied and is allowed.
     *
     * @param name the feature to check if it's allowed.
     * @return if the feature is allowed.
     */
    public boolean isAllowed(String name) {
        return !mIsDenyAll && !mDenySet.contains(name) && (mIsAllowAll || mAllowSet.contains(name));
    }

    /** Returns if {@link #mIsAllowAll} is set. */
    public boolean isAllowAll() {
        return mIsAllowAll;
    }

    /** Returns if {@link #mIsDenyAll} is set. */
    public boolean isDenyAll() {
        return mIsDenyAll;
    }

    /** Returns all elements in {@link #mAllowSet}. */
    public Set<String> getAllowSet() {
        return mAllowSet;
    }

    /** Returns all elements in {@link #mDenySet}. */
    public Set<String> getDenySet() {
        return mDenySet;
    }
}
