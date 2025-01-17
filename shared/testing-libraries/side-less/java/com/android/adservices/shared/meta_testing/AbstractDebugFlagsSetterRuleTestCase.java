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
package com.android.adservices.shared.meta_testing;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.runner.Description.createTestDescription;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.testing.FakeNameValuePairContainer;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairContainer;
import com.android.adservices.shared.testing.annotations.DisableDebugFlag;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.flags.AbstractDebugFlagsSetterRule;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;
import org.junit.runner.Description;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public abstract class AbstractDebugFlagsSetterRuleTestCase<
                R extends AbstractDebugFlagsSetterRule<R>>
        extends SharedSidelessTestCase {

    private static final String TEST_METHOD_NAME = "butItHasATest";
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private static final String FLAG_A = "A Flag has no Name";
    private static final String FLAG_B = "Be Prepared";
    private static final String FLAG_C = "See you";
    private static final String FLAG_D = "Deed you, punk?";

    private final FakeNameValuePairContainer mContainer = new FakeNameValuePairContainer();

    protected abstract R newRule(NameValuePairContainer container);

    @Test
    public final void testNewRule() {
        assertWithMessage("newRule()").that(newRule(mContainer)).isNotNull();
    }

    @Test
    public final void testConstructor_nullArgs() {
        assertThrows(NullPointerException.class, () -> newRule(null));
    }

    @Test
    public final void testNoAnnotationsAtAll() throws Throwable {
        annotationBasedTest(createTestDescription(AClassHasNoNothingAtAll.class, TEST_METHOD_NAME));
    }

    @Test
    public final void testMethodAnnotationsOnly_single() throws Throwable {
        annotationBasedTest(
                createTestDescription(
                        AClassHasNoNothingAtAll.class,
                        TEST_METHOD_NAME,
                        TestAnnotations.disableDebugFlag(FLAG_A),
                        TestAnnotations.enableDebugFlag(FLAG_B)),
                new NameValuePair(FLAG_A, FALSE),
                new NameValuePair(FLAG_B, TRUE));
    }

    @Test
    public final void testMethodAnnotationsOnly_duplicated() throws Throwable {
        annotationBasedTest(
                createTestDescription(
                        AClassHasNoNothingAtAll.class,
                        TEST_METHOD_NAME,
                        TestAnnotations.disableDebugFlag(FLAG_A),
                        TestAnnotations.enableDebugFlag(FLAG_B),
                        // 1st annotation of the same type should prevail, so annotations below
                        // should be ignored
                        TestAnnotations.enableDebugFlag(FLAG_A),
                        TestAnnotations.disableDebugFlag(FLAG_B)),
                new NameValuePair(FLAG_A, FALSE),
                new NameValuePair(FLAG_B, TRUE));
    }

    @Test
    public final void testClassAnnotationsOnly() throws Throwable {
        annotationBasedTest(
                createTestDescription(OrphanClass.class, TEST_METHOD_NAME),
                new NameValuePair(FLAG_D, TRUE));
    }

    @Test
    public final void testSuperClassAnnotationsOnly() throws Throwable {
        annotationBasedTest(
                createTestDescription(OrphanSubClass.class, TEST_METHOD_NAME),
                new NameValuePair(FLAG_D, TRUE));
    }

    @Test
    public final void testClassAndSuperClassAnnotationsOnly() throws Throwable {
        annotationBasedTest(
                createTestDescription(SuperClass.class, TEST_METHOD_NAME),
                new NameValuePair(FLAG_A, FALSE),
                new NameValuePair(FLAG_B, TRUE),
                new NameValuePair(FLAG_C, FALSE));
    }

    @Test
    public final void testMethodAndClassAnnotations_methodPrevails() throws Throwable {
        annotationBasedTest(
                createTestDescription(
                        OrphanClass.class,
                        TEST_METHOD_NAME,
                        TestAnnotations.disableDebugFlag(FLAG_D)),
                new NameValuePair(FLAG_D, FALSE));
    }

    @Test
    public final void testMethodClassAndSuperClassAnnotations() throws Throwable {
        annotationBasedTest(
                createTestDescription(
                        OrdinaryClass.class,
                        TEST_METHOD_NAME,
                        TestAnnotations.enableDebugFlag(FLAG_A),
                        TestAnnotations.disableDebugFlag(FLAG_D)),
                new NameValuePair(FLAG_A, TRUE),
                new NameValuePair(FLAG_B, TRUE),
                new NameValuePair(FLAG_C, TRUE),
                new NameValuePair(FLAG_D, FALSE));
    }

    private void annotationBasedTest(Description test, NameValuePair... expectedFlagsInsideTest)
            throws Throwable {
        var expectedAsMap =
                Arrays.stream(expectedFlagsInsideTest)
                        .collect(Collectors.toMap(nvp -> nvp.name, nvp -> nvp));
        var rule = newRule(mContainer);
        var flagsInsideTest = runTest(rule, test);

        expect.withMessage("DebugFlags inside test")
                .that(flagsInsideTest)
                .containsExactlyEntriesIn(expectedAsMap);
        expect.withMessage("DebugFlags after test").that(mContainer.getAll()).isEmpty();
    }

    private ImmutableMap<String, NameValuePair> runTest(R rule, Description description)
            throws Throwable {
        AtomicReference<ImmutableMap<String, NameValuePair>> nvpsInsideTest =
                new AtomicReference<ImmutableMap<String, NameValuePair>>();
        SimpleStatement test = new SimpleStatement();

        test.onEvaluate(() -> nvpsInsideTest.set(mContainer.getAll()));
        rule.apply(test, description).evaluate();
        test.assertEvaluated();

        return nvpsInsideTest.get();
    }

    @DisableDebugFlag(FLAG_A)
    @DisableDebugFlag(FLAG_B)
    @DisableDebugFlag(FLAG_C)
    private static class UltraClass {}

    @EnableDebugFlag(FLAG_B)
    private static class SuperClass extends UltraClass {}

    @EnableDebugFlag(FLAG_C)
    private static class OrdinaryClass extends SuperClass {}

    @EnableDebugFlag(FLAG_D)
    private static class OrphanClass {}

    private static class OrphanSubClass extends OrphanClass {}
}
