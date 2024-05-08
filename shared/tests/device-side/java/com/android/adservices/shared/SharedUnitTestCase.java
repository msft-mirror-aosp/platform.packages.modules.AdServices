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
package com.android.adservices.shared;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.shared.testing.SdkLevelSupportRule;

import com.google.common.truth.Expect;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.Rule;
import org.junit.rules.TestName;

// TODO(b/285014040): create super class on testing library
public abstract class SharedUnitTestCase {

    private static final String TAG = SharedUnitTestCase.class.getSimpleName();

    protected static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    /** Package name of the app being instrumented. */
    protected static final String sPackageName = sContext.getPackageName();

    protected final String mTag = getClass().getSimpleName();

    /** Reference to the context of package being instrumented (target context). */
    protected final Context mContext = sContext;

    /** Package name of the app being instrumented. */
    protected final String mPackageName = sPackageName;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    @Rule(order = 1)
    public final Expect expect = Expect.create();

    // TODO(b/285014040): use custom rule
    @Rule(order = 2)
    public final TestName name = new TestName();

    /** Sleeps for the given amount of time. */
    @FormatMethod
    protected final void sleep(
            int timeMs, @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        String reason = String.format(reasonFmt, reasonArgs);
        Log.i(
                TAG,
                getTestName()
                        + ": napping "
                        + timeMs
                        + "ms on thread "
                        + Thread.currentThread()
                        + ". Reason: "
                        + reason);
        SystemClock.sleep(timeMs);
        Log.i(TAG, "Little Suzie woke up!");
    }

    /** Gets the name of the test being executed. */
    protected final String getTestName() {
        return name.getMethodName();
    }
}
