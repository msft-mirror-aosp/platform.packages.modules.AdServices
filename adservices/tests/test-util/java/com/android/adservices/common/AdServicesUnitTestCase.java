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
package com.android.adservices.common;

import android.annotation.CallSuper;
import android.content.Context;

import com.android.adservices.shared.testing.common.ApplicationContextSingletonRule;

import org.junit.Before;
import org.junit.Rule;

/**
 * Base class for all unit tests.
 *
 * <p>Contains only the bare minimum functionality required by them, like custom JUnit rules.
 *
 * <p>In fact, this class "reserves" the first 11 rules (as defined by order 0-10), so subclasses
 * should start defining rules with {@code order = 11}.
 */
public abstract class AdServicesUnitTestCase extends AdServicesTestCase {

    private static final String APP_CONTEXT_MSG = "should use existing mAppContext instead";

    @Rule(order = 5)
    public final ApplicationContextSingletonRule appContext =
            new ApplicationContextSingletonRule(/* restoreAfter= */ false);

    /**
     * Reference to the application context of this test's instrumentation package (as defined by
     * {@link android.app.Instrumentation#getContext()}.
     *
     * <p>In other words, it's the same as {@code mContext.getApplicationContext()}.
     */
    protected Context mAppContext;

    @Before
    public final void setAdServicesUnitTestCaseFixtures() {
        mAppContext = mContext.getApplicationContext();
    }

    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        // TODO(b/3847988060): add check to prohibit mFlags, FLAGS, TEST_FLAGS, etc...
        assertTestClassHasNoFieldsFromSuperclass(AdServicesUnitTestCase.class, "mAppContext");
        assertTestClassHasNoSuchField("APPLICATION_CONTEXT", APP_CONTEXT_MSG);
        assertTestClassHasNoSuchField("mApplicationContext", APP_CONTEXT_MSG);
        // TODO(b/384798806): add a check prohibiting realFlags, which is currently using on some
        // unit tests that "really" change the Flags using DeviceConfig - these tests should instead
        // use AdServicesFakeFlagsSetterRule (which will eventually be provided by a superclass).
        // We'll need to fix these test first (for example, some of them also set DebugFlags, which
        // is not supported by AdServicesFakeFlagsSetterRule and won't be, as we should have a
        // separate rule for DebugFlags / SystemProperties)
    }
}
