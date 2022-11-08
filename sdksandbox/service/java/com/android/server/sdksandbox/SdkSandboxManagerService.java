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
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP;
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
import android.content.pm.SharedLibraryInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.DeadObjectException;
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
import android.util.Pair;
import android.view.SurfaceControlViewHost;
import android.webkit.WebViewUpdateService;

import com.android.adservices.AdServicesCommon;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.sdksandbox.ILoadSdkInSandboxCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxDisabledCallback;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.IUnloadSdkCallback;
import com.android.sdksandbox.SandboxLatencyInfo;
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
    private static final String PROPERTY_SDK_PROVIDER_CLASS_NAME =
            "android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME";
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

    // For communication between app<-ManagerService->RemoteSdk for each {CallingInfo, SDK name}
    @GuardedBy("mLock")
    private final ArrayMap<Pair<CallingInfo, String>, AppAndRemoteSdkLink> mAppAndRemoteSdkLinks =
            new ArrayMap<>();

    // Denotes which SDKs are currently in the process of being loaded
    @GuardedBy("mLock")
    private final ArraySet<Pair<CallingInfo, String>> mSdksBeingLoaded = new ArraySet<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, IBinder> mCallingInfosWithDeathRecipients =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private final Set<CallingInfo> mRunningInstrumentations = new ArraySet<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, RemoteCallbackList<ISdkSandboxProcessDeathCallback>>
            mSandboxLifecycleCallbacks = new ArrayMap<>();

    // Keeps track of all callbacks created by the app that have not yet been invoked, to call back
    // in case the sandbox dies.
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ArrayMap<IBinder, Runnable>> mPendingCallbacks =
            new ArrayMap<>();

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
                // TODO(b/223386213): We could miss broadcast or app might be started before we
                // handle broadcast.
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
            for (int i = mAppAndRemoteSdkLinks.size() - 1; i >= 0; i--) {
                AppAndRemoteSdkLink link = mAppAndRemoteSdkLinks.valueAt(i);
                if (link.mCallingInfo.equals(callingInfo) && link.mSandboxedSdk != null) {
                    sandboxedSdks.add(link.mSandboxedSdk);
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
        // Fetch the installed SDK in device
        SdkProviderInfo sdkProviderInfo =
                createSdkProviderInfo(sdkName, callingInfo.getPackageName());

        String errorMsg = "";
        if (sdkProviderInfo == null) {
            errorMsg = sdkName + " not found for loading";
        } else if (TextUtils.isEmpty(sdkProviderInfo.getSdkProviderClassName())) {
            errorMsg = sdkName + " did not set " + PROPERTY_SDK_PROVIDER_CLASS_NAME;
        }

        final AppAndRemoteSdkLink link =
                new AppAndRemoteSdkLink(callingInfo, sdkName, callback, sdkProviderInfo);
        if (!TextUtils.isEmpty(errorMsg)) {
            Log.w(TAG, errorMsg);
            link.handleLoadSdkException(
                    new LoadSdkException(SdkSandboxManager.LOAD_SDK_NOT_FOUND, errorMsg),
                    /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    /*successAtStage=*/ false);
            return;
        }

        // Ensure we are not already loading this sdk. That's determined by checking if we already
        // have an AppAndRemoteSdkLink for the calling info and sdk name.
        synchronized (mLock) {
            final Pair<CallingInfo, String> appAndSdkInfo = Pair.create(callingInfo, sdkName);
            if (mAppAndRemoteSdkLinks.containsKey(appAndSdkInfo)) {
                link.handleLoadSdkException(
                        new LoadSdkException(
                                SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                                sdkName + " has been loaded already"),
                        /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                        SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                        /*successAtStage=*/ false);
                return;
            }
            if (mSdksBeingLoaded.contains(appAndSdkInfo)) {
                link.handleLoadSdkException(
                        new LoadSdkException(
                                SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                                sdkName + " is currently being loaded"),
                        /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                        SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                        /*successAtStage=*/ false);
                return;
            } else {
                mSdksBeingLoaded.add(appAndSdkInfo);
            }
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

        invokeSdkSandboxServiceToLoadSdk(
                callingInfo, sdkProviderInfo, params, link, timeSystemServerReceivedCallFromApp);
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
            final Pair<CallingInfo, String> appAndSdkInfo = Pair.create(callingInfo, sdkName);
            synchronized (mLock) {
                // TODO(b/254657226): Add a callback or return value for unloadSdk() to indicate
                // success of unload.
                if (mSdksBeingLoaded.contains(appAndSdkInfo)) {
                    throw new IllegalArgumentException(
                            "SDK "
                                    + sdkName
                                    + " is currently being loaded for "
                                    + callingInfo
                                    + " - wait till onLoadSdkSuccess() to unload");
                }
                if (mAppAndRemoteSdkLinks.get(appAndSdkInfo) == null) {
                    // Unloading SDK that is not loaded is a no-op, return.
                    Log.i(TAG, "SDK " + sdkName + " is not loaded for " + callingInfo);
                    return;
                }
            }
            unloadSdkWithClearIdentity(callingInfo, sdkName, timeSystemServerReceivedCallFromApp);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void unloadSdkWithClearIdentity(
            CallingInfo callingInfo, String sdkName, long timeSystemServerReceivedCallFromApp) {
        final UnloadSdkRemoveLinksToSdkResponse response =
                removeLinksToSdk(callingInfo, sdkName, timeSystemServerReceivedCallFromApp);
        if (response.shouldStopSandbox()) {
            stopSdkSandboxService(
                    callingInfo, "Caller " + callingInfo + " has no remaining SDKS loaded.");
        }
        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                (int)
                        (mInjector.getCurrentTime()
                                - response.getTimeSystemServerReceivedCallFromSandbox()),
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
            mPendingCallbacks.remove(callingInfo);
            mCallingInfosWithDeathRecipients.remove(callingInfo);
            if (mCallingInfosWithDeathRecipients.size() == 0) {
                mUidImportanceListener.stopListening();
            }
            mSyncDataCallbacks.remove(callingInfo);
            removeAllSdkLinks(callingInfo);
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
        final Pair<CallingInfo, String> appAndSdkInfo = Pair.create(callingInfo, sdkName);
        final AppAndRemoteSdkLink link;
        synchronized (mLock) {
            link = mAppAndRemoteSdkLinks.get(appAndSdkInfo);
        }
        if (link == null) {
            LogUtil.d(
                    TAG,
                    callingInfo + " requested surface package, but could not find SDK " + sdkName);
            handleSurfacePackageError(
                    callingInfo,
                    REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                    "SDK " + sdkName + " is not loaded",
                    timeSystemServerReceivedCallFromApp,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    /*successAtStage*/ false,
                    callback);
            return;
        }

        link.requestSurfacePackageFromSdk(
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
            writer.println("mAppAndRemoteSdkLinks size: " + mAppAndRemoteSdkLinks.size());
            for (Pair<CallingInfo, String> pair : mAppAndRemoteSdkLinks.keySet()) {
                writer.printf("caller: %s, sdkName: %s", pair.first, pair.second);
                writer.println();
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
        void onBindingSuccessful(ISdkSandboxService service);

        void onBindingFailed();
    }

    class SandboxServiceConnection implements ServiceConnection {

        private final SdkSandboxServiceProvider mServiceProvider;
        private final CallingInfo mCallingInfo;
        private boolean mServiceBound = false;

        private final SandboxBindingCallback mCallback;

        SandboxServiceConnection(
                SdkSandboxServiceProvider serviceProvider,
                CallingInfo callingInfo,
                SandboxBindingCallback callback) {
            mServiceProvider = serviceProvider;
            mCallingInfo = callingInfo;
            mCallback = callback;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final ISdkSandboxService mService =
                    ISdkSandboxService.Stub.asInterface(service);
            Log.d(
                    TAG,
                    String.format(
                            "Sdk sandbox has been bound for app package %s with uid %d",
                            mCallingInfo.getPackageName(), mCallingInfo.getUid()));
            mServiceProvider.setBoundServiceForApp(mCallingInfo, mService);

            try {
                service.linkToDeath(() -> removeAllSdkLinks(mCallingInfo), 0);
            } catch (RemoteException re) {
                // Sandbox had already died, cleanup sdk links.
                removeAllSdkLinks(mCallingInfo);
            }

            if (!mServiceBound) {
                mCallback.onBindingSuccessful(mService);
                mServiceBound = true;
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
            mCallback.onBindingFailed();
        }
    }

    void startSdkSandbox(CallingInfo callingInfo, SandboxBindingCallback callback) {
        mServiceProvider.bindService(
                callingInfo, new SandboxServiceConnection(mServiceProvider, callingInfo, callback));
    }

    private void invokeSdkSandboxServiceToLoadSdk(
            CallingInfo callingInfo,
            SdkProviderInfo info,
            Bundle params,
            AppAndRemoteSdkLink link,
            long timeSystemServerReceivedCallFromApp) {
        // check first if service already bound
        ISdkSandboxService service = mServiceProvider.getBoundServiceForApp(callingInfo);
        if (service != null) {
            loadSdkForService(
                    callingInfo,
                    info,
                    params,
                    link,
                    /*timeToLoadSandbox=*/ -1,
                    timeSystemServerReceivedCallFromApp,
                    service);
            return;
        }

        final long startTimeForLoadingSandbox = mInjector.getCurrentTime();
        startSdkSandbox(
                callingInfo,
                new SandboxBindingCallback() {
                    @Override
                    public void onBindingSuccessful(ISdkSandboxService service) {
                        try {
                            service.asBinder().linkToDeath(() -> onSdkSandboxDeath(callingInfo), 0);
                        } catch (RemoteException re) {
                            link.handleLoadSdkException(
                                    new LoadSdkException(
                                            SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                                            SANDBOX_NOT_AVAILABLE_MSG),
                                    /*startTimeOfErrorStage=*/ startTimeForLoadingSandbox,
                                    SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                    /*successAtStage=*/ false);
                            onSdkSandboxDeath(callingInfo);
                            return;
                        }
                        final int timeToLoadSandbox =
                                (int) (mInjector.getCurrentTime() - startTimeForLoadingSandbox);
                        // Log the latency for loading the Sandbox process
                        SdkSandboxStatsLog.write(
                                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                                timeToLoadSandbox,
                                /* success=*/ true,
                                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                callingInfo.getUid());

                        try {
                            onSandboxStart(callingInfo, service);
                        } catch (RemoteException e) {
                            final String errorMsg = "Failed to initialize sandbox";
                            Log.e(TAG, errorMsg);
                            link.handleLoadSdkException(
                                    new LoadSdkException(
                                            SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                                            SANDBOX_NOT_AVAILABLE_MSG + " : " + errorMsg,
                                            e),
                                    /*startTimeOfErrorStage=*/ startTimeForLoadingSandbox,
                                    SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                    /*successAtStage=*/ false);
                            return;
                        }

                        loadSdkForService(
                                callingInfo,
                                info,
                                params,
                                link,
                                timeToLoadSandbox,
                                timeSystemServerReceivedCallFromApp,
                                service);
                    }

                    @Override
                    public void onBindingFailed() {
                        link.handleLoadSdkException(
                                new LoadSdkException(
                                        SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                                        "Failed to bind the service"),
                                /*startTimeOfErrorStage=*/ startTimeForLoadingSandbox,
                                /*stage*/ SdkSandboxStatsLog
                                        .SANDBOX_API_CALLED__STAGE__LOAD_SANDBOX,
                                /*successAtStage=*/ false);
                    }
                });
    }

    private void onSdkSandboxDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            notifyPendingCallbacksLocked(callingInfo);
            handleSandboxLifecycleCallbacksLocked(callingInfo);
            removeSdksBeingLoadedLocked(callingInfo);
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

    @GuardedBy("mLock")
    void notifyPendingCallbacksLocked(CallingInfo callingInfo) {
        if (!mPendingCallbacks.containsKey(callingInfo)) {
            return;
        }
        for (Runnable callbackErrors : mPendingCallbacks.get(callingInfo).values()) {
            callbackErrors.run();
        }
        mPendingCallbacks.remove(callingInfo);
    }

    @GuardedBy("mLock")
    private void removeSdksBeingLoadedLocked(CallingInfo callingInfo) {
        mSdksBeingLoaded.removeIf(it -> it.first.equals(callingInfo));
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
            getSdkSandboxSettingsListener().reset();
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
            getSdkSandboxSettingsListener().reset();
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxSettingsListener getSdkSandboxSettingsListener() {
        synchronized (mLock) {
            return mSdkSandboxSettingsListener;
        }
    }

    class SdkSandboxSettingsListener implements DeviceConfig.OnPropertiesChangedListener {

        private final Context mContext;
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private boolean mIsKillSwitchEnabled =
                DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_DISABLE_SDK_SANDBOX, false);

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
                return mIsKillSwitchEnabled;
            }
        }

        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        void reset() {
            synchronized (mLock) {
                DeviceConfig.setProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_DISABLE_SDK_SANDBOX,
                        "false",
                        false);
                mIsKillSwitchEnabled = false;
            }
        }

        @Override
        public void onPropertiesChanged(@NonNull DeviceConfig.Properties properties) {
            synchronized (mLock) {
                boolean killSwitchPreviouslyEnabled = mIsKillSwitchEnabled;
                mIsKillSwitchEnabled = properties.getBoolean(PROPERTY_DISABLE_SDK_SANDBOX, false);
                if (mIsKillSwitchEnabled && !killSwitchPreviouslyEnabled) {
                    synchronized (SdkSandboxManagerService.this.mLock) {
                        stopAllSandboxesLocked();
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
        for (int i = mAppAndRemoteSdkLinks.size() - 1; i >= 0; --i) {
            stopSdkSandboxService(
                    mAppAndRemoteSdkLinks.keyAt(i).first, "SDK sandbox killswitch enabled");
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

    private void onSandboxStart(CallingInfo callingInfo, ISdkSandboxService service)
            throws RemoteException {
        mSdkSandboxStorageManager.prepareSdkDataOnLoad(callingInfo);

        service.initialize(new SdkToServiceLink());

        notifySyncManagerSandboxStarted(callingInfo);
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
            CallingInfo callingInfo,
            SdkProviderInfo sdkProviderInfo,
            Bundle params,
            AppAndRemoteSdkLink link,
            int timeToLoadSandbox,
            long timeSystemServerReceivedCallFromApp,
            ISdkSandboxService service) {

        if (isSdkSandboxDisabled(service)) {
            link.handleLoadSdkException(
                    new LoadSdkException(LOAD_SDK_SDK_SANDBOX_DISABLED, SANDBOX_DISABLED_MSG),
                    -1,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__METHOD_UNSPECIFIED,
                    false);
            return;
        }
        // Gather sdk storage information
        final StorageDirInfo sdkDataInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(
                        callingInfo, sdkProviderInfo.getSdkInfo().getName());

        synchronized (mLock) {
            mPendingCallbacks.computeIfAbsent(callingInfo, k -> new ArrayMap<>());
            mPendingCallbacks
                    .get(callingInfo)
                    .put(
                            link.asBinder(),
                            () ->
                                    link.handleLoadSdkException(
                                            new LoadSdkException(
                                                    SDK_SANDBOX_PROCESS_NOT_AVAILABLE,
                                                    SANDBOX_NOT_AVAILABLE_MSG),
                                            /*startTimeOfErrorStage=*/ -1,
                                            SdkSandboxStatsLog
                                                    .SANDBOX_API_CALLED__METHOD__METHOD_UNSPECIFIED,
                                            /*successAtStage=*/ false));
        }

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

        final SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(timeSystemServerCalledSandbox);

        try {
            service.loadSdk(
                    callingInfo.getPackageName(),
                    sdkProviderInfo.getApplicationInfo(),
                    sdkProviderInfo.getSdkInfo().getName(),
                    sdkProviderInfo.getSdkProviderClassName(),
                    sdkDataInfo.getCeDataDir(),
                    sdkDataInfo.getDeDataDir(),
                    params,
                    link,
                    sandboxLatencyInfo);
        } catch (DeadObjectException e) {
            link.handleLoadSdkException(
                    new LoadSdkException(
                            SDK_SANDBOX_PROCESS_NOT_AVAILABLE, SANDBOX_NOT_AVAILABLE_MSG),
                    /*startTimeOfErrorStage=*/ -1,
                    SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED,
                    /*successAtStage=*/ false);
        } catch (RemoteException e) {
            String errorMsg = "Failed to load sdk";
            Log.w(TAG, errorMsg, e);
            link.handleLoadSdkException(
                    new LoadSdkException(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR, errorMsg),
                    /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromApp,
                    /*stage*/ SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    /*successAtStage=*/ false);
        }
    }

    /** Clean up all internal data structures related to {@code callingInfo} of the app */
    private void removeAllSdkLinks(CallingInfo callingInfo) {
        removeLinksToSdk(callingInfo, null, /*timeSystemServerReceivedCallFromApp=*/ -1);
    }

    /**
     * Removes {@link AppAndRemoteSdkLink} objects associated with the {@code callingInfo}. If
     * {@code sdkName} is specified, only the object associated with that SDK name will be removed.
     * Otherwise, all objects for the caller will be removed.
     *
     * <p>Returns {@link UnloadSdkRemoveLinksToSdkResponse} object with {@code
     * timeSystemServerReceivedCallFromSandbox} for logging the latency in system server after
     * calling sandbox and {@code shouldStopSandbox} to determine whether to stop the sandbox
     */
    private UnloadSdkRemoveLinksToSdkResponse removeLinksToSdk(
            CallingInfo callingInfo,
            @Nullable String sdkName,
            long timeSystemServerReceivedCallFromApp) {
        final UnloadSdkRemoveLinksToSdkResponse response = new UnloadSdkRemoveLinksToSdkResponse();
        synchronized (mLock) {
            ISdkSandboxService boundSandbox = mServiceProvider.getBoundServiceForApp(callingInfo);
            ArrayList<Pair<CallingInfo, String>> linksToDelete = new ArrayList<>();
            for (int i = 0; i < mAppAndRemoteSdkLinks.size(); i++) {
                Pair<CallingInfo, String> appAndSdkInfo = mAppAndRemoteSdkLinks.keyAt(i);
                if (appAndSdkInfo.first.equals(callingInfo)) {
                    String curSdkName = appAndSdkInfo.second;
                    if (TextUtils.isEmpty(sdkName) || curSdkName.equals(sdkName)) {
                        if (boundSandbox != null) {
                            SandboxLatencyInfo sandboxLatencyInfo =
                                    new SandboxLatencyInfo(mInjector.getCurrentTime());
                            /**
                             * This value will be -1 if called as a part of cleanup in the event of
                             * app death and therefore no need to log latency
                             */
                            if (timeSystemServerReceivedCallFromApp != -1) {
                                SdkSandboxStatsLog.write(
                                        SdkSandboxStatsLog.SANDBOX_API_CALLED,
                                        SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                        (int)
                                                (sandboxLatencyInfo
                                                                .getTimeSystemServerCalledSandbox()
                                                        - timeSystemServerReceivedCallFromApp),
                                        /*success=*/ true,
                                        SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                                        callingInfo.getUid());
                            }
                            try {
                                boundSandbox.unloadSdk(
                                        curSdkName,
                                        new IUnloadSdkCallback.Stub() {
                                            @Override
                                            public void onUnloadSdk(
                                                    SandboxLatencyInfo sandboxLatencyInfo) {
                                                logLatencyMetricsForCallback(
                                                        callingInfo,
                                                        /*timeSystemServerReceivedCallFromSandbox=*/
                                                        mInjector.getCurrentTime(),
                                                        SANDBOX_API_CALLED__METHOD__UNLOAD_SDK,
                                                        sandboxLatencyInfo);
                                            }
                                        },
                                        sandboxLatencyInfo);
                            } catch (DeadObjectException e) {
                                Log.i(TAG, "Sdk sandbox for " + callingInfo
                                        + " is dead, cannot unload SDK " + sdkName);
                                response.setShouldStopSandbox(false);
                                return response;
                            } catch (RemoteException e) {
                                Log.w(TAG, "Failed to unload SDK: ", e);
                            }
                        }
                        response.setTimeSystemServerReceivedCallFromSandbox(
                                mInjector.getCurrentTime());
                        linksToDelete.add(appAndSdkInfo);
                    } else {
                        response.setShouldStopSandbox(false);
                    }
                }
            }

            for (Pair<CallingInfo, String> appAndSdkInfo : linksToDelete) {
                mAppAndRemoteSdkLinks.remove(appAndSdkInfo);
            }
            return response;
        }
    }

    private void enforceAllowedToStartOrBindService(Intent intent) {
        ComponentName component = intent.getComponent();
        String errorMsg = "SDK sandbox uid may not bind to or start a service: ";
        if (component == null) {
            throw new SecurityException(errorMsg + "intent component must be non-null.");
        }
        String componentPackageName = component.getPackageName();
        if (componentPackageName != null) {
            if (!componentPackageName.equals(WebViewUpdateService.getCurrentWebViewPackageName())
                    && !componentPackageName.equals(getAdServicesPackageName())) {
                throw new SecurityException(errorMsg + "component package name "
                        + componentPackageName + " is not allowlisted.");
            }
        } else {
            throw new SecurityException(errorMsg
                    + "the intent's component package name must be non-null.");
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

    private SdkProviderInfo createSdkProviderInfo(
            String sharedLibraryName, String callingPackageName) {
        try {
            PackageManager pm = mContext.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(
                    callingPackageName, PackageManager.GET_SHARED_LIBRARY_FILES);
            List<SharedLibraryInfo> sharedLibraries = info.getSharedLibraryInfos();
            for (int j = 0; j < sharedLibraries.size(); j++) {
                SharedLibraryInfo sharedLibrary = sharedLibraries.get(j);
                if (sharedLibrary.getType() != SharedLibraryInfo.TYPE_SDK_PACKAGE) {
                    continue;
                }

                if (!sharedLibraryName.equals(sharedLibrary.getName())) {
                    continue;
                }

                String sdkProviderClassName = pm.getProperty(PROPERTY_SDK_PROVIDER_CLASS_NAME,
                        sharedLibrary.getDeclaringPackage().getPackageName()).getString();
                ApplicationInfo applicationInfo =
                        pm.getPackageInfo(
                                        sharedLibrary.getDeclaringPackage(),
                                        PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES)
                                .applicationInfo;
                return new SdkProviderInfo(applicationInfo, sharedLibrary, sdkProviderClassName);
            }
            return null;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
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

    private static class UnloadSdkRemoveLinksToSdkResponse {
        private long mTimeSystemServerReceivedCallFromSandbox;
        private boolean mShouldStopSandbox = true;

        public long getTimeSystemServerReceivedCallFromSandbox() {
            return mTimeSystemServerReceivedCallFromSandbox;
        }

        public void setTimeSystemServerReceivedCallFromSandbox(
                long timeSystemServerReceivedCallFromSandbox) {
            mTimeSystemServerReceivedCallFromSandbox = timeSystemServerReceivedCallFromSandbox;
        }

        public boolean shouldStopSandbox() {
            return mShouldStopSandbox;
        }

        public void setShouldStopSandbox(boolean shouldStopSandbox) {
            mShouldStopSandbox = shouldStopSandbox;
        }
    }

    @GuardedBy("mLock")
    private void removePendingCallbackLocked(CallingInfo callingInfo, IBinder callbackBinder) {
        synchronized (mLock) {
            if (mPendingCallbacks.containsKey(callingInfo)) {
                mPendingCallbacks.get(callingInfo).remove(callbackBinder);
            }
        }
    }

    void handleSurfacePackageError(
            CallingInfo callingInfo,
            int errorCode,
            String errorMsg,
            long startTimeOfStageWhereErrorOccurred,
            int stage,
            boolean successAtStage,
            IRequestSurfacePackageCallback callback) {
        synchronized (mLock) {
            removePendingCallbackLocked(callingInfo, callback.asBinder());
        }
        final long timeSystemServerCalledApp = mInjector.getCurrentTime();
        if (stage != SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED) {
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                    (int) (timeSystemServerCalledApp - startTimeOfStageWhereErrorOccurred),
                    successAtStage,
                    stage,
                    callingInfo.getUid());
        }
        try {
            callback.onSurfacePackageError(errorCode, errorMsg, timeSystemServerCalledApp);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to send onSurfacePackageError", e);
        }
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
                for (int i = mAppAndRemoteSdkLinks.size() - 1; i >= 0; i--) {
                    AppAndRemoteSdkLink link = mAppAndRemoteSdkLinks.valueAt(i);
                    if (link.mCallingInfo.equals(callingInfo)) {
                        sandboxedSdks.add(link.mSandboxedSdk);
                    }
                }
            }
            return sandboxedSdks;
        }
    }

    /**
     * A callback object to establish a link between the app calling into manager service and the
     * remote SDK being loaded in SdkSandbox.
     *
     * <p>Overview of communication:
     *
     * <ol>
     *   <li>App to ManagerService: App calls into this service via app context.
     *   <li>ManagerService to App: {@link AppAndRemoteSdkLink} holds reference to {@link
     *       ILoadSdkCallback} object which provides a call back into the app for providing the
     *       status of loading an SDK.
     *   <li>RemoteSdk to ManagerService: {@link AppAndRemoteSdkLink} extends {@link
     *       ILoadSdkInSandboxCallback} interface. We pass on this object to {@link
     *       ISdkSandboxService} so that remote SDK can call back into ManagerService.
     *   <li>ManagerService to RemoteSdk: When the SDK is loaded for the first time and remote SDK
     *       calls back with successful result, it also sends reference to {@link
     *       ISdkSandboxManagerToSdkSandboxCallback} callback object. ManagerService uses this to
     *       callback into the remote SDK.
     * </ol>
     *
     * <p>We maintain a link for each unique {app, remoteSdk} pair, which is identified with {@code
     * sdkName}.
     */
    private class AppAndRemoteSdkLink extends ILoadSdkInSandboxCallback.Stub {
        private final CallingInfo mCallingInfo;
        private final SdkProviderInfo mSdkProviderInfo;
        private final String mSdkName;
        private final ILoadSdkCallback mManagerToAppCallback;
        private SandboxedSdk mSandboxedSdk;

        @GuardedBy("this")
        private ISdkSandboxManagerToSdkSandboxCallback mManagerToCodeCallback;

        AppAndRemoteSdkLink(
                CallingInfo callingInfo,
                String sdkName,
                ILoadSdkCallback managerToAppCallback,
                SdkProviderInfo sdkProviderInfo) {
            mSdkName = sdkName;
            mSdkProviderInfo = sdkProviderInfo;
            mCallingInfo = callingInfo;
            mManagerToAppCallback = managerToAppCallback;
        }

        @Override
        public void onLoadSdkSuccess(
                SandboxedSdk sandboxedSdk,
                ISdkSandboxManagerToSdkSandboxCallback callback,
                SandboxLatencyInfo sandboxLatencyInfo) {
            final long timeSystemServerReceivedCallFromSandbox = mInjector.getCurrentTime();

            logLatencyMetricsForCallback(
                    mCallingInfo,
                    timeSystemServerReceivedCallFromSandbox,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                    sandboxLatencyInfo);

            synchronized (this) {
                // Keep reference to callback so that manager service can
                // callback to remote code loaded.
                mManagerToCodeCallback = callback;
                // attach the SharedLibraryInfo for the loaded SDK to the sandboxedSdk.
                sandboxedSdk.attachSharedLibraryInfo(mSdkProviderInfo.getSdkInfo());
                // Keep reference to sandboxedSdk so that manager service can
                // keep log of all loaded SDKs and their binders for communication.
                mSandboxedSdk = sandboxedSdk;
            }

            handleLoadSdkSuccess(sandboxedSdk, timeSystemServerReceivedCallFromSandbox);
        }

        @Override
        public void onLoadSdkError(
                LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
            final long timeSystemServerReceivedCallFromSandbox = mInjector.getCurrentTime();

            logLatencyMetricsForCallback(
                    mCallingInfo,
                    timeSystemServerReceivedCallFromSandbox,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                    sandboxLatencyInfo);

            handleLoadSdkException(
                    updateLoadSdkErrorCode(exception),
                    /*startTimeOfErrorStage=*/ timeSystemServerReceivedCallFromSandbox,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                    /*successAtStage=*/ true);
        }

        private void handleLoadSdkSuccess(
                SandboxedSdk sandboxedSdk, long timeSystemServerReceivedCallFromSandbox) {
            final Pair<CallingInfo, String> appAndSdkInfo = Pair.create(mCallingInfo, mSdkName);
            synchronized (mLock) {
                removePendingCallbackLocked(mCallingInfo, this.asBinder());
                // The SDK that was in the state of being loaded has now transitioned to the loaded
                // state. This order of first adding to mAppAndRemoteSdkLinks and then removing from
                // mSdksBeingLoaded should be maintained (or alternatively update them together in
                // one synchronized block) as otherwise when loadSdk() is called, the check for
                // mSdksBeingLoaded could be bypassed as the SDK is no longer being loaded, but
                // appAndSdkInfo might not exist in mAppAndRemoteSdkLinks yet
                mAppAndRemoteSdkLinks.put(appAndSdkInfo, this);
                mSdksBeingLoaded.remove(appAndSdkInfo);
            }
            final long timeSystemServerCalledApp = mInjector.getCurrentTime();
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                    (int) (timeSystemServerCalledApp - timeSystemServerReceivedCallFromSandbox),
                    /*success=*/ true,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                    mCallingInfo.getUid());
            try {
                mManagerToAppCallback.onLoadSdkSuccess(sandboxedSdk, timeSystemServerCalledApp);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onLoadCodeSuccess", e);
            }
        }

        void handleLoadSdkException(
                LoadSdkException exception,
                long startTimeOfErrorStage,
                int stage,
                boolean successAtStage) {
            final Pair<CallingInfo, String> appAndSdkInfo = Pair.create(mCallingInfo, mSdkName);
            synchronized (mLock) {
                removePendingCallbackLocked(mCallingInfo, this.asBinder());
                mSdksBeingLoaded.remove(appAndSdkInfo);
            }

            final long timeSystemServerCalledApp = mInjector.getCurrentTime();
            if (stage != SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED) {
                SdkSandboxStatsLog.write(
                        SdkSandboxStatsLog.SANDBOX_API_CALLED,
                        SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__LOAD_SDK,
                        (int) (timeSystemServerCalledApp - startTimeOfErrorStage),
                        successAtStage,
                        stage,
                        mCallingInfo.getUid());
            }

            try {
                mManagerToAppCallback.onLoadSdkFailure(exception, timeSystemServerCalledApp);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onLoadCodeFailure", e);
            }
        }

        private void handleSurfacePackageReady(
                SurfaceControlViewHost.SurfacePackage surfacePackage,
                int surfacePackageId,
                Bundle params,
                long timeSystemServerReceivedCallFromSandbox,
                IRequestSurfacePackageCallback callback) {
            synchronized (mLock) {
                removePendingCallbackLocked(mCallingInfo, callback.asBinder());
            }
            final long timeSystemServerCalledApp = mInjector.getCurrentTime();
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                    (int) (timeSystemServerCalledApp - timeSystemServerReceivedCallFromSandbox),
                    /*success=*/ true,
                    SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                    mCallingInfo.getUid());
            try {
                callback.onSurfacePackageReady(
                        surfacePackage, surfacePackageId, params, timeSystemServerCalledApp);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageReady callback", e);
            }
        }

        void requestSurfacePackageFromSdk(
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                long timeSystemServerReceivedCallFromApp,
                Bundle params,
                IRequestSurfacePackageCallback callback) {
            synchronized (mLock) {
                mPendingCallbacks.computeIfAbsent(mCallingInfo, k -> new ArrayMap<>());
                mPendingCallbacks
                        .get(mCallingInfo)
                        .put(
                                callback.asBinder(),
                                () ->
                                        handleSurfacePackageError(
                                                mCallingInfo,
                                                REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                                                "SDK " + mSdkName + " is not loaded",
                                                /*timeSystemServerReceivedCallFromSandbox=*/ -1,
                                                SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED,
                                                /*successAtStage=*/ false,
                                                callback));
            }
            final long timeSystemServerCalledSandbox = mInjector.getCurrentTime();
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                    (int) (timeSystemServerCalledSandbox - timeSystemServerReceivedCallFromApp),
                    /*success=*/ true,
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_APP_TO_SANDBOX,
                    mCallingInfo.getUid());
            final SandboxLatencyInfo sandboxLatencyInfo =
                    new SandboxLatencyInfo(timeSystemServerCalledSandbox);
            try {
                synchronized (this) {
                    mManagerToCodeCallback.onSurfacePackageRequested(
                            hostToken,
                            displayId,
                            width,
                            height,
                            params,
                            sandboxLatencyInfo,
                            new IRequestSurfacePackageFromSdkCallback.Stub() {
                                @Override
                                public void onSurfacePackageReady(
                                        SurfaceControlViewHost.SurfacePackage surfacePackage,
                                        int surfacePackageId,
                                        Bundle params,
                                        SandboxLatencyInfo sandboxLatencyInfo) {
                                    final long timeSystemServerReceivedCallFromSandbox =
                                            mInjector.getCurrentTime();

                                    LogUtil.d(TAG, "onSurfacePackageReady received");

                                    logLatencyMetricsForCallback(
                                            mCallingInfo,
                                            timeSystemServerReceivedCallFromSandbox,
                                            SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                            sandboxLatencyInfo);

                                    handleSurfacePackageReady(
                                            surfacePackage,
                                            surfacePackageId,
                                            params,
                                            timeSystemServerReceivedCallFromSandbox,
                                            callback);
                                }

                                @Override
                                public void onSurfacePackageError(
                                        int errorCode,
                                        String errorMsg,
                                        SandboxLatencyInfo sandboxLatencyInfo) {
                                    final long timeSystemServerReceivedCallFromSandbox =
                                            mInjector.getCurrentTime();

                                    logLatencyMetricsForCallback(
                                            mCallingInfo,
                                            timeSystemServerReceivedCallFromSandbox,
                                            SANDBOX_API_CALLED__METHOD__REQUEST_SURFACE_PACKAGE,
                                            sandboxLatencyInfo);

                                    int sdkSandboxManagerErrorCode =
                                            toSdkSandboxManagerRequestSurfacePackageErrorCode(
                                                    errorCode);

                                    handleSurfacePackageError(
                                            mCallingInfo,
                                            sdkSandboxManagerErrorCode,
                                            errorMsg,
                                            timeSystemServerReceivedCallFromSandbox,
                                            SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_SANDBOX_TO_APP,
                                            /*successAtStage=*/ true,
                                            callback);
                                }
                            });
                }
            } catch (DeadObjectException e) {
                LogUtil.d(
                        TAG,
                        mCallingInfo
                                + " requested surface package from SDK "
                                + mSdkName
                                + " but sandbox is not alive");
                handleSurfacePackageError(
                        mCallingInfo,
                        REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                        "SDK " + mSdkName + " is not loaded",
                        /*timeSystemServerReceivedCallFromSandbox=*/ -1,
                        SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED,
                        /*successAtStage=*/ false,
                        callback);
            } catch (RemoteException e) {
                String errorMsg = "Failed to requestSurfacePackage";
                Log.w(TAG, errorMsg, e);
                handleSurfacePackageError(
                        mCallingInfo,
                        SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                        errorMsg + ": " + e,
                        /*timeSystemServerReceivedCallFromSandbox=*/ -1,
                        SANDBOX_API_CALLED__STAGE__STAGE_UNSPECIFIED,
                        /*successAtStage=*/ false,
                        callback);
            }
        }

        private LoadSdkException updateLoadSdkErrorCode(LoadSdkException exception) {
            switch (exception.getLoadSdkErrorCode()) {
                case ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED:
                    return new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                            exception.getMessage(),
                            exception.getCause(),
                            exception.getExtraInformation());
                case ILoadSdkInSandboxCallback.LOAD_SDK_NOT_FOUND:
                    return new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_NOT_FOUND,
                            exception.getMessage(),
                            exception.getCause(),
                            exception.getExtraInformation());
                case ILoadSdkInSandboxCallback.LOAD_SDK_PROVIDER_INIT_ERROR:
                case ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR:
                case ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR:
                    return new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                            exception.getMessage(),
                            exception.getCause(),
                            exception.getExtraInformation());
                case SdkSandboxManager.LOAD_SDK_SDK_DEFINED_ERROR:
                    return exception;
                default:
                    Log.e(
                            TAG,
                            "Error code "
                                    + exception.getLoadSdkErrorCode()
                                    + " has no mapping to the SdkSandboxManager error codes");
                    return new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                            exception.getMessage(),
                            exception.getCause(),
                            exception.getExtraInformation());
            }
        }

        @SdkSandboxManager.RequestSurfacePackageErrorCode
        private int toSdkSandboxManagerRequestSurfacePackageErrorCode(int sdkSandboxErrorCode) {
            if (sdkSandboxErrorCode
                    == IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR) {
                return SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
            }
            Log.e(
                    TAG,
                    "Error code"
                            + sdkSandboxErrorCode
                            + "has no mapping to the SdkSandboxManager error codes");
            return SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
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

    private void logLatencyMetricsForCallback(
            CallingInfo callingInfo,
            long timeSystemServerReceivedCallFromSandbox,
            int method,
            SandboxLatencyInfo sandboxLatencyInfo) {
        final int appUid = callingInfo.getUid();

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                method,
                sandboxLatencyInfo.getLatencySystemServerToSandbox(),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SYSTEM_SERVER_TO_SANDBOX,
                appUid);

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                method,
                sandboxLatencyInfo.getSandboxLatency(),
                sandboxLatencyInfo.isSuccessfulAtSandbox(),
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX,
                appUid);

        final int latencySdk = sandboxLatencyInfo.getSdkLatency();
        if (latencySdk != -1) {
            SdkSandboxStatsLog.write(
                    SdkSandboxStatsLog.SANDBOX_API_CALLED,
                    method,
                    latencySdk,
                    sandboxLatencyInfo.isSuccessfulAtSdk(),
                    SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SDK,
                    appUid);
        }

        SdkSandboxStatsLog.write(
                SdkSandboxStatsLog.SANDBOX_API_CALLED,
                method,
                /*latency=*/ (int)
                        (timeSystemServerReceivedCallFromSandbox
                                - sandboxLatencyInfo.getTimeSandboxCalledSystemServer()),
                /*success=*/ true,
                SdkSandboxStatsLog.SANDBOX_API_CALLED__STAGE__SANDBOX_TO_SYSTEM_SERVER,
                appUid);
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

    /** Class which retrieves and stores the sdkName, sdkProviderClassName, and ApplicationInfo */
    private static class SdkProviderInfo {

        private final ApplicationInfo mApplicationInfo;
        private final SharedLibraryInfo mSdkInfo;
        private final String mSdkProviderClassName;

        private SdkProviderInfo(
                ApplicationInfo applicationInfo,
                SharedLibraryInfo sdkInfo,
                String sdkProviderClassName) {
            mApplicationInfo = applicationInfo;
            mSdkInfo = sdkInfo;
            mSdkProviderClassName = sdkProviderClassName;
        }

        public SharedLibraryInfo getSdkInfo() {
            return mSdkInfo;
        }

        public String getSdkProviderClassName() {
            return mSdkProviderClassName;
        }

        public ApplicationInfo getApplicationInfo() {
            return mApplicationInfo;
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
