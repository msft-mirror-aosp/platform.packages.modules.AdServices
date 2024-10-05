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
package com.android.adservices.mockito;

import android.util.Log;

import com.android.adservices.shared.testing.mockito.MockitoHelper;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

/** Base implementation for mockers. */
public abstract class AbstractMocker {

    static final String TAG = "Mocker";

    // TODO(b/338132355): pass object that gets test name on constructor
    protected final String mTag = getClass().getSimpleName();

    @FormatMethod
    protected final void logV(@FormatString String fmt, Object... args) {
        try {
            Log.v(TAG, mTag + ": " + String.format(fmt, args));
        } catch (Exception e) {
            // Typically happens when the object being passed to a mock method is mal-formed; for
            // example, a ResolveInfo without a component name
            Log.w(TAG, mTag + ".logV(fmt=" + fmt + ", args=...): failed to generate string: " + e);
        }
    }

    protected void assertIsMock(String what, Object mock) {
        if (!MockitoHelper.isMock(mock)) {
            throw new IllegalArgumentException(what + " is not a mock: " + mock);
        }
    }
}
