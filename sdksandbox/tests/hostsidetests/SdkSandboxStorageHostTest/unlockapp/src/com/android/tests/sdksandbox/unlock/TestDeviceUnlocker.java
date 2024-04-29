/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tests.sdksandbox.unlock;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unlock user by entering PIN (1-2-3-4). Test app should be direct boot aware to allow startup on
 * locked user.
 */
@RunWith(JUnit4.class)
public class TestDeviceUnlocker {

    @Test
    public void unlockDevice() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        if (context.getSystemService(UserManager.class).isUserUnlocked()) {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        latch.countDown();
                    }
                };

        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        enterPinAndUnlock();

        boolean userUnlocked = latch.await(10, TimeUnit.SECONDS);
        assertWithMessage("Failed to unlock user").that(userUnlocked).isTrue();
    }

    private void enterPinAndUnlock() throws Exception {
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        uiDevice.wakeUp();

        // Press back in case the PIN pad (or other message) is already showing.
        uiDevice.pressBack();

        uiDevice.pressMenu();
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        uiDevice.pressEnter();

        uiDevice.pressHome();
        uiDevice.waitForIdle();
    }
}
