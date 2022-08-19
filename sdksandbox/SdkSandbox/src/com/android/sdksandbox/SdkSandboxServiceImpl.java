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

package com.android.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.Service;
import android.app.sdksandbox.KeyWithType;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import dalvik.system.DexClassLoader;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;

/** Implementation of Sdk Sandbox Service. */
public class SdkSandboxServiceImpl extends Service {

    private static final String TAG = "SdkSandbox";

    @GuardedBy("mHeldSdk")
    private final Map<IBinder, SandboxedSdkHolder> mHeldSdk = new ArrayMap<>();
    private Injector mInjector;
    private ISdkSandboxService.Stub mBinder;

    static class Injector {

        private final Context mContext;

        Injector(Context context) {
            mContext = context;
        }

        int getCallingUid() {
            return Binder.getCallingUidOrThrow();
        }

        Context getContext() {
            return mContext;
        }

        long getCurrentTime() {
            return System.currentTimeMillis();
        }
    }

    public SdkSandboxServiceImpl() {
    }

    @VisibleForTesting
    SdkSandboxServiceImpl(Injector injector) {
        mInjector = injector;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        mBinder = new SdkSandboxServiceDelegate();
        mInjector = new Injector(getApplicationContext());
    }

    /** Loads SDK. */
    public void loadSdk(
            String callingPackageName,
            IBinder sdkToken,
            ApplicationInfo applicationInfo,
            String sdkName,
            String sdkProviderClassName,
            String sdkCeDataDir,
            String sdkDeDataDir,
            Bundle params,
            ILoadSdkInSandboxCallback callback) {
        enforceCallerIsSystemServer();
        final long token = Binder.clearCallingIdentity();
        try {
            loadSdkInternal(
                    callingPackageName,
                    sdkToken,
                    applicationInfo,
                    sdkName,
                    sdkProviderClassName,
                    sdkCeDataDir,
                    sdkDeDataDir,
                    params,
                    callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** Unloads SDK. */
    public void unloadSdk(IBinder sdkToken) {
        enforceCallerIsSystemServer();
        final long token = Binder.clearCallingIdentity();
        try {
            unloadSdkInternal(sdkToken);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** Sync data from client. */
    public void syncDataFromClient(SharedPreferencesUpdate update) {
        SharedPreferences pref =
                PreferenceManager.getDefaultSharedPreferences(mInjector.getContext());
        SharedPreferences.Editor editor = pref.edit();
        final Bundle data = update.getData();
        for (KeyWithType keyInUpdate : update.getKeysInUpdate()) {
            updateSharedPreferences(editor, data, keyInUpdate);
        }
        // TODO(b/239403323): What if writing to persistent storage fails?
        editor.apply();
    }

    private void updateSharedPreferences(
            SharedPreferences.Editor editor, Bundle data, KeyWithType keyInUpdate) {
        final String key = keyInUpdate.getName();

        if (!data.containsKey(key)) {
            // key was specified but bundle didn't have the key; meaning it has been removed.
            editor.remove(key);
            return;
        }

        final int type = keyInUpdate.getType();
        try {
            switch (type) {
                case KeyWithType.KEY_TYPE_STRING:
                    editor.putString(key, data.getString(key, ""));
                    break;
                case KeyWithType.KEY_TYPE_BOOLEAN:
                    editor.putBoolean(key, data.getBoolean(key, false));
                    break;
                case KeyWithType.KEY_TYPE_INTEGER:
                    editor.putInt(key, data.getInt(key, 0));
                    break;
                case KeyWithType.KEY_TYPE_FLOAT:
                    editor.putFloat(key, data.getFloat(key, 0.0f));
                    break;
                case KeyWithType.KEY_TYPE_LONG:
                    editor.putLong(key, data.getLong(key, 0L));
                    break;
                case KeyWithType.KEY_TYPE_STRING_SET:
                    final ArraySet<String> castedValue =
                            new ArraySet<>(data.getStringArrayList(key));
                    editor.putStringSet(key, castedValue);
                    break;
                default:
                    Log.e(
                            TAG,
                            "Unknown type found in default SharedPreferences for Key: "
                                    + key
                                    + " Type: "
                                    + type);
            }
        } catch (ClassCastException ignore) {
            editor.remove(key);
            // TODO(b/239403323): Once error reporting is supported, we should return error to the
            // user instead.
            Log.e(
                    TAG,
                    "Wrong type found in default SharedPreferences for Key: "
                            + key
                            + " Type: "
                            + type);
        }
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mInjector.getContext().enforceCallingPermission(android.Manifest.permission.DUMP,
                "Can't dump " + TAG);
        synchronized (mHeldSdk) {
            // TODO(b/211575098): Use IndentingPrintWriter for better formatting
            if (mHeldSdk.isEmpty()) {
                writer.println("mHeldSdk is empty");
            } else {
                writer.print("mHeldSdk size: ");
                writer.println(mHeldSdk.size());
                for (SandboxedSdkHolder sandboxedSdkHolder : mHeldSdk.values()) {
                    sandboxedSdkHolder.dump(writer);
                    writer.println();
                }
            }
        }
    }

    private void enforceCallerIsSystemServer() {
        if (mInjector.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "Only system_server is allowed to call this API, actual calling uid is "
                            + mInjector.getCallingUid());
        }
    }

    private void loadSdkInternal(
            @NonNull String callingPackageName,
            @NonNull IBinder sdkToken,
            @NonNull ApplicationInfo applicationInfo,
            @NonNull String sdkName,
            @NonNull String sdkProviderClassName,
            @Nullable String sdkCeDataDir,
            @Nullable String sdkDeDataDir,
            @NonNull Bundle params,
            @NonNull ILoadSdkInSandboxCallback callback) {
        synchronized (mHeldSdk) {
            if (mHeldSdk.containsKey(sdkToken)) {
                sendLoadError(
                        callback,
                        ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED,
                        "Already loaded sdk for package " + applicationInfo.packageName);
                return;
            }
        }

        try {
            ClassLoader loader = getClassLoader(applicationInfo);
            Class<?> clz = Class.forName(SandboxedSdkHolder.class.getName(), true, loader);
            SandboxedSdkHolder sandboxedSdkHolder =
                    (SandboxedSdkHolder) clz.getDeclaredConstructor().newInstance();
            SandboxedSdkContext sandboxedSdkContext =
                    new SandboxedSdkContext(
                            mInjector.getContext(),
                            callingPackageName,
                            applicationInfo,
                            sdkName,
                            sdkCeDataDir,
                            sdkDeDataDir);
            sandboxedSdkHolder.init(
                    mInjector.getContext(),
                    params,
                    callback,
                    sdkProviderClassName,
                    loader,
                    sandboxedSdkContext,
                    mInjector);
            synchronized (mHeldSdk) {
                mHeldSdk.put(sdkToken, sandboxedSdkHolder);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            sendLoadError(
                    callback,
                    ILoadSdkInSandboxCallback.LOAD_SDK_NOT_FOUND,
                    "Failed to find: " + SandboxedSdkHolder.class.getName());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            sendLoadError(
                    callback,
                    ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR,
                    "Failed to instantiate " + SandboxedSdkHolder.class.getName() + ": " + e);
        }
    }

    private void unloadSdkInternal(@NonNull IBinder sdkToken) {
        synchronized (mHeldSdk) {
            SandboxedSdkHolder sandboxedSdkHolder = mHeldSdk.get(sdkToken);
            if (sandboxedSdkHolder != null) {
                sandboxedSdkHolder.unloadSdk();
                mHeldSdk.remove(sdkToken);
            }
        }
    }

    private void sendLoadError(ILoadSdkInSandboxCallback callback, int errorCode, String message) {
        try {
            callback.onLoadSdkError(new LoadSdkException(errorCode, message));
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadCodeError");
        }
    }

    private ClassLoader getClassLoader(ApplicationInfo appInfo) {
        return new DexClassLoader(appInfo.sourceDir, null, null, getClass().getClassLoader());
    }

    final class SdkSandboxServiceDelegate extends ISdkSandboxService.Stub {

        @Override
        public void loadSdk(
                @NonNull String callingPackageName,
                @NonNull IBinder sdkToken,
                @NonNull ApplicationInfo applicationInfo,
                @NonNull String sdkName,
                @NonNull String sdkProviderClassName,
                @Nullable String sdkCeDataDir,
                @Nullable String sdkDeDataDir,
                @NonNull Bundle params,
                @NonNull ILoadSdkInSandboxCallback callback) {
            Objects.requireNonNull(callingPackageName, "callingPackageName should not be null");
            Objects.requireNonNull(sdkToken, "sdkToken should not be null");
            Objects.requireNonNull(applicationInfo, "applicationInfo should not be null");
            Objects.requireNonNull(sdkName, "sdkName should not be null");
            Objects.requireNonNull(sdkProviderClassName, "sdkProviderClassName should not be null");
            Objects.requireNonNull(params, "params should not be null");
            Objects.requireNonNull(callback, "callback should not be null");
            if (TextUtils.isEmpty(sdkProviderClassName)) {
                throw new IllegalArgumentException("sdkProviderClassName must not be empty");
            }
            SdkSandboxServiceImpl.this.loadSdk(
                    callingPackageName,
                    sdkToken,
                    applicationInfo,
                    sdkName,
                    sdkProviderClassName,
                    sdkCeDataDir,
                    sdkDeDataDir,
                    params,
                    callback);
        }

        @Override
        public void unloadSdk(@NonNull IBinder sdkToken) {
            Objects.requireNonNull(sdkToken, "sdkToken should not be null");
            SdkSandboxServiceImpl.this.unloadSdk(sdkToken);
        }

        @Override
        public void syncDataFromClient(@NonNull SharedPreferencesUpdate update) {
            Objects.requireNonNull(update, "update should not be null");
            SdkSandboxServiceImpl.this.syncDataFromClient(update);
        }
    }
}
