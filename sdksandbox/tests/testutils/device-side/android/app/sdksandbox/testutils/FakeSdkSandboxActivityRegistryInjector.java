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

package android.app.sdksandbox.testutils;

import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityRegistry;

import java.util.ArrayDeque;
import java.util.List;

public class FakeSdkSandboxActivityRegistryInjector extends SdkSandboxActivityRegistry.Injector {
    private final SdkSandboxLocalSingleton mSdkSandboxLocalSingleton;
    private ArrayDeque<Long> mLatencyTimeSeries = new ArrayDeque<>();

    public FakeSdkSandboxActivityRegistryInjector(
            SdkSandboxLocalSingleton sdkSandboxLocalSingleton) {
        mSdkSandboxLocalSingleton = sdkSandboxLocalSingleton;
    }

    @Override
    public SdkSandboxLocalSingleton getSdkSandboxLocalSingleton() {
        return mSdkSandboxLocalSingleton;
    }

    @Override
    public long elapsedRealtime() {
        if (mLatencyTimeSeries.isEmpty()) {
            return super.elapsedRealtime();
        }

        return mLatencyTimeSeries.poll();
    }

    public void setLatencyTimeSeries(List<Long> latencyTimeSeries) {
        mLatencyTimeSeries = new ArrayDeque<>(latencyTimeSeries);
    }

    public void resetTimeSeries() {
        mLatencyTimeSeries.clear();
    }
}
