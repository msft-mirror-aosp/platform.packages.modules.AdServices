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

package com.android.adservices.service.common;

/**
 * A 4-argument Consumer.
 *
 * @param <A> var1
 * @param <B> var2
 * @param <C> var3
 * @param <D> var4
 */
public interface QuadConsumer<A, B, C, D> {
    /**
     * Function that consumes 4 variables.
     *
     * @param var1 var1
     * @param var2 var2
     * @param var3 var3
     * @param var4 var4
     */
    void accept(A var1, B var2, C var3, D var4);
}
