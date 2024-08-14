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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTestFixture.START_ELAPSED_TIMESTAMP;

public class SignatureVerificationLoggerImplTestFixture {
    public static final int SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS = 3;
    public static final int SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS = 5;
    public static final int SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS = 7;

    public static final long SIGNATURE_VERIFICATION_START_KEY_FETCH = START_ELAPSED_TIMESTAMP + 1L;
    public static final long SIGNATURE_VERIFICATION_END_KEY_FETCH =
            SIGNATURE_VERIFICATION_START_KEY_FETCH + SIGNATURE_VERIFICATION_KEY_FETCH_LATENCY_MS;
    public static final long SIGNATURE_VERIFICATION_START_SERIALIZATION =
            SIGNATURE_VERIFICATION_END_KEY_FETCH + 1L;
    public static final long SIGNATURE_VERIFICATION_END_SERIALIZATION =
            SIGNATURE_VERIFICATION_START_SERIALIZATION
                    + SIGNATURE_VERIFICATION_SERIALIZATION_LATENCY_MS;
    public static final long SIGNATURE_VERIFICATION_START_VERIFICATION =
            SIGNATURE_VERIFICATION_END_SERIALIZATION + 1L;
    public static final long SIGNATURE_VERIFICATION_END_VERIFICATION =
            SIGNATURE_VERIFICATION_START_VERIFICATION
                    + SIGNATURE_VERIFICATION_VERIFICATION_LATENCY_MS;
}
