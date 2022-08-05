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

import com.android.internal.annotations.GuardedBy;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Syncs all keys in default {@link SharedPreferences} containing string values to Sdk Sandbox.
 *
 * @hide
 */
public class SharedPreferencesSyncManager {

    private static final String TAG = "SdkSandboxManager";

    private final ISdkSandboxManager mService;
    private final Context mContext;
    private final ChangeListener mListener = new ChangeListener();
    private final Object mLock = new Object();

    // TODO(b/239403323): Maintain a dynamic sync status based on lifecycle events
    @GuardedBy("mLock")
    private boolean mInitialSyncComplete = false;

    // List of keys that this manager needs to keep in sync.
    @Nullable
    @GuardedBy("mLock")
    private Set<String> mKeysToSync = null;

    public SharedPreferencesSyncManager(
            @NonNull Context context, @NonNull ISdkSandboxManager service) {
        mContext = context.getApplicationContext();
        mService = service;
    }

    /**
     * Set of keys which the sync manager should be syncing to Sandbox.
     *
     * <p>Keys outside of this list will be ignored. This method should be called only once.
     * Subsequent calls won't update the list of keys being synced.
     *
     * @param keysToSync set of keys that will be synced to Sandbox. Must not be null.
     * @return true if set of keys have been successfully updated, otherwise returns false.
     */
    public boolean setKeysToSync(@NonNull Set<String> keysToSync) {
        // TODO(b/239403323): Validate keysToSync does not contain null.
        Objects.requireNonNull(keysToSync, "keysToSync must not be null");
        synchronized (mLock) {
            // TODO(b/239403323): Allow updating mKeysToSync
            if (mKeysToSync == null) {
                mKeysToSync = keysToSync;
                return true;
            } else {
                return false;
            }
        }
    }

    // TODO(b/239403323): On sandbox restart, we need to sync again.
    // TODO(b/239403323): Also sync non-string values.
    /**
     * Sync data to SdkSandbox.
     *
     * <p>Currently syncs all string values from the default {@link SharedPreferences} of the app.
     *
     * <p>Once bulk sync is complete, it also registers listener for updates which maintains the
     * sync.
     *
     * <p>This method is idempotent. Calling it multiple times has same affect as calling it once.
     */
    public void syncData() {
        synchronized (mLock) {
            // Do not sync if keys have not been specified by the client.
            if (mKeysToSync == null || mKeysToSync.isEmpty()) {
                return;
            }

            if (!mInitialSyncComplete) {
                bulkSyncData();

                // Register listener for syncing future updates
                getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(mListener);

                // TODO(b/239403323): We can get out of sync if listener fails to propagate live
                // updates.
                mInitialSyncComplete = true;
            }
        }
    }

    @GuardedBy("mLock")
    private void bulkSyncData() {
        final Bundle data = new Bundle();
        final SharedPreferences pref = getDefaultSharedPreferences();
        final Map<String, ?> allData = pref.getAll();
        for (Map.Entry<String, ?> entry : allData.entrySet()) {
            final String key = entry.getKey();
            // Sync only specified keys
            if (!mKeysToSync.contains(key)) {
                continue;
            }
            if (entry.getValue() instanceof String) {
                data.putString(key, pref.getString(key, ""));
            }
        }

        // No need to sync if there data is empty
        if (data.isEmpty()) {
            return;
        }

        try {
            mService.syncDataFromClient(mContext.getPackageName(), data);
        } catch (RemoteException ignore) {
            // TODO(b/239403323): Sandbox isn't available. We need to retry when it restarts.
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
                if (key == null || mKeysToSync == null || !mKeysToSync.contains(key)) {
                    return;
                }
            }
            final Bundle data = new Bundle();
            final Object value = pref.getAll().get(key);
            if (!(value instanceof String)) {
                // TODO(b/239403323): Add support for non-string values
                return;
            }

            // TODO(b/239403323): Support removal of keys
            data.putString(key, pref.getString(key, ""));

            try {
                mService.syncDataFromClient(mContext.getPackageName(), data);
            } catch (RemoteException e) {
                // TODO(b/239403323): Sandbox isn't available. We need to retry when it restarts.
            }
        }
    }
}
