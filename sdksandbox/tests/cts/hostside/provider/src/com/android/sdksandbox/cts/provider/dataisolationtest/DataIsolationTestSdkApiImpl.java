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

package com.android.sdksandbox.cts.provider.dataisolationtest;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class DataIsolationTestSdkApiImpl extends IDataIsolationTestSdkApi.Stub {
    private final Context mContext;

    private static final String TAG = "SdkSandboxDataIsolationTestProvider";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";
    private static final String JAVA_IS_A_DIRECTORY_ERROR_MSG =
            "open failed: EISDIR (Is a directory)";

    public DataIsolationTestSdkApiImpl(Context sdkContext) {
        mContext = sdkContext;
    }

    @Override
    public void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() {
        verifyDirectoryAccess(mContext.getDataDir().toString(), true);
    }

    private void verifyDirectoryAccess(String path, boolean shouldBeAccessible) {
        File file = new File(path);
        try {
            new FileInputStream(file);
        } catch (FileNotFoundException exception) {
            String exceptionMsg = exception.getMessage();
            if (shouldBeAccessible) {
                if (!exceptionMsg.contains(JAVA_IS_A_DIRECTORY_ERROR_MSG)) {
                    throw new IllegalStateException(
                            path + " should be accessible, but received error: " + exceptionMsg);
                }
            } else if (!exceptionMsg.contains(JAVA_FILE_NOT_FOUND_MSG)
                    || exceptionMsg.contains(JAVA_FILE_PERMISSION_DENIED_MSG)
                    || exceptionMsg.contains(JAVA_IS_A_DIRECTORY_ERROR_MSG)) {
                throw new IllegalStateException(
                        "Accessing "
                                + path
                                + " should have shown ENOENT error, but received error: "
                                + exceptionMsg);
            }
        }
    }
}
