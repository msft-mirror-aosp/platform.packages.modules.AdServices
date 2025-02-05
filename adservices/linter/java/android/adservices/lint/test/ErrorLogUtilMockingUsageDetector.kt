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

import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.ERROR_LOG_UTIL
import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.INVALID_ANNOTATION_MESSAGE
import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.INVALID_MOCKING_MESSAGE
import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.MOCK_STATIC_ANNOTATION
import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.SPY_STATIC_ANNOTATION
import android.adservices.lint.test.ErrorLogUtilMockingUsageDetector.Constants.SUPERCLASS
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.*

class ErrorLogUtilMockingUsageDetector : Detector(), SourceCodeScanner {
    object Constants {
        const val MOCK_STATIC_ANNOTATION = "MockStatic"
        const val SPY_STATIC_ANNOTATION = "SpyStatic"
        const val ERROR_LOG_UTIL = "ErrorLogUtil"
        const val INVALID_ANNOTATION_MESSAGE = "Do not spy or mock ErrorLogUtil"
        const val INVALID_MOCKING_MESSAGE = "Do not mock ErrorLogUtil behavior"

        // Only allow ErrorLogUtil spy/mock annotation in the superclass
        const val SUPERCLASS = "AdServicesExtendedMockitoTestCase"
    }

    override fun getApplicableUastTypes() = listOf(UAnnotation::class.java)

    override fun getApplicableMethodNames(): List<String> {
        return listOf("doNothingOnErrorLogUtilError", "when")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.name == "when" && !isMockingErrorLogUtilInvocation(node)) {
            return
        }

        if (isSubclassOfAdServicesExtendedMockitoTestCase(findUClass(node))) {
            context.report(
                MOCKING_INVOCATION_ISSUE,
                node,
                context.getLocation(node),
                INVALID_MOCKING_MESSAGE,
            )
        }
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitAnnotation(node: UAnnotation) {
                if (isMockOrSpyStaticAnnotation(node)) {
                    val annotationArgs = node.attributeValues
                    annotationArgs.forEach { arg ->
                        val argValue = arg.expression.asSourceString()
                        if (
                            argValue.contains(ERROR_LOG_UTIL) &&
                                findUClass(node)?.name != SUPERCLASS
                        ) {
                            context.report(
                                INVALID_ANNOTATION_ISSUE,
                                node,
                                context.getLocation(node),
                                INVALID_ANNOTATION_MESSAGE,
                            )
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

    private fun findUClass(node: UElement): UClass? {
        var parent: UElement? = node.uastParent
        while (parent != null) {
            if (parent is UClass) {
                return parent
            }
            parent = parent.uastParent
        }

        return null // UClass not found
    }

    private fun isSubclassOfAdServicesExtendedMockitoTestCase(node: UClass?): Boolean {
        var currentClass = node
        while (currentClass != null) {
            val superClass = currentClass.javaPsi.superClass ?: break
            if (superClass.name == SUPERCLASS) {
                return true
            }
            currentClass =
                UastFacade.convertElementWithParent(superClass, UClass::class.java) as? UClass
        }

        return false
    }

    private fun isMockingErrorLogUtilInvocation(node: UCallExpression): Boolean {
        return node.valueArguments.size == 1 &&
            node.valueArguments[0].asSourceString().contains(ERROR_LOG_UTIL)
    }

    companion object Issues {
        val INVALID_ANNOTATION_ISSUE =
            Issue.create(
                id = "DoNotMockOrSpyErrorLogUtil",
                briefDescription =
                    "Do not define @SpyStatic(ErrorLogUtil.class) or @MockStatic(ErrorLogUtil.class)",
                explanation =
                    """
                        All subclasses that extend AdServicesExtendedMockitoTestCase are
                        automatically configured to spy ErrorLogUtil for AdServicesLoggingUsageRule.
                        Simply use @ExpectErrorLogUtilCall and/or @ExpectErrorLogUtilWithExceptionCall
                        directly over test methods to verify ErrorLogUtil calls.
                        """,
                moreInfo = "documentation/ErrorLogUtilMockingUsageDetector.md",
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ErrorLogUtilMockingUsageDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )

        val MOCKING_INVOCATION_ISSUE =
            Issue.create(
                id = "DoNotMockErrorLogUtilBehavior",
                briefDescription = "Do not mock ErrorLogUtil invocation behavior explicitly",
                explanation =
                    """
                        All subclasses that extend AdServicesExtendedMockitoTestCase are
                        automatically configured to spy ErrorLogUtil for AdServicesLoggingUsageRule.
                        ErrorLogUtil invocation behavior is already mocked in a specific way for the
                        rule to work; do not override it by mocking ErrorLogUtil behavior. Simply use
                        @ExpectErrorLogUtilCall and/or @ExpectErrorLogUtilWithExceptionCall directly
                        over test methods to verify ErrorLogUtil calls.
                        """,
                moreInfo = "documentation/ErrorLogUtilMockingUsageDetector.md",
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        ErrorLogUtilMockingUsageDetector::class.java,
                        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
