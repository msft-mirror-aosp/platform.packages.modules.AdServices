/*
 * Copyright (C) 2023 The Android Open Source Project
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

import java.util.Objects;

/** Simple name-value pair, like a flag or system property. */
public final class NameValuePair {

    public final String name;
    public final @Nullable String value;
    public final @Nullable String separator;

    public NameValuePair(String name, @Nullable String value, @Nullable String separator) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.value = value;
        this.separator = separator;
    }

    public NameValuePair(String name, @Nullable String value) {
        this(name, value, /* separator= */ null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        NameValuePair other = (NameValuePair) obj;
        return Objects.equals(name, other.name) && Objects.equals(value, other.value);
    }

    @Override
    public String toString() {
        StringBuilder string = new StringBuilder(name).append('=').append(value);
        if (separator != null) {
            string.append(" (separator=").append(separator).append(')');
        }
        return string.toString();
    }

    /** Used to filter {@link NameValuePair} instances. */
    public interface Matcher {
        /** Checks whether the given pair matches the filter. */
        boolean matches(NameValuePair pair);
    }
}
