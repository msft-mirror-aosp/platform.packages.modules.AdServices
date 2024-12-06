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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/** Lint check for detecting android.content.Context passed in static methods. */
class StaticContextDetector : Detector(), SourceCodeScanner {
    // We have to use getApplicableUastTypes to let linter run on all method. The documented
    // getApplicableMethodNames return null supposed to run on all method but actually it doesn't
    // run
    // on any methods.
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val method = node.javaPsi as? PsiMethod ?: return
                if (isNonPrivateStaticMethod(method)) {
                    for (parameter in method.parameterList.parameters) {
                        if (isContextType(parameter.type)) {
                            context.report(
                                issue = ISSUE,
                                location = context.getNameLocation(node),
                                message =
                                    "DO NOT pass a Context on static methods, but use " +
                                        "ApplicationContextSingleton.get() to get the context instead.",
                            )
                            break
                        }
                    }
                }
            }
        }
    }

    /** Checks if the parameter type is android.context.Context */
    private fun isContextType(type: PsiType): Boolean {
        return type.canonicalText == "android.content.Context"
    }

    private fun isNonPrivateStaticMethod(method: PsiMethod): Boolean {
        return !method.isConstructor &&
            method.hasModifierProperty("static") &&
            !method.hasModifierProperty("private")
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "AvoidStaticContext",
                briefDescription = "DO NOT pass a Context on static methods.",
                explanation =
                    """
                      DO NOT pass a Context on static methods, but use ApplicationContextSingleton.get()
                      to get the context instead. If you think it is a valid case to use Context on
                      static methods, please add @SuppressWarnings("AvoidStaticContext") to suppress
                      this error and add comments why it can be suppressed. Examples:\n
                      * Factory methods that return a new object.\n
                      * UI-related methods that require a UI-specific context.\n
                    """,
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(StaticContextDetector::class.java, Scope.JAVA_FILE_SCOPE),
            )
    }
}
