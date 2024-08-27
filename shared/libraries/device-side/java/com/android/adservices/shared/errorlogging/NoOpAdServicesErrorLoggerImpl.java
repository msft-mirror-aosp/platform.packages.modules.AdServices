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

package com.android.adservices.shared.errorlogging;

/** No-op version of {@link AdServicesErrorLogger}. */
// TODO(b/361855462) : Remove this class once we have Cel in system_server.
public final class NoOpAdServicesErrorLoggerImpl implements AdServicesErrorLogger {
    @Override
    public void logError(int errorCode, int ppapiName) {}

    @Override
    public void logErrorWithExceptionInfo(Throwable tr, int errorCode, int ppapiName) {}
}
