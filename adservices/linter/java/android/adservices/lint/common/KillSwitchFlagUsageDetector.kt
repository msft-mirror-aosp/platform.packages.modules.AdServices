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

package android.adservices.lint.common

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable

/** Lint check for detecting kill switch flag name in Flags interface. */
class KillSwitchFlagUsageDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UVariable::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitVariable(node: UVariable) {
                if (isFlagsInterface(node) && isVariableNameContainsKillSwitch(node)) {
                    context.report(
                        ISSUE,
                        context.getNameLocation(node),
                        "DO NOT USE \"kill switch\" as a flag name, use feature flag instead."
                    )
                }
            }
        }
    }

    /** Returns true if the variable is in Flags interface. */
    private fun isFlagsInterface(node: UVariable): Boolean {
        val containingClass = node.uastParent as? UClass
        return containingClass?.isInterface == true && containingClass.name == "Flags"
    }

    /** Returns true if the variable name contains killswitch or kill_switch. */
    private fun isVariableNameContainsKillSwitch(node: UVariable): Boolean {
        val variableName = node.getName()
        return variableName?.contains("KILL_SWITCH", /* ignoreCase */ true) == true ||
            variableName?.contains("KILLSWITCH", /* ignoreCase */ true) == true
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "AvoidKillSwitchFlagUsage",
                briefDescription = "DO NOT use kill switch flag",
                explanation =
                    """
                      "DO NOT USE \"kill switch\" as a flag name, use feature flag instead."
                    """,
                moreInfo = "documentation/KillSwitchFlagUsageDetector.md",
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(KillSwitchFlagUsageDetector::class.java, Scope.JAVA_FILE_SCOPE)
            )
    }
}
