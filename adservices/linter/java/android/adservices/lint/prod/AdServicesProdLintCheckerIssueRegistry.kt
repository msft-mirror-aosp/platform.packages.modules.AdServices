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

package android.adservices.lint.prod

import android.adservices.lint.common.DeviceConfigUsageDetector
import android.adservices.lint.common.KillSwitchFlagUsageDetector
import android.adservices.lint.common.PreconditionsCheckStateDetector
import android.adservices.lint.common.SystemPropertiesUsageDetector
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.google.auto.service.AutoService

@AutoService(IssueRegistry::class)
@Suppress("UnstableApiUsage")
class AdServicesProdLintCheckerIssueRegistry : IssueRegistry() {
    override val issues =
        listOf(
            BackCompatAndroidProcessDetector.ISSUE,
            BackCompatJobServiceDetector.ISSUE,
            BackCompatNewFileDetector.ISSUE,
            DeviceConfigUsageDetector.ISSUE,
            KillSwitchFlagUsageDetector.ISSUE,
            PreconditionsCheckStateDetector.ISSUE,
            RoomDatabaseMigrationDetector.ISSUE_ERROR,
            RoomDatabaseMigrationDetector.ISSUE_WARNING,
            SystemPropertiesUsageDetector.ISSUE,
        )

    override val api: Int
        get() = CURRENT_API

    override val minApi: Int
        // The minimum lint API version this issue registry works with, not sdk level.
        // Normally the same as [api], but can be smaller than [api] to indicate this
        // Registry works with lower linter API version.
        get() = 11

    override val vendor =
        Vendor(
            vendorName = "Android",
            feedbackUrl = "http://b/issues/new?component=1451456",
            contact = "gehuang@google.com"
        )
}
