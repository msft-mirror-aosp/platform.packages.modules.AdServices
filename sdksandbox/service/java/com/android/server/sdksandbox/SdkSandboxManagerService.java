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

import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_SERVICE;

import static com.android.server.sdksandbox.SdkSandboxStorageManager.SdkDataDirInfo;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.IRequestSurfacePackageCallback;
import android.app.sdksandbox.ISdkSandboxManager;
import android.app.sdksandbox.ISendDataCallback;
import android.app.sdksandbox.SdkSandboxManager;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
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
import com.android.sdksandbox.IDataReceivedCallback;
import com.android.sdksandbox.ILoadSdkInSandboxCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxManagerToSdkSandboxCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of {@link SdkSandboxManager}.
 *
 * @hide
 */
public class SdkSandboxManagerService extends ISdkSandboxManager.Stub {

    private static final String TAG = "SdkSandboxManager";
    private static final String PROPERTY_SDK_PROVIDER_CLASS_NAME =
            "android.sdksandbox.PROPERTY_SDK_PROVIDER_CLASS_NAME";

    private final Context mContext;
    private final SdkTokenManager mSdkTokenManager = new SdkTokenManager();

    private final ActivityManager mActivityManager;
    private final Handler mHandler;
    private final SdkSandboxStorageManager mSdkSandboxStorageManager;
    private final SdkSandboxServiceProvider mServiceProvider;

    private final Object mLock = new Object();

    // TODO(b/239155932): Move to ArrayMaps
    // For communication between app<-ManagerService->RemoteCode for each codeToken
    @GuardedBy("mLock")
    private final Map<IBinder, AppAndRemoteSdkLink> mAppAndRemoteSdkLinks = new ArrayMap<>();

    @GuardedBy("mLock")
    private final Set<CallingInfo> mRunningInstrumentations = new ArraySet<>();

    private final SdkSandboxManagerLocal mLocalManager;

    private final String mAdServicesPackageName;

    SdkSandboxManagerService(Context context, SdkSandboxServiceProvider provider) {
        mContext = context;
        mServiceProvider = provider;
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mSdkSandboxStorageManager = new SdkSandboxStorageManager(mContext);

        // Start the handler thread.
        HandlerThread handlerThread = new HandlerThread("SdkSandboxManagerServiceHandler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        registerBroadcastReceivers();

        mLocalManager = new LocalImpl();
        mAdServicesPackageName = resolveAdServicesPackage();
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
    public List<SharedLibraryInfo> getLoadedSdkLibrariesInfo(String callingPackageName) {
        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);
        List<SharedLibraryInfo> sharedLibraryInfos = new ArrayList<>();
        synchronized (mLock) {
            for (AppAndRemoteSdkLink link : mAppAndRemoteSdkLinks.values()) {
                if (link.mCallingInfo.equals(callingInfo) && link.mSdkProviderInfo != null) {
                    sharedLibraryInfos.add(link.mSdkProviderInfo.mSdkInfo);
                }
            }
            return sharedLibraryInfos;
        }
    }

    @Override
    public void loadSdk(
            String callingPackageName, String sdkName, Bundle params, ILoadSdkCallback callback) {
        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        synchronized (mLock) {
            if (mRunningInstrumentations.contains(callingInfo)) {
                throw new SecurityException(
                        "Currently running instrumentation of this sdk sandbox process");
            }
        }
        enforceCallingPackageBelongsToUid(callingInfo);
        enforceCallerHasNetworkAccess(callingPackageName);

        //TODO(b/232924025): Sdk data should be prepared once per sandbox instantiation
        mSdkSandboxStorageManager.prepareSdkDataOnLoad(callingInfo);
        final long token = Binder.clearCallingIdentity();
        try {
            loadSdkWithClearIdentity(callingInfo, sdkName, params, callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void loadSdkWithClearIdentity(
            CallingInfo callingInfo, String sdkName, Bundle params, ILoadSdkCallback callback) {
        // Step 1: create unique identity for the {callingUid, sdkName} pair
        final IBinder sdkToken = mSdkTokenManager.createOrGetSdkToken(callingInfo, sdkName);

        // Step 2: fetch the installed code in device
        SdkProviderInfo sdkProviderInfo =
                createSdkProviderInfo(sdkName, callingInfo.getPackageName());

        String errorMsg = "";
        if (sdkProviderInfo == null) {
            errorMsg = sdkName + " not found for loading";
        } else if (TextUtils.isEmpty(sdkProviderInfo.getSdkProviderClassName())) {
            errorMsg = sdkName + " did not set " + PROPERTY_SDK_PROVIDER_CLASS_NAME;
        }

        // Ensure we are not already loading sdk for this sdkToken. That's determined by
        // checking if we already have an AppAndRemoteCodeLink for the sdkToken.
        final AppAndRemoteSdkLink link =
                new AppAndRemoteSdkLink(callingInfo, sdkToken, callback, sdkProviderInfo);
        synchronized (mLock) {
            if (mAppAndRemoteSdkLinks.putIfAbsent(sdkToken, link) != null) {
                link.handleLoadSdkError(
                        SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                        sdkName + " is being loaded or has been loaded already",
                        /*cleanUpInternalState=*/ false);
                return;
            }
        }
        if (!TextUtils.isEmpty(errorMsg)) {
            Log.w(TAG, errorMsg);
            link.handleLoadSdkError(
                    SdkSandboxManager.LOAD_SDK_NOT_FOUND, errorMsg, /*cleanUpInternalState=*/ true);
            return;
        }

        // TODO(b/204991850): ensure requested code is included in the AndroidManifest.xml
        invokeSdkSandboxServiceToLoadSdk(callingInfo, sdkToken, sdkProviderInfo, params, link);

        // Register a death recipient to clean up sdkToken and unbind its service after app dies.
        try {
            callback.asBinder().linkToDeath(
                    () -> onAppDeath(sdkToken, callingInfo), 0);
        } catch (RemoteException re) {
            // App has already died, cleanup sdk token and link, and unbind its service
            onAppDeath(sdkToken, callingInfo);
        }
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

    private void enforceCallerHasNetworkAccess(String callingPackage) {
        mContext.enforceCallingPermission(android.Manifest.permission.INTERNET,
                callingPackage + " does not hold INTERNET permission");
        mContext.enforceCallingPermission(android.Manifest.permission.ACCESS_NETWORK_STATE,
                callingPackage + " does not hold ACCESS_NETWORK_STATE permission");
    }

    private void onAppDeath(IBinder sdkToken, CallingInfo callingInfo) {
        cleanUp(sdkToken);
        stopSdkSandboxService(callingInfo, "Caller " + callingInfo + " has died");
    }

    @Override
    public void requestSurfacePackage(
            String callingPackageName,
            String sdkName,
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        final int callingUid = Binder.getCallingUid();
        final long token = Binder.clearCallingIdentity();

        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);
        try {
            final IBinder sdkToken = mSdkTokenManager.getSdkToken(callingInfo, sdkName);
            if (sdkToken == null) {
                throw new SecurityException("Sdk " + sdkName + " is not loaded");
            }
            requestSurfacePackageWithClearIdentity(
                    sdkToken, hostToken, displayId, width, height, params, callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void requestSurfacePackageWithClearIdentity(
            IBinder sdkToken,
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        final AppAndRemoteSdkLink link;
        synchronized (mLock) {
            link = mAppAndRemoteSdkLinks.get(sdkToken);
        }
        link.requestSurfacePackageFromSdk(hostToken, displayId, width, height, params, callback);
    }

    @Override
    public void sendData(
            String callingPackageName, String sdkName, Bundle data, ISendDataCallback callback) {
        final int callingUid = Binder.getCallingUid();
        final long token = Binder.clearCallingIdentity();

        final CallingInfo callingInfo = new CallingInfo(callingUid, callingPackageName);
        enforceCallingPackageBelongsToUid(callingInfo);
        try {
            final IBinder sdkToken = mSdkTokenManager.getSdkToken(callingInfo, sdkName);
            if (sdkToken == null) {
                throw new SecurityException("Sdk " + sdkName + " is not loaded");
            }
            final AppAndRemoteSdkLink link;
            synchronized (mLock) {
                link = mAppAndRemoteSdkLinks.get(sdkToken);
            }
            link.sendDataToSdk(data, callback);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void onUserUnlocking(int userId) {
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
            writer.println("mAppAndRemoteSdkLinks size: " + mAppAndRemoteSdkLinks.size());
        }

        writer.println("mSdkTokenManager:");
        mSdkTokenManager.dump(writer);
        writer.println();

        writer.println("mServiceProvider:");
        mServiceProvider.dump(writer);
        writer.println();
    }

    static class SandboxServiceConnection implements ServiceConnection {

        interface Callback {
            void onBindingSuccessful(ISdkSandboxService service);

            void onBindingFailed();
        }

        private final SdkSandboxServiceProvider mServiceProvider;
        private final CallingInfo mCallingInfo;
        private boolean mServiceBound = false;

        private final Callback mCallback;

        SandboxServiceConnection(
                SdkSandboxServiceProvider serviceProvider,
                CallingInfo callingInfo,
                Callback callback) {
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
            mServiceProvider.setBoundServiceForApp(mCallingInfo, null);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mServiceProvider.setBoundServiceForApp(mCallingInfo, null);
            mServiceProvider.unbindService(mCallingInfo);
            mServiceProvider.bindService(mCallingInfo, this);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            mCallback.onBindingFailed();
        }
    }

    void startSdkSandbox(CallingInfo callingInfo, SandboxServiceConnection.Callback callback) {
        mServiceProvider.bindService(
                callingInfo, new SandboxServiceConnection(mServiceProvider, callingInfo, callback));
    }

    private void invokeSdkSandboxServiceToLoadSdk(CallingInfo callingInfo, IBinder sdkToken,
            SdkProviderInfo info, Bundle params, AppAndRemoteSdkLink link) {
        // check first if service already bound
        ISdkSandboxService service = mServiceProvider.getBoundServiceForApp(callingInfo);
        if (service != null) {
            loadSdkForService(callingInfo, sdkToken, info, params, link, service);
            return;
        }

        startSdkSandbox(
                callingInfo,
                new SandboxServiceConnection.Callback() {
                    @Override
                    public void onBindingSuccessful(ISdkSandboxService service) {
                        try {
                            service.asBinder().linkToDeath(() -> cleanUp(callingInfo), 0);
                        } catch (RemoteException re) {
                            // Sandbox had already died, cleanup sdk tokens and links.
                            cleanUp(callingInfo);
                        }
                        loadSdkForService(callingInfo, sdkToken, info, params, link, service);
                    }

                    @Override
                    public void onBindingFailed() {
                        link.handleLoadSdkError(
                                SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                                "Failed to bind the service",
                                /*cleanUpInternalState=*/ true);
                    }
                });
    }

    void stopSdkSandboxService(CallingInfo callingInfo, String reason) {
        mServiceProvider.unbindService(callingInfo);
        final int sdkSandboxUid = Process.toSdkSandboxUid(callingInfo.getUid());
        Log.i(TAG, "Killing sdk sandbox/s with uid " + sdkSandboxUid);
        // TODO(b/230839879): Avoid killing by uid
        mActivityManager.killUid(sdkSandboxUid, reason);
    }

    boolean isSdkSandboxServiceRunning(CallingInfo callingInfo) {
        return mServiceProvider.getBoundServiceForApp(callingInfo) != null;
    }

    private void loadSdkForService(CallingInfo callingInfo, IBinder sdkToken,
            SdkProviderInfo sdkProviderInfo, Bundle params, AppAndRemoteSdkLink link,
            ISdkSandboxService service) {

        // Gather sdk storage information
        SdkDataDirInfo sdkDataInfo =
                mSdkSandboxStorageManager.getSdkDataDirInfo(
                        callingInfo, sdkProviderInfo.getSdkInfo().getName());
        try {
            service.loadSdk(
                    callingInfo.getPackageName(),
                    sdkToken,
                    sdkProviderInfo.getApplicationInfo(),
                    sdkProviderInfo.getSdkInfo().getName(),
                    sdkProviderInfo.getSdkProviderClassName(),
                    sdkDataInfo.getCeDataDir(),
                    sdkDataInfo.getDeDataDir(),
                    params,
                    link);
        } catch (RemoteException e) {
            String errorMsg = "Failed to load code";
            Log.w(TAG, errorMsg, e);
            link.handleLoadSdkError(SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR, errorMsg,
                    /*cleanupInternalState=*/ true);
        }
    }

    /**
     * Clean up all internal data structures related to {@code sdkToken}
     */
    private void cleanUp(IBinder sdkToken) {
        // Destroy the sdkToken first, to free up the {callingUid, name} pair
        mSdkTokenManager.destroy(sdkToken);
        // Now clean up rest of the state which is using an obsolete sdkToken
        synchronized (mLock) {
            mAppAndRemoteSdkLinks.remove(sdkToken);
        }
    }

    /** Clean up all internal data structures related to {@code callingInfo} of the app */
    private void cleanUp(CallingInfo callingInfo) {
        // Destroy all sdkTokens related to the app
        mSdkTokenManager.destroy(callingInfo);

        synchronized (mLock) {
            // Now clean up rest of the state which is using obsolete sdkTokens
            Iterator<Map.Entry<IBinder, AppAndRemoteSdkLink>> it =
                    mAppAndRemoteSdkLinks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<IBinder, AppAndRemoteSdkLink> entry = it.next();
                AppAndRemoteSdkLink link = entry.getValue();
                if (link.mCallingInfo.equals(callingInfo)) {
                    mAppAndRemoteSdkLinks.remove(entry.getKey());
                }
            }
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
        return new SdkSandboxShellCommand(this, mContext).exec(this,
                in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(), args);
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

    @ThreadSafe
    private static class SdkTokenManager {
        // Keep track of codeToken for each unique pair of {callingUid, sdkName}
        @GuardedBy("mSdkTokens")
        final ArrayMap<Pair<CallingInfo, String>, IBinder> mSdkTokens = new ArrayMap<>();
        @GuardedBy("mSdkTokens")
        final ArrayMap<IBinder, Pair<CallingInfo, String>> mReverseSdkTokens =
                new ArrayMap<>();

        /**
         * For the given {callingUid, name} pair, create unique {@code sdkToken} or
         * return existing one.
         */
        public IBinder createOrGetSdkToken(CallingInfo callingInfo, String sdkName) {
            final Pair<CallingInfo, String> pair = Pair.create(callingInfo, sdkName);
            synchronized (mSdkTokens) {
                if (mSdkTokens.containsKey(pair)) {
                    return mSdkTokens.get(pair);
                }
                final IBinder sdkToken = new Binder();
                mSdkTokens.put(pair, sdkToken);
                mReverseSdkTokens.put(sdkToken, pair);
                return sdkToken;
            }
        }

        @Nullable
        public IBinder getSdkToken(CallingInfo callingInfo, String sdkName) {
            final Pair<CallingInfo, String> pair = Pair.create(callingInfo, sdkName);
            synchronized (mSdkTokens) {
                return mSdkTokens.get(pair);
            }
        }

        public void destroy(IBinder sdkToken) {
            synchronized (mSdkTokens) {
                mSdkTokens.remove(mReverseSdkTokens.get(sdkToken));
                mReverseSdkTokens.remove(sdkToken);
            }
        }

        public void destroy(CallingInfo callingInfo) {
            synchronized (mSdkTokens) {
                for (int i = 0; i < mSdkTokens.size(); i++) {
                    Pair<CallingInfo, String> pair = mSdkTokens.keyAt(i);
                    if (pair.first.equals(callingInfo)) {
                        destroy(mSdkTokens.get(pair));
                    }
                }
            }
        }

        void dump(PrintWriter writer) {
            synchronized (mSdkTokens) {
                if (mSdkTokens.isEmpty()) {
                    writer.println("mSdkTokens is empty");
                } else {
                    writer.print("mSdkTokens size: ");
                    writer.println(mSdkTokens.size());
                    for (Pair<CallingInfo, String> pair : mSdkTokens.keySet()) {
                        writer.printf("caller: %s, sdkName: %s",
                                pair.first,
                                pair.second);
                        writer.println();
                    }
                }
            }
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
     * <p>We maintain a link for each unique {app, remoteCode} pair, which is identified with {@code
     * codeToken}.
     */
    private class AppAndRemoteSdkLink extends ILoadSdkInSandboxCallback.Stub {
        private final CallingInfo mCallingInfo;
        private final SdkProviderInfo mSdkProviderInfo;
        // The codeToken for which this channel has been created
        private final IBinder mSdkToken;
        private final ILoadSdkCallback mManagerToAppCallback;

        @GuardedBy("this")
        private ISdkSandboxManagerToSdkSandboxCallback mManagerToCodeCallback;

        AppAndRemoteSdkLink(
                CallingInfo callingInfo,
                IBinder sdkToken,
                ILoadSdkCallback managerToAppCallback,
                SdkProviderInfo sdkProviderInfo) {
            mSdkToken = sdkToken;
            mSdkProviderInfo = sdkProviderInfo;
            mCallingInfo = callingInfo;
            mManagerToAppCallback = managerToAppCallback;
        }

        @Override
        public void onLoadSdkSuccess(
                Bundle params, ISdkSandboxManagerToSdkSandboxCallback callback) {
            // Keep reference to callback so that manager service can
            // callback to remote code loaded.
            synchronized (this) {
                mManagerToCodeCallback = callback;
            }

            sendLoadSdkSuccessToApp(params);
        }

        @Override
        public void onLoadSdkError(int errorCode, String errorMsg) {
            handleLoadSdkError(
                    toSdkSandboxManagerLoadSdkErrorCode(errorCode),
                    errorMsg,
                    /*cleanUpInternalState=*/ true);
        }

        private void sendLoadSdkSuccessToApp(Bundle params) {
            try {
                mManagerToAppCallback.onLoadSdkSuccess(params);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onLoadCodeSuccess", e);
            }
        }

        void handleLoadSdkError(int errorCode, String errorMsg, boolean cleanUpInternalState) {
            if (cleanUpInternalState) {
                // If an SDK fails to load entirely and does not exist in the sandbox, cleanup
                // might need to occur so that the manager has to no longer concern itself with
                // communication between the app and a non-existing remote code.
                cleanUp(mSdkToken);
            }
            try {
                mManagerToAppCallback.onLoadSdkFailure(errorCode, errorMsg);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onLoadCodeFailure", e);
            }
        }

        void sendSurfacePackageErrorToApp(
                int errorCode, String errorMsg, IRequestSurfacePackageCallback callback) {
            try {
                callback.onSurfacePackageError(errorCode, errorMsg);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageError", e);
            }
        }

        private void sendSurfacePackageReadyToApp(
                SurfaceControlViewHost.SurfacePackage surfacePackage,
                int surfacePackageId,
                Bundle params,
                IRequestSurfacePackageCallback callback) {
            try {
                callback.onSurfacePackageReady(surfacePackage, surfacePackageId, params);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageReady callback", e);
            }
        }

        void requestSurfacePackageFromSdk(
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                Bundle params,
                IRequestSurfacePackageCallback callback) {
            try {
                synchronized (this) {
                    mManagerToCodeCallback.onSurfacePackageRequested(
                            hostToken,
                            displayId,
                            width,
                            height,
                            params,
                            new IRequestSurfacePackageFromSdkCallback.Stub() {
                                @Override
                                public void onSurfacePackageReady(
                                        SurfaceControlViewHost.SurfacePackage surfacePackage,
                                        int surfacePackageId,
                                        Bundle params) {
                                    sendSurfacePackageReadyToApp(
                                            surfacePackage, surfacePackageId, params, callback);
                                }

                                @Override
                                public void onSurfacePackageError(int errorCode, String errorMsg) {
                                    int sdkSandboxManagerErrorCode =
                                            toSdkSandboxManagerRequestSurfacePackageErrorCode(
                                                    errorCode);
                                    sendSurfacePackageErrorToApp(
                                            sdkSandboxManagerErrorCode, errorMsg, callback);
                                }
                            });
                }
            } catch (RemoteException e) {
                String errorMsg = "Failed to requestSurfacePackage";
                Log.w(TAG, errorMsg, e);
                sendSurfacePackageErrorToApp(
                        SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                        errorMsg + ": " + e,
                        callback);
            }
        }

        private void sendSendDataSuccessToApp(Bundle params, ISendDataCallback callback) {
            try {
                callback.onSendDataSuccess(params);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSendDataSuccess", e);
            }
        }

        private void sendSendDataErrorToApp(
                int errorCode, String errorMsg, ISendDataCallback callback) {
            try {
                callback.onSendDataError(errorCode, errorMsg);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSendDataError", e);
            }
        }

        void sendDataToSdk(Bundle data, ISendDataCallback callback) {
            try {
                synchronized (this) {
                    mManagerToCodeCallback.onDataReceived(
                            data,
                            new IDataReceivedCallback.Stub() {
                                @Override
                                public void onDataReceivedSuccess(Bundle params) {
                                    sendSendDataSuccessToApp(params, callback);
                                }

                                @Override
                                public void onDataReceivedError(int errorCode, String errorMsg) {
                                    sendSendDataErrorToApp(
                                            toSdkSandboxManagerSendDataErrorCode(errorCode),
                                            errorMsg,
                                            callback);
                                }
                            });
                }
            } catch (RemoteException e) {
                String errorMsg = "Failed to sendData";
                Log.w(TAG, errorMsg, e);
                sendSendDataErrorToApp(
                        SdkSandboxManager.SEND_DATA_INTERNAL_ERROR, errorMsg + ": " + e, callback);
            }
        }

        @SdkSandboxManager.LoadSdkErrorCode
        private int toSdkSandboxManagerLoadSdkErrorCode(int sdkSandboxErrorCode) {
            switch (sdkSandboxErrorCode) {
                case ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED:
                    return SdkSandboxManager.LOAD_SDK_ALREADY_LOADED;
                case ILoadSdkInSandboxCallback.LOAD_SDK_NOT_FOUND:
                    return SdkSandboxManager.LOAD_SDK_NOT_FOUND;
                case ILoadSdkInSandboxCallback.LOAD_SDK_PROVIDER_INIT_ERROR:
                    return SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
                case ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR:
                    return SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
                default:
                    Log.e(TAG, "Error code" + sdkSandboxErrorCode
                            + "has no mapping to the SdkSandboxManager error codes");
                    return SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
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

        @SdkSandboxManager.SendDataErrorCode
        private int toSdkSandboxManagerSendDataErrorCode(int sdkSandboxErrorCode) {
            if (sdkSandboxErrorCode == IDataReceivedCallback.DATA_RECEIVED_INTERNAL_ERROR) {
                return SdkSandboxManager.SEND_DATA_INTERNAL_ERROR;
            }
            Log.e(
                    TAG,
                    "Error code"
                            + sdkSandboxErrorCode
                            + "has no mapping to the SdkSandboxManager error codes");
            return SdkSandboxManager.SEND_DATA_INTERNAL_ERROR;
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
            mServiceProvider.unbindService(callingInfo);
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

    /** @hide */
    public static class Lifecycle extends SystemService {
        private final SdkSandboxManagerService mService;

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
        public void enforceAllowedToSendBroadcast(@NonNull Intent intent) {
            // TODO(b/209599396): Have a meaningful allowlist.
            if (intent.getAction() != null && !Intent.ACTION_VIEW.equals(intent.getAction())) {
                throw new SecurityException("Intent " + intent.getAction()
                        + " may not be broadcast from an SDK sandbox uid");
            }
        }

        @Override
        public void enforceAllowedToStartActivity(@NonNull Intent intent) {
            enforceAllowedToSendBroadcast(intent);
        }

        @Override
        public void enforceAllowedToStartOrBindService(@NonNull Intent intent) {
            SdkSandboxManagerService.this.enforceAllowedToStartOrBindService(intent);
        }
    }
}
