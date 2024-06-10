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

class DeviceConfigUsageDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> {
        return listOf("getString", "getBoolean", "getInt", "getLong", "getFloat", "getProperty", "getProperties")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // TODO(b/325135083): ideally check below should be just for qualifiedName = ...
        // But somehow it doesn't work when getApplicableMethodNames() returns more than one method
        if (method.name in getApplicableMethodNames() &&
            method.containingClass?.qualifiedName == "android.provider.DeviceConfig") {
            context.report(issue = ISSUE, location = context.getNameLocation(node),
                    message = "DO NOT CALL android.provider.DeviceConfig methods directly, but "
                            + "adservices-specifc helpers instead. For example, on PhFlags.java,"
                            + " you should call getDeviceConfigFlag(name, defaultValue).")
        }
    }

    companion object {
        val ISSUE = Issue.create(
                id = "AvoidDeviceConfigUsage",
                briefDescription = "DO NOT CALL android.provider.DeviceConfig methods directly",
                explanation = """
                      DO NOT CALL android.provider.DeviceConfig methods directly, use
                      adservices-specific helpers instead, as they provide additional abstractions
                      (like checking SystemProperties first) and make it easier to migrate to
                      different flag mechanisms.
                    """,
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation = Implementation(DeviceConfigUsageDetector::class.java, Scope.JAVA_FILE_SCOPE))
    }
}