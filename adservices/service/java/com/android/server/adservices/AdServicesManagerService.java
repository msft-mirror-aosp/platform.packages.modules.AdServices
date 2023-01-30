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

import android.adservices.common.AdServicesPermissions;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.consent.ConsentParcel;
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
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.sdksandbox.SdkSandboxManagerLocal;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** @hide */
public class AdServicesManagerService extends IAdServicesManager.Stub {
    // The base directory for AdServices System Service.
    private static final String SYSTEM_DATA = "/data/system/";
    public static String ADSERVICES_BASE_DIR = SYSTEM_DATA + "adservices";
    private static final String ERROR_MESSAGE_NOT_PERMITTED_TO_CALL_ADSERVICESMANAGER_API =
            "Unauthorized caller. Permission to call AdServicesManager API is not granted in System"
                    + " Server.";
    private final Object mRegisterReceiverLock = new Object();

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
    private BroadcastReceiver mSystemServiceUserActionReceiver;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    // This will be triggered when there is a flag change.
    private final DeviceConfig.OnPropertiesChangedListener mOnFlagsChangedListener =
            properties -> {
                if (!properties.getNamespace().equals(DeviceConfig.NAMESPACE_ADSERVICES)) {
                    return;
                }
                registerReceivers();
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

        registerReceivers();
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        private AdServicesManagerService mService;

        /** @hide */
        public Lifecycle(Context context) {
            super(context);
            mService =
                    new AdServicesManagerService(
                            context, new UserInstanceManager(ADSERVICES_BASE_DIR));
        }

        /** @hide */
        @Override
        public void onStart() {
            LogUtil.d("AdServicesManagerService started!");

            // TODO(b/262282035): Fix this work around in U+.
            // TODO(b/263128170): Add cts-root tests to make sure that we can start the
            //  AdServicesManager in U+

            // Register the AdServicesManagerService with the SdkSandboxManagerService.
            // This is a workaround for b/262282035.
            // This works since we start the SdkSandboxManagerService before the
            // AdServicesManagerService in the SystemServer.java
            SdkSandboxManagerLocal sdkSandboxManagerLocal =
                    LocalManagerRegistry.getManager(SdkSandboxManagerLocal.class);
            if (sdkSandboxManagerLocal != null) {
                sdkSandboxManagerLocal.registerAdServicesManagerService(mService);
            } else {
                throw new IllegalStateException(
                        "SdkSandboxManagerLocal not found when registering AdServicesManager!");
            }
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();

        LogUtil.v("getConsent() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .getConsent(consentApiType);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to getConsent with exception. Return REVOKED!");
            return ConsentParcel.createRevokedConsent(consentApiType);
        }
    }

    // Return the User Identifier from the CallingUid.
    private int getUserIdentifierFromBinderCallingUid() {
        return UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setConsent(ConsentParcel consentParcel) {
        enforceAdServicesManagerPermission();

        Objects.requireNonNull(consentParcel);

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setConsent(consentParcel);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to persist the consent.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordNotificationDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordNotificationDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Failed to Record Notification Displayed.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasNotificationDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("wasNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .wasNotificationDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Failed to get the wasNotificationDisplayed.");
            return false;
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordGaUxNotificationDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordGaUxNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordGaUxNotificationDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to Record GA UX Notification Displayed.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasGaUxNotificationDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("wasGaUxNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .wasGaUxNotificationDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get the wasGaUxNotificationDisplayed.");
            return false;

        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordTopicsConsentPageDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordTopicsConsentPageDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordTopicsConsentPageDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to Record Topics Consent Page Displayed.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasTopicsConsentPageDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("wasTopicsConsentPageDisplayed() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .wasTopicsConsentPageDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get the wasTopicsConsentPageDisplayed.");
            return false;
        }
    }

    /** method to Record Fledge and Msmt consent page displayed or not */
    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordFledgeAndMsmtConsentPageDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "recordFledgeAndMsmtConsentPageDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordFledgeAndMsmtConsentPageDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to Record Fledge and Msmt Consent Page Displayed.");
        }
    }

    /** method to get Fledge and Msmt consent page displayed or not */
    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasFledgeAndMsmtConsentPageDisplayed() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("wasFledgeAndMsmtConsentPageDisplayed() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .wasFledgeAndMsmtConsentPageDisplayed();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get the wasFledgeAndMsmtConsentPageDisplayed.");
            return false;
        }
    }

    @Override
    @RequiresPermission
    public List<String> getKnownAppsWithConsent(@NonNull List<String> installedPackages) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("getKnownAppsWithConsent() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .getKnownAppsWithConsent(installedPackages);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to get the getKnownAppsWithConsent() for user identifier %d.",
                    userIdentifier);
            return List.of();
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public List<String> getAppsWithRevokedConsent(@NonNull List<String> installedPackages) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("getAppsWithRevokedConsent() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .getAppsWithRevokedConsent(installedPackages);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to getAppsWithRevokedConsent() for user identifier %d.",
                    userIdentifier);
            return List.of();
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setConsentForApp(
            @NonNull String packageName, int packageUid, boolean isConsentRevoked) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();

        LogUtil.v(
                "setConsentForApp() for User Identifier %d, package name %s, and package uid %d to"
                        + " %s.",
                userIdentifier, packageName, packageUid, isConsentRevoked);
        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .setConsentForApp(packageName, packageUid, isConsentRevoked);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to setConsentForApp() for User Identifier %d, package name %s, and"
                            + " package uid %d to %s.",
                    userIdentifier,
                    packageName,
                    packageUid,
                    isConsentRevoked);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void clearKnownAppsWithConsent() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("clearKnownAppsWithConsent() for user identifier %d.", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .clearKnownAppsWithConsent();
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to clearKnownAppsWithConsent() for user identifier %d",
                    userIdentifier);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void clearAllAppConsentData() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("clearAllAppConsentData() for user identifier %d.", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .clearAllAppConsentData();
        } catch (IOException e) {
            LogUtil.e(
                    e, "Failed to clearAllAppConsentData() for user identifier %d", userIdentifier);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean isConsentRevokedForApp(@NonNull String packageName, int packageUid)
            throws IllegalArgumentException {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "isConsentRevokedForApp() for user identifier %d, package name %s, and package uid"
                        + " %d.",
                userIdentifier, packageName, packageUid);
        try {
            return mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .isConsentRevokedForApp(packageName, packageUid);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to call isConsentRevokedForApp() for user identifier %d, package name"
                            + " %s, and package uid %d.",
                    userIdentifier,
                    packageName,
                    packageUid);
            return true;
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean setConsentForAppIfNew(
            @NonNull String packageName, int packageUid, boolean isConsentRevoked)
            throws IllegalArgumentException {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "setConsentForAppIfNew() for user identifier %d, package name"
                        + " %s, and package uid %d to %s.",
                userIdentifier, packageName, packageUid, isConsentRevoked);
        try {
            return mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .setConsentForAppIfNew(packageName, packageUid, isConsentRevoked);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to setConsentForAppIfNew() for user identifier %d, package name"
                            + " %s, and package uid %d to %s.",
                    userIdentifier,
                    packageName,
                    packageUid,
                    isConsentRevoked);
            return true;
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void clearConsentForUninstalledApp(@NonNull String packageName, int packageUid) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "clearConsentForUninstalledApp() for user identifier %d, package name"
                        + " %s, and package uid %d.",
                userIdentifier, packageName, packageUid);
        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .clearConsentForUninstalledApp(packageName, packageUid);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to clearConsentForUninstalledApp() for user identifier %d, package name"
                            + " %s, and package uid %d.",
                    userIdentifier,
                    packageName,
                    packageUid);
        }
    }

    @VisibleForTesting
    void registerReceivers() {
        // There could be race condition between registerReceivers call
        // in the AdServicesManagerService constructor and the mOnFlagsChangedListener.
        synchronized (mRegisterReceiverLock) {
            if (!FlagsFactory.getFlags().getAdServicesSystemServiceEnabled()) {
                LogUtil.d("AdServicesSystemServiceEnabled is FALSE.");
                // If there is a SystemServicePackageChangeReceiver, unregister it.
                if (mSystemServicePackageChangedReceiver != null) {
                    LogUtil.d("Unregistering the existing SystemServicePackageChangeReceiver");
                    mContext.unregisterReceiver(mSystemServicePackageChangedReceiver);
                    mSystemServicePackageChangedReceiver = null;
                }

                // If there is a SystemServiceUserActionReceiver, unregister it.
                if (mSystemServiceUserActionReceiver != null) {
                    LogUtil.d("Unregistering the existing SystemServiceUserActionReceiver");
                    mContext.unregisterReceiver(mSystemServiceUserActionReceiver);
                    mSystemServiceUserActionReceiver = null;
                }

                if (mHandler != null) {
                    mHandlerThread.quitSafely();
                    mHandler = null;
                }
                return;
            }

            // Start the handler thread.
            if (mHandler == null) {
                mHandlerThread = new HandlerThread("AdServicesManagerServiceHandler");
                mHandlerThread.start();
                mHandler = new Handler(mHandlerThread.getLooper());
            }
            registerPackagedChangedBroadcastReceiversLocked();
            registerUserActionBroadcastReceiverLocked();
        }
    }

    /**
     * Registers a receiver for any broadcasts related to user profile removal for all users on the
     * device at boot up. After receiving the broadcast, we delete consent manager instance and
     * remove the user related data.
     */
    private void registerUserActionBroadcastReceiverLocked() {
        if (mSystemServiceUserActionReceiver != null) {
            // We already register the receiver.
            LogUtil.d("SystemServiceUserActionReceiver is already registered.");
            return;
        }
        mSystemServiceUserActionReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        mHandler.post(() -> onUserRemoved(intent));
                    }
                };
        mContext.registerReceiverForAllUsers(
                mSystemServiceUserActionReceiver,
                new IntentFilter(Intent.ACTION_USER_REMOVED),
                /*broadcastPermission=*/ null,
                mHandler);
        LogUtil.d("SystemServiceUserActionReceiver registered.");
    }

    /** Deletes the user instance and remove the user consent related data. */
    @VisibleForTesting
    void onUserRemoved(@NonNull Intent intent) {
        Objects.requireNonNull(intent);
        if (Intent.ACTION_USER_REMOVED.equals(intent.getAction())) {
            UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
            if (userHandle == null) {
                LogUtil.e("Extra " + Intent.EXTRA_USER + " is missing in the intent: " + intent);
                return;
            }
            LogUtil.d("Deleting user instance with user id: " + userHandle.getIdentifier());
            try {
                mUserInstanceManager.deleteUserInstance(userHandle.getIdentifier());
            } catch (Exception e) {
                LogUtil.e(e, "Failed to delete the consent manager directory");
            }
        }
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    private void registerPackagedChangedBroadcastReceiversLocked() {
        if (mSystemServicePackageChangedReceiver != null) {
            // We already register the receiver.
            LogUtil.d("SystemServicePackageChangedReceiver is already registered.");
            return;
        }

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

    // Check if caller has permission to invoke AdServicesManager APIs.
    @VisibleForTesting
    void enforceAdServicesManagerPermission() {
        mContext.enforceCallingPermission(
                AdServicesPermissions.ACCESS_ADSERVICES_MANAGER,
                ERROR_MESSAGE_NOT_PERMITTED_TO_CALL_ADSERVICESMANAGER_API);
    }
}
