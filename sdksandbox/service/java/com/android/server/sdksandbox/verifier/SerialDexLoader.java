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

package com.android.server.sdksandbox.verifier;

import android.os.Handler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the loading of dex files for multiple apks to be verified, ensures that a single dex file
 * is loaded at any time.
 *
 * @hide
 */
public class SerialDexLoader {
    private static final String TAG = "SdkSandboxVerifier";

    private DexParser mParser;
    private Handler mHandler;
    private DexLoadResult mDexLoadResult;

    public SerialDexLoader(DexParser parser, Handler handler) {
        mParser = parser;
        mHandler = handler;
        mDexLoadResult = new DexLoadResult();
    }

    /**
     * Queues all dex files found for an apk for serially loading and analyzing.
     *
     * @param apkPathFile path to apk containing one or more dex files
     * @param packagename packagename associated with the apk
     * @param verificationHandler object to handle the verification of the loaded dex
     */
    public void queueApkToLoad(
            File apkPathFile, String packagename, VerificationHandler verificationHandler) {

        mHandler.post(
                () -> {
                    Map<File, List<String>> dexEntries;
                    try {
                        dexEntries = mParser.getDexFilePaths(apkPathFile);
                    } catch (IOException e) {
                        verificationHandler.onVerificationErrorForPackage(e);
                        return;
                    }

                    for (Map.Entry<File, List<String>> dexFileEntries : dexEntries.entrySet()) {
                        for (String dexEntry : dexFileEntries.getValue()) {
                            try {
                                mParser.loadDexSymbols(
                                        dexFileEntries.getKey(), dexEntry, mDexLoadResult);
                            } catch (IOException e) {
                                verificationHandler.onVerificationErrorForPackage(e);
                                return;
                            }
                            if (!verificationHandler.verify(mDexLoadResult)) {
                                verificationHandler.onVerificationCompleteForPackage(false);
                                return;
                            }
                        }
                    }

                    verificationHandler.onVerificationCompleteForPackage(true);
                });
    }

    /** Interface for handling processing of the loaded dex contents */
    public interface VerificationHandler {

        /**
         * Takes in the DexLoadResult and verifies its contents.
         *
         * @param result object contains the symbols parsed from the loaded dex file
         */
        boolean verify(DexLoadResult result);

        /**
         * Called when all the loaded dex files have passed verification, or when one has failed.
         *
         * @param passed is false if the last loaded dex failed verification, or true if all dexes
         *     passed.
         */
        void onVerificationCompleteForPackage(boolean result);

        /**
         * Error occurred on verifying.
         *
         * @param e exception thrown while attempting to load and verify the apk.
         */
        void onVerificationErrorForPackage(Exception e);
    }

    /** Result class that contains symbols loaded from a DEX file */
    public static class DexLoadResult {

        public static final int DEX_MAX_METHOD_COUNT = 65536;

        /** The table of methods referenced by the DEX file. */
        private ArrayList<String> mReferencedMethods = new ArrayList<>(DEX_MAX_METHOD_COUNT);

        /** Adds a new method to the referencedMethods table */
        public void addReferencedMethod(String method) {
            mReferencedMethods.add(method);
        }

        /** Returns true if the method string is present in the referencedMethods table */
        public boolean hasReferencedMethod(String method) {
            return mReferencedMethods.contains(method);
        }

        /** Clears the internal state of DexLoadResult to load next dex file. */
        public void clear() {
            mReferencedMethods.clear();
        }
    }
}
