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

import android.adservices.lint.test.ErrorLogUtilStaticMockSpyUsageDetector.Constants.ALLOWED_CLASS
import android.adservices.lint.test.ErrorLogUtilStaticMockSpyUsageDetector.Constants.ERROR_LOG_UTIL
import android.adservices.lint.test.ErrorLogUtilStaticMockSpyUsageDetector.Constants.ERROR_MESSAGE
import android.adservices.lint.test.ErrorLogUtilStaticMockSpyUsageDetector.Constants.MOCK_STATIC_ANNOTATION
import android.adservices.lint.test.ErrorLogUtilStaticMockSpyUsageDetector.Constants.SPY_STATIC_ANNOTATION
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement

class ErrorLogUtilStaticMockSpyUsageDetector : Detector(), SourceCodeScanner {
    object Constants {
        const val MOCK_STATIC_ANNOTATION = "MockStatic"
        const val SPY_STATIC_ANNOTATION = "SpyStatic"
        const val ERROR_LOG_UTIL = "ErrorLogUtil"
        const val ERROR_MESSAGE = "Do not spy or mock ErrorLogUtil"

        // Only allow ErrorLogUtil spy/mock annotation in the superclass
        const val ALLOWED_CLASS = "AdServicesExtendedMockitoTestCase"
    }

    override fun getApplicableUastTypes() = listOf(UAnnotation::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitAnnotation(node: UAnnotation) {
                if (isMockOrSpyStaticAnnotation(node)) {
                    val annotationArgs = node.attributeValues
                    annotationArgs.forEach { arg ->
                        val argValue = arg.expression.asSourceString()
                        if (
                            argValue.contains(ERROR_LOG_UTIL) &&
                                findUClass(node)?.name != ALLOWED_CLASS
                        ) {
                            context.report(ISSUE, node, context.getLocation(node), ERROR_MESSAGE)
                        }
                    }
                }
            }
        }
    }

    private fun isMockOrSpyStaticAnnotation(node: UAnnotation): Boolean {
        val annotation = node.qualifiedName?.substringAfterLast('.')
        return annotation == MOCK_STATIC_ANNOTATION || annotation == SPY_STATIC_ANNOTATION
    }

    private fun findUClass(node: UAnnotation): UClass? {
        var parent: UElement? = node.uastParent

        while (parent != null) {
            if (parent is UClass) {
                return parent
            }
            parent = parent.uastParent
        }

        return null // UClass not found
    }

    companion object Issues {
        val ISSUE =
            Issue.create(
                id = "AvoidMockingOrSpyingErrorLogUtil",
                briefDescription = "Do not spy or mock ErrorLogUtil explicitly in test classes",
                explanation =
                    """
                        All subclasses that extend AdServicesExtendedMockitoTestCase are
                        automatically configured to spy ErrorLogUtil for AdServicesLoggingUsageRule.
                        Simply use @ExpectErrorLogUtilCall and/or @ExpectErrorLogUtilWithExceptionCall
                        directly over test methods to verify ErrorLogUtil calls without any
                        mocking / spying.
                        """,
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ErrorLogUtilStaticMockSpyUsageDetector::class.java,
                        Scope.JAVA_FILE_SCOPE
                    )
            )
    }
}
