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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.adservices.shared.meta_testing.SimpleStatement;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.Description;
import org.mockito.Mock;

import java.util.List;

@SpyStatic(TestHelper.class)
public final class AbstractLoggingUsageRuleTest extends SharedExtendedMockitoTestCase {
    private final SimpleStatement mBaseStatement = new SimpleStatement();

    @Mock private Description mDescription;
    @Mock private LogVerifier mLogVerifier1;
    @Mock private LogVerifier mLogVerifier2;
    @Mock private SkipLoggingUsageRule mAnnotation;

    @Test
    public void testEvaluate_withNonTestDescription_throwsException() {
        when(mDescription.isTest()).thenReturn(false);
        AbstractLoggingUsageRule rule = new ConcreteLoggingUsageRule(ImmutableList.of());

        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> rule.evaluate(mBaseStatement, mDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "This rule can only be applied to individual tests, it cannot be used as"
                                + " @ClassRule or in a test suite");
    }

    @Test
    public void testEvaluate_withSkipAnnotation_gracefullyExits() throws Throwable {
        when(mDescription.isTest()).thenReturn(true);
        doReturn(mAnnotation)
                .when(() -> TestHelper.getAnnotationFromAnywhere(any(Description.class), any()));
        AbstractLoggingUsageRule rule =
                new ConcreteLoggingUsageRule(ImmutableList.of(mLogVerifier1));

        rule.evaluate(mBaseStatement, mDescription);

        // Base should still be evaluated
        mBaseStatement.assertEvaluated();
        // No setup work and verification should take place
        verifyZeroInteractions(mLogVerifier1);
    }

    @Test
    public void testEvaluate_withNoLogVerifiers_gracefullyExits() throws Throwable {
        when(mDescription.isTest()).thenReturn(true);
        AbstractLoggingUsageRule rule = new ConcreteLoggingUsageRule(ImmutableList.of());

        rule.evaluate(mBaseStatement, mDescription);

        // Base should still be evaluated
        mBaseStatement.assertEvaluated();
    }

    @Test
    public void testEvaluate_withStatementThrowsException_setupDoneButNoVerification() {
        when(mDescription.isTest()).thenReturn(true);
        mBaseStatement.failWith(new RuntimeException());
        AbstractLoggingUsageRule rule =
                new ConcreteLoggingUsageRule(ImmutableList.of(mLogVerifier1));

        assertThrows(RuntimeException.class, () -> rule.evaluate(mBaseStatement, mDescription));

        verify(mLogVerifier1).setup();
        verifyNoMoreInteractions(mLogVerifier1);
    }

    @Test
    public void testEvaluate_withLogVerifierThrowsException_throwsSameException() {
        when(mDescription.isTest()).thenReturn(true);
        doThrow(IllegalArgumentException.class).when(mLogVerifier1).verify(any());
        AbstractLoggingUsageRule rule =
                new ConcreteLoggingUsageRule(ImmutableList.of(mLogVerifier1, mLogVerifier2));

        assertThrows(
                IllegalArgumentException.class, () -> rule.evaluate(mBaseStatement, mDescription));

        // Ensure setup work is done by log verifiers
        verify(mLogVerifier1).setup();
        verify(mLogVerifier2).setup();

        // Ensure test has been evaluated
        mBaseStatement.assertEvaluated();

        // Only first log verifier should have performed verification
        verify(mLogVerifier1).verify(any());

        verifyNoMoreInteractions(mLogVerifier1, mLogVerifier2);
    }

    @Test
    public void testEvaluate_withSuccessfulExecution() throws Throwable {
        when(mDescription.isTest()).thenReturn(true);
        AbstractLoggingUsageRule rule =
                new ConcreteLoggingUsageRule(ImmutableList.of(mLogVerifier1, mLogVerifier2));

        rule.evaluate(mBaseStatement, mDescription);

        // Ensure setup work is done by log verifiers
        verify(mLogVerifier1).setup();
        verify(mLogVerifier2).setup();

        // Ensure test has been evaluated
        mBaseStatement.assertEvaluated();

        // Both log verifiers should have performed verification
        verify(mLogVerifier1).verify(any());
        verify(mLogVerifier2).verify(any());

        verifyNoMoreInteractions(mLogVerifier1, mLogVerifier2);
    }

    private static final class ConcreteLoggingUsageRule extends AbstractLoggingUsageRule {
        private final List<LogVerifier> mLogVerifiers;

        ConcreteLoggingUsageRule(List<LogVerifier> logVerifiers) {
            super();
            mLogVerifiers = logVerifiers;
        }

        @Override
        protected List<LogVerifier> getLogVerifiers() {
            return mLogVerifiers;
        }
    }
}
