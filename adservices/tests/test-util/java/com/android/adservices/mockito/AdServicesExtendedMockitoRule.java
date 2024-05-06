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

package com.android.adservices.mockito;

import static com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.shouldClearInlineMocksAfterTest;
import static com.android.adservices.shared.testing.common.TestHelper.getAnnotation;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.adservices.mockito.ExtendedMockitoInlineCleanerRule.ClearInlineMocksMode;
import com.android.adservices.shared.testing.common.TestHelper;
import com.android.modules.utils.testing.AbstractExtendedMockitoRule;
import com.android.modules.utils.testing.StaticMockFixture;

import com.google.common.collect.ImmutableSet;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

// NOTE: javadoc below copied mostly as-is from ExtendedMockitoRule
/**
 * Rule to make it easier to use Extended Mockito:
 *
 * <ul>
 *   <li>Automatically creates and finishes the mock session.
 *   <li>Provides multiple ways to set which classes must be statically mocked or spied
 *   <li>Automatically starts mocking (so tests don't need a mockito runner or rule)
 *   <li>Automatically clears the inlined mocks at the end (to avoid OOM)
 *   <li>Allows other customization like strictness
 * </ul>
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * &#064;Rule
 * public final AdServicesExtendedMockitoRule extendedMockito =
 *   new AdServicesExtendedMockitoRule.Builder(this)
 *     .spyStatic(SomeClassWithStaticMethodsToBeMocked)
 *     .build();
 * </pre>
 */
public final class AdServicesExtendedMockitoRule
        extends AbstractExtendedMockitoRule<
                AdServicesExtendedMockitoRule, AdServicesExtendedMockitoRule.Builder> {

    private static final String TAG = AdServicesExtendedMockitoRule.class.getSimpleName();

    private final Set<Class<?>> mSpiedOrMockedStaticClasses = new HashSet<>();

    @Nullable private String mTestName;

    public AdServicesExtendedMockitoRule(Builder builder) {
        super(builder);
    }

    @SafeVarargs
    public AdServicesExtendedMockitoRule(Supplier<? extends StaticMockFixture>... suppliers) {
        super(new Builder().addStaticMockFixtures(suppliers));
    }

    // TODO(b/312802824): add unit tests (for rule itself)
    /**
     * Gets the name of the test being executed.
     *
     * @throws IllegalStateException if not running a test.
     */
    public final String getTestName() {
        if (mTestName == null) {
            throw new IllegalStateException("not running a test");
        }
        return mTestName;
    }

    // Overridden to get test name
    @Override
    public final Statement apply(Statement base, Description description) {
        Statement realStatement = super.apply(base, description);
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                mTestName = TestHelper.getTestName(description);
                try {
                    realStatement.evaluate();
                } finally {
                    mTestName = null;
                }
            }
        };
    }

    @Override
    protected final Set<Class<?>> getSpiedStaticClasses(Description description) {
        Set<Class<?>> spiedStaticClasses = super.getSpiedStaticClasses(description);
        mSpiedOrMockedStaticClasses.addAll(spiedStaticClasses);
        return spiedStaticClasses;
    }

    @Override
    protected final Set<Class<?>> getMockedStaticClasses(Description description) {
        Set<Class<?>> mockedStaticClasses = super.getMockedStaticClasses(description);
        mSpiedOrMockedStaticClasses.addAll(mockedStaticClasses);
        return mockedStaticClasses;
    }

    @Override
    protected final boolean getClearInlineMethodsAtTheEnd(Description description) {
        ClearInlineMocksMode annotation = getAnnotation(description, ClearInlineMocksMode.class);
        if (annotation != null) {
            boolean shouldClear = shouldClearInlineMocksAfterTest(description, annotation.value());
            Log.d(
                    TAG,
                    "getClearInlineMethodsAtTheEnd(): returning value based on annotation ("
                            + shouldClear
                            + ") for "
                            + TestHelper.getTestName(description));
            return shouldClear;
        }
        return super.getClearInlineMethodsAtTheEnd(description);
    }

    /** Gets the classes that are spied or mocked. */
    public ImmutableSet<Class<?>> getSpiedOrMockedClasses() {
        return ImmutableSet.copyOf(mSpiedOrMockedStaticClasses);
    }

    public static final class Builder
            extends AbstractBuilder<AdServicesExtendedMockitoRule, Builder> {

        public Builder() {
            super();
        }

        public Builder(Object testClassInstance) {
            super(testClassInstance);
        }

        @Override
        public AdServicesExtendedMockitoRule build() {
            return new AdServicesExtendedMockitoRule(this);
        }
    }
}
