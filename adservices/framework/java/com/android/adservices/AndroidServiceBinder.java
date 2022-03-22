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
package com.android.adservices;

import static com.android.adservices.AdServicesCommon.ACTION_TOPICS_SERVICE;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Service binder that connects to a service in the APK.
 *
 * TODO: Make it robust. Currently this class ignores edge cases.
 * TODO: Clean up the log
 *
 * @hide
 */
class AndroidServiceBinder<T> extends ServiceBinder<T> {
    // TODO: Revisit it.
    private static final int BIND_FLAGS = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT;

    // TODO(b/218519915): have a better timeout handling.
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;

    private final String mServiceIntentAction;
    private final Function<IBinder, T> mBinderConverter;
    private final Context mContext;

    // A CountDownloadLatch which will be opened when the connection is established or any error
    // occurs.
    private CountDownLatch mConnectionCountDownLatch;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private T mService;

    @GuardedBy("mLock")
    private ServiceConnection mServiceConnection;

    protected AndroidServiceBinder(Context context, String serviceIntentAction,
            Function<IBinder, T> converter) {
        mServiceIntentAction = serviceIntentAction;
        mContext = context;
        mBinderConverter = converter;
    }

    public T getService() {
        synchronized (mLock) {
            // If we already have a service, just return it.
            if (mService != null) {
                // Note there's a chance the service dies right after we return here,
                // but we can't avoid that.
                return mService;
            }

            // If there's no pending bindService(), we need to start one.
            if (mServiceConnection == null) {
                // There's no other pending connection, creating one.
                ComponentName componentName = getServiceComponentName();
                if (componentName == null) {
                    LogUtil.e("Failed to find AdServices service");
                    return null;
                }
                final Intent intent = new Intent().setComponent(componentName);

                LogUtil.d("bindService: " + mServiceIntentAction);

                // This latch will open when the connection is established or any error occurs.
                mConnectionCountDownLatch = new CountDownLatch(1);
                mServiceConnection = new AdServicesServiceConnection();

                // We use Runnable::run so that the callback is called on a binder thread.
                // Otherwise we'd use the main thread, which could cause a deadlock.
                final boolean success =
                        mContext.bindService(intent, BIND_FLAGS, Runnable::run, mServiceConnection);
                if (!success) {
                    LogUtil.e("Failed to bindService: " + intent);
                    mServiceConnection = null;
                    return null;
                } else {
                    LogUtil.d("bindService() already pending...");
                }
            }
        }

        // Then wait for connection result.
        // Note: We must not hold the lock while waiting for the connection since the
        // onServiceConnected callback also needs to acquire the lock. This would cause a deadlock.
        try {
            // TODO(b/218519915): Better timeout handling
            mConnectionCountDownLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread interrupted"); // TODO Handle it better.
        }

        synchronized (mLock) {
            if (mService == null) {
                throw new RuntimeException("Failed to connect to the service");
            }
            return mService;
        }
    }

    // A class to handle the connection to the AdService Services.
    private class AdServicesServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtil.d("onServiceConnected " + mServiceIntentAction);
            synchronized (mLock) {
                mService = mBinderConverter.apply(service);
            }
            // Connection is established, open the latch.
            mConnectionCountDownLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtil.d("onServiceDisconnected " + mServiceIntentAction);
            synchronized (mLock) {
                mService = null;
            }
            mConnectionCountDownLatch.countDown();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            LogUtil.d("onBindingDied " + mServiceIntentAction);
            synchronized (mLock) {
                mService = null;
            }
            mConnectionCountDownLatch.countDown();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            LogUtil.e("onNullBinding shouldn't happen: " + mServiceIntentAction);
            synchronized (mLock) {
                mService = null;
            }
            mConnectionCountDownLatch.countDown();
        }
    }

    @Nullable
    private ComponentName getServiceComponentName() {
        final Intent intent = new Intent(ACTION_TOPICS_SERVICE);
        final ResolveInfo resolveInfo = mContext.getPackageManager().resolveService(intent,
                PackageManager.GET_SERVICES);
        if (resolveInfo == null) {
            LogUtil.e("Failed to find resolveInfo for adServices service");
            return null;
        }

        final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        if (serviceInfo == null) {
            LogUtil.e("Failed to find serviceInfo for adServices service");
            return null;
        }

        return new ComponentName(serviceInfo.packageName, serviceInfo.name);
    }

    @Override
    public void unbindFromService() {
        synchronized (mLock) {
            if (mService == null || mServiceConnection == null) {
                return; // Nothing to release.
            }

            LogUtil.d("unbinding...");
            mContext.unbindService(mServiceConnection);
            mServiceConnection = null;
            mService = null;
        }
    }
}
