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
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Syncs specified keys in default {@link SharedPreferences} to Sandbox.
 *
 * @hide
 */
public class SharedPreferencesSyncManager {

    private static final String TAG = "SdkSandboxManager";

    private final ISdkSandboxManager mService;
    private final Context mContext;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mIsRunning = false;

    // Set to true if initial bulk sync fails due to sandbox being unavailable
    @GuardedBy("mLock")
    private boolean mWaitingForSandbox = false;

    // Set to a listener after initial bulk sync is successful
    @GuardedBy("mLock")
    private ChangeListener mListener = null;

    @GuardedBy("mLock")
    private SharedPreferencesSyncCallback mCallback = null;

    /**
     * Type of keys that needs to be synced.
     *
     * <p>Generated from {@link #mKeysToSync}.
     */
    @Nullable
    @GuardedBy("mLock")
    private ArrayMap<String, Integer> mTypeOfKey = null;

    // List of keys that this manager needs to keep in sync.
    @Nullable
    @GuardedBy("mLock")
    private ArrayList<SharedPreferencesKey> mKeysToSync = null;

    public SharedPreferencesSyncManager(
            @NonNull Context context, @NonNull ISdkSandboxManager service) {
        mContext = context.getApplicationContext();
        mService = service;
    }

    // TODO(b/237410689): Update links to getClientSharedPreferences when cl is merged.
    /**
     * Starts syncing data from app's default {@link SharedPreferences} to SdkSandbox.
     *
     * <p>Synced data will be available for sdks to read using their {@code
     * getClientSharedPreferences} api.
     *
     * <p>Only specified keys provided in {@code keysWithTypeToSync} will be synced. Only one set of
     * keys can be synced at a time. Calling this API while sync is already running will throw
     * {@link IllegalStateException}. If you want to modify the set of keys being synced, you need
     * to call {@link #stopSharedPreferencesSync()} first. When you modify the set of keys being
     * synced, the synced value of old keys will not be erased from the Sandbox. To avoid storage
     * leak, always sync removal of keys from default {@link SharedPreferences} before updating the
     * keys.
     *
     * <p>If this API is called before Sandbox has started, {@link SdkSandboxManager} will wait for
     * sandbox to start and then start the syncing. If Sandbox is already running, it will start the
     * sync immediately. Once data has been synced once successfully, {@link
     * ISharedPreferencesSyncCallback#onStart()} will be called. From there on, updates to any of
     * the keys will also be propagated to the Sandbox. If there is an error at any point, {@link
     * ISyncSharedPreferencesDataCallback#onError(int, String)} will be called and sync will break.
     * Sync will also break if {@link #stopSharedPreferencesSync()} is called. To restart the sync,
     * the user needs to call this API again.
     *
     * @param keysWithTypeToSync set of keys and their type that will be synced to Sandbox.
     * @param callback callback to receive notification for change in sync status.
     */
    public void startSharedPreferencesSync(
            @NonNull Set<SharedPreferencesKey> keysWithTypeToSync,
            @NonNull SharedPreferencesSyncCallback callback) {
        // TODO(b/239403323): Validate the parameters in SdkSandboxManager
        synchronized (mLock) {
            if (mIsRunning) {
                throw new IllegalStateException("Sync is already in progress");
            }

            mIsRunning = true;
            mCallback = callback;
            mKeysToSync = new ArrayList<>(keysWithTypeToSync);
            mTypeOfKey = new ArrayMap<>();
            for (SharedPreferencesKey keyWithTypeToSync : mKeysToSync) {
                mTypeOfKey.put(keyWithTypeToSync.getName(), keyWithTypeToSync.getType());
            }

            syncData();
        }
    }

    /**
     * Stops syncing data from app's default {@link SharedPreferences} to SdkSandbox.
     *
     * <p>This breaks any existing sync started using {@link #startSharedPreferencesSync(Set,
     * SharedPreferencesSyncCallback)}. If there is no active sync present, then calling this is a
     * no-op.
     *
     * <p>Data synced to Sandbox so far won't be erased.
     */
    public boolean stopSharedPreferencesSync() {
        synchronized (mLock) {
            if (mIsRunning) {
                mCallback.onStop();
                cleanUp();
                return true;
            }
            return false;
        }
    }

    @GuardedBy("mLock")
    private void cleanUp() {
        getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(mListener);
        mCallback = null;
        mListener = null;
        mIsRunning = false;
        mWaitingForSandbox = false;
    }

    // TODO(b/239403323): On sandbox restart, we need to sync again.
    /**
     * Syncs data to SdkSandbox.
     *
     * <p>Syncs values of specified keys {@code mKeysToSync} from the default {@link
     * SharedPreferences} of the app.
     *
     * <p>Once bulk sync is complete, it also registers listener for updates which maintains the
     * sync.
     */
    private void syncData() {
        synchronized (mLock) {
            // Do not sync if keys have not been specified by the client.
            if (mKeysToSync == null || mKeysToSync.isEmpty()) {
                return;
            }

            bulkSyncData();

            // Register listener for syncing future updates
            getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(mListener);
        }
    }

    @GuardedBy("mLock")
    private void bulkSyncData() {
        // Collect data in a bundle
        final Bundle data = new Bundle();
        final SharedPreferences pref = getDefaultSharedPreferences();
        for (int i = 0; i < mTypeOfKey.size(); i++) {
            final String key = mTypeOfKey.keyAt(i);
            updateBundle(data, pref, key);
        }
        final SharedPreferencesUpdate update = new SharedPreferencesUpdate(mKeysToSync, data);
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
            if (mIsRunning && !mWaitingForSandbox && mListener == null) {
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
            if (!mIsRunning) {
                return;
            }

            // If we are not waiting for sandbox and we haven't registered a listener yet, then
            // this error is from initial bulk sync request.
            if (!mWaitingForSandbox
                    && mListener == null
                    && errorCode == ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE) {
                // Wait for sandbox to start. When it starts, server will call onSandboxStart
                mWaitingForSandbox = true;
                return;
            }
            mCallback.onError(errorCode, errorMsg);
            cleanUp();
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
                // Do not sync if sync has been stopped
                if (!mIsRunning) {
                    return;
                }

                if (key == null) {
                    // All keys have been cleared. Bulk sync so that we send null for every key.
                    bulkSyncData();
                    return;
                }
                if (mTypeOfKey == null || !mTypeOfKey.containsKey(key)) {
                    return;
                }

                final Bundle data = new Bundle();
                updateBundle(data, pref, key);

                final SharedPreferencesKey keyWithType =
                        new SharedPreferencesKey(key, mTypeOfKey.get(key));
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

        final int type = mTypeOfKey.get(key);
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

    // TODO(b/239403323): Move to SdkSandboxManager
    /** Callback to receive notification about sync status. */
    public interface SharedPreferencesSyncCallback {

        /**
         * Called when a pipeline for syncing has been successfully established.
         *
         * <p>All keys have been synced at least once and new updates will now be propagated to
         * sandbox automatically, until the sync is broken due to error or by calling {@link
         * stopSharedPreferencesSync}.
         */
        void onStart();

        /**
         * Called when sync is broken by calling {@link stopSharedPreferencesSync} api.
         *
         * <p>This breaks the sync and user needs to call {@link startSharedPreferenceSync} again to
         * restart syncing.
         */
        void onStop();

        // TODO(b/239403323): Create intdef for errorcodes.
        /**
         * Called when there is an error in the sync process.
         *
         * <p>This breaks the sync and user needs to call {@link startSharedPreferenceSync} again to
         * restart syncing.
         */
        void onError(int errorCode, String errorMsg);
    }
}
