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
     * Sets a {@code nvp}, or remove it if its {@link NameValuePair#value value} is {@code null}.
     *
     * @return previous {@code nvp} for that {@code NameValuePair#name name}.
     */
    @Nullable
    NameValuePair set(NameValuePair nvp);
}
