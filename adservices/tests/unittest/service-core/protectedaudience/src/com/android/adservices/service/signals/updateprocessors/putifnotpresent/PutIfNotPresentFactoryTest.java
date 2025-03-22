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

package com.android.adservices.service.signals.updateprocessors.putifnotpresent;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Test;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class PutIfNotPresentFactoryTest extends AdServicesUnitTestCase {
    private static final int UNSUPPORTED_VERSION = -1;

    private final PutIfNotPresentFactory mPutIfNotPresentFactory = new PutIfNotPresentFactory();

    @Test
    public void testGetUpdateProcessor_supportedVersions() {
        expect.withMessage("Expected update processor for schema version " + UpdateSchemaVersion.V0)
                .that(mPutIfNotPresentFactory.getUpdateProcessor(UpdateSchemaVersion.V0))
                .isInstanceOf(PutIfNotPresentV0.class);
    }

    @Test
    public void testGetUpdateProcessor_unsupportedVersion() {
        assertThrows(
                "Expected exception for unsupported version " + UNSUPPORTED_VERSION,
                IllegalArgumentException.class,
                () -> mPutIfNotPresentFactory.getUpdateProcessor(UNSUPPORTED_VERSION));
    }
}
