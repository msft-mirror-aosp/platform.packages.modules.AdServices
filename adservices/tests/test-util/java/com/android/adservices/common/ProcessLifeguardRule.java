/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.common;

import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Rule used to protect the test process from crashing if an uncaught exception is thrown in the
 * background.
 *
 * <p><b>NOTE: </b>once this rule is used, it will call {@link
 * Thread#setUncaughtExceptionHandler(java.lang.Thread.UncaughtExceptionHandler)} and never reset
 * it.
 */
public final class ProcessLifeguardRule extends AbstractProcessLifeguardRule {

    public ProcessLifeguardRule() {
        super(AndroidLogger.getInstance());
    }

    @Override
    protected boolean isMainThread() {
        return Looper.getMainLooper().isCurrentThread();
    }

    @Override
    protected UncaughtBackgroundException newUncaughtBackgroundException(
            String testName,
            List<String> allTests,
            List<String> lastTests,
            Throwable uncaughtThrowable) {
        saveToSdCard(testName, allTests, lastTests, /* testFailure= */ null, uncaughtThrowable);
        return super.newUncaughtBackgroundException(
                testName, allTests, lastTests, uncaughtThrowable);
    }

    @Override
    protected UncaughtBackgroundException newUncaughtBackgroundException(
            String testName,
            List<String> allTests,
            List<String> lastTests,
            Throwable testFailure,
            Throwable uncaughtThrowable) {
        saveToSdCard(testName, allTests, lastTests, testFailure, uncaughtThrowable);
        return super.newUncaughtBackgroundException(
                testName, allTests, lastTests, testFailure, uncaughtThrowable);
    }

    private void saveToSdCard(
            String testName,
            List<String> allTests,
            List<String> lastTests,
            @Nullable Throwable testFailure,
            Throwable uncaughtThrowable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        sw.append("Uncaught failure: ");
        uncaughtThrowable.printStackTrace(pw);

        if (testFailure != null) {
            sw.append("Test failure: ");
            testFailure.printStackTrace(pw);
        }

        sw.append("" + allTests.size()).append(" total tests:\n");
        allTests.forEach(t -> sw.append('\t').append(t).append('\n'));
        sw.append("" + lastTests.size()).append(" tests since last failure:\n");
        lastTests.forEach(t -> sw.append('\t').append(t).append('\n'));

        // TODO(b/303112789): un-hardcode /Documents
        writeFile(
                "Documents",
                getClass().getSimpleName()
                        + "-"
                        + testName
                        + "-"
                        + System.currentTimeMillis()
                        + ".txt",
                sw.toString());
    }

    // TODO(b/303112789): move to common class
    private void writeFile(String directory, String filename, String contents) {
        // TODO(b/303112789): un-hardcode /sdcard
        String path = "/sdcard/" + directory;
        Path filePath = Paths.get(path, filename);
        try {
            mLog.i("Creating file %s", filePath);
            Files.createFile(filePath);
            byte[] bytes = contents.getBytes();
            mLog.d("Writing %d bytes to %s", bytes.length, filePath);
            Files.write(filePath, bytes);
            mLog.d("Saul Goodman!");
        } catch (Exception e) {
            mLog.e(e, "Failed to save %s", filePath);
        }
    }
}
