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

import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.ANY;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.R;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.S2;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.T;
import static com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel.U;
import static com.android.adservices.common.TestAnnotations.newAnnotationForAtLeast;
import static com.android.adservices.common.TestAnnotations.newAnnotationForLessThanT;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AbstractSdkLevelSupportedRule.AndroidSdkLevel;
import com.android.adservices.common.Logger.RealLogger;

import com.google.common.truth.Expect;
import com.google.common.truth.StringSubject;

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.util.Objects;

// TODO(b/295321663): provide host-side implementation
/**
 * Test case for {@link AbstractSdkLevelSupportedRule} implementations.
 *
 * <p>By default, it uses a {@link FakeSdkLevelSupportedRule bogus rule} so it can be run by IDEs,\
 * but subclasses should implement {@link #newRule(AndroidSdkLevel, AndroidSdkLevel)} and {@link
 * #newRuleForDeviceLevelAndRuleAtLeastLevel(AndroidSdkLevel)}.
 */
public class AbstractSdkLevelSupportedRuleTest {

    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    private static final String REASON = "Because I said so";

    private final SimpleStatement mBaseStatement = new SimpleStatement();

    protected final Logger mLog;

    @Rule public final Expect expect = Expect.create();

    public AbstractSdkLevelSupportedRuleTest() {
        this(StandardStreamsLogger.getInstance());
    }

    protected AbstractSdkLevelSupportedRuleTest(RealLogger realLogger) {
        mLog = new Logger(Objects.requireNonNull(realLogger), getClass());
    }

    // NOTE: the testRuleIsAtLeastMethods... refers to the device SDK, not the rule's

    @Test
    public void testRuleIsAtLeastMethods_deviceIsR() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ R);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isFalse();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isFalse();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isFalse();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsS() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ S);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isFalse();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isFalse();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsS2() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ S2);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isTrue();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isFalse();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsT() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ T);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isTrue();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isTrue();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isFalse();
    }

    @Test
    public void testRuleIsAtLeastMethods_deviceIsU() throws Exception {
        var rule = newRule(/* ruleLevel= */ ANY, /* deviceLevel= */ U);

        expect.withMessage("rule.atLeastR()").that(rule.isAtLeastR()).isTrue();
        expect.withMessage("rule.atLeastS()").that(rule.isAtLeastS()).isTrue();
        expect.withMessage("rule.atLeastS2()").that(rule.isAtLeastS2()).isTrue();
        expect.withMessage("rule.atLeastT()").that(rule.isAtLeastT()).isTrue();
        expect.withMessage("rule.atLeastU()").that(rule.isAtLeastU()).isTrue();
    }

    /*
     * Tests for rule constructed for any level.
     */
    @Test
    public void testRuleIsAtLeastAny_deviceIsR_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ S2);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ ANY, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastAny_deviceIsR_testAnnotatedWithR_runs() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ ANY, /* deviceLevel= */ R, /* annotationLevel=*/ S);
    }

    /*
     * Tests for rule constructed for at least R.
     */
    @Test
    public void testRuleIsAtLeastR_deviceIsR_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ R, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastR_deviceIsR_testAnnotatedWithR_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ R, /* deviceLevel= */ R, /* annotationLevel=*/ R);
    }

    /*
     * Tests for rule constructed for at least S.
     */
    @Test
    public void testRuleIsAtLeastS_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ S2);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_testAnnotatedWithT_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S, /* deviceLevel= */ S, /* annotationLevel=*/ T);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_testAnnotatedWithU_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S, /* deviceLevel= */ S, /* annotationLevel=*/ U);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastS_deviceIsS_testAnnotatedWithS_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S, /* deviceLevel= */ S, /* annotationLevel=*/ S);
    }

    /*
     * Tests for rule constructed for at least S2.
     */
    @Test
    public void testRuleIsAtLeastS2_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ S2);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_testAnnotatedWithT_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2, /* annotationLevel=*/ T);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_testAnnotatedWithU_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2, /* annotationLevel=*/ U);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ S2, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastS2_deviceIsS2_testAnnotatedWithS2_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2, /* annotationLevel=*/ S2);
    }

    /*
     * Tests for rule constructed for at least T.
     */
    @Test
    public void testRuleIsAtLeastT_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsS_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsT_testAnnotatedWithU_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ T, /* deviceLevel= */ T, /* annotationLevel=*/ U);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ T, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastT_deviceIsT_testAnnotatedWithT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ T, /* deviceLevel= */ T, /* annotationLevel=*/ T);
    }

    /*
     * Tests for rule constructed for at least U.
     */
    @Test
    public void testRuleIsAtLeastU_deviceIsR_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ R);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsS_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ S);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsT_skips() throws Throwable {
        testSkippedWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ T);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXAndDeviceIsY(/* ruleLevel= */ U, /* deviceLevel= */ U);
    }

    @Test
    public void testRuleIsAtLeastU_deviceIsU_testAnnotatedWithU_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
                /* ruleLevel= */ U, /* deviceLevel= */ U, /* annotationLevel=*/ U);
    }

    /*
     * Tests for tests annotated with @RequiresSdkLevelLessThanT - the rule is currently hard-coded
     * to handle that annotation, so we need unit tests as a safety net to refactor that logic (even
     * if this annotation is removed / deprecated later).
     */

    @Test
    public void testAnnotatedWithRequiresSdkLevelLessThanT_skips() {
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S2, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S2, /* deviceLevel= */ U);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ T, /* deviceLevel= */ T);
        testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ T, /* deviceLevel= */ U);
    }

    @Test
    public void testAnnotatedWithRequiresSdkLevelLessThanT_runs() throws Throwable {
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ R);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ S);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ ANY, /* deviceLevel= */ S2);

        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ R);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ S);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ R, /* deviceLevel= */ S2);

        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ S);
        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S, /* deviceLevel= */ S2);

        testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
                /* ruleLevel= */ S2, /* deviceLevel= */ S2);
    }

    private void testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForLessThanT(REASON));

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        StringSubject exceptionMessage =
                expect.withMessage("exception message").that(e).hasMessageThat();
        exceptionMessage.contains("@RequiresSdkLevelLessThanT");
        exceptionMessage.contains(REASON);

        mBaseStatement.assertNotEvaluated();
    }

    private void testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithLessThanT(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) throws Throwable {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForLessThanT(REASON));

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception(
                    "test should not throw AssumptionViolatedException: " + e.getMessage(), e);
        }

        mBaseStatement.assertEvaluated();
    }

    /**
     * Creates a rule that is constructed for at least {@code level} and also mocks that rule's
     * {@link AbstractSdkLevelSupportedRule#getDeviceApiLevel()} to return {@code level}.
     */
    protected AbstractSdkLevelSupportedRule newRuleForDeviceLevelAndRuleAtLeastLevel(
            AndroidSdkLevel level) {
        return new FakeSdkLevelSupportedRule(/* ruleLevel= */ level, /* deviceLevel= */ level);
    }

    /**
     * Creates a rule that is constructed for at least {@code ruleLevel} and also mocks that rule's
     * {@link AbstractSdkLevelSupportedRule#getDeviceApiLevel()} to return {@code deviceLevel}.
     */
    protected AbstractSdkLevelSupportedRule newRule(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        return new FakeSdkLevelSupportedRule(ruleLevel, deviceLevel);
    }

    // NOTE: eventually there will be releases X, Y, Z, but other names would make these methods
    // even longer than what they already are

    private void testRanWhenRuleIsAtLeastXAndDeviceIsY(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) throws Throwable {
        var rule = newRuleForDeviceLevelAndRuleAtLeastLevel(ruleLevel);
        Description testMethod = newTestMethod();

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception("test should not throw", e);
        }

        mBaseStatement.assertEvaluated();
    }

    private void testRanWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel, AndroidSdkLevel annotationLevel)
            throws Throwable {
        var rule = newRuleForDeviceLevelAndRuleAtLeastLevel(ruleLevel);
        Description testMethod = newTestMethod(newAnnotationForAtLeast(annotationLevel, REASON));

        try {
            rule.apply(mBaseStatement, testMethod).evaluate();
        } catch (AssumptionViolatedException e) {
            throw new Exception(
                    "test should not throw AssumptionViolatedException: " + e.getMessage(), e);
        }

        mBaseStatement.assertEvaluated();
    }

    private void testSkippedWhenRuleIsAtLeastXAndDeviceIsY(
            AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod();

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        expect.withMessage("exception message")
                .that(e)
                .hasMessageThat()
                .contains("SDK level " + ruleLevel);

        mBaseStatement.assertNotEvaluated();
    }

    private void testSkippedWhenRuleIsAtLeastXDeviceIsYAndTestAnnotatedWithZ(
            AndroidSdkLevel ruleLevel,
            AndroidSdkLevel deviceLevel,
            AndroidSdkLevel annotationLevel) {
        var rule = newRule(ruleLevel, deviceLevel);
        Description testMethod = newTestMethod(newAnnotationForAtLeast(annotationLevel, REASON));

        AssumptionViolatedException e =
                assertThrows(
                        AssumptionViolatedException.class,
                        () -> rule.apply(mBaseStatement, testMethod).evaluate());

        StringSubject exceptionMessage =
                expect.withMessage("exception message").that(e).hasMessageThat();
        exceptionMessage.contains("SDK level " + annotationLevel);
        exceptionMessage.contains(REASON);

        mBaseStatement.assertNotEvaluated();
    }

    private static Description newTestMethod(Annotation... annotations) {
        return newTestMethod(TEST_METHOD_BEING_EXECUTED, annotations);
    }

    private static Description newTestMethod(String methodName, Annotation... annotations) {
        return Description.createTestDescription(
                AbstractSdkLevelSupportedRuleTest.class, methodName, annotations);
    }

    /**
     * Bogus implementation of {@link AbstractSdkLevelSupported}.
     *
     * <p>It's returned by default by {@link
     * AbstractSdkLevelSupportedRuleTest#newRule(AndroidSdkLevel, AndroidSdkLevel)} and {@link
     * AbstractSdkLevelSupportedRuleTest#newRuleForDeviceLevelAndRuleAtLeastLevel(AndroidSdkLevel)},
     * so this test class can run independently of the real implementation, which makes it possible
     * to run this test directly from an IDE.
     */
    private static final class FakeSdkLevelSupportedRule extends AbstractSdkLevelSupportedRule {

        private final AndroidSdkLevel mDeviceLevel;

        private FakeSdkLevelSupportedRule(AndroidSdkLevel ruleLevel, AndroidSdkLevel deviceLevel) {
            super(StandardStreamsLogger.getInstance(), ruleLevel);

            mDeviceLevel = deviceLevel;
        }

        @Override
        public AndroidSdkLevel getDeviceApiLevel() {
            return mDeviceLevel;
        }
    }
}
