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
    private final ChangeListener mListener = new ChangeListener();
    private final Object mLock = new Object();

    // TODO(b/239403323): Maintain a dynamic sync status based on lifecycle events
    @GuardedBy("mLock")
    private boolean mInitialSyncComplete = false;

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
    private ArrayList<KeyWithType> mKeysToSync = null;

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
     * @param keysWithTypeToSync set of keys that will be synced to Sandbox.
     * @return true if set of keys have been successfully updated, otherwise returns false.
     */
    public boolean setKeysToSync(@NonNull Set<KeyWithType> keysWithTypeToSync) {
        // TODO(b/239403323): Validate keysWithTypeToSync does not contain null.
        // TODO(b/239403323): Validate keysWithTypeToSync does not contain duplicate key name

        synchronized (mLock) {
            // TODO(b/239403323): Allow updating mKeysToSync
            if (mKeysToSync == null) {
                mTypeOfKey = new ArrayMap<>();
                for (KeyWithType keyWithType : keysWithTypeToSync) {
                    mTypeOfKey.put(keyWithType.getName(), keyWithType.getType());
                }
                mKeysToSync = new ArrayList<>(keysWithTypeToSync);
                return true;
            } else {
                return false;
            }
        }
    }

    // TODO(b/239403323): On sandbox restart, we need to sync again.
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
                    update);
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

                final KeyWithType keyWithType = new KeyWithType(key, mTypeOfKey.get(key));
                final SharedPreferencesUpdate update =
                        new SharedPreferencesUpdate(List.of(keyWithType), data);
                try {
                    mService.syncDataFromClient(
                            mContext.getPackageName(),
                            /*timeAppCalledSystemServer=*/ System.currentTimeMillis(),
                            update);
                } catch (RemoteException e) {
                    // TODO(b/239403323): Sandbox isn't available. We need to retry when it
                    // restarts.
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
                case KeyWithType.KEY_TYPE_STRING:
                    data.putString(key, pref.getString(key, ""));
                    break;
                case KeyWithType.KEY_TYPE_BOOLEAN:
                    data.putBoolean(key, pref.getBoolean(key, false));
                    break;
                case KeyWithType.KEY_TYPE_INTEGER:
                    data.putInt(key, pref.getInt(key, 0));
                    break;
                case KeyWithType.KEY_TYPE_FLOAT:
                    data.putFloat(key, pref.getFloat(key, 0.0f));
                    break;
                case KeyWithType.KEY_TYPE_LONG:
                    data.putLong(key, pref.getLong(key, 0L));
                    break;
                case KeyWithType.KEY_TYPE_STRING_SET:
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
