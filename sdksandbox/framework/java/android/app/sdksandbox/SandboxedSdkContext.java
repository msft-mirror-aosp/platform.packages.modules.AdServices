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

import java.io.File;
import java.util.concurrent.Executor;

/**
 * Refers to the context of the SDK loaded in the SDK sandbox process.
 *
 * <p>It is a wrapper of the client application (which loading SDK to the sandbox) context, to
 * represent the context of the SDK loaded by that application.
 *
 * <p>This context contains methods that an SDK loaded into sdk sandbox can use to interact with the
 * sdk sandbox process, or other SDKs loaded into the same sdk sandbox process.
 *
 * <p>An instance of the {@link SandboxedSdkContext} will be created by the SDK sandbox, and then
 * passed to the {@link SandboxedSdkProvider#onLoadSdk(SandboxedSdkContext, Bundle, Executor,
 * SandboxedSdkProvider.OnLoadSdkCallback)} after SDK is loaded.
 *
 * <p>Each sdk will get their own private storage directory and the file storage API on this object
 * will utilize those area.
 *
 * <p>Note: All APIs defined in this class are not stable and subject to change.
 */
public final class SandboxedSdkContext extends ContextWrapper {

    private final Resources mResources;
    private final AssetManager mAssets;
    private final String mClientPackageName;
    private final String mSdkName;
    private final ApplicationInfo mSdkProviderInfo;
    @Nullable private final File mCeDataDir;
    @Nullable private final File mDeDataDir;

    /** @hide */
    public SandboxedSdkContext(
            @NonNull Context baseContext,
            @NonNull String clientPackageName,
            @NonNull ApplicationInfo info,
            @NonNull String sdkName,
            @Nullable String sdkCeDataDir,
            @Nullable String sdkDeDataDir) {
        super(baseContext);
        mClientPackageName = clientPackageName;
        mSdkName = sdkName;
        mSdkProviderInfo = info;
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

        mCeDataDir = (sdkCeDataDir != null) ? new File(sdkCeDataDir) : null;
        mDeDataDir = (sdkDeDataDir != null) ? new File(sdkDeDataDir) : null;
    }

    /**
     * Return a new Context object for the current SandboxedSdkContext but whose storage APIs are
     * backed by sdk specific credential-protected storage.
     *
     * @see Context#isCredentialProtectedStorage()
     * @hide
     */
    @Override
    @NonNull
    public Context createCredentialProtectedStorageContext() {
        Context newBaseContext = getBaseContext().createCredentialProtectedStorageContext();
        return new SandboxedSdkContext(
                newBaseContext,
                mClientPackageName,
                mSdkProviderInfo,
                mSdkName,
                (mCeDataDir != null) ? mCeDataDir.toString() : null,
                (mDeDataDir != null) ? mDeDataDir.toString() : null);
    }

    /**
     * Return a new Context object for the current SandboxedSdkContext but whose storage
     * APIs are backed by sdk specific device-protected storage.
     *
     * @see Context#isDeviceProtectedStorage()
     */
    @Override
    @NonNull
    public Context createDeviceProtectedStorageContext() {
        Context newBaseContext = getBaseContext().createDeviceProtectedStorageContext();
        return new SandboxedSdkContext(
                newBaseContext,
                mClientPackageName,
                mSdkProviderInfo,
                mSdkName,
                (mCeDataDir != null) ? mCeDataDir.toString() : null,
                (mDeDataDir != null) ? mDeDataDir.toString() : null);
    }

    /**
     * Returns the SDK name defined in the SDK's manifest.
     * @hide
     */
    @NonNull
    public String getSdkName() {
        return mSdkName;
    }

    /**
     * Returns the SDK package name defined in the SDK's manifest.
     *
     * @hide
     */
    @NonNull
    public String getSdkPackageName() {
        return mSdkProviderInfo.packageName;
    }

    /**
     * Returns the package name of the client application corresponding to the sandbox.
     *
     * @hide
     */
    @NonNull
    public String getClientPackageName() {
        return mClientPackageName;
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

    /** Returns sdk-specific internal storage directory. */
    @Override
    @Nullable
    public File getDataDir() {
        File res = null;
        if (isCredentialProtectedStorage()) {
            res = mCeDataDir;
        } else if (isDeviceProtectedStorage()) {
            res = mDeDataDir;
        }
        if (res == null) {
            throw new RuntimeException("No data directory found for sdk: " + getSdkName());
        }
        return res;
    }
}
