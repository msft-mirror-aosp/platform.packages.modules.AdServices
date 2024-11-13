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
        Throwable testException = new Throwable("testException");
        StackTraceElement[] stackTrace =
                new StackTraceElement[] {
                    new StackTraceElement("testClass1", "testMethod1", "testFile1", 123),
                    new StackTraceElement("testClass2", "testMethod2", "testFile2", 456)
                };
        testException.setStackTrace(stackTrace);
        String stackString = AdServicesFrameworkHelper.getExceptionStackTraceString(testException);
        assertWithMessage("stackString does not contain class1")
                .that(stackString)
                .contains("testClass1");
        assertWithMessage("stackString does not contain testMethod2")
                .that(stackString)
                .contains("testMethod2");
    }
}
