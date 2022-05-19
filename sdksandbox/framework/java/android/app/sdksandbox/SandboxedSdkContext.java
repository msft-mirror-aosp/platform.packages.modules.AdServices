/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.app.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;

import java.util.concurrent.Executor;

/**
 * Refers to the context of the SDK loaded in the SDK sandbox process.
 *
 * <p>It is a wrapper of the client application (which loading SDK to the sandbox) context,
 * to represent the context of the SDK loaded by that application.
 * <p>This context contains methods that an SDK loaded into sdk sandbox can use to interact
 * with the sdk sandbox process, or other SDKs loaded into the same sdk sandbox process.
 *
 * <p>An instance of the {@link SandboxedSdkContext} will be created by the SDK sandbox, and then
 * passed to the {@link SandboxedSdkProvider#initSdk(SandboxedSdkContext,
 * Bundle, Executor, SandboxedSdkProvider.InitSdkCallback)} after SDK is loaded.
 *
 * <p>Note: All APIs defined in this class are not stable and subject to change.
 */
public final class SandboxedSdkContext extends ContextWrapper {

    private final Resources mResources;
    private final AssetManager mAssets;
    private final String mSdkName;

    /** @hide */
    public SandboxedSdkContext(@NonNull Context baseContext, @NonNull ApplicationInfo info,
            @NonNull String sdkName) {
        super(baseContext);
        mSdkName = sdkName;
        Resources resources = null;
        try {
            resources = baseContext.getPackageManager().getResourcesForApplication(info);
        } catch (Exception ignored) {
        }

        if (resources != null) {
            mResources = resources;
            mAssets = resources.getAssets();
        } else {
            mResources = null;
            mAssets = null;
        }
    }

    /**
     * Returns the SDK name defined in the SDK's manifest.
     * @hide
     */
    @NonNull
    public String getSdkName() {
        return mSdkName;
    }

    /** Returns the resources defined in the SDK's .apk file. */
    @Override
    @Nullable
    public Resources getResources() {
        return mResources;
    }

    /** Returns the assets defined in the SDK's .apk file. */
    @Override
    @Nullable
    public AssetManager getAssets() {
        return mAssets;
    }
}
