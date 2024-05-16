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
package com.android.adservices.shared.testing;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Superclass for all device-side tests, it contains just the bare minimum features used by all
 * tests.
 */
public abstract class DeviceSideTestCase extends SidelessTestCase {

    /** Reference to the context of package being instrumented (target context). */
    protected static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    /** Package name of the app being instrumented. */
    protected static final String sPackageName = sContext.getPackageName();

    /** Reference to the context of package being instrumented (target context). */
    protected final Context mContext = sContext;

    /** Package name of the app being instrumented. */
    protected final String mPackageName = sPackageName;

    /** {@code Logcat} tag. */
    protected final String mTag = getClass().getSimpleName();
}
