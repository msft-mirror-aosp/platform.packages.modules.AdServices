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
package com.android.cobalt.domain;

import com.google.auto.value.AutoValue;

/** Domain object that uniquely identifies a report in Cobalt's registry. */
@AutoValue
public abstract class ReportIdentifier {
    /** The Cobalt customer id. */
    public abstract int customerId();

    /** The Cobalt project id. */
    public abstract int projectId();

    /** The Cobalt metric id. */
    public abstract int metricId();

    /** The Cobalt report id. */
    public abstract int reportId();

    /** Creates a report identifier from individual ids. */
    public static ReportIdentifier create(
            int customerId, int projectId, int metricId, int reportId) {
        return new AutoValue_ReportIdentifier(customerId, projectId, metricId, reportId);
    }
}
