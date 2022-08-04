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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;

import java.util.Map;

// TODO(b/239403323): Implement support for live sync
// TODO(b/239403323): Make this class thread safe
/**
 * Syncs all keys in default {@link SharedPreferences} containing string values to Sdk Sandbox.
 *
 * @hide
 */
public class SharedPreferencesSyncManager {

    private static final String TAG = "SdkSandboxManager";

    private final ISdkSandboxManager mService;
    private final Context mContext;

    public SharedPreferencesSyncManager(Context context, ISdkSandboxManager service) {
        mContext = context.getApplicationContext();
        mService = service;
    }

    // TODO(b/239403323): Calling this multiple times should result in sycing once only.
    // TODO(b/239403323): On sandbox restart, we need to sync again.
    // TODO(b/239403323): Setup listeners for updates.
    // TODO(b/239403323): Also sync non-string values.
    /**
     * Sync data to SdkSandbox.
     *
     * <p>Currently syncs all string values from the default {@link SharedPreferences} of the app.
     */
    public void syncData() {
        final Bundle data = new Bundle();
        final SharedPreferences pref = getDefaultSharedPreferences();
        final Map<String, ?> allData = pref.getAll();
        for (Map.Entry<String, ?> entry : allData.entrySet()) {
            final String key = entry.getKey();
            if (entry.getValue() instanceof String) {
                data.putString(key, pref.getString(key, ""));
            }
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
}
