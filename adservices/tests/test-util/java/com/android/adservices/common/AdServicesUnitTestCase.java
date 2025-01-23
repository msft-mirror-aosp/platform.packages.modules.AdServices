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

import static org.mockito.Mockito.mock;

import android.annotation.CallSuper;
import android.content.Context;

import com.android.adservices.flags.AdServicesFakeDebugFlagsSetterRule;
import com.android.adservices.flags.AdServicesFakeFlagsSetterRule;
import com.android.adservices.flags.AdServicesFlagsSetterRuleForUnitTests;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.shared.testing.common.ApplicationContextSingletonRule;

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

    // NOTE: must be defined here (instead of on AdServicesMockerLessExtendedMockitoTestCase),
    // otherwise it would be null when flags call newFlagsRule() below (and tests override that
    // method to return AdServicesMockFlagsSetterRule)
    /**
     * @deprecated tests should use {@link AdServicesFakeFlagsSetterRule} instead.
     */
    @Deprecated protected final Flags mMockFlags = mock(Flags.class);

    private static final String APP_CONTEXT_MSG = "should use existing mAppContext instead";

    private static final String FAKE_FLAGS_MSG = "should use mFakeFlags and flags rule insteads";

    private static final String FAKE_DEBUG_FLAGS_MSG =
            "should use mFakeDebugFlags and debugFlags rule insteads";

    @Rule(order = 5)
    public final ApplicationContextSingletonRule appContext =
            new ApplicationContextSingletonRule(/* restoreAfter= */ false);

    @Rule(order = 6)
    public final AdServicesFlagsSetterRuleForUnitTests<?, ? extends Flags> flags = newFlagsRule();

    @Rule(order = 7)
    public final AdServicesFakeDebugFlagsSetterRule debugFlags =
            new AdServicesFakeDebugFlagsSetterRule();

    /**
     * Reference to the application context of this test's instrumentation package (as defined by
     * {@link android.app.Instrumentation#getContext()}.
     *
     * <p>In other words, it's the same as {@code mContext.getApplicationContext()}.
     */
    protected final Context mAppContext = mContext.getApplicationContext();

    /** Stubbed {@link Flags} implementation that can be set by {@code flags}. */
    protected final Flags mFakeFlags = flags.getFlags();

    /** Stubbed {@link DebugFlags} implementation that can be set by {@code debugFlags}. */
    protected final DebugFlags mFakeDebugFlags = debugFlags.getDebugFlags();

    /**
     * Creates the rule that will be referenced as {@code flags}.
     *
     * <p>Returns a new {@link AdServicesFakeFlagsSetterRule} by default, should be overridden by
     * tests that want to use {@code AdServicesMockFlagsSetterRule} instead.
     */
    protected AdServicesFlagsSetterRuleForUnitTests<?, ? extends Flags> newFlagsRule() {
        return new AdServicesFakeFlagsSetterRule();
    }

    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        // TODO(b/388097793): add mLegacyFakeFlags once tests are refactored to not used it
        assertTestClassHasNoFieldsFromSuperclass(
                AdServicesUnitTestCase.class,
                "mAppContext",
                "appContext",
                "flags",
                "mMockFlags",
                "mFakeFlags",
                "debugFlags",
                "mFakeDebugFlags");
        assertTestClassHasNoSuchField("APPLICATION_CONTEXT", APP_CONTEXT_MSG);
        assertTestClassHasNoSuchField("mApplicationContext", APP_CONTEXT_MSG);
        assertTestClassHasNoSuchField("FLAGS", FAKE_FLAGS_MSG);
        // TODO(b/384798806): add a check prohibiting realFlags, which is currently using on some
        // unit tests that "really" change the Flags using DeviceConfig - these tests should instead
        // use AdServicesFakeFlagsSetterRule (which will eventually be provided by a superclass).
        // We'll need to fix these test first (for example, some of them also set DebugFlags, which
        // is not supported by AdServicesFakeFlagsSetterRule and won't be, as we should have a
        // separate rule for DebugFlags / SystemProperties)
        assertTestClassHasNoSuchField("mDebugFlags", FAKE_DEBUG_FLAGS_MSG);
        // TODO(b/338067482): add mMockDebugFlags as well
    }
}
