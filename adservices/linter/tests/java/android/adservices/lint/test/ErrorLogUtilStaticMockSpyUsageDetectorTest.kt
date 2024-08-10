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

import android.adservices.lint.test.ErrorLogUtilStaticMockSpyUsageDetector.Constants.ERROR_MESSAGE
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ErrorLogUtilStaticMockSpyUsageDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ErrorLogUtilStaticMockSpyUsageDetector()

    override fun getIssues(): List<Issue> = listOf(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testNoErrorLogUtilMocksSpies_pass() {
        lint()
            .files(
                java(
                        """
                @MockStatic(Foo1.class)
                @SpyStatic(Foo2.class)
                public final class FooTest extends AdServicesExtendedMockitoTestCase {
                  @Test
                  @MockStatic(Foo3.class)
                  @SpyStatic(Foo4.class)
                  public void testFoo() {
                    doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
                  }
                }
                  """
                    )
                    .indented()
            )
            .issues(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testSpyErrorLogUtilInSuperclass_pass() {
        lint()
            .files(
                java(
                        """
                @SpyStatic(ErrorLogUtil.class)
                public class AdServicesExtendedMockitoTestCase {
                  // ...
                }
                  """
                    )
                    .indented()
            )
            .issues(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testMockErrorLogUtilOverClass_throws() {
        lint()
            .files(
                java(
                        """
                @MockStatic(Foo1.class)
                @MockStatic(ErrorLogUtil.class)
                public final class FooTest extends AdServicesExtendedMockitoTestCase {
                  @Test
                  public void testFoo() {
                  }
                }
                  """
                    )
                    .indented()
            )
            .issues(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains(ERROR_MESSAGE)
    }

    @Test
    fun testSpyErrorLogUtilOverClass_throws() {
        lint()
            .files(
                java(
                        """
                @MockStatic(Foo1.class)
                @SpyStatic(ErrorLogUtil.class)
                public final class FooTest extends AdServicesExtendedMockitoTestCase {
                  @Test
                  public void testFoo() {
                  }
                }
                  """
                    )
                    .indented()
            )
            .issues(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains(ERROR_MESSAGE)
    }

    @Test
    fun testMockErrorLogUtilOverMethod_throws() {
        lint()
            .files(
                java(
                        """
                @MockStatic(Foo1.class)
                public final class FooTest extends AdServicesExtendedMockitoTestCase {
                  @Test
                  @MockStatic(ErrorLogUtil.class)
                  public void testFoo() {
                  }
                }
                  """
                    )
                    .indented()
            )
            .issues(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains(ERROR_MESSAGE)
    }

    @Test
    fun testSpyErrorLogUtilOverMethod_throws() {
        lint()
            .files(
                java(
                        """
                @MockStatic(Foo1.class)
                public final class FooTest extends AdServicesExtendedMockitoTestCase {
                  @Test
                  @SpyStatic(ErrorLogUtil.class)
                  public void testFoo() {
                  }
                }
                  """
                    )
                    .indented()
            )
            .issues(ErrorLogUtilStaticMockSpyUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expectContains(ERROR_MESSAGE)
    }
}
