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

import static org.mockito.Mockito.any;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

/** Tests {@link SharedPreferencesSyncManager} APIs. */
@RunWith(JUnit4.class)
public class SharedPreferencesSyncManagerUnitTest {

    private SharedPreferencesSyncManager mSyncManager;
    private ISdkSandboxManager mSpySdkSandboxManager;
    private Context mContext;

    // TODO(b/239403323): Write test where we try to sync non-string values like null or object.
    private static final Map<String, String> TEST_DATA =
            Map.of("hello1", "world1", "hello2", "world2", "empty", "");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        // TODO(b/239403323): Consider using a Fake instead.
        mSpySdkSandboxManager = Mockito.spy(ISdkSandboxManager.Stub.class);
        mSyncManager = new SharedPreferencesSyncManager(mContext, mSpySdkSandboxManager);
    }

    @After
    public void tearDown() throws Exception {
        getDefaultSharedPreferences().edit().clear().commit();
    }

    // TODO(b/239403323): Bulk sync should be syncing a subset of keys as defined by app
    @Test
    public void test_bulkSyncData_syncsAllKeys() throws Exception {
        // Populate default shared preference with test data
        populateDefaultSharedPreference(TEST_DATA);
        mSyncManager.syncData();

        // Verify that sync manager passes the correct data to SdkSandboxManager
        final ArgumentCaptor<String> packageNameCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Bundle> dataCaptor = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mSpySdkSandboxManager)
                .syncDataFromClient(packageNameCaptor.capture(), dataCaptor.capture());

        assertThat(packageNameCaptor.getValue()).isEqualTo(mContext.getPackageName());
        assertThat(dataCaptor.getValue().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(dataCaptor.getValue().getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    @Test
    public void test_bulkSyncData_syncsAllKeys_ignoresUnsupportedValues() throws Exception {
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
        final ArgumentCaptor<Bundle> dataCaptor = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(mSpySdkSandboxManager).syncDataFromClient(any(), dataCaptor.capture());

        assertThat(dataCaptor.getValue().keySet()).containsExactlyElementsIn(TEST_DATA.keySet());
        for (String key : TEST_DATA.keySet()) {
            assertThat(dataCaptor.getValue().getString(key)).isEqualTo(TEST_DATA.get(key));
        }
    }

    /** Write all key-values provided in the map to app's default SharedPreferences */
    private void populateDefaultSharedPreference(Map<String, String> data) {
        final SharedPreferences.Editor editor = getDefaultSharedPreferences().edit();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.commit();
    }

    private SharedPreferences getDefaultSharedPreferences() {
        final Context appContext = mContext.getApplicationContext();
        return PreferenceManager.getDefaultSharedPreferences(appContext);
    }
}
