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

import static org.junit.Assert.fail;

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
import java.util.Collections;
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
    private static final String KEY_TO_UPDATE = "hello1";
    private static final Map<String, String> TEST_DATA =
            Map.of(KEY_TO_UPDATE, "world1", "hello2", "world2", "empty", "");
    private static final Set<KeyWithType> KEYS_TO_SYNC =
            Set.of(
                    new KeyWithType(KEY_TO_UPDATE, KeyWithType.KEY_TYPE_STRING),
                    new KeyWithType("hello2", KeyWithType.KEY_TYPE_STRING),
                    new KeyWithType("empty", KeyWithType.KEY_TYPE_STRING));

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

    @Test
    public void test_syncData_doesNotSyncIfKeysNotSpecified() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);

        // Sync data without specifying list of keys to sync
        mSyncManager.syncData();

        // Verify that sync manager does not try to sync at all
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(0);
    }

    @Test
    public void test_syncData_doesNotSyncEmptyUpdates() throws Exception {
        // Unpopulated shared preference. There is nothing to sync.
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);

        mSyncManager.syncData();

        // Verify that sync manager does not try to sync with empty data
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(0);
    }

    @Test
    public void test_syncData_syncSpecifiedKeys() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);
        // Set specific shared keys that we want to sync
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);

        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(mSdkSandboxManagerService.getCallingPackageName())
                .isEqualTo(mContext.getPackageName());
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(capturedData.getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    @Test
    public void test_syncData_ignoreUnspecifiedKeys() throws Exception {
        // Populate default shared preference and set specific keys for sycing
        populateDefaultSharedPreference(TEST_DATA);
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);

        // Populate extra data outside of shared key list
        populateDefaultSharedPreference(Map.of("extraKey", "notSpecifiedByApi"));

        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
    }

    // TODO(b/239403323): Once error reporting is supported, we should return error to the user
    // instead.
    @Test
    public void test_syncData_ignoreValueOfWrongType() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);

        // Update key with a wrong type
        getDefaultSharedPreferences().edit().putFloat(KEY_TO_UPDATE, 1.0f).commit();

        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).doesNotContain(KEY_TO_UPDATE);
    }

    @Test
    public void test_syncData_multipleCalls() throws Exception {
        // Populate default shared preference and set specific keys for sycing
        populateDefaultSharedPreference(TEST_DATA);
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);

        // Sync data multiple times
        mSyncManager.syncData();
        mSyncManager.syncData();

        // Verify that SyncManager bulk syncs only once
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(1);
    }

    @Test
    public void test_syncData_supportsAllTypesOfValues() throws Exception {
        // Populate default shared preference with all valid types

        final SharedPreferences pref = getDefaultSharedPreferences();
        final SharedPreferences.Editor editor = pref.edit();
        editor.putString("string", "value");
        editor.putBoolean("boolean", true);
        editor.putFloat("float", 1.2f);
        editor.putInt("int", 1);
        editor.putLong("long", 1L);
        editor.putStringSet("set", Set.of("value"));
        editor.commit();

        // Set keys to sync and then sync data
        final Set<KeyWithType> keysToSync =
                Set.of(
                        new KeyWithType("string", KeyWithType.KEY_TYPE_STRING),
                        new KeyWithType("boolean", KeyWithType.KEY_TYPE_BOOLEAN),
                        new KeyWithType("float", KeyWithType.KEY_TYPE_FLOAT),
                        new KeyWithType("int", KeyWithType.KEY_TYPE_INTEGER),
                        new KeyWithType("long", KeyWithType.KEY_TYPE_LONG),
                        new KeyWithType("set", KeyWithType.KEY_TYPE_STRING_SET));
        mSyncManager.setKeysToSync(keysToSync);
        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).hasSize(6);
        assertThat(capturedData.getString("string")).isEqualTo(pref.getString("string", ""));
        assertThat(capturedData.getBoolean("boolean")).isEqualTo(pref.getBoolean("boolean", false));
        assertThat(capturedData.getFloat("float")).isEqualTo(pref.getFloat("float", 0.0f));
        assertThat(capturedData.getInt("int")).isEqualTo(pref.getInt("int", 0));
        assertThat(capturedData.getLong("long")).isEqualTo(pref.getLong("long", 0L));
        assertThat(capturedData.getStringArrayList("set"))
                .containsExactlyElementsIn(pref.getStringSet("set", Collections.emptySet()));
    }

    // TODO(b/239403323): We probably want to allow client update this the list dynamically.
    @Test
    public void test_setKeysToSync_canBeSetOnlyOnce() throws Exception {
        // Populate default shared preference and set specific keys for sycing
        populateDefaultSharedPreference(TEST_DATA);
        // Setting keys to sync for the first time should return true
        assertThat(mSyncManager.setKeysToSync(KEYS_TO_SYNC)).isTrue();

        // Try to update keys to sync again
        assertThat(mSyncManager.setKeysToSync(Collections.emptySet())).isFalse();

        mSyncManager.syncData();

        // Verify that sync manager is still using first set of keys
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
    }

    @Test
    public void test_updateListener_syncsFurtherUpdates() throws Exception {
        // Set specified keys for sycing and register listener
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);
        mSyncManager.syncData();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Verify we registered a listener that called SdkSandboxManagerService
        mSdkSandboxManagerService.blockForReceivingUpdates(1);
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate();
        assertThat(capturedData.keySet()).containsExactly(KEY_TO_UPDATE);
        assertThat(capturedData.getString(KEY_TO_UPDATE)).isEqualTo("update");
    }

    @Test
    public void test_updateListener_ignoresUnspecifiedKeys() throws Exception {
        // Set specified keys for sycing and register listener
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);
        mSyncManager.syncData();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString("unspecified_key", "update").commit();

        // Verify SdkSandboxManagerService does not receive the update for unspecified key
        Thread.sleep(5000);
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(0);
    }

    @Test
    public void test_updateListener_supportsAllTypesOfValues() throws Exception {
        // Set keys to sync and then sync data to register listener
        final Set<KeyWithType> keysToSync =
                Set.of(
                        new KeyWithType("string", KeyWithType.KEY_TYPE_STRING),
                        new KeyWithType("boolean", KeyWithType.KEY_TYPE_BOOLEAN),
                        new KeyWithType("float", KeyWithType.KEY_TYPE_FLOAT),
                        new KeyWithType("int", KeyWithType.KEY_TYPE_INTEGER),
                        new KeyWithType("long", KeyWithType.KEY_TYPE_LONG),
                        new KeyWithType("set", KeyWithType.KEY_TYPE_STRING_SET));
        mSyncManager.setKeysToSync(keysToSync);
        mSyncManager.syncData();

        // Update the shared preference
        final SharedPreferences pref = getDefaultSharedPreferences();
        final SharedPreferences.Editor editor = pref.edit();
        editor.putString("string", "value");
        editor.putBoolean("boolean", true);
        editor.putFloat("float", 1.2f);
        editor.putInt("int", 1);
        editor.putLong("long", 1L);
        editor.putStringSet("set", Set.of("value"));
        editor.commit();

        // Verify that sync manager receives one bundle for each key update
        mSdkSandboxManagerService.blockForReceivingUpdates(6);
        final ArrayList<Bundle> allUpdates = mSdkSandboxManagerService.getAllUpdates();
        assertThat(allUpdates).hasSize(6);
        for (Bundle update : allUpdates) {
            assertThat(update.keySet()).hasSize(1);
            final String key = update.keySet().toArray()[0].toString();
            if (key.equals("string")) {
                assertThat(update.getString(key)).isEqualTo(pref.getString(key, ""));
            } else if (key.equals("boolean")) {
                assertThat(update.getBoolean(key)).isEqualTo(pref.getBoolean(key, false));
            } else if (key.equals("float")) {
                assertThat(update.getFloat(key)).isEqualTo(pref.getFloat(key, 0.0f));
            } else if (key.equals("int")) {
                assertThat(update.getInt(key)).isEqualTo(pref.getInt(key, 0));
            } else if (key.equals("long")) {
                assertThat(update.getLong(key)).isEqualTo(pref.getLong(key, 0L));
            } else if (key.equals("set")) {
                assertThat(update.getStringArrayList(key))
                        .containsExactlyElementsIn(pref.getStringSet(key, Collections.emptySet()));
            } else {
                fail("Unknown key found");
            }
        }
    }

    /** Test that listener for live update is registered only once */
    @Test
    public void test_updateListener_registersOnlyOnce() throws Exception {
        // Populate default shared preference and set specific keys for sycing
        populateDefaultSharedPreference(TEST_DATA);
        mSyncManager.setKeysToSync(KEYS_TO_SYNC);

        // Sync data multiple times
        mSyncManager.syncData();
        mSyncManager.syncData();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

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
        public synchronized void syncDataFromClient(
                String callingPackageName,
                long timeAppCalledSystemServer,
                SharedPreferencesUpdate update) {
            if (mCallingPackageName == null) {
                mCallingPackageName = callingPackageName;
            } else {
                assertThat(mCallingPackageName).isEqualTo(callingPackageName);
            }

            mDataCache.add(update.getData());
            mWaitForMoreUpdates.countDown();
        }

        public synchronized String getCallingPackageName() {
            return mCallingPackageName;
        }

        @Nullable
        public synchronized Bundle getLastUpdate() {
            if (mDataCache.isEmpty()) {
                throw new AssertionError(
                        "Fake SdkSandboxManagerService did not receive any update");
            }
            return new Bundle(mDataCache.get(mDataCache.size() - 1));
        }

        public synchronized ArrayList<Bundle> getAllUpdates() {
            return new ArrayList<>(mDataCache);
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
