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
package com.android.server.adservices;

import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;

import android.app.adservices.ConsentParcel;
import android.app.adservices.IAdServicesManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** @hide */
public class AdServicesManagerService extends IAdServicesManager.Stub {
    // The base directory for AdServices System Service.
    private static final String SYSTEM_DATA = "/data/system/";
    public static String ADSERVICES_BASE_DIR = SYSTEM_DATA + "adservices";

    /**
     * Broadcast send from the system service to the AdServices module when a package has been
     * installed/uninstalled. This intent must match the intent defined in the AdServices manifest.
     */
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";

    /** Key for designating the specific action. */
    private static final String ACTION_KEY = "action";

    /** Value if the package change was an uninstallation. */
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";

    /** Value if the package change was an installation. */
    private static final String PACKAGE_ADDED = "package_added";

    /** Value if the package has its data cleared. */
    private static final String PACKAGE_DATA_CLEARED = "package_data_cleared";

    private final Context mContext;

    private BroadcastReceiver mSystemServicePackageChangedReceiver;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    // This will be triggered when there is a flag change.
    private final DeviceConfig.OnPropertiesChangedListener mOnFlagsChangedListener =
            properties -> {
                if (!properties.getNamespace().equals(DeviceConfig.NAMESPACE_ADSERVICES)) {
                    return;
                }
                registerPackagedChangedBroadcastReceivers();
            };

    private final UserInstanceManager mUserInstanceManager;

    @VisibleForTesting
    AdServicesManagerService(Context context, UserInstanceManager userInstanceManager) {
        mContext = context;
        mUserInstanceManager = userInstanceManager;

        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_ADSERVICES,
                mContext.getMainExecutor(),
                mOnFlagsChangedListener);

        registerPackagedChangedBroadcastReceivers();
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        private AdServicesManagerService mService;

        /** @hide */
        public Lifecycle(Context context) {
            super(context);
            mService =
                    new AdServicesManagerService(
                            getContext(), new UserInstanceManager(ADSERVICES_BASE_DIR));
        }

        /** @hide */
        @Override
        public void onStart() {
            publishBinderService(AD_SERVICES_SYSTEM_SERVICE, mService);
            LogUtil.d("AdServicesManagerService started!");
        }
    }

    @Override
    public ConsentParcel getConsent() {
        final int userIdentifier = getUserIdentifier();

        LogUtil.v("getConsent() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .getConsent();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to getConsent with exception. Return REVOKED!");
            return ConsentParcel.REVOKED;
        }
    }

    // Return the User Identifier from the CallingUid.
    private int getUserIdentifier() {
        return UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
    }

    @Override
    public void setConsent(ConsentParcel consentParcel) {
        Objects.requireNonNull(consentParcel);

        final int userIdentifier = getUserIdentifier();
        LogUtil.v("setConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setConsent(consentParcel);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to persist the consent.");
        }
    }

    @Override
    public void recordNotificationDisplayed() {
        final int userIdentifier = getUserIdentifier();
        LogUtil.v("recordNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordNotificationDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to Record Notification Displayed.");
        }
    }

    @Override
    public boolean wasNotificationDisplayed() {
        final int userIdentifier = getUserIdentifier();
        LogUtil.v("wasNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .wasNotificationDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get the wasNotificationDisplayed.");
            return false;
        }
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    @VisibleForTesting
    void registerPackagedChangedBroadcastReceivers() {
        // There could be race condition between registerPackagedChangedBroadcastReceivers call
        // in the AdServicesManagerService constructor and the mOnFlagsChangedListener.
        synchronized (AdServicesManagerService.class) {
            if (FlagsFactory.getFlags().getAdServicesSystemServiceEnabled()) {
                if (mSystemServicePackageChangedReceiver != null) {
                    // We already register the receiver.
                    LogUtil.d("SystemServicePackageChangedReceiver is already registered.");
                    return;
                }

                // mSystemServicePackageChangedReceiver == null
                // We haven't registered the receiver.
                // Start the handler thread.
                mHandlerThread = new HandlerThread("AdServicesManagerServiceHandler");
                mHandlerThread.start();
                mHandler = new Handler(mHandlerThread.getLooper());

                final IntentFilter packageChangedIntentFilter = new IntentFilter();

                packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
                packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
                packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
                packageChangedIntentFilter.addDataScheme("package");

                mSystemServicePackageChangedReceiver =
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                UserHandle user = getSendingUser();
                                mHandler.post(() -> onPackageChange(intent, user));
                            }
                        };
                mContext.registerReceiverForAllUsers(
                        mSystemServicePackageChangedReceiver,
                        packageChangedIntentFilter,
                        /* broadcastPermission */ null,
                        mHandler);
                LogUtil.d("Package changed broadcast receivers registered.");
            } else {
                // FlagsFactory.getFlags().getAdServicesSystemServiceEnabled() == false
                LogUtil.d("AdServicesSystemServiceEnabled is FALSE.");

                // If there is a SystemServicePackageChangeReceiver, unregister it.
                if (mSystemServicePackageChangedReceiver != null) {
                    LogUtil.d("Unregistering the existing SystemServicePackageChangeReceiver");
                    mContext.unregisterReceiver(mSystemServicePackageChangedReceiver);
                    mSystemServicePackageChangedReceiver = null;
                    mHandlerThread.quitSafely();
                    mHandler = null;
                }

                return;
            }
        }
    }

    /** Sends an explicit broadcast to the AdServices module when a package change occurs. */
    @VisibleForTesting
    public void onPackageChange(Intent intent, UserHandle user) {
        Intent explicitBroadcast = new Intent();
        explicitBroadcast.setAction(PACKAGE_CHANGED_BROADCAST);
        explicitBroadcast.setData(intent.getData());

        final Intent i = new Intent(PACKAGE_CHANGED_BROADCAST);
        final List<ResolveInfo> resolveInfo =
                mContext.getPackageManager()
                        .queryBroadcastReceiversAsUser(
                                i,
                                PackageManager.ResolveInfoFlags.of(PackageManager.GET_RECEIVERS),
                                user);
        if (resolveInfo != null && !resolveInfo.isEmpty()) {
            for (ResolveInfo info : resolveInfo) {
                explicitBroadcast.setClassName(
                        info.activityInfo.packageName, info.activityInfo.name);
                int uidChanged = intent.getIntExtra(Intent.EXTRA_UID, -1);
                LogUtil.v("Package changed with UID " + uidChanged);
                explicitBroadcast.putExtra(Intent.EXTRA_UID, uidChanged);
                switch (intent.getAction()) {
                    case Intent.ACTION_PACKAGE_DATA_CLEARED:
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_DATA_CLEARED);
                        mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        break;
                    case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                        // TODO (b/233373604): Propagate broadcast to users not currently running
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_FULLY_REMOVED);
                        mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        break;
                    case Intent.ACTION_PACKAGE_ADDED:
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_ADDED);
                        // For users where the app is merely being updated rather than added, we
                        // don't want to send the broadcast.
                        if (!intent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false)) {
                            mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        }
                        break;
                }
            }
        }
    }
}
