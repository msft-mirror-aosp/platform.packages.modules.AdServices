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

package android.adservices.lint.test

import android.adservices.lint.common.CompletableFutureUsageDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CompletableFutureUsageDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = CompletableFutureUsageDetector()

    override fun getIssues(): List<Issue> = listOf(CompletableFutureUsageDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testListenableFuture_pass() {
        lint()
            .files(
                java(
                        "package test.pkg;\n" +
                            "import com.google.common.util.concurrent.ListenableFuture;\n" +
                            "class TestClass {\n" +
                            "    public ListenableFuture<String> test() {\n" +
                            "        return Futures.immediateFuture(\"OK\");\n" +
                            "    }\n" +
                            "}\n"
                    )
                    .indented()
            )
            .allowCompilationErrors()
            .issues(CompletableFutureUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testCompletableFutureConstructor_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example;

                    import java.util.concurrent.CompletableFuture;

                    public class TestClass {
                        public void useCompletableFuture() {
                            CompletableFuture<String> future = new CompletableFuture<>(); // Expect warning here
                        }
                    }
                    """
                    )
                    .indented()
            )
            .allowCompilationErrors()
            .issues(CompletableFutureUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/TestClass.java:7: Warning: DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead. (Constructor call to CompletableFuture). [CompletableFutureUsage]
        CompletableFuture<String> future = new CompletableFuture<>(); // Expect warning here
                                           ~~~~~~~~~~~~~~~~~~~~~~~~~
src/com/example/TestClass.java:7: Warning: DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead. (Variable/field of type CompletableFuture). [CompletableFutureUsage]
        CompletableFuture<String> future = new CompletableFuture<>(); // Expect warning here
        ~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 2 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testCompletableFutureMethodCall_throws() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                import java.util.concurrent.CompletableFuture;
                import java.util.function.Supplier;

                class MyTest {
                    public void test() {
                      CompletableFuture.supplyAsync(() -> "Hello");
                    }
                }
                """
                    )
                    .indented()
            )
            .allowCompilationErrors()
            .issues(CompletableFutureUsageDetector.ISSUE)
            .run()
            .expect(
                """
            src/test/pkg/MyTest.java:7: Warning: DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead. (Method call on CompletableFuture). [CompletableFutureUsage]
                  CompletableFuture.supplyAsync(() -> "Hello");
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            0 errors, 1 warnings
            """
                    .trimIndent()
            )
    }

    @Test
    fun testCompletableFutureInstanceMethodCall_throws() {
        lint()
            .files(
                java(
                        """
                package test.pkg;
                import java.util.concurrent.CompletableFuture;

                class MyTest {
                    public void test() {
                      CompletableFuture<String> future = new CompletableFuture<>();
                      future.thenApply(value -> value + "!");
                    }
                }
                """
                    )
                    .indented()
            )
            .allowCompilationErrors()
            .issues(CompletableFutureUsageDetector.ISSUE)
            .run()
            .expect(
                """
            src/test/pkg/MyTest.java:6: Warning: DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead. (Constructor call to CompletableFuture). [CompletableFutureUsage]
      CompletableFuture<String> future = new CompletableFuture<>();
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/MyTest.java:6: Warning: DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead. (Variable/field of type CompletableFuture). [CompletableFutureUsage]
      CompletableFuture<String> future = new CompletableFuture<>();
      ~~~~~~~~~~~~~~~~~~~~~~~~~
src/test/pkg/MyTest.java:7: Warning: DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead. (Method call on CompletableFuture). [CompletableFutureUsage]
      future.thenApply(value -> value + "!");
      ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 3 warnings
            """
                    .trimIndent()
            )
    }
}
