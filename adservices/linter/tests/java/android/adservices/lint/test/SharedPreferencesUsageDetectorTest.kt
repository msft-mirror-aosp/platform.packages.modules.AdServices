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

package android.adservices.lint.test

import android.adservices.lint.common.SharedPreferencesUsageDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SharedPreferencesUsageDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = SharedPreferencesUsageDetector()

    override fun getIssues(): List<Issue> = listOf(SharedPreferencesUsageDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testApplicableFileMethods2_throws() {
        lint()
            .files(
                java(
                        """
            package com.android.adservices;

            import android.content.Context;
            import android.content.SharedPreferences;

            public class MyClass {
                public void myMethod(Context context) {
                    SharedPreferences sharedPrefs = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
                }
            }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(SharedPreferencesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/android/adservices/MyClass.java:8: Error: DO NOT USE SharedPreferences for any new usage, use ProtoDatastore instead. Check GuavaDatastore.java which is a wrapper around ProtoDatastore. For existing usage, the warning can be suppressed. [AvoidSharedPreferences]
                        SharedPreferences sharedPrefs = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
                                                                ~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
            """
                    .trimIndent()
            )
    }

    @Test
    fun testSameClass_differentMethodName_doesNotThrow() {
        lint()
            .files(
                java(
                        """
            package com.android.adservices;

            import android.content.Context;
            import android.content.SharedPreferences;

            public class MyClass {
                public void myMethod(Context context) {
                    String unused = context.getPackageCodePath();
                }
            }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(SharedPreferencesUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun sameMethodNames_differentClasses_doesNotThrow() {
        lint()
            .files(
                java(
                        """
            package com.android.adservices;

            import android.adservices.fakeContext.Context;
            import android.content.SharedPreferences;

            public class MyClass {
                public void myMethod(Context context) {
                    SharedPreferences sharedPrefs = context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE);
                    String value = sharedPrefs.getString("my_key", "default");
                }
            }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(SharedPreferencesUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    // Generate the classes imported by the compiled code.

    private val androidSharedPreferencesClassStub: TestFile =
        java(
                """
            package android.content;

            public class SharedPreferences {
                public static String getString(String namespace, String name, String defaultValue) {
                   return defaultValue;
                }
                public static boolean getBoolean(String namespace, String name, boolean defaultValue) {
                   return defaultValue;
                }
                public static int getInt(String namespace, String name, int defaultValue) {
                   return defaultValue;
                }
            }
        """
            )
            .indented()

    private val contextStub: TestFile =
        java(
                """
            package android.content;

            import android.content.SharedPreferences;

            public abstract class Context {
                public static final int MODE_PRIVATE = 0x0000;
                public abstract SharedPreferences getSharedPreferences(String name, int mode);
                public abstract String getPackageCodePath();
            }
        """
            )
            .indented()

    private val otherContextStub: TestFile =
        java(
                """
            package android.adservices.fakeContext;

            import android.content.SharedPreferences;

            public abstract class Context {
                public static final int MODE_PRIVATE = 0x0000;
                public abstract SharedPreferences getSharedPreferences(String name, int mode);
            }
        """
            )
            .indented()

    private val stubs = arrayOf(androidSharedPreferencesClassStub, contextStub, otherContextStub)
}
