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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.sdksandbox.verifier.DexParser.DexEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            File apkPathFile,
            String packagename,
            Context context,
            VerificationHandler verificationHandler) {

        mHandler.post(
                () -> {
                    List<DexEntry> dexEntries = null;
                    try {
                        dexEntries = mParser.getDexFilePaths(apkPathFile);
                    } catch (IOException e) {
                        verificationHandler.onVerificationErrorForPackage(e);
                        return;
                    }

                    File installedPackageFile = null;
                    List<String> verifiedEntries = new ArrayList<>();
                    boolean passedVerification = false;
                    try {
                        passedVerification =
                                verifyDexEntries(dexEntries, verifiedEntries, verificationHandler);
                        verificationHandler.onVerificationCompleteForPackage(passedVerification);
                        return;
                    } catch (IOException e) {
                        // tmp dex files were deleted while verifying, this happens when
                        // installation completes, try fetching installed apk to continue verifying
                        installedPackageFile = getInstalledPackageFile(packagename, context);
                        if (installedPackageFile == null) {
                            verificationHandler.onVerificationErrorForPackage(
                                    new Exception("apk files not found for " + packagename));
                            return;
                        }
                    }

                    // verify installed dex entries if we ran out of time to verify the tmp files
                    List<DexEntry> installedDexEntries = null;
                    try {
                        installedDexEntries = mParser.getDexFilePaths(installedPackageFile);
                    } catch (IOException e) {
                        verificationHandler.onVerificationErrorForPackage(e);
                        return;
                    }

                    // avoid verifying the same dex file twice
                    List<DexEntry> pendingDexEntries =
                            installedDexEntries.stream()
                                    .filter(
                                            entry ->
                                                    !verifiedEntries.contains(
                                                            entry.getEntryFilename()))
                                    .collect(Collectors.toList());

                    try {
                        passedVerification =
                                verifyDexEntries(
                                        pendingDexEntries, verifiedEntries, verificationHandler);
                        verificationHandler.onVerificationCompleteForPackage(passedVerification);
                        return;
                    } catch (IOException e) {
                        verificationHandler.onVerificationErrorForPackage(e);
                        return;
                    }
                });
    }

    private boolean verifyDexEntries(
            List<DexEntry> dexEntries,
            List<String> verifiedEntries,
            VerificationHandler verificationHandler)
            throws IOException {
        for (DexEntry entry : dexEntries) {
            mParser.loadDexSymbols(entry.getApkFile(), entry.getDexEntry(), mDexSymbols);
            if (!verificationHandler.verify(mDexSymbols)) {
                return false;
            }
            verifiedEntries.add(entry.getEntryFilename());
        }
        return true;
    }

    private File getInstalledPackageFile(String packagename, Context context) {
        try {
            ApplicationInfo applicationInfo =
                    context.getPackageManager()
                            .getPackageInfo(
                                    packagename,
                                    PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES
                                            | PackageManager.MATCH_ANY_USER)
                            .applicationInfo;
            return new File(applicationInfo.sourceDir);
        } catch (NameNotFoundException e) {
            return null;
        }
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
