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

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.server.LocalManagerRegistry;
import com.android.server.am.ActivityManagerLocal;

import java.io.PrintWriter;
import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of {@link SdkSandboxServiceProvider}.
 *
 * @hide
 */
@ThreadSafe
class SdkSandboxServiceProviderImpl implements SdkSandboxServiceProvider {

    private static final String TAG = "SdkSandboxManager";
    private static final String SANDBOX_PROCESS_NAME_SUFFIX = "_sdk_sandbox";

    private final Object mLock = new Object();

    private final Context mContext;
    private final ActivityManagerLocal mActivityManagerLocal;

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, SdkSandboxConnection> mAppSdkSandboxConnections =
            new ArrayMap<>();

    SdkSandboxServiceProviderImpl(Context context) {
        mContext = context;
        mActivityManagerLocal = LocalManagerRegistry.getManager(ActivityManagerLocal.class);
    }

    @Override
    @Nullable
    public void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection) {
        synchronized (mLock) {
            if (getBoundServiceForApp(callingInfo) != null) {
                Log.i(TAG, "SDK sandbox for " + callingInfo + " is already bound");
                return;
            }

            Log.i(TAG, "Binding sdk sandbox for " + callingInfo);

            ComponentName componentName = getServiceComponentName();
            if (componentName == null) {
                Log.e(TAG, "Failed to find sdk sandbox service");
                notifyFailedBinding(serviceConnection);
                return;
            }
            final Intent intent = new Intent().setComponent(componentName);

            SdkSandboxConnection sdkSandboxConnection =
                    new SdkSandboxConnection(serviceConnection);

            final String callingPackageName = callingInfo.getPackageName();
            String sandboxProcessName = getProcessName(callingPackageName)
                    + SANDBOX_PROCESS_NAME_SUFFIX;
            try {
                boolean bound = mActivityManagerLocal.bindSdkSandboxService(intent,
                        serviceConnection, callingInfo.getUid(), callingPackageName,
                        sandboxProcessName, Context.BIND_AUTO_CREATE);
                if (!bound) {
                    mContext.unbindService(serviceConnection);
                    notifyFailedBinding(serviceConnection);
                    return;
                }
            } catch (RemoteException e) {
                notifyFailedBinding(serviceConnection);
                return;
            }
            mAppSdkSandboxConnections.put(callingInfo, sdkSandboxConnection);
            Log.i(TAG, "Sdk sandbox has been bound");
        }
    }

    // a way to notify manager that binding never happened
    private void notifyFailedBinding(ServiceConnection serviceConnection) {
        serviceConnection.onNullBinding(null);
    }

    @Override
    public void dump(PrintWriter writer) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.size() == 0) {
                writer.println("mAppSdkSandboxConnections is empty");
            } else {
                writer.print("mAppSdkSandboxConnections size: ");
                writer.println(mAppSdkSandboxConnections.size());
                for (int i = 0; i < mAppSdkSandboxConnections.size(); i++) {
                    CallingInfo callingInfo = mAppSdkSandboxConnections.keyAt(i);
                    SdkSandboxConnection sdkSandboxConnection =
                            mAppSdkSandboxConnections.get(callingInfo);
                    writer.printf("Sdk sandbox for UID: %s, app package: %s, isConnected: %s",
                            callingInfo.getUid(), callingInfo.getPackageName(),
                            Objects.requireNonNull(sdkSandboxConnection).isConnected());
                    writer.println();
                }
            }
        }
    }

    @Override
    public void unbindService(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection sandbox = getSdkSandboxConnectionLocked(callingInfo);

            if (sandbox == null) {
                // Skip, already unbound
                return;
            }

            mContext.unbindService(sandbox.getServiceConnection());
            mAppSdkSandboxConnections.remove(callingInfo);
            Log.i(TAG, "Sdk sandbox has been unbound");
        }
    }

    @Override
    @Nullable
    public ISdkSandboxService getBoundServiceForApp(CallingInfo callingInfo) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.containsKey(callingInfo)) {
                return Objects.requireNonNull(mAppSdkSandboxConnections.get(callingInfo))
                        .getSdkSandboxService();
            }
        }
        return null;
    }

    @Override
    public void setBoundServiceForApp(CallingInfo callingInfo, ISdkSandboxService service) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.containsKey(callingInfo)) {
                Objects.requireNonNull(mAppSdkSandboxConnections.get(callingInfo))
                        .setSdkSandboxService(service);
            }
        }
    }

    @Nullable
    private ComponentName getServiceComponentName() {
        final Intent intent = new Intent(SdkSandboxManagerLocal.SERVICE_INTERFACE);
        intent.setPackage(mContext.getPackageManager().getSdkSandboxPackageName());

        final ResolveInfo resolveInfo = mContext.getPackageManager().resolveService(intent,
                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        if (resolveInfo == null) {
            Log.e(TAG, "Failed to find resolveInfo for sdk sandbox service");
            return null;
        }

        final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        if (serviceInfo == null) {
            Log.e(TAG, "Failed to find serviceInfo for sdk sandbox service");
            return null;
        }

        return new ComponentName(serviceInfo.packageName, serviceInfo.name);
    }

    @GuardedBy("mLock")
    @Nullable
    private SdkSandboxConnection getSdkSandboxConnectionLocked(CallingInfo callingInfo) {
        return mAppSdkSandboxConnections.get(callingInfo);
    }

    private String getProcessName(String packageName) {
        try {
            return mContext.getPackageManager().getApplicationInfo(packageName,
                    /*flags=*/ 0).processName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, packageName + " package not found");
        }
        return packageName;
    }

    private static class SdkSandboxConnection {
        private final ServiceConnection mServiceConnection;
        @Nullable
        private ISdkSandboxService mSupplementalProcessService = null;

        SdkSandboxConnection(ServiceConnection serviceConnection) {
            mServiceConnection = serviceConnection;
        }

        @Nullable
        public ISdkSandboxService getSdkSandboxService() {
            return mSupplementalProcessService;
        }

        public ServiceConnection getServiceConnection() {
            return mServiceConnection;
        }

        public void setSdkSandboxService(ISdkSandboxService service) {
            mSupplementalProcessService = service;
        }

        boolean isConnected() {
            return mSupplementalProcessService != null;
        }
    }
}
