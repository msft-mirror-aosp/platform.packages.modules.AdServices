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
import static com.android.adservices.shared.testing.TestHelper.getAnnotationFromAnywhere;
import static com.android.adservices.shared.testing.TestHelper.getAnnotationFromTypesOnly;
import static com.android.adservices.shared.testing.TestHelper.getAnnotationsFromEverywhere;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.runner.Description.createSuiteDescription;
import static org.junit.runner.Description.createTestDescription;

import com.android.adservices.shared.meta_testing.CommonDescriptions.AClassHasNoNothingAtAll;
import com.android.adservices.shared.meta_testing.SharedSidelessTestCase;

import com.google.auto.value.AutoAnnotation;

import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

public final class TestHelperTest extends SharedSidelessTestCase {

    // Used to create a test Description
    private static final String TEST_NAME = "The name is Case, Test Case!";

    private static final String VALUE_ON_METHOD = "I annotate, therefore I am!";
    private static final String VALUE_ON_CLASS = "A class has an annotation!";
    private static final String VALUE_ON_SUPERCLASS = "A class has an annotation and a parent!";
    private static final String VALUE_ON_GRAND_SUPERCLASS =
            "A superclass of a superclass of a class has an annotation!";
    private static final String VALUE_ON_INTERFACE = "An interface has an annotation!";

    private final Description mNotTestDescription =
            newTestMethodForClassRule(AClassHasNoNothingAtAll.class);

    private final Function<Annotation, Boolean> mDaRealAnnotationFilta =
            a -> DaRealAnnotation.class.isInstance(a);

    @Test
    public void testGetTestName_null() {
        assertThrows(NullPointerException.class, () -> TestHelper.getTestName(null));
    }

    @Test
    public void testGetTestName_testMethod() {
        Description test = createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

        expect.withMessage("getTestName(%s)", test)
                .that(TestHelper.getTestName(test))
                .isEqualTo("AClassHasNoNothingAtAll#The name is Case, Test Case!()");
    }

    @Test
    public void testGetTestName_testClass() {
        Description test = createSuiteDescription(AClassHasNoNothingAtAll.class);

        expect.withMessage("getTestName(%s)", test)
                .that(TestHelper.getTestName(test))
                .isEqualTo("AClassHasNoNothingAtAll");
    }

    @Test
    public void testGetAnnotationFromAnywhere_null() {
        Description test = createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

        assertThrows(
                NullPointerException.class,
                () -> getAnnotationFromAnywhere(test, /* annotationClass= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getAnnotationFromAnywhere((Description) null, DaRealAnnotation.class));
    }

    @Test
    public void testGetAnnotationFromAnywhere_notSetAnywhere() {
        Description test = createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

        expect.withMessage("getAnnotation(%s)", test)
                .that(getAnnotationFromAnywhere(test, DaRealAnnotation.class))
                .isNull();
    }

    @Test
    public void testGetAnnotationFromAnywhere_fromMethod() {
        Description test =
                createTestDescription(
                        AClassHasAnAnnotationAndAParent.class,
                        TEST_NAME,
                        newDaFakeAnnotation(VALUE_ON_METHOD));

        DaRealAnnotation annotation = getAnnotationFromAnywhere(test, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from method", VALUE_ON_METHOD);
    }

    @Test
    public void testGetAnnotationFromAnywhere_fromClass() {
        Description test = createTestDescription(AClassHasAnAnnotationAndAParent.class, TEST_NAME);

        DaRealAnnotation annotation = getAnnotationFromAnywhere(test, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from class", VALUE_ON_SUPERCLASS);
    }

    @Test
    public void testGetAnnotationFromAnywhere_fromParentClass() {
        Description test =
                createTestDescription(AClassHasNoAnnotationButItsParentDoes.class, TEST_NAME);

        DaRealAnnotation annotation = getAnnotationFromAnywhere(test, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from superclass", VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationFromAnywhere_fromImplementedInterface() {
        Description test =
                createTestDescription(AClassHasNoAnnotationButItsInterfaceDoes.class, TEST_NAME);

        DaRealAnnotation annotation = getAnnotationFromAnywhere(test, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from interface", VALUE_ON_INTERFACE);
    }

    @Test
    public void testGetAnnotationFromAnywhere_fromParentsImplementedInterface() {
        Description test =
                createTestDescription(
                        AClassHasNoAnnotationButItsParentsInterfaceDoes.class, TEST_NAME);

        DaRealAnnotation annotation = getAnnotationFromAnywhere(test, DaRealAnnotation.class);

        assertDaRealAnnotation(
                annotation, "annotation from parent's interface", VALUE_ON_INTERFACE);
    }

    @Test
    public void testGetAnnotationFromAnywhere_fromGrandParentClass() {
        Description test =
                createTestDescription(AClassHasNoAnnotationButItsGrandParentDoes.class, TEST_NAME);

        DaRealAnnotation annotation = getAnnotationFromAnywhere(test, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from class", VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationFromTypesOnly_null() {
        assertThrows(
                NullPointerException.class,
                () ->
                        getAnnotationFromTypesOnly(
                                AClassHasNoNothingAtAll.class, /* annotationClass= */ null));
        assertThrows(
                NullPointerException.class,
                () ->
                        getAnnotationFromTypesOnly(
                                /* testClass= */ (Class<?>) null, DaRealAnnotation.class));
    }

    @Test
    public void testGetAnnotationFromTypesOnly_notSetAnywhere() {
        Class<?> testClass = AClassHasNoNothingAtAll.class;

        expect.withMessage("getAnnotation(%s)", testClass)
                .that(getAnnotationFromTypesOnly(testClass, DaRealAnnotation.class))
                .isNull();
    }

    @Test
    public void testGetAnnotationFromTypesOnly_fromClass() {
        Class<?> testClass = AClassHasAnAnnotationAndAParent.class;

        DaRealAnnotation annotation = getAnnotationFromTypesOnly(testClass, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from parent class", VALUE_ON_SUPERCLASS);
    }

    @Test
    public void testGetAnnotationFromTypesOnly_fromParentClass() {
        Class<?> testClass = AClassHasNoAnnotationButItsParentDoes.class;

        DaRealAnnotation annotation = getAnnotationFromTypesOnly(testClass, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from class", VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationFromTypesOnly_fromGrandParentClass() {
        Class<?> testClass = AClassHasNoAnnotationButItsGrandParentDoes.class;

        DaRealAnnotation annotation = getAnnotationFromTypesOnly(testClass, DaRealAnnotation.class);

        assertDaRealAnnotation(annotation, "annotation from class", VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationsFromEverywhere_null() {
        Description test = createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

        assertThrows(
                NullPointerException.class,
                () -> getAnnotationsFromEverywhere(test, /* filter= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getAnnotationsFromEverywhere(/* test= */ null, mDaRealAnnotationFilta));
    }

    @Test
    public void testGetAnnotationsFromEverywhere_notSetAnywhere() {
        Description test = createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

        expect.withMessage("getAnnotations(%s)", test)
                .that(getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta))
                .isEmpty();
    }

    @Test
    public void testGetAnnotationsFromEverywhere_fromMethodOnly() {
        Description test =
                createTestDescription(
                        AClassHasNoNothingAtAll.class,
                        TEST_NAME,
                        newDaFakeAnnotation(VALUE_ON_METHOD));

        var annotations = getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta);

        assertWithMessage("getAnnotations(%s)", test).that(annotations).isNotNull();
        assertWithMessage("getAnnotations(%s)", test).that(annotations).hasSize(1);

        assertDaRealAnnotation(annotations.get(0), "method annotation", VALUE_ON_METHOD);
    }

    @Test
    public void testGetAnnotationsFromEverywhere_fromClassOnly() {
        Description test = createTestDescription(AClassHasAnAnnotation.class, TEST_NAME);

        var annotations = getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta);

        assertWithMessage("getAnnotations(%s)", test).that(annotations).isNotNull();
        assertWithMessage("getAnnotations(%s)", test).that(annotations).hasSize(1);

        assertDaRealAnnotation(annotations.get(0), "class annotation", VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationsFromEverywhere_fromInterfaceOnly() {
        Description test =
                createTestDescription(
                        AClassHasNoAnnotationButItsParentsInterfaceDoes.class, TEST_NAME);

        var annotations = getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta);

        assertWithMessage("getAnnotations(%s)", test).that(annotations).isNotNull();
        assertWithMessage("getAnnotations(%s)", test).that(annotations).hasSize(1);

        assertDaRealAnnotation(annotations.get(0), "interface annotation", VALUE_ON_INTERFACE);
    }

    @Test
    public void testGetAnnotationsFromEverywhere_fromSuperClassOnly() {
        Description test = createTestDescription(AClassHasAnAnnotation.class, TEST_NAME);

        var annotations = getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta);

        assertWithMessage("getAnnotations(%s)", test).that(annotations).isNotNull();
        assertWithMessage("getAnnotations(%s)", test).that(annotations).hasSize(1);

        var annotation = annotations.get(0);
        assertWithMessage("annotation").that(annotation).isInstanceOf(DaRealAnnotation.class);

        DaRealAnnotation daRealAnnotation = (DaRealAnnotation) annotation;
        expect.withMessage("annotation.value")
                .that(daRealAnnotation.value())
                .isEqualTo(VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationsFromEverywhere_fromMethodAndClass() {
        Description test =
                Description.createTestDescription(
                        AClassHasAnAnnotation.class, "test", newDaFakeAnnotation(VALUE_ON_METHOD));

        var annotations = getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta);

        assertWithMessage("getAnnotations(%s)", test).that(annotations).isNotNull();
        assertWithMessage("getAnnotations(%s)", test).that(annotations).hasSize(2);

        assertDaRealAnnotation(annotations.get(0), "method annotation", VALUE_ON_METHOD);
        assertDaRealAnnotation(annotations.get(1), "class annotation", VALUE_ON_CLASS);
    }

    @Test
    public void testGetAnnotationsFromEverywhere_fromEverywhere() {
        Description test =
                Description.createTestDescription(
                        AClassWithEverything.class, "test", newDaFakeAnnotation(VALUE_ON_METHOD));

        var annotations = getAnnotationsFromEverywhere(test, mDaRealAnnotationFilta);

        assertWithMessage("getAnnotations(%s)", test).that(annotations).isNotNull();
        assertWithMessage("getAnnotations(%s)", test).that(annotations).hasSize(7);

        assertDaRealAnnotation(annotations.get(0), "method annotation", VALUE_ON_METHOD);
        assertDaRealAnnotation(annotations.get(1), "class annotation", VALUE_ON_CLASS);
        assertDaRealAnnotation(annotations.get(2), "interface annotation", VALUE_ON_INTERFACE);
        assertDaRealAnnotation(annotations.get(3), "superclass annotation", VALUE_ON_SUPERCLASS);
        assertDaRealAnnotation(
                annotations.get(4), "superclass interface annotation", VALUE_ON_INTERFACE);
        assertDaRealAnnotation(
                annotations.get(5), "grand-superclass annotation", VALUE_ON_GRAND_SUPERCLASS);
        assertDaRealAnnotation(
                annotations.get(6), "grand-superclass interface annotation", VALUE_ON_INTERFACE);
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
                Description.createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

        // No exception should be thrown for test.
        TestHelper.throwIfNotTest(test);
    }

    @Test
    public void testThrowIfTest_withNonTestDescription_throwsException() {
        Description test =
                Description.createTestDescription(AClassHasNoNothingAtAll.class, TEST_NAME);

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

    private void assertDaRealAnnotation(
            Annotation annotation, String description, String expectedValue) {
        assertWithMessage(description).that(annotation).isInstanceOf(DaRealAnnotation.class);
        DaRealAnnotation daRealAnnotation = (DaRealAnnotation) annotation;
        expect.withMessage("value of %s", description)
                .that(daRealAnnotation.value())
                .isEqualTo(expectedValue);
    }

    @DaRealAnnotation(VALUE_ON_CLASS)
    private static class AClassHasAnAnnotation {}

    @DaRealAnnotation(VALUE_ON_INTERFACE)
    private interface AnInterfaceHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsInterfaceDoes
            implements AnInterfaceHasAnAnnotation {}

    @DaRealAnnotation(VALUE_ON_SUPERCLASS)
    private static class AClassHasAnAnnotationAndAParent extends AClassHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsParentDoes extends AClassHasAnAnnotation {}

    private static class AClassHasNoAnnotationButItsParentsInterfaceDoes
            extends AClassHasNoAnnotationButItsInterfaceDoes {}

    private static class AClassHasNoAnnotationButItsGrandParentDoes
            extends AClassHasNoAnnotationButItsParentDoes {}

    @DaRealAnnotation(VALUE_ON_GRAND_SUPERCLASS)
    private static class AGrandParentClassHasAnAnnotationAndAnInterface
            implements AnInterfaceHasAnAnnotation {}

    @DaRealAnnotation(VALUE_ON_SUPERCLASS)
    private static class AParentClassHasAnAnnotationAndAnInterface
            extends AGrandParentClassHasAnAnnotationAndAnInterface
            implements AnInterfaceHasAnAnnotation {}

    @DaRealAnnotation(VALUE_ON_CLASS)
    private static class AClassWithEverything extends AParentClassHasAnAnnotationAndAnInterface
            implements AnInterfaceHasAnAnnotation {}

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
