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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class JUnitAssertDetector : Detector(), SourceCodeScanner {

    // List of Junit Assert Methods to check. This does not include assertThrow.
    override fun getApplicableMethodNames(): List<String> =
        listOf(
            "assertEquals",
            "assertTrue",
            "assertFalse",
            "assertNotEquals",
            "assertArrayEquals",
            "assertNull",
            "assertNotNull",
            "assertSame",
            "assertNotSame",
        )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (context.evaluator.isMemberInClass(method, JUNIT_ASSERT_CLASS)) {
            context.report(ISSUE, node, context.getLocation(node), MESSAGE)
        }
    }

    companion object {
        private const val JUNIT_ASSERT_CLASS = "org.junit.Assert"
        private const val MESSAGE =
            "Prefer using Truth test assertions instead of org.junit.Assert (except assertThrows)."

        val ISSUE =
            Issue.create(
                id = "JUnitAssertUsage",
                briefDescription = "Use of org.junit.Assert",
                explanation = MESSAGE,
                category = Category.COMPLIANCE,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        JUnitAssertDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
