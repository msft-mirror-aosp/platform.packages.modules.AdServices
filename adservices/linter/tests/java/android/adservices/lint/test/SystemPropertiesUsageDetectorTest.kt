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

import android.adservices.lint.common.SystemPropertiesUsageDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SystemPropertiesUsageDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = SystemPropertiesUsageDetector()

    override fun getIssues(): List<Issue> = listOf(SystemPropertiesUsageDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testSystemProperties_get_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.get("test");
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.get("test");
                             ~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_getInt_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.getInt("test", 0);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.getInt("test", 0);
                             ~~~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_getLong_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.getLong("test", 0);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.getLong("test", 0);
                             ~~~~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_getBoolean_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.getBoolean("test", false);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.getBoolean("test", false);
                             ~~~~~~~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_set_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.set("test", "value");
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.set("test", "value");
                             ~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_addChangeCallback_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.addChangeCallback(null);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.addChangeCallback(null);
                             ~~~~~~~~~~~~~~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_removeChangeCallback_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.removeChangeCallback(null);
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.removeChangeCallback(null);
                             ~~~~~~~~~~~~~~~~~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_reportSyspropChanged_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.reportSyspropChanged("test");
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.reportSyspropChanged("test");
                             ~~~~~~~~~~~~~~~~~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testSystemProperties_find_throws() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.os.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.find("test");
                        }
                    }
                    """
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/Test.java:5: Error: DO NOT USE android.os.SystemProperties methods. Use DebugFlags
    (service-core/java/com/android/adservices/service/DebugFlags.java) instead. [AvoidSystemPropertiesUsage]
            SystemProperties.find("test");
                             ~~~~
    1 errors, 0 warnings
                """
                    .trimMargin()
            )
    }

    @Test
    fun testNonAndroidSystemProperties_pass() {
        lint()
            .files(
                java(
                        """
                    package test.pkg;
                    import android.somewhereelse.SystemProperties;
                    public class Test {
                        public void test() {
                            SystemProperties.get("test");
                            SystemProperties.find("test");
                            SystemProperties.reportSyspropChanged("test");
                            SystemProperties.removeChangeCallback(null);
                            SystemProperties.addChangeCallback(null);
                            SystemProperties.set("test", "value");
                            SystemProperties.getBoolean("test", false);
                            SystemProperties.getLong("test", 0);
                            SystemProperties.getInt("test", 0);
                        }
                    }
                    """
                            .trimIndent()
                    )
                    .indented(),
                *stubs
            )
            .issues(SystemPropertiesUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    private val androidSystemPropertiesClassStub: TestFile =
        java(
                """
        package android.os;

        public class SystemProperties {
            public static String get(String key) {
                return "";
            }

            public static int getInt(String key, int def) {
                return 0;
            }

            public static long getLong(String key, long def) {
                return 0;
            }

            public static boolean getBoolean(String key, boolean def) {
                return false;
            }

            public static void set(String key, String value) {
            }

            public static void addChangeCallback(Runnable callback) {
            }

            public static void removeChangeCallback(Runnable callback) {
            }

            public static void reportSyspropChanged(String name) {
            }

            public static String find(String prefix) {
                return "";
            }
        }
        """
            )
            .indented()

    private val otherSystemPropertiesClassStub: TestFile =
        java(
                """
        package android.somewhereelse;

        public class SystemProperties {
            public static String get(String key) {
                return "";
            }

            public static int getInt(String key, int def) {
                return 0;
            }

            public static long getLong(String key, long def) {
                return 0;
            }

            public static boolean getBoolean(String key, boolean def) {
                return false;
            }

            public static void set(String key, String value) {
            }

            public static void addChangeCallback(Runnable callback) {
            }

            public static void removeChangeCallback(Runnable callback) {
            }

            public static void reportSyspropChanged(String name) {
            }

            public static String find(String prefix) {
                return "";
            }
        }
        """
            )
            .indented()

    private val stubs = arrayOf(androidSystemPropertiesClassStub, otherSystemPropertiesClassStub)
}
