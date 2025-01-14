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

/** Abstraction used to set {@link NameValuePair} objects. */
public interface NameValuePairSetter {

    /**
     * Sets a {@code nvp}.
     *
     * @return previous {@code nvp} for that {@code NameValuePair#name name}.
     */
    @Nullable
    NameValuePair set(NameValuePair nvp);

    /** Removes the {@code nvp} with the given name. */
    default void remove(String name) {
        new Logger(DynamicLogger.getInstance(), NameValuePairSetter.class)
                .w("remove(%s): not implemented by subclass, setting to null instead", name);
        set(new NameValuePair(name, /* value= */ null));
    }

    // TODO(b/373446366): for now it's only used to set, but eventually we might need to add other
    // methods like reset all, or even a more generic interface (like NameValuePairManager /
    // NameValuePairContainer). But even we add a more generic one, it might still be useful /
    // cleaner to keep this one (for example, the new interface could extend this one).
}
