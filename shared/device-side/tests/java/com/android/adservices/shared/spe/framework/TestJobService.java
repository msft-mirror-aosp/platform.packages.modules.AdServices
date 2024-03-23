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

package com.android.adservices.shared.spe.framework;

/**
 * Helper class to implement {@link AbstractJobService}. Note this service is registered in {@code
 * AndroidManifest.xml}.
 */
public final class TestJobService extends AbstractJobService {

    @Override
    protected JobServiceFactory getJobServiceFactory() {
        throw new UnsupportedOperationException("This method should be mocked in the test!");
    }
}
