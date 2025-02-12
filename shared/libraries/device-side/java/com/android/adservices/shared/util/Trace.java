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

package com.android.adservices.shared.util;

/** Class for android.os.Trace call. */
public final class Trace {
    /** Wrapper for {@link Trace#isEnabled()}. */
    public boolean isEnabled() {
        return android.os.Trace.isEnabled();
    }

    /** Wrapper for {@link Trace#beginSection(String)}. */
    public void beginSection(String sectionName) {
        android.os.Trace.beginSection(sectionName);
    }

    /** Wrapper for {@link Trace#beginAsyncSection(String, int)}. */
    public void beginAsyncSection(String sectionName, int cookie) {
        android.os.Trace.beginAsyncSection(sectionName, cookie);
    }

    /** Wrapper for {@link Trace#endSection()}. */
    public void endSection() {
        android.os.Trace.endSection();
    }

    /** Wrapper for {@link Trace#endAsyncSection()}. */
    public void endAsyncSection(String metricName, int cookie) {
        android.os.Trace.endAsyncSection(metricName, cookie);
    }
}
