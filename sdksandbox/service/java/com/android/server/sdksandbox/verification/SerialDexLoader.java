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

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the loading of dex files for multiple apks to be verified, ensures that a single dex file
 * is loaded at any time.
 */
public class SerialDexLoader {
    private static final String TAG = "SdkSandboxVerifier";

    private DexParser mParser;
    private Handler mHandler;
    private DexSymbols mDexSymbols;

    public SerialDexLoader(DexParser parser, Handler handler) {
        mParser = parser;
        mHandler = handler;
        mDexSymbols = new DexSymbols();
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
                                        dexFileEntries.getKey(), dexEntry, mDexSymbols);
                            } catch (IOException e) {
                                verificationHandler.onVerificationErrorForPackage(e);
                                return;
                            }
                            if (!verificationHandler.verify(mDexSymbols)) {
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
         * Takes in the DexSymbols and verifies its contents.
         *
         * @param result object contains the symbols parsed from the loaded dex file
         */
        boolean verify(DexSymbols result);

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
    public static class DexSymbols {

        private static final int DEX_MAX_METHOD_COUNT = 65536;

        private String mDexEntry;

        /** The table of classes referenced by the DEX file. */
        private ArrayList<String> mReferencedClasses = new ArrayList<>(DEX_MAX_METHOD_COUNT);

        /** The table of methods referenced by the DEX file. */
        private ArrayList<String> mReferencedMethods = new ArrayList<>(DEX_MAX_METHOD_COUNT);

        /** Maps referenced methods to their declaring class in the referenced classes table. */
        private ArrayList<Integer> mClassIndex = new ArrayList<>(DEX_MAX_METHOD_COUNT);

        /**
         * Adds a new method to the referencedMethods table and its containing class to the
         * referenced classes table if it's different to the last seen class.
         *
         * <p>Referenced methods should be added in the order that they are present in the methods
         * table from the dex file.
         *
         * @param classname describes the class with / as separator of its subpackages
         * @param method the method name, parameter types and return types with ; as separator
         */
        public void addReferencedMethod(String classname, String method) {
            // the method table is sorted by class, so new classnames can be stored when first
            // encountered
            if (mReferencedClasses.size() == 0
                    || !mReferencedClasses.get(mReferencedClasses.size() - 1).equals(classname)) {
                mReferencedClasses.add(classname);
            }
            mReferencedMethods.add(method);
            mClassIndex.add(mReferencedClasses.size() - 1);
        }

        @VisibleForTesting
        boolean hasReferencedMethod(String classname, String method) {
            int methodIdx = mReferencedMethods.indexOf(method);
            return methodIdx >= 0
                    && mReferencedClasses.get(mClassIndex.get(methodIdx)).equals(classname);
        }

        /**
         * Clears the internal state of DexSymbols and sets dex entry name to load next dex file.
         */
        public void clearAndSetDexEntry(String dexEntry) {
            this.mDexEntry = dexEntry;
            mReferencedClasses.clear();
            mReferencedMethods.clear();
            mClassIndex.clear();
        }

        /** Returns the number of referenced methods loaded for the current dex */
        public int getReferencedMethodCount() {
            return mReferencedMethods.size();
        }

        /**
         * Returns the method indexed by methodIndex in the table of loaded methods from the dex
         * file
         */
        public String getReferencedMethodAtIndex(int methodIndex) {
            if (methodIndex < 0 || methodIndex >= mReferencedMethods.size()) {
                throw new IndexOutOfBoundsException("Method index out of bounds: " + methodIndex);
            }
            return mReferencedMethods.get(methodIndex);
        }

        /** Returns the declaring class for the method indexed by methodIndex */
        public String getClassForMethodAtIndex(int methodIndex) {
            if (methodIndex < 0 || methodIndex >= mReferencedMethods.size()) {
                throw new IndexOutOfBoundsException("Method index out of bounds: " + methodIndex);
            }
            return mReferencedClasses.get(mClassIndex.get(methodIndex));
        }

        @Override
        public String toString() {
            return "DexSymbols: " + mDexEntry;
        }
    }
}
