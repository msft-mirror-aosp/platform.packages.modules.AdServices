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

package com.android.server.sdksandbox;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED;
import static android.app.sdksandbox.SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED;
import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE;
import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_SERVICE;

import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__UNLOAD_SDK;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX;
import static com.android.server.sdksandbox.SdkSandboxStorageManager.StorageDirInfo;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.IRequestSurfacePackageCallback;
import android.app.sdksandbox.ISdkSandboxManager;
import android.app.sdksandbox.ISdkSandboxProcessDeathCallback;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.LogUtil;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.webkit.WebViewUpdateService;

import com.android.adservices.AdServicesCommon;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.sdksandbox.IComputeSdkStorageCallback;
import com.android.sdksandbox.ISdkSandboxDisabledCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.pm.PackageManagerLocal;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Implementation of {@link SdkSandboxManager}.
 *
 * @hide
 */
public class SdkSandboxManagerService extends ISdkSandboxManager.Stub {

    private static final String TAG = "SdkSandboxManager";

    private static final String STOP_SDK_SANDBOX_PERMISSION =
            "com.android.app.sdksandbox.permission.STOP_SDK_SANDBOX";

    private static final String SANDBOX_NOT_AVAILABLE_MSG = "Sandbox is unavailable";
    private static final String SANDBOX_DISABLED_MSG = "SDK sandbox is disabled";

    private final Context mContext;

    private final ActivityManager mActivityManager;
    private final Handler mHandler;
    private final SdkSandboxStorageManager mSdkSandboxStorageManager;
    private final SdkSandboxServiceProvider mServiceProvider;

    private final Object mLock = new Object();

    /**
     * For each app, keep a mapping from SDK name to it's corresponding LoadSdkSession. This can
     * contain all SDKs that are pending load, have been loaded, unloaded etc. Therefore, it is
     * important to filter out by the type needed.
     */
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ArrayMap<String, LoadSdkSession>> mLoadSdkSessions =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, IBinder> mCallingInfosWithDeathRecipients =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private final Set<CallingInfo> mRunningInstrumentations = new ArraySet<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, RemoteCallbackList<ISdkSandboxProcessDeathCallback>>
            mSandboxLifecycleCallbacks = new ArrayMap<>();

    // Callbacks that need to be invoked when the sandbox binding has occurred (either successfully
    // or unsuccessfully).
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ArrayList<SandboxBindingCallback>>
            mSandboxBindingCallbacks = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ISharedPreferencesSyncCallback> mSyncDataCallbacks =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private final UidImportanceListener mUidImportanceListener = new UidImportanceListener();

    private final SdkSandboxManagerLocal mLocalManager;

    private final String mAdServicesPackageName;

    private Injector mInjector;

    // The device must have a change that allows the Webview provider to be visible in order for the
    // sandbox to be enabled.
    @GuardedBy("mLock")
    private boolean mHasVisibilityPatch;

    @GuardedBy("mLock")
    private boolean mCheckedVisibilityPatch = false;

    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;

    private static final String PROPERTY_DISABLE_SDK_SANDBOX = "disable_sdk_sandbox";
    private static final String GMS_PACKAGENAME_PREFIX = "com.google.android.gms";
    private static final String PROPERTY_SERVICE_BIND_ALLOWED_PACKAGENAMES =
            "runtime_service_bind_allowed_packagenames";
    private static final String PROPERTY_SERVICE_BIND_ALLOWED_ACTIONS =
            "runtime_service_bind_allowed_actions";

    static class Injector {
        long getCurrentTime() {
            return System.currentTimeMillis();
        }

        SdkSandboxShellCommand createShellCommand(
                SdkSandboxManagerService service, Context context) {
            return new SdkSandboxShellCommand(service, context);
        }
    }

    @VisibleForTesting
    SdkSandboxManagerService(Context context, SdkSandboxServiceProvider provider) {
        this(context, provider, new Injector());
    }

    SdkSandboxManagerService(
            Context context, SdkSandboxServiceProvider provider, Injector injector) {
        mContext = context;
        mServiceProvider = provider;
        mInjector = injector;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mLocalManager = new LocalImpl();

        PackageManagerLocal packageManagerLocal =
                LocalManagerRegistry.getManager(PackageManagerLocal.class);
        mSdkSandboxStorageManager =
                new SdkSandboxStorageManager(mContext, mLocalManager, packageManagerLocal);

        // Start the handler thread.
        HandlerThread handlerThread = new HandlerThread("SdkSandboxManagerServiceHandler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        registerBroadcastReceivers();

        mAdServicesPackageName = resolveAdServicesPackage();
        mSdkSandboxSettingsListener = new SdkSandboxSettingsListener(mContext);
        mSdkSandboxSettingsListener.registerObserver();
    }

    private void registerBroadcastReceivers() {
        // Register for package addition and update
        final IntentFilter packageAddedIntentFilter = new IntentFilter();
        packageAddedIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageAddedIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageAddedIntentFilter.addDataScheme("package");
        BroadcastReceiver packageAddedIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String packageName = intent.getData().getSchemeSpecificPart();
                final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                final CallingInfo callingInfo = new CallingInfo(uid, packageName);
                mHandler.post(() -> mSdkSandboxStorageManager.onPackageAddedOrUpdated(callingInfo));
            }
        };
        mContext.registerReceiver(packageAddedIntentReceiver, packageAddedIntentFilter,
                /*broadcastPermission=*/null, mHandler);
    }

    @Override
    public List<SandboxedSdk> getSandboxedSdks(
            String callingPackageName, long timeAppCalledSystemServer) {
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__GET_SANDBOXED_SDKS,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        final List<SandboxedSdk> sandboxedSdks = new ArrayList<>();
        synchronized (mLock) {
            ArrayList<LoadSdkSession> loadedSdks = getLoadedSdksForApp(callingInfo);
            for (int i = 0; i < loadedSdks.size(); i++) {
                LoadSdkSession sdk = loadedSdks.get(i);
                SandboxedSdk sandboxedSdk = sdk.getSandboxedSdk();
                if (sandboxedSdk != null) {
                    sandboxedSdks.add(sandboxedSdk);
                } else {
                    Log.w(
                            TAG,
                            "SandboxedSdk is null for SDK "
                                    + sdk.mSdkName
                                    + " despite being loaded");
                }
            }
        }
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__GET_SANDBOXED_SDKS,
                /*latency=*/ (int)
                        (mInjector.getCurrentTime() - timeSystemServerReceivedCallFromApp),
                /*success=*/ true,
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                callingUid);
        return sandboxedSdks;
    }

    private ArrayList<LoadSdkSession> getLoadedSdksForApp(CallingInfo callingInfo) {
        ArrayList<LoadSdkSession> loadedSdks = new ArrayList<>();
        synchronized (mLock) {
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                ArrayList<LoadSdkSession> loadSessions =
                        new ArrayList<>(mLoadSdkSessions.get(callingInfo).values());
                for (int i = 0; i < loadSessions.size(); i++) {
                    LoadSdkSession sdk = loadSessions.get(i);
                    if (sdk.getStatus() == LoadSdkSession.LOADED) {
                        loadedSdks.add(sdk);
                    }
                }
            }
        }
        return loadedSdks;
    }

    @Override
    public void addSdkSandboxProcessDeathCallback(
            String callingPackageName,
            long timeAppCalledSystemServer,
            ISdkSandboxProcessDeathCallback callback) {
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        synchronized (mLock) {
            if (mSandboxLifecycleCallbacks.containsKey(callingInfo)) {
                mSandboxLifecycleCallbacks.get(callingInfo).register(callback);
            } else {
                RemoteCallbackList<ISdkSandboxProcessDeathCallback> sandboxLifecycleCallbacks =
                        new RemoteCallbackList<>();
                sandboxLifecycleCallbacks.register(callback);
                mSandboxLifecycleCallbacks.put(callingInfo, sandboxLifecycleCallbacks);
            }
        }
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                /*latency=*/ (int)
                        (mInjector.getCurrentTime() - timeSystemServerReceivedCallFromApp),
                /*success=*/ true,
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                callingUid);
    }

    @Override
    public void removeSdkSandboxProcessDeathCallback(
            String callingPackageName,
            long timeAppCalledSystemServer,
            ISdkSandboxProcessDeathCallback callback) {
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog
                        .SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        synchronized (mLock) {
            RemoteCallbackList<ISdkSandboxProcessDeathCallback> sandboxLifecycleCallbacks =
                    mSandboxLifecycleCallbacks.get(callingInfo);
            if (sandboxLifecycleCallbacks != null) {
                sandboxLifecycleCallbacks.unregister(callback);
            }
        }
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog
                        .SANDBOX_API_CALLED__METHOD__REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                /*latency=*/ (int)
                        (mInjector.getCurrentTime() - timeSystemServerReceivedCallFromApp),
                /*success=*/ true,
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                callingUid);
    }

    @Override
    public void loadSdk(
            String callingPackageName,
            String sdkName,
            long timeAppCalledSystemServer,
            Bundle params,
            ILoadSdkCallback callback) {
        // Log the IPC latency from app to system server
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);
        enforceCallerHasNetworkAccess(callingPackageName);
        enforceCallerRunsInForeground(callingInfo);
        synchronized (mLock) {
            if (mRunningInstrumentations.contains(callingInfo)) {
                throw new SecurityException(
                        "Currently running instrumentation of this sdk sandbox process");
            }
        }

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        final long token = Binder.clearCallingIdentity();
        try {
            loadSdkWithClearIdentity(
                    callingInfo, sdkName, params, callback, timeSystemServerReceivedCallFromApp);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void loadSdkWithClearIdentity(
            CallingInfo callingInfo,
            String sdkName,
            Bundle params,
            ILoadSdkCallback callback,
            long timeSystemServerReceivedCallFromApp) {
        LoadSdkSession loadSdkSession =
                new LoadSdkSession(mContext, mInjector, sdkName, callingInfo, params, callback);

        // SDK provider was invalid. This load request should fail.
        String errorMsg = loadSdkSession.getSdkProviderErrorIfExists();
        if (!TextUtils.isEmpty(errorMsg)) {
            Log.w(TAG, errorMsg);
            loadSdkSession.handleLoadFailure(
                    new LoadSdkException(SdkSandboxManager.LOAD_SDK_NOT_FOUND, errorMsg),
                    /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    /*successAtStage=*/ false);
            return;
        }

        // Ensure we are not already loading this sdk. That's determined by checking if we already
        // have a completed LoadSdkSession with the same SDK name for the calling info.
        synchronized (mLock) {
            LoadSdkSession prevLoadSession = null;
            // Get any previous load session for this SDK if exists.
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                prevLoadSession = mLoadSdkSessions.get(callingInfo).get(sdkName);
            }

            // If there was a previous load session and the status is loaded, this new load request
            // should fail.
            if (prevLoadSession != null && prevLoadSession.getStatus() == LoadSdkSession.LOADED) {
                loadSdkSession.handleLoadFailure(
                        new LoadSdkException(
                                SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                                sdkName + " has been loaded already"),
                        /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                        SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                        /*successAtStage=*/ false);
                return;
            }

            // If there was an ongoing load session for this SDK, this new load request should fail.
            if (prevLoadSession != null
                    && prevLoadSession.getStatus() == LoadSdkSession.LOAD_PENDING) {
                loadSdkSession.handleLoadFailure(
                        new LoadSdkException(
                                SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                                sdkName + " is currently being loaded"),
                        /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                        SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                        /*successAtStage=*/ false);
                return;
            }

            // If there was no previous load session (or there was one but its load status was
            // unloaded or failed), it should be replaced by the new load session.
            mLoadSdkSessions.computeIfAbsent(callingInfo, k -> new ArrayMap<>());
            mLoadSdkSessions.get(callingInfo).put(sdkName, loadSdkSession);
        }

        // Register a death recipient to clean up app related state and unbind its service after
        // the app dies.
        try {
            synchronized (mLock) {
                if (!mCallingInfosWithDeathRecipients.containsKey(callingInfo)) {
                    Log.d(TAG, "Registering " + callingInfo + " for death notification");
                    callback.asBinder().linkToDeath(() -> onAppDeath(callingInfo), 0);
                    mCallingInfosWithDeathRecipients.put(callingInfo, callback.asBinder());
                    mUidImportanceListener.startListening();
                }
            }
        } catch (RemoteException re) {
            // Log the time taken in System Server before the exception occurred
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                    /*latency=*/ (int)
                            (mInjector.getCurrentTime() - timeSystemServerReceivedCallFromApp),
                    /*success=*/ false,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    callingInfo.getUid());

            // App has already died, cleanup sdk link, and unbind its service
            onAppDeath(callingInfo);
            return;
        }

        invokeSdkSandboxServiceToLoadSdk(loadSdkSession, timeSystemServerReceivedCallFromApp);
    }

    @Override
    public void unloadSdk(
            String callingPackageName, String sdkName, long timeAppCalledSystemServer) {
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);
        enforceCallerRunsInForeground(callingInfo);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        final long token = Binder.clearCallingIdentity();
        try {
            unloadSdkWithClearIdentity(callingInfo, sdkName, timeSystemServerReceivedCallFromApp);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void unloadSdkWithClearIdentity(
            CallingInfo callingInfo, String sdkName, long timeSystemServerReceivedCallFromApp) {
        LoadSdkSession prevLoadSession = null;
        long timeSystemServerReceivedCallFromSandbox;
        synchronized (mLock) {
            // TODO(b/254657226): Add a callback or return value for unloadSdk() to indicate
            // success of unload.

            // Get any previous load session for this SDK if exists.
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                prevLoadSession = mLoadSdkSessions.get(callingInfo).get(sdkName);
            }
        }

        // If there was no previous load session or the SDK is not loaded, there is nothing to
        // unload.
        if (prevLoadSession == null) {
            // Unloading SDK that is not loaded is a no-op, return.
            Log.i(TAG, "SDK " + sdkName + " is not loaded for " + callingInfo);
            return;
        }

        prevLoadSession.unload(timeSystemServerReceivedCallFromApp);
        timeSystemServerReceivedCallFromSandbox = mInjector.getCurrentTime();

        ArrayList<LoadSdkSession> loadedSdks = getLoadedSdksForApp(callingInfo);
        if (loadedSdks.isEmpty()) {
            stopSdkSandboxService(
                    callingInfo, "Caller " + callingInfo + " has no remaining SDKS loaded.");
        }
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                (int) (mInjector.getCurrentTime() - timeSystemServerReceivedCallFromSandbox),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                callingInfo.getUid());
    }

    private void enforceCallingPackageBelongsToUid(CallingInfo callingInfo) {
        int callingUid = callingInfo.getUid();
        String callingPackage = callingInfo.getPackageName();
        int packageUid;
        PackageManager pm = mContext.createContextAsUser(
                UserHandle.getUserHandleForUid(callingUid), 0).getPackageManager();
        try {
            packageUid = pm.getPackageUid(callingPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(callingPackage + " not found");
        }
        if (packageUid != callingUid) {
            throw new SecurityException(callingPackage + " does not belong to uid " + callingUid);
        }
    }

    private void enforceCallerRunsInForeground(CallingInfo callingInfo) {
        String callingPackage = callingInfo.getPackageName();
        final long token = Binder.clearCallingIdentity();
        try {
            int importance = mActivityManager.getUidImportance(callingInfo.getUid());
            if (importance > IMPORTANCE_FOREGROUND) {
                throw new SecurityException(callingPackage + " does not run in the foreground");
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void enforceCallerHasNetworkAccess(String callingPackage) {
        mContext.enforceCallingPermission(android.Manifest.permission.INTERNET,
                callingPackage + " does not hold INTERNET permission");
        mContext.enforceCallingPermission(android.Manifest.permission.ACCESS_NETWORK_STATE,
                callingPackage + " does not hold ACCESS_NETWORK_STATE permission");
    }

    private void onAppDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            Log.d(TAG, "App " + callingInfo + " has died, cleaning up associated sandbox info");
            mSandboxLifecycleCallbacks.remove(callingInfo);
            mSandboxBindingCallbacks.remove(callingInfo);
            mCallingInfosWithDeathRecipients.remove(callingInfo);
            if (mCallingInfosWithDeathRecipients.size() == 0) {
                mUidImportanceListener.stopListening();
            }
            mSyncDataCallbacks.remove(callingInfo);
            mLoadSdkSessions.remove(callingInfo);
            stopSdkSandboxService(callingInfo, "Caller " + callingInfo + " has died");
        }
    }

    @Override
    public void requestSurfacePackage(
            String callingPackageName,
            String sdkName,
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            long timeAppCalledSystemServer,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        LogUtil.d(
                TAG,
                "requestSurfacePackage call received. callingPackageName: " + callingPackageName);

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);
        enforceCallerRunsInForeground(callingInfo);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        final long token = Binder.clearCallingIdentity();
        try {
            requestSurfacePackageWithClearIdentity(
                    callingInfo,
                    sdkName,
                    hostToken,
                    displayId,
                    width,
                    height,
                    timeSystemServerReceivedCallFromApp,
                    params,
                    callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void requestSurfacePackageWithClearIdentity(
            CallingInfo callingInfo,
            String sdkName,
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            long timeSystemServerReceivedCallFromApp,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        LoadSdkSession loadSdkSession = null;
        synchronized (mLock) {
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                loadSdkSession = mLoadSdkSessions.get(callingInfo).get(sdkName);
            }
        }
        if (loadSdkSession == null) {
            LogUtil.d(
                    TAG,
                    callingInfo + " requested surface package, but could not find SDK " + sdkName);

            final long timeSystemServerCalledApp = mInjector.getCurrentTime();
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                    (int) (timeSystemServerCalledApp - timeSystemServerReceivedCallFromApp),
                    /*successAtStage*/ false,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    callingInfo.getUid());

            try {
                callback.onSurfacePackageError(
                        REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                        "SDK " + sdkName + " is not loaded",
                        timeSystemServerCalledApp);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageError", e);
            }
            return;
        }

        loadSdkSession.requestSurfacePackage(
                hostToken,
                displayId,
                width,
                height,
                timeSystemServerReceivedCallFromApp,
                params,
                callback);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void onUserUnlocking(int userId) {
        Log.i(TAG, "onUserUnlocking " + userId);
        // using postDelayed to wait for other volumes to mount
        mHandler.postDelayed(() -> mSdkSandboxStorageManager.onUserUnlocking(userId), 20000);
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mContext.enforceCallingPermission(android.Manifest.permission.DUMP,
                "Can't dump " + TAG);

        // TODO(b/211575098): Use IndentingPrintWriter for better formatting
        synchronized (mLock) {
            writer.println("Checked Webview visibility patch exists: " + mCheckedVisibilityPatch);
            if (mCheckedVisibilityPatch) {
                writer.println("Build contains Webview visibility patch: " + mHasVisibilityPatch);
            }
            writer.println(
                    "Killswitch enabled: " + mSdkSandboxSettingsListener.isKillSwitchEnabled());
            writer.println("mLoadSdkSessions size: " + mLoadSdkSessions.size());
            for (CallingInfo callingInfo : mLoadSdkSessions.keySet()) {
                writer.printf("Caller: %s has following SDKs", callingInfo);
                writer.println();
                ArrayList<LoadSdkSession> loadSessions =
                        new ArrayList<>(mLoadSdkSessions.get(callingInfo).values());
                for (int i = 0; i < loadSessions.size(); i++) {
                    LoadSdkSession sdk = loadSessions.get(i);
                    writer.printf("SDK: %s Status: %s", sdk.mSdkName, sdk.getStatus());
                    writer.println();
                }
            }
            writer.println();
        }

        writer.println("mServiceProvider:");
        mServiceProvider.dump(writer);
        writer.println();
    }

    @Override
    public void syncDataFromClient(
            String callingPackageName,
            long timeAppCalledSystemServer,
            SharedPreferencesUpdate update,
            ISharedPreferencesSyncCallback callback) {
        final long timeSystemServerReceivedCallFromApp = mInjector.getCurrentTime();

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__SYNC_DATA_FROM_CLIENT,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromApp - timeAppCalledSystemServer),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__APP_TO_SYSTEM_SERVER,
                callingUid);

        final long token = Binder.clearCallingIdentity();
        try {
            syncDataFromClientInternal(callingInfo, update, callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void syncDataFromClientInternal(
            CallingInfo callingInfo,
            SharedPreferencesUpdate update,
            ISharedPreferencesSyncCallback callback) {
        // check first if service already bound
        ISdkSandboxService service = mServiceProvider.getBoundServiceForApp(callingInfo);
        if (service != null) {
            try {
                service.syncDataFromClient(update);
            } catch (RemoteException e) {
                syncDataOnError(callingInfo, callback, e.getMessage());
            }
        } else {
            syncDataOnError(callingInfo, callback, "Sandbox not available");
        }
    }

    private void syncDataOnError(
            CallingInfo callingInfo, ISharedPreferencesSyncCallback callback, String errorMsg) {
        // Store reference to the callback so that we can notify SdkSandboxManager when sandbox
        // starts
        synchronized (mLock) {
            mSyncDataCallbacks.put(callingInfo, callback);
        }
        try {
            callback.onError(ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE, errorMsg);
        } catch (RemoteException ignore) {
            // App died. Sync will be re-established again by app later.
        }
    }

    @Override
    public void logLatencyFromSystemServerToApp(String method, int latency) {
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                convertToStatsLogMethodCode(method),
                latency,
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_APP,
                Binder.getCallingUid());
    }

    private int convertToStatsLogMethodCode(String method) {
        switch (method) {
            case ISdkSandboxManager.REQUEST_SURFACE_PACKAGE:
                return SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE;
            case ISdkSandboxManager.LOAD_SDK:
                return SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK;
            default:
                return SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__METHOD_UNSPECIFIED;
        }
    }

    interface SandboxBindingCallback {
        void onBindingSuccessful(ISdkSandboxService service, int timeToLoadSandbox);

        void onBindingFailed(LoadSdkException exception, long startTimeForLoadingSandbox);
    }

    class SandboxServiceConnection implements ServiceConnection {

        private final SdkSandboxServiceProvider mServiceProvider;
        private final CallingInfo mCallingInfo;
        private boolean mHasConnectedBefore = false;
        private long mStartTimeForLoadingSandbox;


        SandboxServiceConnection(
                SdkSandboxServiceProvider serviceProvider,
                CallingInfo callingInfo,
                long startTimeForLoadingSandbox) {
            mServiceProvider = serviceProvider;
            mCallingInfo = callingInfo;
            mStartTimeForLoadingSandbox = startTimeForLoadingSandbox;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final ISdkSandboxService mService = ISdkSandboxService.Stub.asInterface(service);
            // Perform actions needed after every sandbox restart.
            LoadSdkException exception = onSandboxConnected(mService);

            // Set bound service for app once all initialization has finished. This needs to be set
            // after every sandbox restart as well.
            // TODO(b/259387335): Maybe kill the sandbox if the connection is not valid? For now,
            // setting the bound service so that unbinding and killing of the sandbox happens when
            // the app dies.
            mServiceProvider.setBoundServiceForApp(mCallingInfo, mService);

            // Once bound service has been set, sync manager is notified.
            notifySyncManagerSandboxStarted(mCallingInfo);

            try {
                computeSdkStorage(mCallingInfo, mService);
            } catch (RemoteException e) {
                Log.e(TAG, "Error while computing sdk storage for CallingInfo: " + mCallingInfo);
            }

            if (!mHasConnectedBefore) {
                final int timeToLoadSandbox =
                        (int) (mInjector.getCurrentTime() - mStartTimeForLoadingSandbox);
                logSandboxStart(timeToLoadSandbox);

                mHasConnectedBefore = true;

                ArrayList<SandboxBindingCallback> sandboxBindingCallbacksForApp =
                        clearAndGetSandboxBindingCallbacks();
                for (int i = 0; i < sandboxBindingCallbacksForApp.size(); i++) {
                    SandboxBindingCallback callback = sandboxBindingCallbacksForApp.get(i);
                    if (exception == null) {
                        // Connection is valid - set bound service for app and load SDKs.
                        callback.onBindingSuccessful(mService, timeToLoadSandbox);
                    } else {
                        // Connection is not valid
                        callback.onBindingFailed(exception, mStartTimeForLoadingSandbox);
                    }
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Sdk sandbox crashed or killed, system will start it again.
            // TODO(b/204991850): Handle restarts differently
            //  (e.g. Exponential backoff retry strategy)
            Log.d(TAG, "Sandbox service for " + mCallingInfo + " has been disconnected");
            mServiceProvider.setBoundServiceForApp(mCallingInfo, null);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "Sandbox service failed to bind for " + mCallingInfo + " : died on binding");
            mServiceProvider.setBoundServiceForApp(mCallingInfo, null);
            mServiceProvider.unbindService(mCallingInfo, true);
            mServiceProvider.bindService(mCallingInfo, this);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "Sandbox service failed to bind for " + mCallingInfo + " : service is null");
            LoadSdkException exception =
                    new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                            "Failed to bind the service");
            ArrayList<SandboxBindingCallback> sandboxBindingCallbacksForApp =
                    clearAndGetSandboxBindingCallbacks();
            for (int i = 0; i < sandboxBindingCallbacksForApp.size(); i++) {
                SandboxBindingCallback callback = sandboxBindingCallbacksForApp.get(i);
                callback.onBindingFailed(exception, mStartTimeForLoadingSandbox);
            }
        }

        /**
         * Actions to be performed every time the sandbox connects for a particular app, such as the
         * first time the sandbox is brought up and every time it restarts.
         *
         * @return null if all actions were performed successfully, otherwise a {@link
         *     LoadSdkException} specifying the error that need to be sent back to SDKs waiting to
         *     be loaded.
         */
        @Nullable
        private LoadSdkException onSandboxConnected(ISdkSandboxService service) {
            Log.i(
                    TAG,
                    String.format(
                            "Sdk sandbox has been bound for app package %s with uid %d",
                            mCallingInfo.getPackageName(), mCallingInfo.getUid()));
            try {
                service.asBinder().linkToDeath(() -> onSdkSandboxDeath(mCallingInfo), 0);
            } catch (RemoteException e) {
                // Sandbox had already died, cleanup sdk links, notify app etc.
                onSdkSandboxDeath(mCallingInfo);
                return new LoadSdkException(
                        SDK_SANDBOX_PROCESS_NOT_AVAILABLE, SANDBOX_NOT_AVAILABLE_MSG, e);
            }

            try {
                service.initialize(new SdkToServiceLink());
            } catch (RemoteException e) {
                final String errorMsg = "Failed to initialize sandbox";
                Log.e(TAG, errorMsg + " for " + mCallingInfo);
                return new LoadSdkException(
                        SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                        SANDBOX_NOT_AVAILABLE_MSG + " : " + errorMsg,
                        e);
            }

            return null;
        }

        private void logSandboxStart(int timeToLoadSandbox) {
            // Log the latency for loading the Sandbox process
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                    timeToLoadSandbox,
                    /* success=*/ true,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                    mCallingInfo.getUid());
        }

        private ArrayList<SandboxBindingCallback> clearAndGetSandboxBindingCallbacks() {
            ArrayList<SandboxBindingCallback> sandboxBindingCallbacksForApp;
            synchronized (mLock) {
                sandboxBindingCallbacksForApp = mSandboxBindingCallbacks.get(mCallingInfo);
                mSandboxBindingCallbacks.remove(mCallingInfo);
            }
            if (sandboxBindingCallbacksForApp == null) {
                sandboxBindingCallbacksForApp = new ArrayList<>();
            }
            return sandboxBindingCallbacksForApp;
        }
    }

    void startSdkSandbox(CallingInfo callingInfo, long startTimeForLoadingSandbox) {
        mServiceProvider.bindService(
                callingInfo,
                new SandboxServiceConnection(
                        mServiceProvider, callingInfo, startTimeForLoadingSandbox));
    }

    private void invokeSdkSandboxServiceToLoadSdk(
            LoadSdkSession loadSdkSession, long timeSystemServerReceivedCallFromApp) {
        ISdkSandboxService service = null;
        boolean isSandboxStartRequired = false;
        synchronized (mLock) {
            // Check if service is already bound for the app.
            service = mServiceProvider.getBoundServiceForApp(loadSdkSession.mCallingInfo);
            if (service == null) {
                if (mSandboxBindingCallbacks.get(loadSdkSession.mCallingInfo) == null) {
                    // No other SDKs are waiting to be loaded. Sandbox start is required.
                    isSandboxStartRequired = true;
                }
                SandboxBindingCallback callback =
                        createSdkLoadCallback(loadSdkSession, timeSystemServerReceivedCallFromApp);
                addSandboxBindingCallback(loadSdkSession.mCallingInfo, callback);
                if (!isSandboxStartRequired) {
                    // Sandbox is in the process of being brought up. Nothing more to do here.
                    return;
                }
            }
        }

        if (!isSandboxStartRequired) {
            loadSdkForService(
                    loadSdkSession,
                    /*timeToLoadSandbox=*/ -1,
                    timeSystemServerReceivedCallFromApp,
                    service);
            return;
        }

        final long startTimeForLoadingSandbox = mInjector.getCurrentTime();

        // Prepare sdk data directories before starting the sandbox. If sdk data package directory
        // is missing, starting the sandbox process would crash as we will fail to mount data_mirror
        // for sdk-data isolation.
        mSdkSandboxStorageManager.prepareSdkDataOnLoad(loadSdkSession.mCallingInfo);
        startSdkSandbox(loadSdkSession.mCallingInfo, startTimeForLoadingSandbox);
    }

    private SandboxBindingCallback createSdkLoadCallback(
            LoadSdkSession loadSdkSession, long timeSystemServerReceivedCallFromApp) {
        return new SandboxBindingCallback() {
            @Override
            public void onBindingSuccessful(ISdkSandboxService service, int timeToLoadSandbox) {
                loadSdkForService(
                        loadSdkSession,
                        timeToLoadSandbox,
                        timeSystemServerReceivedCallFromApp,
                        service);
            }

            @Override
            public void onBindingFailed(
                    LoadSdkException exception, long startTimeForLoadingSandbox) {
                loadSdkSession.handleLoadFailure(
                        exception,
                        /*startTimeOfErrorStage=*/ startTimeForLoadingSandbox,
                        /*stage*/ SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                        /*successAtStage=*/ false);
            }
        };
    }

    void addSandboxBindingCallback(CallingInfo callingInfo, SandboxBindingCallback callback) {
        synchronized (mLock) {
            mSandboxBindingCallbacks.computeIfAbsent(callingInfo, k -> new ArrayList<>());
            mSandboxBindingCallbacks.get(callingInfo).add(callback);
        }
    }

    private void onSdkSandboxDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            handleSandboxLifecycleCallbacksLocked(callingInfo);
            mSandboxBindingCallbacks.remove(callingInfo);
            // All SDK state is lost on death.
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                ArrayList<LoadSdkSession> loadSessions =
                        new ArrayList<>(mLoadSdkSessions.get(callingInfo).values());
                for (int i = 0; i < loadSessions.size(); i++) {
                    LoadSdkSession loadSdkSession = loadSessions.get(i);
                    loadSdkSession.onSandboxDeath();
                }
                mLoadSdkSessions.remove(callingInfo);
            }
        }
    }

    @GuardedBy("mLock")
    private void handleSandboxLifecycleCallbacksLocked(CallingInfo callingInfo) {
        RemoteCallbackList<ISdkSandboxProcessDeathCallback> sandboxLifecycleCallbacks;
        sandboxLifecycleCallbacks = mSandboxLifecycleCallbacks.get(callingInfo);

        if (sandboxLifecycleCallbacks == null) {
            return;
        }

        int size = sandboxLifecycleCallbacks.beginBroadcast();
        for (int i = 0; i < size; ++i) {
            try {
                sandboxLifecycleCallbacks.getBroadcastItem(i).onSdkSandboxDied();
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to send sdk sandbox death event to app", e);
            }
        }
        sandboxLifecycleCallbacks.finishBroadcast();

        mSandboxLifecycleCallbacks.remove(callingInfo);
    }

    @Override
    public void stopSdkSandbox(String callingPackageName) {
        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);

        mContext.enforceCallingPermission(
                STOP_SDK_SANDBOX_PERMISSION,
                callingPackageName + " does not have permission to stop their sandbox");

        final long token = Binder.clearCallingIdentity();
        try {
            stopSdkSandboxService(callingInfo, "App requesting sandbox kill");
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean isSdkSandboxDisabled(ISdkSandboxService boundService) {
        synchronized (mLock) {
            if (!mCheckedVisibilityPatch) {
                SdkSandboxDisabledCallback callback = new SdkSandboxDisabledCallback();
                try {
                    boundService.isDisabled(callback);
                    boolean isDisabled = callback.getIsDisabled();
                    mCheckedVisibilityPatch = true;
                    mHasVisibilityPatch = !isDisabled;
                } catch (Exception e) {
                    Log.w(TAG, "Could not verify SDK sandbox state", e);
                    return true;
                }
            }
            return !mHasVisibilityPatch || getSdkSandboxSettingsListener().isKillSwitchEnabled();
        }
    }

    /**
     * Clears the SDK sandbox state. This will result in the state being checked again the next time
     * an SDK is loaded.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void clearSdkSandboxState() {
        synchronized (mLock) {
            mCheckedVisibilityPatch = false;
            getSdkSandboxSettingsListener().setKillSwitchState(true);
        }
    }

    /**
     * Enables the sandbox for testing purposes. Note that the sandbox can still be disabled by
     * setting the killswitch.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void forceEnableSandbox() {
        synchronized (mLock) {
            mCheckedVisibilityPatch = true;
            mHasVisibilityPatch = true;
            getSdkSandboxSettingsListener().setKillSwitchState(false);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxSettingsListener getSdkSandboxSettingsListener() {
        synchronized (mLock) {
            return mSdkSandboxSettingsListener;
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void setSdkSandboxSettingsListener(SdkSandboxSettingsListener listener) {
        synchronized (mLock) {
            mSdkSandboxSettingsListener = listener;
        }
    }

    class SdkSandboxSettingsListener implements DeviceConfig.OnPropertiesChangedListener {

        private final Context mContext;
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private boolean mKillSwitchEnabled =
                DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_DISABLE_SDK_SANDBOX, true);

        @GuardedBy("mLock")
        private String mBindServiceAllowedPackageNames =
                DeviceConfig.getString(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_SERVICE_BIND_ALLOWED_PACKAGENAMES,
                        null);

        @GuardedBy("mLock")
        private String mBindServiceAllowedActions =
                DeviceConfig.getString(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_SERVICE_BIND_ALLOWED_ACTIONS,
                        null);

        SdkSandboxSettingsListener(Context context) {
            mContext = context;
        }

        private void registerObserver() {
            DeviceConfig.addOnPropertiesChangedListener(
                    DeviceConfig.NAMESPACE_ADSERVICES, mContext.getMainExecutor(), this);
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        boolean isKillSwitchEnabled() {
            synchronized (mLock) {
                return mKillSwitchEnabled;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        String getServiceBindPackageNamesAllowlist() {
            synchronized (mLock) {
                return mBindServiceAllowedPackageNames;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        String getServiceBindActionsAllowlist() {
            synchronized (mLock) {
                return mBindServiceAllowedActions;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        void setKillSwitchState(boolean enabled) {
            synchronized (mLock) {
                DeviceConfig.setProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_DISABLE_SDK_SANDBOX,
                        Boolean.toString(enabled),
                        false);
                mKillSwitchEnabled = enabled;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        void setBindServiceAllowedPackageNames(String packageNames) {
            synchronized (mLock) {
                DeviceConfig.setProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_SERVICE_BIND_ALLOWED_PACKAGENAMES,
                        packageNames,
                        false);
                mBindServiceAllowedPackageNames = packageNames;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        void setBindServiceAllowedActions(String actions) {
            synchronized (mLock) {
                DeviceConfig.setProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_SERVICE_BIND_ALLOWED_ACTIONS,
                        actions,
                        false);
                mBindServiceAllowedActions = actions;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        void unregisterPropertiesListener() {
            DeviceConfig.removeOnPropertiesChangedListener(this);
        }

        @Override
        public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
            synchronized (mLock) {
                for (String name : properties.getKeyset()) {
                    if (name == null) {
                        continue;
                    }

                    if (name.equals(PROPERTY_DISABLE_SDK_SANDBOX)) {
                        boolean killSwitchPreviouslyEnabled = mKillSwitchEnabled;
                        mKillSwitchEnabled =
                                properties.getBoolean(PROPERTY_DISABLE_SDK_SANDBOX, true);
                        if (mKillSwitchEnabled && !killSwitchPreviouslyEnabled) {
                            Log.i(TAG, "SDK sandbox killswitch has become enabled");
                            synchronized (SdkSandboxManagerService.this.mLock) {
                                stopAllSandboxesLocked();
                            }
                        }
                    }

                    if (name.equals(PROPERTY_SERVICE_BIND_ALLOWED_PACKAGENAMES)) {
                        mBindServiceAllowedPackageNames =
                                properties.getString(
                                        PROPERTY_SERVICE_BIND_ALLOWED_PACKAGENAMES, null);
                    }

                    if (name.equals(PROPERTY_SERVICE_BIND_ALLOWED_ACTIONS)) {
                        mBindServiceAllowedActions =
                                properties.getString(PROPERTY_SERVICE_BIND_ALLOWED_ACTIONS, null);
                    }
                }
            }
        }
    }

    static class SdkSandboxDisabledCallback extends ISdkSandboxDisabledCallback.Stub {
        CountDownLatch mLatch;
        boolean mIsDisabled = false;

        SdkSandboxDisabledCallback() {
            mLatch = new CountDownLatch(1);
        }

        @Override
        public void onResult(boolean isDisabled) {
            mIsDisabled = isDisabled;
            mLatch.countDown();
        }

        boolean getIsDisabled() {
            try {
                if (mLatch.await(1, TimeUnit.SECONDS)) {
                    return mIsDisabled;
                }
                return true;
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for SDK sandbox state", e);
                return true;
            }
        }
    }

    /** Stops all running sandboxes in the case that the killswitch is triggered. */
    @GuardedBy("mLock")
    private void stopAllSandboxesLocked() {
        for (int i = mLoadSdkSessions.size() - 1; i >= 0; --i) {
            stopSdkSandboxService(mLoadSdkSessions.keyAt(i), "SDK sandbox killswitch enabled");
        }
    }

    void stopSdkSandboxService(CallingInfo currentCallingInfo, String reason) {
        if (!isSdkSandboxServiceRunning(currentCallingInfo)) {
            Log.d(TAG, "Cannot kill sandbox for " + currentCallingInfo + ", already dead");
            return;
        }

        mServiceProvider.unbindService(currentCallingInfo, true);

        // For apps with shared uid, unbind the sandboxes for all the remaining apps since we kill
        // the sandbox by uid.
        synchronized (mLock) {
            for (int i = 0; i < mCallingInfosWithDeathRecipients.size(); i++) {
                final CallingInfo callingInfo = mCallingInfosWithDeathRecipients.keyAt(i);
                if (callingInfo.getUid() == currentCallingInfo.getUid()) {
                    mServiceProvider.unbindService(callingInfo, true);
                }
            }
        }
        final int sdkSandboxUid = Process.toSdkSandboxUid(currentCallingInfo.getUid());
        Log.i(TAG, "Killing sdk sandbox/s with uid " + sdkSandboxUid);
        // TODO(b/230839879): Avoid killing by uid
        mActivityManager.killUid(sdkSandboxUid, reason);
    }

    boolean isSdkSandboxServiceRunning(CallingInfo callingInfo) {
        return mServiceProvider.getBoundServiceForApp(callingInfo) != null;
    }

    private void computeSdkStorage(CallingInfo callingInfo, ISdkSandboxService service)
            throws RemoteException {
        final List<StorageDirInfo> sharedStorageDirsInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(callingInfo);
        final List<StorageDirInfo> sdkStorageDirsInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(callingInfo);

        service.computeSdkStorage(
                getListOfStoragePaths(sharedStorageDirsInfo),
                getListOfStoragePaths(sdkStorageDirsInfo),
                new IComputeSdkStorageCallback.Stub() {
                    @Override
                    public void onStorageInfoComputed(float sharedStorageKb, float sdkStorageKb) {
                        // TODO(b/257952392): Store the storage information in memory in system
                    }
                });
    }

    private List<String> getListOfStoragePaths(List<StorageDirInfo> storageDirInfos) {
        final List<String> paths = new ArrayList<>();

        for (int i = 0; i < storageDirInfos.size(); i++) {
            paths.add(storageDirInfos.get(i).getCeDataDir());
            paths.add(storageDirInfos.get(i).getDeDataDir());
        }
        return paths;
    }

    private void notifySyncManagerSandboxStarted(CallingInfo callingInfo) {
        ISharedPreferencesSyncCallback syncManagerCallback = null;
        synchronized (mLock) {
            syncManagerCallback = mSyncDataCallbacks.get(callingInfo);
            if (syncManagerCallback != null) {
                try {
                    syncManagerCallback.onSandboxStart();
                } catch (RemoteException ignore) {
                    // App died.
                }
            }
            mSyncDataCallbacks.remove(callingInfo);
        }
    }

    private void loadSdkForService(
            LoadSdkSession loadSdkSession,
            int timeToLoadSandbox,
            long timeSystemServerReceivedCallFromApp,
            ISdkSandboxService service) {
        CallingInfo callingInfo = loadSdkSession.mCallingInfo;

        if (isSdkSandboxDisabled(service)) {
            Log.e(TAG, "SDK cannot be loaded because SDK sandbox is disabled");
            loadSdkSession.handleLoadFailure(
                    new LoadSdkException(LOAD_SDK_SDK_SANDBOX_DISABLED, SANDBOX_DISABLED_MSG),
                    -1,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__METHOD_UNSPECIFIED,
                    false);
            return;
        }
        // Gather sdk storage information
        final StorageDirInfo sdkDataInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(
                        callingInfo, loadSdkSession.mSdkProviderInfo.getSdkInfo().getName());

        final long timeSystemServerCalledSandbox = mInjector.getCurrentTime();
        int latencySystemServerAppToSandbox =
                (int) (timeSystemServerCalledSandbox - timeSystemServerReceivedCallFromApp);
        if (timeToLoadSandbox != -1) {
            latencySystemServerAppToSandbox -= timeToLoadSandbox;
        }

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                latencySystemServerAppToSandbox,
                /*success=*/ true,
                SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                callingInfo.getUid());

        loadSdkSession.load(
                service,
                sdkDataInfo.getCeDataDir(),
                sdkDataInfo.getDeDataDir(),
                timeSystemServerCalledSandbox,
                timeSystemServerReceivedCallFromApp);
    }

    private void failStartOrBindService(Intent intent) {
        throw new SecurityException(
                "SDK sandbox uid may not bind to or start to this service: " + intent.toString());
    }

    private void enforceAllowedToStartOrBindService(Intent intent) {
        ComponentName component = intent.getComponent();
        if (component == null) {
            failStartOrBindService(intent);
        }
        String componentPackageName = component.getPackageName();
        if (componentPackageName == null) {
            failStartOrBindService(intent);
        }
        if (componentPackageName.equals(WebViewUpdateService.getCurrentWebViewPackageName())
                || componentPackageName.equals(getAdServicesPackageName())) {
            return;
        }

        String dynamicPackageNamesAllowlist =
                mSdkSandboxSettingsListener.getServiceBindPackageNamesAllowlist();
        if (dynamicPackageNamesAllowlist == null
                || !dynamicPackageNamesAllowlist.contains(componentPackageName)) {
            failStartOrBindService(intent);
        }

        if (componentPackageName.startsWith(GMS_PACKAGENAME_PREFIX)) {
            String action = intent.getAction();
            if (action == null) {
                failStartOrBindService(intent);
            }
            String dynamicActionAllowlist =
                    mSdkSandboxSettingsListener.getServiceBindActionsAllowlist();
            if (dynamicActionAllowlist == null || !dynamicActionAllowlist.contains(action)) {
                failStartOrBindService(intent);
            }
        }
    }

    @Override
    public int handleShellCommand(ParcelFileDescriptor in, ParcelFileDescriptor out,
            ParcelFileDescriptor err, String[] args) {
        return mInjector
                .createShellCommand(this, mContext)
                .exec(
                        this,
                        in.getFileDescriptor(),
                        out.getFileDescriptor(),
                        err.getFileDescriptor(),
                        args);
    }

    private String resolveAdServicesPackage() {
        PackageManager pm = mContext.getPackageManager();
        Intent serviceIntent = new Intent(AdServicesCommon.ACTION_TOPICS_SERVICE);
        List<ResolveInfo> resolveInfos =
                pm.queryIntentServicesAsUser(
                        serviceIntent,
                        PackageManager.GET_SERVICES
                                | PackageManager.MATCH_SYSTEM_ONLY
                                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                        UserHandle.SYSTEM);
        if (resolveInfos == null || resolveInfos.size() == 0) {
            Log.e(TAG, "AdServices package could not be resolved");
        } else if (resolveInfos.size() > 1) {
            Log.e(TAG, "More than one service matched intent " + serviceIntent.getAction());
        } else {
            return resolveInfos.get(0).serviceInfo.packageName;
        }
        return null;
    }

    @VisibleForTesting
    String getAdServicesPackageName() {
        return mAdServicesPackageName;
    }

    /**
     * A callback object to establish a link between the sdk in sandbox calling into manager
     * service.
     *
     * <p>When a sandbox is initialized, a callback object of {@link SdkToServiceLink} is passed to
     * be used as a part of {@link SdkSandboxController}. The Controller can then can call APIs on
     * the link object to get data from the manager service.
     */
    private class SdkToServiceLink extends ISdkToServiceCallback.Stub {

        /**
         * Fetches {@link SandboxedSdk} for all SDKs that are loaded in the sandbox.
         *
         * <p>This provides the information on the library that is currently loaded in the sandbox
         * and also channels to communicate with loaded SDK.
         *
         * @param clientPackageName package name of the app for which the sdk was loaded in the
         *     sandbox
         * @return List of {@link SandboxedSdk} containing all currently loaded sdks
         */
        @Override
        public List<SandboxedSdk> getSandboxedSdks(String clientPackageName)
                throws RemoteException {
            // TODO(b/258195148): Write multiuser tests
            // TODO(b/242039497): Add authorisation checks to make sure only the sandbox calls this
            //  API.
            int uid = Binder.getCallingUid();
            if (Process.isSdkSandboxUid(uid)) {
                uid = Process.getAppUidForSdkSandboxUid(uid);
            }
            CallingInfo callingInfo = new CallingInfo(uid, clientPackageName);
            final List<SandboxedSdk> sandboxedSdks = new ArrayList<>();
            synchronized (mLock) {
                List<LoadSdkSession> loadedSdks = getLoadedSdksForApp(callingInfo);
                for (int i = 0; i < loadedSdks.size(); i++) {
                    LoadSdkSession sdk = loadedSdks.get(i);
                    SandboxedSdk sandboxedSdk = sdk.getSandboxedSdk();
                    if (sandboxedSdk != null) {
                        sandboxedSdks.add(sandboxedSdk);
                    } else {
                        Log.e(
                                TAG,
                                "SandboxedSdk is null for SDK "
                                        + sdk.mSdkName
                                        + " despite being loaded");
                    }
                }
            }
            return sandboxedSdks;
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxManagerLocal getLocalManager() {
        return mLocalManager;
    }

    private void notifyInstrumentationStarted(CallingInfo callingInfo) {
        Log.d(TAG, "notifyInstrumentationStarted: clientApp = " + callingInfo.getPackageName()
                + " clientAppUid = " + callingInfo.getUid());
        synchronized (mLock) {
            mServiceProvider.unbindService(callingInfo, true);
            int sdkSandboxUid = Process.toSdkSandboxUid(callingInfo.getUid());
            mActivityManager.killUid(sdkSandboxUid, "instrumentation started");
            mRunningInstrumentations.add(callingInfo);
        }
        // TODO(b/223386213): we need to check if there is reconcileSdkData task already enqueued
        //  because the instrumented client app was just installed.
        mSdkSandboxStorageManager.notifyInstrumentationStarted(callingInfo);
    }

    private void notifyInstrumentationFinished(CallingInfo callingInfo) {
        Log.d(TAG, "notifyInstrumentationFinished: clientApp = " + callingInfo.getPackageName()
                + " clientAppUid = " + callingInfo.getUid());
        synchronized (mLock) {
            mRunningInstrumentations.remove(callingInfo);
        }
    }

    private boolean isInstrumentationRunning(CallingInfo callingInfo) {
        synchronized (mLock) {
            return mRunningInstrumentations.contains(callingInfo);
        }
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        SdkSandboxManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            SdkSandboxServiceProvider provider = new SdkSandboxServiceProviderImpl(getContext());
            mService = new SdkSandboxManagerService(getContext(), provider);
        }

        @Override
        public void onStart() {
            publishBinderService(SDK_SANDBOX_SERVICE, mService);
            LocalManagerRegistry.addManager(
                    SdkSandboxManagerLocal.class, mService.getLocalManager());
        }

        @Override
        public void onUserUnlocking(TargetUser user) {
            final int userId = user.getUserHandle().getIdentifier();
            mService.onUserUnlocking(userId);
        }
    }

    private class UidImportanceListener implements ActivityManager.OnUidImportanceListener {

        private static final int IMPORTANCE_CUTPOINT = IMPORTANCE_VISIBLE;

        public boolean isListening = false;

        public void startListening() {
            synchronized (mLock) {
                if (isListening) {
                    return;
                }
                mActivityManager.addOnUidImportanceListener(this, IMPORTANCE_CUTPOINT);
                isListening = true;
            }
        }

        public void stopListening() {
            synchronized (mLock) {
                if (!isListening) {
                    return;
                }
                mActivityManager.removeOnUidImportanceListener(this);
                isListening = false;
            }
        }

        @Override
        public void onUidImportance(int uid, int importance) {
            if (importance <= IMPORTANCE_CUTPOINT) {
                // The lower the importance value, the more "important" the process is. We
                // are only interested when the process is no longer in the foreground.
                return;
            }
            synchronized (mLock) {
                for (int i = 0; i < mCallingInfosWithDeathRecipients.size(); i++) {
                    final CallingInfo callingInfo = mCallingInfosWithDeathRecipients.keyAt(i);
                    if (callingInfo.getUid() == uid) {
                        LogUtil.d(
                                TAG,
                                "App with uid "
                                        + uid
                                        + " has gone to the background, unbinding sandbox");
                        // Unbind the sandbox when the app goes to the background to lower its
                        // priority.
                        mServiceProvider.unbindService(callingInfo, false);
                    }
                }
            }
        }
    }

    private class LocalImpl implements SdkSandboxManagerLocal {

        @NonNull
        @Override
        public String getSdkSandboxProcessNameForInstrumentation(
                @NonNull ApplicationInfo clientAppInfo) {
            return clientAppInfo.processName + "_sdk_sandbox_instr";
        }

        @Override
        public void notifyInstrumentationStarted(
                @NonNull String clientAppPackageName, int clientAppUid) {
            SdkSandboxManagerService.this.notifyInstrumentationStarted(
                    new CallingInfo(clientAppUid, clientAppPackageName));
        }

        @Override
        public void notifyInstrumentationFinished(
                @NonNull String clientAppPackageName, int clientAppUid) {
            SdkSandboxManagerService.this.notifyInstrumentationFinished(
                    new CallingInfo(clientAppUid, clientAppPackageName));
        }

        @Override
        public boolean isInstrumentationRunning(
                @NonNull String clientAppPackageName, int clientAppUid) {
            return SdkSandboxManagerService.this.isInstrumentationRunning(
                    new CallingInfo(clientAppUid, clientAppPackageName));
        }

        @Override
        public void enforceAllowedToSendBroadcast(@NonNull Intent intent) {
            if (intent.getAction() != null) {
                throw new SecurityException(
                        "Intent "
                                + intent.getAction()
                                + " may not be broadcast from an SDK sandbox uid");
            }
        }

        @Override
        public void enforceAllowedToStartActivity(@NonNull Intent intent) {
            if (intent.getAction() != null) {
                if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
                    throw new SecurityException(
                            "Intent "
                                    + intent.getAction()
                                    + " may not be broadcast from an SDK sandbox uid.");
                }

                if (intent.getPackage() != null || intent.getComponent() != null) {
                    throw new SecurityException(
                            "Intent "
                                    + intent.getAction()
                                    + " broadcast from an SDK sandbox uid may not specify a"
                                    + " package name or component.");
                }
            }
        }

        @Override
        public void enforceAllowedToStartOrBindService(@NonNull Intent intent) {
            SdkSandboxManagerService.this.enforceAllowedToStartOrBindService(intent);
        }
    }
}
