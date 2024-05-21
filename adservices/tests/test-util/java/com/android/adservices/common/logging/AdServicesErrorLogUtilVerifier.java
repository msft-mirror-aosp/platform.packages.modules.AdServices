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

package com.android.adservices.common.logging;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.shared.testing.AbstractLogVerifier;

/** Log verifier for {@link ErrorLogUtil} calls. */
// TODO (b/323000746): Implement ErrorLogUtil verifiers. Design for log verifiers subjected to
//  change. This was only created to abstract out logic from AdServicesLoggingUsageRule.
public final class AdServicesErrorLogUtilVerifier extends AbstractLogVerifier {
    @Override
    protected void mockLogCalls() {
        throw new UnsupportedOperationException("TODO");
    }
}
