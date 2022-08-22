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

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.annotation.Nullable;
import android.app.sdksandbox.SharedPreferencesSyncManager.SharedPreferencesSyncCallback;
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

    private static final String KEY_TO_UPDATE = "hello1";
    private static final SharedPreferencesKey KEY_WITH_TYPE_TO_UPDATE =
            new SharedPreferencesKey(KEY_TO_UPDATE, SharedPreferencesKey.KEY_TYPE_STRING);
    private static final Map<String, String> TEST_DATA =
            Map.of(KEY_TO_UPDATE, "world1", "hello2", "world2", "empty", "");
    private static final Set<SharedPreferencesKey> KEYS_TO_SYNC =
            Set.of(
                    new SharedPreferencesKey(KEY_TO_UPDATE, SharedPreferencesKey.KEY_TYPE_STRING),
                    new SharedPreferencesKey("hello2", SharedPreferencesKey.KEY_TYPE_STRING),
                    new SharedPreferencesKey("empty", SharedPreferencesKey.KEY_TYPE_STRING));

    private static final int INTERNAL_ERROR_CODE = ISharedPreferencesSyncCallback.INTERNAL_ERROR;
    private static final String INTERNAL_ERROR_MSG = "Some error occurred";
    private static final int SANDBOX_NOT_AVAILABLE_ERROR_CODE =
            ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE;
    private static final String SANDBOX_NOT_AVAILABLE_ERROR_MSG = "Sandbox has not started yet";

    private SharedPreferencesSyncManager mSyncManager;
    private FakeSdkSandboxManagerService mSdkSandboxManagerService;
    private SharedPreferencesSyncCallbackImpl mCallback;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mSdkSandboxManagerService = new FakeSdkSandboxManagerService();
        mCallback = new SharedPreferencesSyncCallbackImpl();
        mSyncManager = new SharedPreferencesSyncManager(mContext, mSdkSandboxManagerService);
    }

    @After
    public void tearDown() throws Exception {
        getDefaultSharedPreferences().edit().clear().commit();
    }

    @Test
    public void test_startSync_syncSpecifiedKeys() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);
        // Set specific shared keys that we want to sync
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(mSdkSandboxManagerService.getCallingPackageName())
                .isEqualTo(mContext.getPackageName());
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(capturedData.getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    @Test
    public void test_startSync_syncMissingKeys() throws Exception {
        // Set specific shared keys that we want to sync
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Verify that sync manager passes null for missing keys
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getKeysInUpdate()).containsExactlyElementsIn(KEYS_TO_SYNC);
        assertThat(update.getData().keySet()).isEmpty();
    }

    @Test
    public void test_startSync_ignoreUnspecifiedKeys() throws Exception {
        // Populate default shared preference and set specific keys for sycing
        populateDefaultSharedPreference(TEST_DATA);
        // Populate extra data outside of shared key list
        populateDefaultSharedPreference(Map.of("extraKey", "notSpecifiedByApi"));

        // Set specific shared keys that we want to sync
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
    }

    // TODO(b/239403323): Once error reporting is supported, we should return error to the user
    // instead.
    @Test
    public void test_startSync_ignoreValueOfWrongType() throws Exception {
        // Update key with a wrong type
        getDefaultSharedPreferences().edit().putFloat(KEY_TO_UPDATE, 1.0f).commit();

        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Verify that sync manager ignores wrong type
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.keySet()).doesNotContain(KEY_TO_UPDATE);
    }

    @Test
    public void test_startSync_supportsAllTypesOfValues() throws Exception {
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
        final Set<SharedPreferencesKey> keysToSync =
                Set.of(
                        new SharedPreferencesKey("string", SharedPreferencesKey.KEY_TYPE_STRING),
                        new SharedPreferencesKey("boolean", SharedPreferencesKey.KEY_TYPE_BOOLEAN),
                        new SharedPreferencesKey("float", SharedPreferencesKey.KEY_TYPE_FLOAT),
                        new SharedPreferencesKey("int", SharedPreferencesKey.KEY_TYPE_INTEGER),
                        new SharedPreferencesKey("long", SharedPreferencesKey.KEY_TYPE_LONG),
                        new SharedPreferencesKey("set", SharedPreferencesKey.KEY_TYPE_STRING_SET));
        mSyncManager.startSharedPreferencesSync(keysToSync, mCallback);

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.keySet()).hasSize(6);
        assertThat(capturedData.getString("string")).isEqualTo(pref.getString("string", ""));
        assertThat(capturedData.getBoolean("boolean")).isEqualTo(pref.getBoolean("boolean", false));
        assertThat(capturedData.getFloat("float")).isEqualTo(pref.getFloat("float", 0.0f));
        assertThat(capturedData.getInt("int")).isEqualTo(pref.getInt("int", 0));
        assertThat(capturedData.getLong("long")).isEqualTo(pref.getLong("long", 0L));
        assertThat(capturedData.getStringArrayList("set"))
                .containsExactlyElementsIn(pref.getStringSet("set", Collections.emptySet()));
    }

    @Test
    public void test_startSync_updateListener_syncsFurtherUpdates() throws Exception {
        // Set specified keys for sycing and register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final Bundle capturedData = mSdkSandboxManagerService.getLastUpdate().getData();
        assertThat(capturedData.keySet()).containsExactly(KEY_TO_UPDATE);
        assertThat(capturedData.getString(KEY_TO_UPDATE)).isEqualTo("update");
    }

    @Test
    public void test_startSync_updateListener_ignoresUnspecifiedKeys() throws Exception {
        // Set specified keys for sycing and register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString("unspecified_key", "update").commit();

        // Verify SdkSandboxManagerService does not receive the update for unspecified key
        Thread.sleep(5000);
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(1);
    }

    @Test
    public void test_startSync_updateListener_supportsAllTypesOfValues() throws Exception {
        // Set keys to sync and then sync data to register listener
        final Set<SharedPreferencesKey> keysToSync =
                Set.of(
                        new SharedPreferencesKey("string", SharedPreferencesKey.KEY_TYPE_STRING),
                        new SharedPreferencesKey("boolean", SharedPreferencesKey.KEY_TYPE_BOOLEAN),
                        new SharedPreferencesKey("float", SharedPreferencesKey.KEY_TYPE_FLOAT),
                        new SharedPreferencesKey("int", SharedPreferencesKey.KEY_TYPE_INTEGER),
                        new SharedPreferencesKey("long", SharedPreferencesKey.KEY_TYPE_LONG),
                        new SharedPreferencesKey("set", SharedPreferencesKey.KEY_TYPE_STRING_SET));
        mSyncManager.startSharedPreferencesSync(keysToSync, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Clear the bulk update for ease of reasoning
        mSdkSandboxManagerService.clearUpdates();

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
        final ArrayList<SharedPreferencesUpdate> allUpdates =
                mSdkSandboxManagerService.getAllUpdates();
        assertThat(allUpdates).hasSize(6);
        for (SharedPreferencesUpdate update : allUpdates) {
            final Bundle data = update.getData();
            assertThat(data.keySet()).hasSize(1);
            final String key = data.keySet().toArray()[0].toString();
            if (key.equals("string")) {
                assertThat(data.getString(key)).isEqualTo(pref.getString(key, ""));
            } else if (key.equals("boolean")) {
                assertThat(data.getBoolean(key)).isEqualTo(pref.getBoolean(key, false));
            } else if (key.equals("float")) {
                assertThat(data.getFloat(key)).isEqualTo(pref.getFloat(key, 0.0f));
            } else if (key.equals("int")) {
                assertThat(data.getInt(key)).isEqualTo(pref.getInt(key, 0));
            } else if (key.equals("long")) {
                assertThat(data.getLong(key)).isEqualTo(pref.getLong(key, 0L));
            } else if (key.equals("set")) {
                assertThat(data.getStringArrayList(key))
                        .containsExactlyElementsIn(pref.getStringSet(key, Collections.emptySet()));
            } else {
                fail("Unknown key found");
            }
        }
    }

    /** Test that we can handle removal of keys */
    @Test
    public void test_startSync_updateListener_removeKey() throws Exception {
        populateDefaultSharedPreference(TEST_DATA);
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().remove(KEY_TO_UPDATE).commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getData().keySet()).doesNotContain(KEY_TO_UPDATE);
        assertThat(update.getKeysInUpdate()).containsExactly(KEY_WITH_TYPE_TO_UPDATE);
    }

    /** Test that we can handle removal of keys by putting null */
    @Test
    public void test_startSync_updateListener_putNullValueForKey() throws Exception {
        populateDefaultSharedPreference(TEST_DATA);
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, null).commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getData().keySet()).doesNotContain(KEY_TO_UPDATE);
        assertThat(update.getKeysInUpdate()).containsExactly(KEY_WITH_TYPE_TO_UPDATE);
    }

    @Test
    public void test_startSync_updateListener_removeAllKeys() throws Exception {
        populateDefaultSharedPreference(TEST_DATA);
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Clear all keys
        getDefaultSharedPreferences().edit().clear().commit();

        // Verify that SyncManager tried to sync only twice: once for bulk and once for live update.
        mSdkSandboxManagerService.blockForReceivingUpdates(2);
        final SharedPreferencesUpdate lastUpdate = mSdkSandboxManagerService.getLastUpdate();
        assertThat(lastUpdate.getData().keySet()).isEmpty();
        assertThat(lastUpdate.getKeysInUpdate()).containsExactlyElementsIn(KEYS_TO_SYNC);
    }

    @Test
    public void test_startSync_multipleCalls_throwsException() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Calling start sync again throws exception
        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () -> mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback));
        assertThat(thrown).hasMessageThat().contains("Sync is already in progress");
    }

    @Test
    public void test_startSync_multipleCalls_stopFirst() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Stop the sync
        mSyncManager.stopSharedPreferencesSync();

        // Calling start sync again does not throw exception
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        // SdkSandboxManagerService should receive update for the second startSync call too
        assertThat(mSdkSandboxManagerService.getNumberOfUpdatesReceived()).isEqualTo(2);
    }

    @Test
    public void test_startSync_multipleCalls_updateKeysToSync() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Stop the sync
        mSyncManager.stopSharedPreferencesSync();

        // Start with new set of keys
        final SharedPreferencesKey newKey =
                new SharedPreferencesKey("new", SharedPreferencesKey.KEY_TYPE_STRING);
        mSyncManager.startSharedPreferencesSync(Set.of(newKey), mCallback);
        final SharedPreferencesUpdate update = mSdkSandboxManagerService.getLastUpdate();
        assertThat(update.getKeysInUpdate()).containsExactly(newKey);
    }

    @Test
    public void test_startSync_multipleCalls_updateListenerRegisteredOnce() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Stop the sync
        mSyncManager.stopSharedPreferencesSync();

        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Verify updating SharedPreferences results in only one update
        mSdkSandboxManagerService.clearUpdates(); // For cleaner observation
        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();
        // Only one update should be received
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(2));
    }

    @Test
    public void test_stopSync_callsOnStop() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Stop the sync
        mSyncManager.stopSharedPreferencesSync();

        // Verify on stop was called
        assertThat(mCallback.getOnStopCalled()).isTrue();
    }

    @Test
    public void test_stopSync_doesNotCallOnError() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Stop the sync
        mSyncManager.stopSharedPreferencesSync();

        // Verify on stop was called
        assertThat(mCallback.getOnErrorCalled()).isFalse();
    }

    @Test
    public void test_stopSync_updateListener_shouldNotUpdate() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Stop the sync
        mSyncManager.stopSharedPreferencesSync();

        mSdkSandboxManagerService.clearUpdates(); // For cleaner observation

        // Update the SharedPreference to trigger listeners
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();
        // Should not receive any updates
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(1));
    }

    @Test
    public void test_stopSync_multipleCalls() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Stop the sync
        assertThat(mSyncManager.stopSharedPreferencesSync()).isTrue();
        assertThat(mSyncManager.stopSharedPreferencesSync()).isFalse();
    }

    @Test
    public void test_onError_bulksync_callsCorrectCallback() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MSG);

        // Verify error is passed to callback
        assertThat(mCallback.getOnErrorCalled()).isTrue();
        assertThat(mCallback.getErrorCode()).isEqualTo(INTERNAL_ERROR_CODE);
        assertThat(mCallback.getErrorMsg()).isEqualTo(INTERNAL_ERROR_MSG);
        // Verify on stop is not called
        assertThat(mCallback.getOnStopCalled()).isFalse();
    }

    @Test
    public void test_onError_bulksync_canBeRestarted() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MSG);

        // Verify we can restart the sync
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
    }

    @Test
    public void test_onError_bulksync_stopsOnInternalError() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MSG);

        // Verify that sync is no longer running
        assertThat(mSyncManager.stopSharedPreferencesSync()).isFalse();
    }

    /** Test that we support starting sync before sandbox is created */
    @Test
    public void test_onError_bulksync_doesNotStopOnSandboxNotAvailableError() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Report sandbox has not been created
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);
        // Verify that sync was still running
        assertThat(mSyncManager.stopSharedPreferencesSync()).isTrue();
    }

    @Test
    public void test_onError_updateListener_callsCorrectCallback() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        mSdkSandboxManagerService.clearUpdates(); // For ease of reasoning
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Wait until update is received
        mSdkSandboxManagerService.blockForReceivingUpdates(1);
        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MSG);

        // Verify error is passed to callback
        assertThat(mCallback.getOnErrorCalled()).isTrue();
        assertThat(mCallback.getErrorCode()).isEqualTo(INTERNAL_ERROR_CODE);
        assertThat(mCallback.getErrorMsg()).isEqualTo(INTERNAL_ERROR_MSG);
        // Verify on stop is not called
        assertThat(mCallback.getOnStopCalled()).isFalse();
    }

    @Test
    public void test_onError_updateListener_canBeRestarted() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        mSdkSandboxManagerService.clearUpdates(); // For ease of reasoning
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Wait until update is received
        mSdkSandboxManagerService.blockForReceivingUpdates(1);
        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MSG);

        // Verify we can restart the sync
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
    }

    /** Test that we support starting sync before sandbox is created */
    @Test
    public void test_onError_updateListener_stopsOnSandboxNotAvailableError() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Update the SharedPreference to trigger listeners
        mSdkSandboxManagerService.clearUpdates(); // For ease of reasoning
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Wait until update is received
        mSdkSandboxManagerService.blockForReceivingUpdates(1);
        // Report an error via the callback
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);
        // Verify that sync was stopped
        assertThat(mSyncManager.stopSharedPreferencesSync()).isFalse();
    }

    @Test
    public void test_onError_updateListener_notRegisteredWhenWaitingForSandbox() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);
        mSdkSandboxManagerService.getLastCallback().onSuccess();

        // Send SyncManager to waiting state
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);

        // Update the SharedPreference to trigger listeners
        mSdkSandboxManagerService.clearUpdates(); // For ease of reasoning
        getDefaultSharedPreferences().edit().putString(KEY_TO_UPDATE, "update").commit();

        // Verify update not received
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(1));
    }

    @Test
    public void test_onSandboxStart_bulkSyncRetries() throws Exception {
        // Set keys to sync and then sync data to register listener
        mSyncManager.startSharedPreferencesSync(KEYS_TO_SYNC, mCallback);

        // Send SyncManager to waiting state
        mSdkSandboxManagerService
                .getLastCallback()
                .onError(SANDBOX_NOT_AVAILABLE_ERROR_CODE, SANDBOX_NOT_AVAILABLE_ERROR_MSG);

        // Notify syncmanager eventually when sandbox starts
        final ISharedPreferencesSyncCallback firstCallback =
                mSdkSandboxManagerService.getLastCallback();
        mSdkSandboxManagerService.getLastCallback().onSandboxStart();

        // Verify another bulk sync update is sent to SdkSandboxManagerService
        mSdkSandboxManagerService.blockForReceivingUpdates(2);

        // Notify again, but this time it should not trigger a new update since we were not waiting.
        mSdkSandboxManagerService.getLastCallback().onSandboxStart();
        firstCallback.onSandboxStart();
        assertThrows(
                TimeoutException.class,
                () -> mSdkSandboxManagerService.blockForReceivingUpdates(3));
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
        private ArrayList<SharedPreferencesUpdate> mUpdateCache = new ArrayList<>();

        @GuardedBy("this")
        private ISharedPreferencesSyncCallback mLastCallback = null;

        @GuardedBy("this")
        private String mCallingPackageName = null;

        /** Gets updated when {@link blockForReceivingUpdates} is called. */
        private CountDownLatch mWaitForMoreUpdates = new CountDownLatch(0);

        @Override
        public synchronized void syncDataFromClient(
                String callingPackageName,
                long timeAppCalledSystemServer,
                SharedPreferencesUpdate update,
                ISharedPreferencesSyncCallback callback) {
            if (mCallingPackageName == null) {
                mCallingPackageName = callingPackageName;
            } else {
                assertThat(mCallingPackageName).isEqualTo(callingPackageName);
            }

            mUpdateCache.add(update);
            mLastCallback = callback;
            mWaitForMoreUpdates.countDown();
        }

        public synchronized String getCallingPackageName() {
            return mCallingPackageName;
        }

        @Nullable
        public synchronized SharedPreferencesUpdate getLastUpdate() {
            if (mUpdateCache.isEmpty()) {
                throw new AssertionError(
                        "Fake SdkSandboxManagerService did not receive any update");
            }
            return mUpdateCache.get(mUpdateCache.size() - 1);
        }

        @Nullable
        public synchronized ISharedPreferencesSyncCallback getLastCallback() {
            return mLastCallback;
        }

        public synchronized ArrayList<SharedPreferencesUpdate> getAllUpdates() {
            return new ArrayList<>(mUpdateCache);
        }

        public synchronized int getNumberOfUpdatesReceived() {
            return mUpdateCache.size();
        }

        public synchronized void clearUpdates() {
            mUpdateCache.clear();
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

    private static class SharedPreferencesSyncCallbackImpl
            implements SharedPreferencesSyncCallback {
        private boolean mOnStartCalled = false;
        private boolean mOnStopCalled = false;
        private boolean mOnErrorCalled = false;

        private int mErrorCode = -1;
        private String mErrorMsg = "";

        private CountDownLatch mWaitForResponse = new CountDownLatch(1);

        @Override
        public void onStart() {
            mOnStartCalled = true;
            mWaitForResponse.countDown();
        }

        @Override
        public void onStop() {
            mOnStopCalled = true;
            mWaitForResponse.countDown();
        }

        @Override
        public void onError(int errorCode, String errorMsg) {
            mOnErrorCalled = true;
            mErrorCode = errorCode;
            mErrorMsg = errorMsg;
            mWaitForResponse.countDown();
        }

        public boolean getOnStartCalled() {
            return mOnStartCalled;
        }

        public boolean getOnStopCalled() {
            return mOnStopCalled;
        }

        public boolean getOnErrorCalled() {
            return mOnErrorCalled;
        }

        public int getErrorCode() throws Exception {
            waitForResponse();
            return mErrorCode;
        }

        public String getErrorMsg() throws Exception {
            waitForResponse();
            return mErrorMsg;
        }

        private void waitForResponse() throws Exception {
            if (!mWaitForResponse.await(5000, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Didn't receive response in 5000 ms");
            }
        }
    }
}
