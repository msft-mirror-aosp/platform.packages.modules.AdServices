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
package com.android.adservices.shared.testing.concurrency;

import com.android.adservices.shared.testing.Identifiable;

/**
 * Hack to avoid assertion failures when checking logged messages, as the number of calls could be
 * changed.
 */
interface FreezableToString extends Identifiable {

    /**
     * When called, {@code toString()} should return something like {@code
     * FREOZEN[simpleClassName#id]}.
     */
    void freezeToString();
}
