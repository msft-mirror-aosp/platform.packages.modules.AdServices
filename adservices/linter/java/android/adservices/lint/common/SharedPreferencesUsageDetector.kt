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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class SharedPreferencesUsageDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> {
        return listOf("getSharedPreferences")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (
            method.name in getApplicableMethodNames() &&
                method.containingClass?.qualifiedName == "android.content.Context"
        ) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(node),
                message =
                    "DO NOT USE SharedPreferences for any new usage, use ProtoDatastore instead. Check GuavaDatastore.java which is a wrapper around ProtoDatastore. For existing usage, the warning can be suppressed.",
            )
        }
    }

    companion object {
        val ISSUE =
            Issue.create(
                id = "AvoidSharedPreferences",
                briefDescription =
                    "DO NOT USE SharedPreferences for any new usage, use ProtoDatastore instead.",
                explanation =
                    """
                      DO NOT USE SharedPreferences for any new usage, use ProtoDatastore instead.
                    """,
                moreInfo = "documentation/SharedPreferencesUsageDetector.md",
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        SharedPreferencesUsageDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
