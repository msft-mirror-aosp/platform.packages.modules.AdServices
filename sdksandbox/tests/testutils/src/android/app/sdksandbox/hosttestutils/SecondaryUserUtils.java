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

package android.app.sdksandbox.hosttestutils;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

public class SecondaryUserUtils {

    private static final long SWITCH_USER_COMPLETED_NUMBER_OF_POLLS = 60;
    private static final long SWITCH_USER_COMPLETED_POLL_INTERVAL_IN_MILLIS = 1000;

    private final BaseHostJUnit4Test mTest;

    private int mOriginalUserId = -1;
    private int mSecondaryUserId = -1;

    public SecondaryUserUtils(BaseHostJUnit4Test test) {
        mTest = test;
    }

    public int createAndStartSecondaryUser() throws Exception {
        if (mSecondaryUserId != -1) {
            throw new IllegalStateException("Cannot create secondary user, it already exists");
        }
        mOriginalUserId = mTest.getDevice().getCurrentUser();
        String name = "SdkSandboxStorageHost_User" + System.currentTimeMillis();
        mSecondaryUserId = mTest.getDevice().createUser(name);
        mTest.getDevice().startUser(mSecondaryUserId);
        // Note we can't install apps on a locked user
        awaitUserUnlocked(mSecondaryUserId);
        return mSecondaryUserId;
    }

    private void awaitUserUnlocked(int userId) throws Exception {
        for (int i = 0; i < SWITCH_USER_COMPLETED_NUMBER_OF_POLLS; ++i) {
            String userState =
                    mTest.getDevice().executeShellCommand("am get-started-user-state " + userId);
            if (userState.contains("RUNNING_UNLOCKED")) {
                return;
            }
            Thread.sleep(SWITCH_USER_COMPLETED_POLL_INTERVAL_IN_MILLIS);
        }
        fail("Timed out in unlocking user: " + userId);
    }

    public void removeSecondaryUserIfNecessary() throws Exception {
        if (mOriginalUserId != -1 && mSecondaryUserId != -1) {
            // Can't remove the 2nd user without switching out of it
            assertThat(mTest.getDevice().switchUser(mOriginalUserId)).isTrue();
            mTest.getDevice().removeUser(mSecondaryUserId);
            waitForUserDataDeletion(mSecondaryUserId);
            mSecondaryUserId = -1;
        }
    }

    private void waitForUserDataDeletion(int userId) throws Exception {
        int timeElapsed = 0;
        final String deSdkSandboxDataRootPath = "/data/misc_de/" + userId + "/sdksandbox";
        while (timeElapsed <= 30000) {
            if (!mTest.getDevice().isDirectory(deSdkSandboxDataRootPath)) {
                return;
            }
            Thread.sleep(1000);
            timeElapsed += 1000;
        }
        throw new AssertionError("User data was not deleted for UserId " + userId);
    }
}
