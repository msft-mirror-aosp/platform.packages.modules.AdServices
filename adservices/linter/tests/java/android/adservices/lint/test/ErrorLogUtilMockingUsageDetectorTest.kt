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

import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.INVALID_ANNOTATION_MESSAGE
import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.INVALID_MOCKING_MESSAGE
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ErrorLogUtilMockingUsageDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ErrorLogUtilMockingUsageDetector()

    override fun getIssues(): List<Issue> =
        listOf(
            ErrorLogUtilMockingUsageDetector.INVALID_ANNOTATION_ISSUE,
            ErrorLogUtilMockingUsageDetector.MOCKING_INVOCATION_ISSUE
        )

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
                  }
                }
                  """
                    )
                    .indented()
            )
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
                }
                  """
                    )
                    .indented()
            )
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
            .run()
            .expectErrorCount(1)
            .expectContains(INVALID_ANNOTATION_MESSAGE)
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
            .run()
            .expectErrorCount(1)
            .expectContains(INVALID_ANNOTATION_MESSAGE)
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
            .run()
            .expectErrorCount(1)
            .expectContains(INVALID_ANNOTATION_MESSAGE)
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
            .run()
            .expectErrorCount(1)
            .expectContains(INVALID_ANNOTATION_MESSAGE)
    }

    @Test
    fun testErrorLogUtilMockingInNonExtendedMockitoSubclass_passes() {
        lint()
            .files(
                java(
                        """
                package com.test;
                import static com.test.ExtendedMockito.doNothing;
                public final class FooTest {
                  @Before
                  public void setup() {
                    doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
                  }
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .run()
            .expectClean()
    }

    @Test
    fun testNoErrorLogUtilMockingIssues_passes() {
        lint()
            .files(
                java(
                        """
                package com.test;
                @MockStatic(Foo1.class)
                @SpyStatic(Foo2.class)
                public final class FooTest extends AdServicesExtendedMockitoTestCase {
                  @Before
                  public void setup() {
                    fooMethod();
                  }
                  @Test
                  public void testFoo() {
                    foo();
                  }
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .run()
            .expectClean()
    }

    @Test
    fun testErrorLogUtilMockingUsingUtilsInNonExtendedMockitoSubclass_passes() {
        lint()
            .files(
                java(
                        """
                package com.test;
                import static com.test.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
                public final class FooTest extends IntermediateClass {
                  @Before
                  public void setup() {
                    doNothingOnErrorLogUtilError();
                  }
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .run()
            .expectClean()
    }

    @Test
    fun testErrorLogUtilMockingInExtendedMockitoSubclass_throws() {
        lint()
            .files(
                java(
                        """
                package com.test;
                import static com.test.ExtendedMockito.doNothing;
                public final class FooTest extends IntermediateClassExtendsSuperclass {
                  @Before
                  public void setup() {
                    doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
                  }
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .run()
            .expectErrorCount(1)
            .expectContains(INVALID_MOCKING_MESSAGE)
    }

    @Test
    fun testErrorLogUtilMockingUsingUtilsInExtendedMockitoSubclass_throws() {
        lint()
            .files(
                java(
                        """
                package com.test;
                import static com.test.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
                public final class FooTest extends IntermediateClassExtendsSuperclass {
                  @Before
                  public void setup() {
                    doNothingOnErrorLogUtilError();
                  }
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .run()
            .expectErrorCount(1)
            .expectContains(INVALID_MOCKING_MESSAGE)
    }

    @Test
    fun testMultipleErrorLogUtilMockingIssues_throwsMultiple() {
        lint()
            .files(
                java(
                        """
                package com.test;
                import static com.test.ExtendedMockitoExpectations.doNothingOnErrorLogUtilError;
                @SpyStatic(ErrorLogUtil.class)
                public final class FooTest extends IntermediateClassExtendsSuperclass {
                  @Before
                  public void setup() {
                    doNothingOnErrorLogUtilError();
                  }
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .run()
            .expectErrorCount(2)
            .expectContains(INVALID_ANNOTATION_MESSAGE)
            .expectContains(INVALID_MOCKING_MESSAGE)
    }

    private val extendedMockitoExpectations: TestFile =
        java(
                """
            package com.test;
            public final class ExtendedMockitoExpectations {
                public static void doNothingOnErrorLogUtilError(){
                }
            }
        """
            )
            .indented()

    private val adServicesExtendedMockitoTestCase: TestFile =
        java(
                """
            package com.test;
            public class AdServicesExtendedMockitoTestCase {
            }
        """
            )
            .indented()

    private val intermediateClassExtendsSuperclass: TestFile =
        java(
                """
            package com.test;
            public class IntermediateClassExtendsSuperclass extends AdServicesExtendedMockitoTestCase {
            }
        """
            )
            .indented()

    private val intermediateClass: TestFile =
        java(
                """
            package com.test;
            public class IntermediateClass {
            }
        """
            )
            .indented()

    private val extendedMockito: TestFile =
        java(
                """
            package com.test;
            public final class ExtendedMockito {
                public static StaticCapableStubber doNothing() {}
            }
        """
            )
            .indented()

    private val staticCapableStubber: TestFile =
        java(
                """
            package com.test;
            public final class StaticCapableStubber {
                public void when(MockedVoidMethod method) {}
            }
        """
            )
            .indented()

    private val stubs =
        arrayOf(
            extendedMockitoExpectations,
            adServicesExtendedMockitoTestCase,
            intermediateClassExtendsSuperclass,
            intermediateClass,
            extendedMockito,
            staticCapableStubber
        )
}
