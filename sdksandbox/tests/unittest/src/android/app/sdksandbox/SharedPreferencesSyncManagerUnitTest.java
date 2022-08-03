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

import static com.google.common.truth.Truth.assertThat;

import android.annotation.Nullable;
import android.app.sdksandbox.testutils.StubSdkSandboxManagerService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;

import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Tests {@link SharedPreferencesSyncManager} APIs. */
@RunWith(JUnit4.class)
public class SharedPreferencesSyncManagerUnitTest {

    private SharedPreferencesSyncManager mSyncManager;
    private FakeSdkSandboxManagerService mSdkSandboxManagerService;
    private Context mContext;

    // TODO(b/239403323): Write test where we try to sync non-string values like null or object.
    private static final Map<String, String> TEST_DATA =
            Map.of("hello1", "world1", "hello2", "world2", "empty", "");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mSdkSandboxManagerService = new FakeSdkSandboxManagerService();
        mSyncManager = new SharedPreferencesSyncManager(mContext, mSdkSandboxManagerService);
    }

    @After
    public void tearDown() throws Exception {
        getDefaultSharedPreferences().edit().clear().commit();
    }

    // TODO(b/239403323): Bulk sync should be syncing a subset of keys as defined by app
    @Test
    public void test_syncData_syncsAllKeys() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);
        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        assertThat(mSdkSandboxManagerService.getCallingPackageName())
                .isEqualTo(mContext.getPackageName());
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(capturedData.getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    @Test
    public void test_syncData_multipleCalls() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);

        // Sync data multiple times
        mSyncManager.syncData();
        mSyncManager.syncData();

        // Verify that SyncManager bulk syncs only once
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(1);
    }

    @Test
    public void test_syncData_syncsAllKeys_ignoresUnsupportedValues() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);

        // Populate default shared preference with invalid data
        final SharedPreferences.Editor editor = getDefaultSharedPreferences().edit();
        editor.putBoolean("boolean", true);
        editor.putFloat("float", 1.2f);
        editor.putInt("int", 1);
        editor.putLong("long", 1L);
        editor.putStringSet("long", Set.of("value"));
        editor.commit();

        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(capturedData.getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    @Test
    public void test_syncData_registerListener_syncsFurtherUpdates() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);

        mSyncManager.syncData();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString("update", "value").commit();

        // Verify we registered a listener that called SdkSandboxManagerService
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).containsExactly("update");
    }

    /** Test that listener for live update is registered only once */
    @Test
    public void test_syncData_registerListener_onlyOnce() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);

        // Sync data multiple times
        mSyncManager.syncData();
        mSyncManager.syncData();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString("update", "value").commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(2);
    }

    /** Write all key-values provided in the map to app's default SharedPreferences */
    private void populateDefaultSharedPreference(Map<String, String> data) {
        final SharedPreferences.Editor editor = getDefaultSharedPreferences().edit();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = mContext.getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    private static class FakeSdkSandboxManagerService extends StubSdkSandboxManagerService {
        @GuardedBy("this")
        private ArrayList<Bundle> mDataCache = new ArrayList<>();

        @GuardedBy("this")
        private String mCallingPackageName = null;

        /** Gets updated when {@link blockForReceivingUpdates} is called. */
        private CountDownLatch mWaitForMoreUpdates = new CountDownLatch(0);

        @Override
        public synchronized void syncDataFromClient(String callingPackageName, Bundle data) {
            if (mCallingPackageName == null) {
                mCallingPackageName = callingPackageName;
            } else {
                assertThat(mCallingPackageName).isEqualTo(callingPackageName);
            }

            mDataCache.add(data);
            mWaitForMoreUpdates.countDown();
        }

        public synchronized String getCallingPackageName() {
            return mCallingPackageName;
        }

        @Nullable
        public synchronized Bundle getLastUpdate() {
            if (mDataCache.isEmpty()) {
                return null;
            }
            return mDataCache.get(mDataCache.size() - 1);
        }

        public synchronized int getNumberOfUpdatesReceived() {
            return mDataCache.size();
        }

        public void blockForReceivingUpdates(int numberOfUpdates) throws Exception {
            synchronized (this) {
                final int updatesNeeded = numberOfUpdates - getNumberOfUpdatesReceived();
                if (updatesNeeded <= 0) {
                    return;
                }
                mWaitForMoreUpdates = new CountDownLatch(updatesNeeded);
            }
            if (!mWaitForMoreUpdates.await(5000, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException(
                    "Failed to receive required number of updates. Required: "
                            + numberOfUpdates
                            + ", but found: "
                            + getNumberOfUpdatesReceived());
            }
        }
    }
}
