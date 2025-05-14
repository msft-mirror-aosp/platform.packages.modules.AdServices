/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UVariable

/** Lint check for detecting java.util.concurrent.CompletableFuture usage. */
class CompletableFutureUsageDetector : Detector(), Detector.UastScanner {
    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(
            UCallExpression::class.java, // For constructor calls and method calls
            UVariable::class.java, // For variable/field declarations
            UMethod::class.java, // For method return types and parameters
            UTypeReferenceExpression::class.java, // For type usages in various contexts
        )
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            // Detects constructor calls and method calls
            override fun visitCallExpression(node: UCallExpression) {
                // Check constructor calls: new CompletableFuture<>()
                val constructorResolved = node.resolve() as? PsiMethod ?: return
                if (
                    constructorResolved?.isConstructor == true &&
                        context.evaluator.isMemberInClass(
                            constructorResolved,
                            COMPLETABLE_FUTURE_CLASS_NAME,
                        )
                ) {
                    reportCompletableFutureUsage(
                        context,
                        node,
                        "Constructor call to CompletableFuture",
                    )
                    return
                }

                // Check method calls: CompletableFuture.supplyAsync(...)
                val methodResolved = node.resolve()
                if (methodResolved is PsiMethod) {
                    val containingClass = methodResolved.containingClass
                    if (
                        containingClass != null &&
                            containingClass.qualifiedName == COMPLETABLE_FUTURE_CLASS_NAME
                    ) {
                        reportCompletableFutureUsage(
                            context,
                            node,
                            "Method call on CompletableFuture",
                        )
                        return
                    }
                }

                // Check instance method calls: completableFuture.thenApply(...)
                val receiverType = node.receiverType
                if (receiverType != null && isCompletableFutureType(receiverType)) {
                    reportCompletableFutureUsage(
                        context,
                        node,
                        "Instance method call on CompletableFuture",
                    )
                }
            }

            // Detects variable/field declarations
            override fun visitVariable(node: UVariable) {
                if (isCompletableFutureType(node.type)) {
                    val locationNode = node.typeReference ?: node.uastAnchor ?: node
                    reportCompletableFutureUsage(
                        context,
                        locationNode,
                        "Variable/field of type CompletableFuture",
                    )
                }
            }

            // Detects method return types and parameter types
            override fun visitMethod(node: UMethod) {
                // Check return type
                if (isCompletableFutureType(node.returnType)) {
                    val locationNode =
                        node.returnTypeReference ?: node.uastAnchor ?: node.nameIdentifier ?: node
                    reportCompletableFutureUsage(
                        context,
                        locationNode as UElement,
                        "Method return type is CompletableFuture",
                    )
                }

                // Check parameter types
                node.uastParameters.forEach { parameter ->
                    if (isCompletableFutureType(parameter.type)) {
                        val locationNode =
                            parameter.typeReference ?: parameter.uastAnchor ?: parameter
                        reportCompletableFutureUsage(
                            context,
                            locationNode,
                            "Method parameter of type CompletableFuture",
                        )
                    }
                }
            }

            // Catches general type references (e.g., in casts, instanceof)
            override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
                if (isCompletableFutureType(node.type)) {
                    reportCompletableFutureUsage(
                        context,
                        node,
                        "Type reference to CompletableFuture",
                    )
                }
            }

            private fun isCompletableFutureType(psiType: PsiType?): Boolean {
                if (psiType == null) return false
                return context.evaluator.typeMatches(psiType, COMPLETABLE_FUTURE_CLASS_NAME) ||
                    (psiType is PsiClassType &&
                        psiType.resolve()?.qualifiedName == COMPLETABLE_FUTURE_CLASS_NAME)
            }

            private fun reportCompletableFutureUsage(
                context: JavaContext,
                node: UElement,
                detail: String,
            ) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "${ISSUE.getBriefDescription(TextFormat.TEXT)} ($detail).",
                )
            }
        }
    }

    companion object {
        private const val COMPLETABLE_FUTURE_CLASS_NAME = "java.util.concurrent.CompletableFuture"

        private val EXPLANATION =
            "DO NOT use java.util.concurrent.CompletableFuture. Use ListenableFuture instead."
        val ISSUE: Issue =
            Issue.create(
                id = "CompletableFutureUsage",
                briefDescription = EXPLANATION,
                explanation = EXPLANATION,
                category = Category.COMPLIANCE,
                severity = Severity.WARNING,
                implementation =
                    Implementation(
                        CompletableFutureUsageDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
