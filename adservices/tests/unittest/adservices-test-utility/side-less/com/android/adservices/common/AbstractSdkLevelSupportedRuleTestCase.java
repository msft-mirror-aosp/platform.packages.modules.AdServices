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

import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.T;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel;

import com.google.common.truth.Expect;

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.Annotation;

// TODO(b/295321663): provide host-side implementation
/**
 * Test case for {@link AbstractSdkLevelSupportedRule} implementations.
 *
 * @param <RULE> rule implementation. NOTE: cannot be {@code T} or {@code R} as it would overshadow
 *     the statically imported constants.
 */
public abstract class AbstractSdkLevelSupportedRuleTestCase<
        RULE extends AbstractSdkLevelSupportedRule> {

    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    private final SimpleStatement mBaseStatement = new SimpleStatement();

    @Rule public final Expect expect = Expect.create();

    // NOTE: the isAtLeast methods refers to the device SDK, not the rule's
    @Test
    public void testRuleIsAtLeastMethods_deviceIsT() throws Exception {
        RULE rule = newRuleForAtLeast(T);
        setDeviceSdkLevel(T);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testConstructorRequiresT_deviceIsS_skipped() throws Throwable {
        RULE rule = newRuleForAtLeast(T);
        setDeviceSdkLevel(S);
        Description testMethod = newTestMethod();

        assertThrows(
                AssumptionViolatedException.class,
                () -> rule.apply(mBaseStatement, testMethod).evaluate());

        mBaseStatement.assertNotEvaluated();
    }

    @Test
    public void testConstructorRequiresT_deviceIsT_run() throws Throwable {
        RULE rule = newRuleForAtLeast(T);
        setDeviceSdkLevel(T);
        Description testMethod = newTestMethod();

        rule.apply(mBaseStatement, testMethod).evaluate();

        mBaseStatement.assertEvaluated();
    }

    protected abstract RULE newRuleForAtLeast(AndroidSdkLevel level);

    protected abstract void setDeviceSdkLevel(AndroidSdkLevel level);

    public static Description newTestMethod(Annotation... annotations) {
        return newTestMethod(TEST_METHOD_BEING_EXECUTED, annotations);
    }

    public static Description newTestMethod(String methodName, Annotation... annotations) {
        return Description.createTestDescription(
                AbstractSdkLevelSupportedRuleTestCase.class, methodName, annotations);
    }
}
