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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.meta_testing.CommonDescriptions.newTestMethodForClassRule;
import static com.android.adservices.shared.testing.TestHelper.getAnnotation;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import com.google.auto.value.AutoAnnotation;

import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class TestHelperTest extends SharedSidelessTestCase {
    private final Description mNotTestDescription =
            newTestMethodForClassRule(AClassHasNoNothingAtAll.class);

    @Test
    public void testGetTestName_null() {
        assertThrows(NullPointerException.class, () -> TestHelper.getTestName(null));
    }

    @Test
    public void testGetTestName_testMethod() {
        Description test =
                Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

        expect.withMessage("getTestName(%s)", test)
                .that(TestHelper.getTestName(test))
                .isEqualTo("AClassHasNoNothingAtAll#butItHasATest()");
    }

    @Test
    public void testGetTestName_testClass() {
        Description test = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

        expect.withMessage("getTestName(%s)", test)
                .that(TestHelper.getTestName(test))
                .isEqualTo("AClassHasNoNothingAtAll");
    }

    @Test
    public void testGetAnnotation_null() {
        Description test = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

        assertThrows(
                NullPointerException.class, () -> getAnnotation(test, /* annotationClass= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getAnnotation((Description) null, DaRealAnnotation.class));
    }

    @Test
    public void testGetAnnotation_notSetAnywhere() {
        Description test = Description.createSuiteDescription(AClassHasNoNothingAtAll.class);

        expect.withMessage("getAnnotation(%s)", test)
                .that(getAnnotation(test, DaRealAnnotation.class))
                .isNull();
    }

    @Test
    public void testGetAnnotation_fromMethod() {
        Description test =
                Description.createSuiteDescription(
                        AClassHasAnAnnotationAndAParent.class,
                        newDaFakeAnnotation("I annotate, therefore I am!"));

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("I annotate, therefore I am!");
    }

    @Test
    public void testGetAnnotation_fromClass() {
        Description test =
                Description.createSuiteDescription(AClassHasAnAnnotationAndAParent.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("A class has an annotation and a parent!");
    }

    @Test
    public void testGetAnnotation_fromParentClass() {
        Description test =
                Description.createSuiteDescription(AClassHasNoAnnotationButItsParentDoes.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("A class has an annotation!");
    }

    @Test
    public void testGetAnnotation_fromImplementedInterface() {
        Description test =
                Description.createSuiteDescription(AClassHasNoAnnotationButItsInterfaceDoes.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("An interface has an annotation!");
    }

    @Test
    public void testGetAnnotation_fromParentsImplementedInterface() {
        Description test =
                Description.createSuiteDescription(
                        AClassHasNoAnnotationButItsParentsInterfaceDoes.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("An interface has an annotation!");
    }

    @Test
    public void testGetAnnotation_fromGrandParentClass() {
        Description test =
                Description.createSuiteDescription(
                        AClassHasNoAnnotationButItsGrandParentDoes.class);

        DaRealAnnotation annotation = getAnnotation(test, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", test).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", test)
                .that(annotation.value())
                .isEqualTo("A class has an annotation!");
    }

    @Test
    public void testGetAnnotationFromTestClass_null() {
        assertThrows(
                NullPointerException.class,
                () -> getAnnotation(AClassHasNoNothingAtAll.class, /* annotationClass= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getAnnotation(/* testClass= */ (Class<?>) null, DaRealAnnotation.class));
    }

    @Test
    public void testGetAnnotationFromTestClass_notSetAnywhere() {
        Class<?> testClass = AClassHasNoNothingAtAll.class;

        expect.withMessage("getAnnotation(%s)", testClass)
                .that(getAnnotation(testClass, DaRealAnnotation.class))
                .isNull();
    }

    @Test
    public void testGetAnnotationFromTestClass_fromClass() {
        Class<?> testClass = AClassHasAnAnnotationAndAParent.class;

        DaRealAnnotation annotation = getAnnotation(testClass, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", testClass).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", testClass)
                .that(annotation.value())
                .isEqualTo("A class has an annotation and a parent!");
    }

    @Test
    public void testGetAnnotationFromTestClass_fromParentClass() {
        Class<?> testClass = AClassHasNoAnnotationButItsParentDoes.class;

        DaRealAnnotation annotation = getAnnotation(testClass, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", testClass).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", testClass)
                .that(annotation.value())
                .isEqualTo("A class has an annotation!");
    }

    @Test
    public void testGetAnnotationFromTestClass_fromGrandParentClass() {
        Class<?> testClass = AClassHasNoAnnotationButItsGrandParentDoes.class;

        DaRealAnnotation annotation = getAnnotation(testClass, DaRealAnnotation.class);

        assertWithMessage("getAnnotation(%s)", testClass).that(annotation).isNotNull();
        expect.withMessage("getAnnotation(%s).value()", testClass)
                .that(annotation.value())
                .isEqualTo("A class has an annotation!");
    }

    @Test
    public void testThrowIfNotTest_withNonTestDescription_throwsException() {
        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> TestHelper.throwIfNotTest(mNotTestDescription));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "This rule can only be applied to individual tests, it cannot be used as"
                                + " @ClassRule or in a test suite");
    }

    @Test
    public void testThrowIfNotTest_withTestDescription_throwsNoException() {
        Description test =
                Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

        // No exception should be thrown for test.
        TestHelper.throwIfNotTest(test);
    }

    @Test
    public void testThrowIfTest_withNonTestDescription_throwsException() {
        Description test =
                Description.createTestDescription(AClassHasNoNothingAtAll.class, "butItHasATest");

        Exception exception =
                assertThrows(IllegalStateException.class, () -> TestHelper.throwIfTest(test));

        expect.that(exception)
                .hasMessageThat()
                .isEqualTo(
                        "This rule can only be used as a @ClassRule or in a test suit, it cannot be"
                                + " applied to individual tests as a @Rule");
    }

    @Test
    public void testThrowIfTest_withTestDescription_throwsNoException() {
        // No exception should be thrown for test.
        TestHelper.throwIfTest(mNotTestDescription);
    }

    @DaRealAnnotation("A class has an annotation!")
    private static class AClassHasAnAnnotation {}

    @DaRealAnnotation("An interface has an annotation!")
    private interface AnInterfaceHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsInterfaceDoes
            implements AnInterfaceHasAnAnnotation {}

    @DaRealAnnotation("A class has an annotation and a parent!")
    private static class AClassHasAnAnnotationAndAParent extends AClassHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsParentDoes extends AClassHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsParentsInterfaceDoes
            extends AClassHasNoAnnotationButItsInterfaceDoes {}

    private static class AClassHasNoAnnotationButItsGrandParentDoes
            extends AClassHasNoAnnotationButItsParentDoes {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    public @interface DaRealAnnotation {
        String value();
    }

    @AutoAnnotation
    private static DaRealAnnotation newDaFakeAnnotation(String value) {
        return new AutoAnnotation_TestHelperTest_newDaFakeAnnotation(value);
    }
}
