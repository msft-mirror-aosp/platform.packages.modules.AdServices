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

import android.annotation.CallSuper;
import android.content.Context;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.shared.testing.Logger.LogLevel;

import com.google.common.annotations.VisibleForTesting;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Superclass for all device-side tests, it contains just the bare minimum features used by all
 * tests.
 */
public abstract class DeviceSideTestCase extends SidelessTestCase {

    private static final String TAG = DeviceSideTestCase.class.getSimpleName();

    private static final String REASON_SESSION_MANAGED_BY_RULE =
            "mockito session is automatically managed by a @Rule";

    // TODO(b/335935200): figure out if there is a way to read it from AndroidTest.xml
    @VisibleForTesting static final String RAVENWOOD_PACKAGE_NAME = "I.am.Groot.I.mean.Ravenwood";

    /** {@code logcat} tag. */
    protected final String mTag = getClass().getSimpleName();

    // NOTE: references below CANNOT be set when declared as the call to InstrumentationRegistry
    // would fail when running on host / under Ravenwood

    /**
     * @deprecated use {@link #mContext}
     */
    @Deprecated protected static Context sContext;

    /**
     * @deprecated use {@link #mTargetContext}
     */
    @Deprecated protected static Context sTargetContext;

    /**
     * Package name of the app being instrumented.
     *
     * @deprecated use {@link #mPackageName} instead.
     */
    @Deprecated protected static String sPackageName;

    @Deprecated protected static String sTargetPackageName;

    /**
     * Reference to the context of this test's instrumentation package (as defined by {@link
     * android.app.Instrumentation#getContext()})
     */
    protected Context mContext;

    /**
     * Reference to the context of app being instrumented (as defined by {@link
     * android.app.Instrumentation#getTargetContext()})
     */
    protected Context mTargetContext;

    /**
     * Package name of this test's instrumentation package (as defined by {@link
     * android.app.Instrumentation#getContext()})
     */
    protected String mPackageName;

    /**
     * Package name of the app being instrumented (as defined by {@link
     * android.app.Instrumentation#getTargetContext()})
     */
    protected String mTargetPackageName;

    // TODO(b/355286824) - Used only to set the static context, it doesn't skip tests. There is a
    // RavenwoodClassRule which skips tests, but it doesn't set the Context, so need to use
    // RavenwoodRule both here and as an instance rule.
    @ClassRule
    public static final RavenwoodRule sRavenwood =
            new RavenwoodRule.Builder()
                    .setProvideMainThread(true)
                    .setPackageName(RAVENWOOD_PACKAGE_NAME)
                    .build();

    // TODO(b/342639109): set proper order
    @Rule public final RavenwoodRule ravenwood = sRavenwood;

    // TODO(b/342639109): make sure it's the right order
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAnyLevel();

    // TODO(b/342639109): set order
    @Rule
    public final ProcessLifeguardRule processLifeguard =
            new ProcessLifeguardRule(ProcessLifeguardRule.Mode.IGNORE);

    @BeforeClass
    public static void setStaticFixtures() {
        if (sContext != null) {
            // TODO(b/335935200): remove this check once the static initialization is gone
            return;
        }
        try {
            sContext = InstrumentationRegistry.getInstrumentation().getContext();
            sPackageName = sContext.getPackageName();
            sTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
            sTargetPackageName = sTargetContext.getPackageName();
        } catch (Exception e) {
            DynamicLogger.getInstance()
                    .log(
                            LogLevel.ERROR,
                            TAG,
                            e,
                            "setStaticFixtures() failed (usually happens under Ravenwood). Setting"
                                    + " sContext=%s, sPackageName=%s, sTargetContext=%s,"
                                    + " sTargetPackageName=%s",
                            sContext,
                            sPackageName,
                            sTargetContext,
                            sTargetPackageName);
        }
    }

    @Before
    public final void setInstanceFixtures() {
        mContext = sContext;
        mPackageName = sPackageName;
        mTargetContext = sTargetContext;
        mTargetPackageName = sTargetPackageName;
    }

    // TODO(b/361555631): merge 2 classes below into testDeviceSideTestCaseFixtures() and annotate
    // it with @MetaTest
    @Test
    @Override
    public final void testValidTestCaseFixtures() throws Exception {
        assertValidTestCaseFixtures();
    }

    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        assertTestClassHasNoFieldsFromSuperclass(
                DeviceSideTestCase.class,
                "mContext",
                "mTargetContext",
                "mPackageName",
                "mTargetPackageName",
                "mTag",
                "ravenwood",
                "sdkLevel",
                "processLifeGuard",
                "sContext",
                "sTargetContext",
                "sPackageName",
                "sTargetPackageName",
                "sRavenWood",
                "RAVENWOOD_PACKAGE_NAME");
        assertTestClassHasNoSuchField(
                "CONTEXT",
                "should use existing mContext (or sContext when that's not possible) instead");
        assertTestClassHasNoSuchField("context", "should use existing mContext instead");
    }

    // NOTE: it's static so it can be used by other mockito-related superclasses, as often test
    // cases are converted to use AdServicesMockitoTestCase and still defined the ExtendedMockito
    // session - they should migrate to AdServicesExtendedMockitoTestCase instead.
    protected static <T extends DeviceSideTestCase> void checkProhibitedMockitoFields(
            Class<T> superclass, T testInstance) throws Exception {
        // NOTE: same fields below are not defined (yet?) SharedExtendedMockitoTestCase or
        // SharedMockitoTestCase, but they might; and even if they don't, this method is also used
        // by the classes on AdServices (AdServicesMockitoTestCase /
        // AdServicesExtendedMockitoTestCase)
        testInstance.assertTestClassHasNoFieldsFromSuperclass(
                superclass,
                "mMockContext",
                "mSpyContext",
                "extendedMockito",
                "errorLogUtilUsageRule",
                "mocker",
                "sInlineCleaner",
                "sSpyContext",
                "mMockFlags");
        testInstance.assertTestClassHasNoSuchField(
                "mContextMock", "should use existing mMockContext instead");
        testInstance.assertTestClassHasNoSuchField(
                "mContextSpy", "should use existing mSpyContext instead");
        testInstance.assertTestClassHasNoSuchField("mockito", "already taken care by @Rule");
        testInstance.assertTestClassHasNoSuchField(
                "mFlagsMock", "should use existing mMockFlags instead");
        testInstance.assertTestClassHasNoSuchField(
                "mFlags",
                superclass.getSimpleName()
                        + " already define a mMockFlags, and often subclasses define a @Mock"
                        + " mFlags; to avoid confusion, either use the existing mMockFlags, or"
                        + " create a non-mock instance like mFakeFlags");

        // Listed below are existing names for the extended mockito session on test classes that
        // don't use the rule / superclass:
        testInstance.assertTestClassHasNoSuchField(
                "mStaticMockSession", REASON_SESSION_MANAGED_BY_RULE);
        testInstance.assertTestClassHasNoSuchField(
                "mMockitoSession", REASON_SESSION_MANAGED_BY_RULE);
        testInstance.assertTestClassHasNoSuchField(
                "mockitoSession", REASON_SESSION_MANAGED_BY_RULE);
        testInstance.assertTestClassHasNoSuchField("session", REASON_SESSION_MANAGED_BY_RULE);
        testInstance.assertTestClassHasNoSuchField(
                "sStaticMockitoSession", REASON_SESSION_MANAGED_BY_RULE);
        testInstance.assertTestClassHasNoSuchField(
                "staticMockitoSession", REASON_SESSION_MANAGED_BY_RULE);
        testInstance.assertTestClassHasNoSuchField(
                "staticMockSession", REASON_SESSION_MANAGED_BY_RULE);
    }

    // TODO(b/335935200): temporary hac^H^H^Hworkaround to set context references before subclasses
    // when the class or instance is initialized.
    // In the long term, these tests must be refactored to use them "inside" the test (otherwise
    // it would not work when running on host-side / ravenwood), then they can be removed.
    static {
        setStaticFixtures();
    }

    protected DeviceSideTestCase() {
        setInstanceFixtures();
    }
}
