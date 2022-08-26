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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Syncs specified keys in default {@link SharedPreferences} to Sandbox.
 *
 * <p>This class is a singleton since we want to maintain sync between app process and sandbox
 * process.
 *
 * @hide
 */
public class SharedPreferencesSyncManager {

    private static final String TAG = "SdkSandboxManager";

    private static SharedPreferencesSyncManager sInstance = null;

    private final ISdkSandboxManager mService;
    private final Context mContext;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mWaitingForSandbox = false;

    // Set to a listener after initial bulk sync is successful
    @GuardedBy("mLock")
    private ChangeListener mListener = null;

    // Map of keyName->SharedPreferenceKey that this manager needs to keep in sync.
    @GuardedBy("mLock")
    private ArrayMap<String, SharedPreferencesKey> mKeysToSync = new ArrayMap<>();

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public SharedPreferencesSyncManager(
            @NonNull Context context, @NonNull ISdkSandboxManager service) {
        mContext = context.getApplicationContext();
        mService = service;
    }

    /** Returns a singleton instance of this class. */
    public static synchronized SharedPreferencesSyncManager getInstance(
            @NonNull Context context, @NonNull ISdkSandboxManager service) {
        if (sInstance == null) {
            sInstance = new SharedPreferencesSyncManager(context, service);
        }
        return sInstance;
    }

    // TODO(b/237410689): Update links to getClientSharedPreferences when cl is merged.
    // TODO(b/237410689): Implement removeSyncKeys
    /**
     * Adds {@link SharedPreferencesKey}s to set of keys being synced from app's default {@link
     * SharedPreferences} to SdkSandbox.
     *
     * <p>Synced data will be available for sdks to read using the {@code
     * getClientSharedPreferences} api.
     *
     * <p>To stop syncing any key that has been added using this API, use {@link #removeSyncKeys}.
     *
     * <p>If a provided {@link SharedPreferencesKey} conflicts with an existing key in the pool,
     * i.e., they have the same name but different type, then the old key is replaced with the new
     * one.
     *
     * <p>The sync breaks if the app restarts and user must call this API to rebuild the pool of
     * keys for syncing.
     *
     * @param keysWithTypeToSync set of keys and their type that will be synced to Sandbox.
     * @param callback callback to receive notification for change in sync status.
     */
    public void addSharedPreferencesSyncKeys(
            @NonNull Set<SharedPreferencesKey> keysWithTypeToSync) {
        // TODO(b/239403323): Validate the parameters in SdkSandboxManager
        synchronized (mLock) {
            for (SharedPreferencesKey keyWithType : keysWithTypeToSync) {
                mKeysToSync.put(keyWithType.getName(), keyWithType);
            }

            syncData();
        }
    }

    /**
     * Returns the set of all {@link SharedPreferencesKey} that are being synced from app's default
     * {@link SharedPreferences} to sandbox.
     */
    public Set<SharedPreferencesKey> getSharedPreferencesSyncKeys() {
        synchronized (mLock) {
            return new ArraySet(mKeysToSync.values());
        }
    }

    /**
     * Returns true if sync is in waiting state.
     *
     * <p>Sync transitions into waiting state whenever sdksandbox is unavailable. It resumes syncing
     * again when SdkSandboxManager notifies us that sdksandbox is available again.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    public boolean isWaitingForSandbox() {
        synchronized (mLock) {
            return mWaitingForSandbox;
        }
    }

    /**
     * Syncs data to SdkSandbox.
     *
     * <p>Syncs values of specified keys {@link #mKeysToSync} from the default {@link
     * SharedPreferences} of the app.
     *
     * <p>Once bulk sync is complete, it also registers listener for updates which maintains the
     * sync.
     */
    private void syncData() {
        synchronized (mLock) {
            // Do not sync if keys have not been specified by the client.
            if (mKeysToSync.isEmpty()) {
                return;
            }

            bulkSyncData();
        }
    }

    @GuardedBy("mLock")
    private void bulkSyncData() {
        // Collect data in a bundle
        final Bundle data = new Bundle();
        final SharedPreferences pref = getDefaultSharedPreferences();
        for (int i = 0; i < mKeysToSync.size(); i++) {
            final String key = mKeysToSync.keyAt(i);
            updateBundle(data, pref, key);
        }
        final SharedPreferencesUpdate update =
                new SharedPreferencesUpdate(mKeysToSync.values(), data);
        try {
            mService.syncDataFromClient(
                    mContext.getPackageName(),
                    /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                    update,
                    new ISharedPreferencesSyncCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            handleSuccess();
                        }

                        @Override
                        public void onSandboxStart() {
                            handleSandboxStart();
                        }

                        @Override
                        public void onError(int errorCode, String errorMsg) {
                            handleError(errorCode, errorMsg);
                        }
                    });
        } catch (RemoteException e) {
            handleError(
                    ISharedPreferencesSyncCallback.INTERNAL_ERROR,
                    "Couldn't connect to SdkSandboxManagerService: " + e.getMessage());
        }
    }

    private void handleSuccess() {
        synchronized (mLock) {
            if (!mWaitingForSandbox && mListener == null) {
                mListener = new ChangeListener();
                getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(mListener);
            }
        }
    }

    private void handleSandboxStart() {
        synchronized (mLock) {
            if (mWaitingForSandbox) {
                // Retry bulk sync if we were waiting for sandbox to start
                mWaitingForSandbox = false;
                bulkSyncData();
            }
        }
    }

    private void handleError(int errorCode, String errorMsg) {
        synchronized (mLock) {
            // Transition to waiting state when sandbox is unavailable
            if (!mWaitingForSandbox
                    && errorCode == ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE) {
                // Wait for sandbox to start. When it starts, server will call onSandboxStart
                mWaitingForSandbox = true;
                return;
            }
        }
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = mContext.getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private class ChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences pref, @Nullable String key) {
            // Sync specified keys only
            synchronized (mLock) {
                // Do not sync if we are in waiting state
                if (mWaitingForSandbox) {
                    return;
                }

                if (key == null) {
                    // All keys have been cleared. Bulk sync so that we send null for every key.
                    bulkSyncData();
                    return;
                }

                if (!mKeysToSync.containsKey(key)) {
                    return;
                }

                final Bundle data = new Bundle();
                updateBundle(data, pref, key);

                final SharedPreferencesKey keyWithType =
                        new SharedPreferencesKey(key, mKeysToSync.get(key).getType());
                final SharedPreferencesUpdate update =
                        new SharedPreferencesUpdate(List.of(keyWithType), data);
                try {
                    mService.syncDataFromClient(
                            mContext.getPackageName(),
                            /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                            update,
                            // When live syncing, we are only interested in knowing about errors.
                            new ISharedPreferencesSyncCallback.Stub() {
                                @Override
                                public void onSuccess() {}

                                @Override
                                public void onSandboxStart() {}

                                @Override
                                public void onError(int errorCode, String errorMsg) {
                                    handleError(errorCode, errorMsg);
                                }
                            });
                } catch (RemoteException e) {
                    handleError(
                            ISharedPreferencesSyncCallback.INTERNAL_ERROR,
                            "Couldn't connect to SdkSandboxManagerService: " + e.getMessage());
                }
            }
        }
    }

    // Add key to bundle based on type of value
    @GuardedBy("mLock")
    private void updateBundle(Bundle data, SharedPreferences pref, String key) {
        if (!pref.contains(key)) {
            // Keep the key missing from the bundle; that means key has been removed.
            return;
        }

        final int type = mKeysToSync.get(key).getType();
        try {
            switch (type) {
                case SharedPreferencesKey.KEY_TYPE_STRING:
                    data.putString(key, pref.getString(key, ""));
                    break;
                case SharedPreferencesKey.KEY_TYPE_BOOLEAN:
                    data.putBoolean(key, pref.getBoolean(key, false));
                    break;
                case SharedPreferencesKey.KEY_TYPE_INTEGER:
                    data.putInt(key, pref.getInt(key, 0));
                    break;
                case SharedPreferencesKey.KEY_TYPE_FLOAT:
                    data.putFloat(key, pref.getFloat(key, 0.0f));
                    break;
                case SharedPreferencesKey.KEY_TYPE_LONG:
                    data.putLong(key, pref.getLong(key, 0L));
                    break;
                case SharedPreferencesKey.KEY_TYPE_STRING_SET:
                    data.putStringArrayList(
                            key, new ArrayList<>(pref.getStringSet(key, Collections.emptySet())));
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
            data.remove(key);
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
}
