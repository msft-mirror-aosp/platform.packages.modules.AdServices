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

// TODO(b/373446366): it might be worth to merge it with NameValuePairSetter (and having just one)
/** Abstraction used to manage{@link NameValuePair} objects. */
public interface NameValuePairContainer extends NameValuePairSetter {

    /** Gets all values. */
    ImmutableMap<String, NameValuePair> getAll();

    /** Gets the NVP for the given name. */
    @Nullable
    NameValuePair get(String name);
}
