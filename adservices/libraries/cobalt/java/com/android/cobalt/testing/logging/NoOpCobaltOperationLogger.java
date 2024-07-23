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

package com.android.cobalt.testing.logging;

import androidx.annotation.VisibleForTesting;

import com.android.cobalt.logging.CobaltOperationLogger;

/** An operation logger that doesn't log. */
public final class NoOpCobaltOperationLogger implements CobaltOperationLogger {
    /** NoOp log that a Cobalt logging event exceeds the string buffer max. */
    @VisibleForTesting
    public void logStringBufferMaxExceeded(int metricId, int reportId) {}
}
