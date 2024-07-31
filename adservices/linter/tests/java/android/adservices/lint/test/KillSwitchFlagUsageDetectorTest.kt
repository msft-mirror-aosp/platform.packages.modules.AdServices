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

import android.adservices.lint.common.KillSwitchFlagUsageDetector
import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KillSwitchFlagUsageDetectorTest : LintDetectorTest() {

    override fun getDetector(): Detector = KillSwitchFlagUsageDetector()

    override fun getIssues(): List<Issue> = listOf(KillSwitchFlagUsageDetector.ISSUE)

    override fun lint(): TestLintTask = super.lint().allowMissingSdk(true)

    @Test
    fun testKillSwitchVariableName_withUnderscore_throws() {
        lint()
            .files(
                java(
                        """
                package com.android.adservices.service;

                import com.android.adservices.shared.common.flags.ModuleSharedFlags;

                public interface Flags extends ModuleSharedFlags {
                    boolean AWESOME_FEATURE_KILL_SWITCH = false;
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .issues(KillSwitchFlagUsageDetector.ISSUE)
            .run()
            .expect(
                """
src/com/android/adservices/service/Flags.java:6: Error: DO NOT USE "kill switch" as a flag name, use feature flag instead. [AvoidKillSwitchFlagUsage]
    boolean AWESOME_FEATURE_KILL_SWITCH = false;
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
                    """
                    .trimIndent()
            )
    }

    @Test
    fun testKillSwitchVariableName_withoutUnderscore_throws() {
        lint()
            .files(
                java(
                        """
                package com.android.adservices.service;

                import com.android.adservices.shared.common.flags.ModuleSharedFlags;

                public interface Flags extends ModuleSharedFlags {
                    boolean AWESOME_FEATURE_KILLSWITCH = true;
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .issues(KillSwitchFlagUsageDetector.ISSUE)
            .run()
            .expect(
                """
src/com/android/adservices/service/Flags.java:6: Error: DO NOT USE "kill switch" as a flag name, use feature flag instead. [AvoidKillSwitchFlagUsage]
    boolean AWESOME_FEATURE_KILLSWITCH = true;
            ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
                 """
                    .trimIndent()
            )
    }

    @Test
    fun testKillSwitchVariableName_lowerCase_throws() {
        lint()
            .files(
                java(
                        """
                package com.android.adservices.service;

                import com.android.adservices.shared.common.flags.ModuleSharedFlags;

                public interface Flags extends ModuleSharedFlags {
                    boolean awesome_feature_killswitch = true;
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .issues(KillSwitchFlagUsageDetector.ISSUE)
            .run()
            .expect(
                """
src/com/android/adservices/service/Flags.java:6: Error: DO NOT USE "kill switch" as a flag name, use feature flag instead. [AvoidKillSwitchFlagUsage]
    boolean awesome_feature_killswitch = true;
            ~~~~~~~~~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
                """
                    .trimIndent()
            )
    }

    @Test
    fun testKillSwitchClassName_pass() {
        lint()
            .files(
                java(
                        """
                package com.android.adservices.service;

                import com.android.adservices.shared.common.flags.ModuleSharedFlags;

                public interface KillSwitch extends ModuleSharedFlags {
                    boolean AWESOME_FEATURE_ENABLED = true;
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .issues(KillSwitchFlagUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testKillSwitchString_pass() {
        lint()
            .files(
                java(
                        """
                package com.android.adservices.service;

                import com.android.adservices.shared.common.flags.ModuleSharedFlags;

                public interface Flag extends ModuleSharedFlags {
                  // Set the default value to KILL_SWITCH
                  String AWESOME_FEATURE_ENABLED = "KILL_SWITCH";
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .issues(KillSwitchFlagUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun testKillSwitchNotInFlagsInterface_pass() {
        lint()
            .files(
                java(
                        """
                package com.android.adservices.service;

                import com.android.adservices.shared.common.flags.ModuleSharedFlags;

                public interface OtherClass extends ModuleSharedFlags {
                  boolean AWESOME_FEATURE_KILL_SWITCH = true;
                }
                  """
                    )
                    .indented(),
                *stubs
            )
            .issues(KillSwitchFlagUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    private val moduleSharedFlagsStub: TestFile =
        java(
            """
            package com.android.adservices.shared.common.flags;
            public interface ModuleSharedFlags {}
        """
                .trimIndent()
        )

    private val stubs =
        arrayOf(
            moduleSharedFlagsStub,
        )
}
