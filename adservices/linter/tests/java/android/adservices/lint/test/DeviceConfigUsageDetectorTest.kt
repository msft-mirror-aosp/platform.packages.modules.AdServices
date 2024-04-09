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

import android.adservices.lint.BackCompatNewFileDetector
import android.adservices.lint.DeviceConfigUsageDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DeviceConfigUsageDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = DeviceConfigUsageDetector()

    override fun getIssues(): List<Issue> = listOf(DeviceConfigUsageDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun applicableMethodCalls_getString_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getString("Namespace", "Bond", "James Bond");
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getString("Namespace", "Bond", "James Bond");
                     ~~~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_getBoolean_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getBoolean("To Be", "Or Not To Be", true);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getBoolean("To Be", "Or Not To Be", true);
                     ~~~~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_getInt_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getInt("MeaningOf", "Live, Universe, and Everything", 42);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getInt("MeaningOf", "Live, Universe, and Everything", 42);
                     ~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_getLong_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getLong("MeaningOf", "Live, Universe, and Everything", 42L);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getLong("MeaningOf", "Live, Universe, and Everything", 42L);
                     ~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }


    @Test
    fun applicableMethodCalls_getFloat_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getFloat("MeaningOf", "Live, Universe, and Everything", 4.20);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getFloat("MeaningOf", "Live, Universe, and Everything", 4.20);
                     ~~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_getProperty_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getProperty("Of", "Ned Flanders");
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getProperty("Of", "Ned Flanders");
                     ~~~~~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_getProperties_throws() {
        lint().files(java("""
package com.android.adservices;

import android.provider.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getProperties("Of", "Ned", "Flanders");
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expect(
                        """
                        src/com/android/adservices/FakeClass.java:7: Error: DO NOT CALL android.provider.DeviceConfig methods directly, but adservices-specifc helpers instead. For example, on PhFlags.java, you should call getDeviceConfigFlag(name, defaultValue). [AvoidDeviceConfigUsage]
        DeviceConfig.getProperties("Of", "Ned", "Flanders");
                     ~~~~~~~~~~~~~
1 errors, 0 warnings
                        """.trimIndent())
    }

    @Test
    fun applicableMethodCalls_getString_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getString("Namespace", "Bond", "James Bond");
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_getBoolean_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getBoolean("To Be", "Or Not To Be", true);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_getInt_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getInt("Meaning", "Of Live", 42);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_getLong_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getLong("Meaning", "Of Live", 42L);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_getFloat_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getFloat("Meaning", "Of Live", 4.20);
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_getProperty_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getProperty("Of", "Ned Flanders");
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    @Test
    fun applicableMethodCalls_getProperties_pass() {
        lint().files(java("""
package com.android.adservices;

import com.android.adservices.util.awesome_helpers.DeviceConfig;

public final class FakeClass {
    public FakeClass() {
        DeviceConfig.getProperties("Of", "Ned", "Flanders");
    }
}
                """).indented(),
                *stubs)
                .issues(DeviceConfigUsageDetector.ISSUE)
                .run()
                .expectClean()
    }

    // Generate the classes imported by the compiled code.

    private val androidDeviceConfigClassStub: TestFile =
            java("""
            package android.provider;

            public class DeviceConfig {
                public static String getString(String namespace, String name, String defaultValue) {
                   return defaultValue;
                }
                public static boolean getBoolean(String namespace, String name, boolean defaultValue) {
                   return defaultValue;
                }
                public static int getInt(String namespace, String name, int defaultValue) {
                   return defaultValue;
                }
                public static long getLong(String namespace, String name, long defaultValue) {
                   return defaultValue;
                }
                public static float getFloat(String namespace, String name, float defaultValue) {
                   return defaultValue;
                }
                public static String getProperty(String namespace, String name) {
                   return null;
                }
                public static Properties getProperties(String namespace, String... names) {
                   return null;
                }
            }
        """).indented()

    private val adServicesDeviceConfigClassStub: TestFile =
            java("""
            package com.android.adservices.util.awesome_helpers;

            public class DeviceConfig {
                public static String getString(String namespace, String name, String defaultValue) {
                   return defaultValue;
                }
                public static boolean getBoolean(String namespace, String name, boolean defaultValue) {
                   return defaultValue;
                }
                public static int getInt(String namespace, String name, int defaultValue) {
                   return defaultValue;
                }
                public static long getLong(String namespace, String name, long defaultValue) {
                   return defaultValue;
                }
                public static float getFloat(String namespace, String name, float defaultValue) {
                   return defaultValue;
                }
                public static String getProperty(String namespace, String name) {
                   return null;
                }
                public static Properties getProperties(String namespace, String... names) {
                   return null;
                }
            }
        """).indented()

    private val stubs =
            arrayOf(
                    androidDeviceConfigClassStub,
                    adServicesDeviceConfigClassStub
            )
}