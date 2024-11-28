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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.AdServicesFrameworkHelper;

import androidx.annotation.Nullable;

import org.junit.Test;

/** Cts Test of {@link AdServicesFrameworkHelper}. */
public final class AdservicesFrameworkHelperTest extends CtsAdServicesDeviceTestCase {
    @Test
    public void testGetExceptionStackTraceString_empty() {
        Throwable testException = new Throwable("testEmptyStackException");
        String stackString = AdServicesFrameworkHelper.getExceptionStackTraceString(testException);
        assertWithMessage("no stacktrace should return testEmptyStackException message")
                .that(stackString)
                .contains("java.lang.Throwable: testEmptyStackException");
    }

    @Test
    public void testGetExceptionStackTraceString_regular() {
        Throwable testException = new Throwable("test exception message");
        StackTraceElement[] stackTrace =
                new StackTraceElement[] {
                    new StackTraceElement("testClass1", "testMethod1", "testFile1", 123),
                    new StackTraceElement("testClass2", "testMethod2", "testFile2", 456)
                };
        testException.setStackTrace(stackTrace);

        String stackString = AdServicesFrameworkHelper.getExceptionStackTraceString(testException);

        assertWithMessage("stackString").that(stackString).startsWith("test exception message");
        assertWithMessage("stackString").that(stackString).contains("testClass1");
        assertWithMessage("stackString").that(stackString).contains("testMethod2");
    }

    @Test
    public void testGetExceptionStackTraceString_getCause() {
        Throwable generatedThrowable =
                createThrowableWithMultipleCauses(
                        /* className= */ "causeClass",
                        /* methodName= */ "causeMethod",
                        /* lineNumber= */ 12,
                        /* number= */ 2);
        Throwable testException = new Throwable("test exception message", generatedThrowable);
        testException.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("testClass1", "testMethod1", "testFile1", 123)
                });

        String stackString = AdServicesFrameworkHelper.getExceptionStackTraceString(testException);

        assertWithMessage("stackString").that(stackString).contains("causeClass");
    }

    @Nullable
    private Throwable createThrowableWithMultipleCauses(
            String className, String methodName, int lineNumber, int number) {
        if (number == 0) {
            return null;
        }
        Throwable lastThrowable = new Throwable("lastThrowable");
        StackTraceElement[] lastStackTraceElements =
                new StackTraceElement[] {
                    new StackTraceElement(className, methodName, "file", lineNumber)
                };
        lastThrowable.setStackTrace(lastStackTraceElements);
        Throwable formerThrowable = lastThrowable;
        for (int i = 0; i < number - 1; i++) {
            Throwable throwable =
                    new Throwable(String.format("createThrowable%d", i), formerThrowable);
            StackTraceElement[] stackTraceElements =
                    new StackTraceElement[] {
                        new StackTraceElement(
                                "testClassName" + i, "testMethodName%d" + i, "file", i)
                    };
            throwable.setStackTrace(stackTraceElements);
            formerThrowable = throwable;
        }
        return formerThrowable;
    }
}
