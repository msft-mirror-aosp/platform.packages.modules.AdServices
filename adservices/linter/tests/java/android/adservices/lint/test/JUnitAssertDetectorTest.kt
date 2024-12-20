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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class JUnitAssertDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = JUnitAssertDetector()

    override fun getIssues(): List<Issue> = listOf(JUnitAssertDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testJUnitAssertEquals_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertEquals;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertEquals(1, 2);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertEquals(1, 2);
                        ~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertTrue_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertTrue;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertTrue(true);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertTrue(true);
                        ~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertFalse_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertFalse;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertFalse(false);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertFalse(false);
                        ~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertNotEquals_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertNotEquals;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertNotEquals(1, 2);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertNotEquals(1, 2);
                        ~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertArrayEquals_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertArrayEquals;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertArrayEquals(new int[] {1, 2, 3}, new int[] {1, 2, 3});
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertArrayEquals(new int[] {1, 2, 3}, new int[] {1, 2, 3});
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertNull_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertNull;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertNull(new Object());
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertNull(new Object());
                        ~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertNotNull_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertNotNull;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertNotNull(new Object());
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertNotNull(new Object());
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertSame_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertSame;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertSame(new Object(), new Object());
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertSame(new Object(), new Object());
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testJunitAssertNotSame_throws() {
        lint()
            .files(
                java(
                        """
                    package com.example.app;
                    import static org.junit.Assert.assertNotSame;
                    public class ExampleUnitTest {
                        public void testMethod() {
                            assertNotSame(new Object(), new Object());
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs,
            )
            .issues(JUnitAssertDetector.ISSUE)
            .run()
            .expect(
                """
                src/com/example/app/ExampleUnitTest.java:5: Warning: Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows). [JUnitAssertUsage]
                        assertNotSame(new Object(), new Object());
                        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                0 errors, 1 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testAssertThrows_pass() {
        lint()
            .files(
                java(
                        """
                package com.example.app;
                import static org.junit.Assert.assertThrows;
                public class ExampleUnitTest {
                    public void testMethod() {
                        assertThrows(IllegalArgumentException.class, () -> {
                            // Some code that throws exception
                        });
                    }
                }
                """
                    )
                    .indented(),
                *stubs,
            )
            .run()
            .expectClean()
    }

    private val junitAssertStub =
        java(
                """
            package org.junit;
            public class Assert {
                public static void assertTrue(boolean condition) {}
                public static void assertEquals(Object expected, Object actual) {}
                public static void assertThrows(Class<?> expectedThrowable, ThrowingRunnable runnable) {}
                public static void assertFalse(boolean condition) {}
                public static void assertNotEquals(Object expected, Object actual) {}
                public static void assertArrayEquals(Object expected, Object actual) {}
                public static void assertNull(Object actual) {}
                public static void assertNotNull(Object actual) {}
                public static void assertSame(Object expected, Object actual) {}
                public static void assertNotSame(Object expected, Object actual) {}
                public static void fail() {}
            }
            """
            )
            .indented()

    private val stubs = arrayOf(junitAssertStub)
}
