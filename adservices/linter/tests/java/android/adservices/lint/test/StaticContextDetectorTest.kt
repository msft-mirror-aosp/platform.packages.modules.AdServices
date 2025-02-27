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

import android.adservices.lint.common.StaticContextDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StaticContextDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = StaticContextDetector()

    override fun getIssues(): List<Issue> = listOf(StaticContextDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testContextInGetInstance_throws() {
        lint()
            .files(
                java(
                        """
        package com.android.andservices;

        import android.content.Context;

        class Test {
          public static void getInstance(Context context) {}
        }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(StaticContextDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/android/andservices/Test.java:6: Error: DO NOT pass a Context on static methods, but use ApplicationContextSingleton.get() to get the context instead. [AvoidStaticContext]
  public static void getInstance(Context context) {}
                     ~~~~~~~~~~~
1 errors, 0 warnings"""
                    .trimIndent()
            )
    }

    @Test
    fun testContextInStaticMethodMultipleParams_throws() {
        lint()
            .files(
                java(
                        """
        package com.android.andservices;

        import android.content.Context;

        class Test {
          static void createTest(String string, boolean isTrue, Context context) {}
        }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(StaticContextDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/android/andservices/Test.java:6: Error: DO NOT pass a Context on static methods, but use ApplicationContextSingleton.get() to get the context instead. [AvoidStaticContext]
  static void createTest(String string, boolean isTrue, Context context) {}
              ~~~~~~~~~~
1 errors, 0 warnings"""
                    .trimIndent()
            )
    }

    @Test
    fun testOtherContextInStaticMethod_pass() {
        lint()
            .files(
                java(
                        """
        package com.android.andservices;

        import android.somepackage.content.Context;

        class Test {
          static void createTest(String string, boolean isTrue, Context context) {}
        }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(StaticContextDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testContextInNonStaticMethod_pass() {
        lint()
            .files(
                java(
                        """
        package com.android.andservices;

        import android.content.Context;

        class Test {
          public void createTest(String string, boolean isTrue, Context context) {}
        }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(StaticContextDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testContextInConstructor_pass() {
        lint()
            .files(
                java(
                        """
        package com.android.andservices;

        import android.content.Context;

        class Test {
          Context mContext;
          public Test(Context context) {
            mContext = context;
          }
        }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(StaticContextDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testContextInPrivateStaticMethod_pass() {
        lint()
            .files(
                java(
                        """
        package com.android.andservices;

        import android.content.Context;

        class Test {
          Context mContext;
          private static test(Context context) {
            mContext = context;
          }
        }
        """
                    )
                    .indented(),
                *stubs,
            )
            .issues(StaticContextDetector.ISSUE)
            .run()
            .expectClean()
    }

    private val androidContextStub: TestFile =
        java("""
    package android.content;

    public class Context {}
    """).indented()

    private val otherContextStub: TestFile =
        java("""
    package android.somepackage.content;

    public class Context {}
    """)
            .indented()

    private val stubs = arrayOf(androidContextStub, otherContextStub)
}
