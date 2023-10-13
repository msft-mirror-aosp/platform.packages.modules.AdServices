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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.SystemClock;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.sdksandbox.proto.Verifier.AllowedApi;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisList;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisPerTargetSdk;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the SDK being installed against the APIs allowlist. Verification runs on DEX files of
 * the SDK package.
 *
 * @hide
 */
public class SdkDexVerifier {

    private final Object mPlatformApiAllowlistsLock = new Object();

    private static final String TAG = "SdkSandboxVerifier";

    private static final String ALLOW_LIST_PROTO_CURRENT_RES =
            "platform_api_allowlist_per_target_sdk_version_current.binarypb";

    private static final String WILDCARD = "*";
    private static final String EMPTY_STRING = "";

    private static SdkDexVerifier sSdkDexVerifier;

    // Maps targetSdkVersion to its allowlist
    @GuardedBy("mPlatformApiAllowlistsLock")
    private Map<Long, AllowedApisList> mPlatformApiAllowlists;

    @GuardedBy("mPlatformApiAllowlistsLock")
    private Map<Long, StringTrie> mPlatformApiAllowTries;

    private SdkDexVerifier() {
        // TODO(b/279165123): initialize dex parser

        synchronized (mPlatformApiAllowlistsLock) {
            mPlatformApiAllowTries = new HashMap<>();
        }
    }

    private void loadPlatformApiAllowlist() {
        // TODO: Read a DeviceConfig to decide between current or next
        try {
            byte[] allowlistBytes =
                    this.getClass()
                            .getClassLoader()
                            .getResource(ALLOW_LIST_PROTO_CURRENT_RES)
                            .openStream()
                            .readAllBytes();

            AllowedApisPerTargetSdk allowedApisPerTargetSdk =
                    AllowedApisPerTargetSdk.parseFrom(allowlistBytes);

            synchronized (mPlatformApiAllowlistsLock) {
                mPlatformApiAllowlists = allowedApisPerTargetSdk.getAllowlistPerTargetSdk();
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not parse allowlists", e);
        }
    }

    private void buildAllowTrie(long targetSdkVersion) {
        AllowedApisList allowedApisList;

        synchronized (mPlatformApiAllowlistsLock) {
            if (mPlatformApiAllowlists == null) {
                return;
            }
            allowedApisList = mPlatformApiAllowlists.get(targetSdkVersion);
        }
        if (allowedApisList == null) {
            Log.e(TAG, "No allowlist found for targetSdk " + targetSdkVersion);
            return;
        }

        StringTrie<AllowedApi> allowTrie = new StringTrie();

        for (AllowedApi apiRule : allowedApisList.getAllowedApisList()) {
            String[] apiTokens = getApiTokens(apiRule);
            if (apiTokens != null) {
                allowTrie.put(apiRule, apiTokens);
            } else {
                Log.w(TAG, "API Rule was malformed for rule with class " + apiRule.getClassName());
                return;
            }
        }
        synchronized (mPlatformApiAllowlistsLock) {
            mPlatformApiAllowTries.put(targetSdkVersion, allowTrie);
        }
    }

    /*
     * Converts an AllowedApi object into an array of keys that will be added to the verification
     * trie. The AllowedApi rules should follow TypeDescriptors semantics from the DEX format.
     *
     * The list of tokens is generated splitting the class name at '/' and adding the
     * subsequent fields to be matched for equality or wildcard. The parameters list specifies
     * all of the parameter types, its order is preserved from the method signature in
     * the source file and there is no distinction between input and return parameters.
     * Therefore, the order of the parameters in the rules matters when computing a rule match.
     * Omitted fields will add a wildcard to match all possibilities.
     *
     * A fully qualified rule will match an exact method, an example:
     * {
     *      allow : false
     *      class_name : "Landroid/view/inputmethod/InputMethodManager"
     *      method_name : "getCurrentInputMethodSubtype"
     *      parameters: ["V"]
     *      return_type: "Landroid/view/inputmethod/InputMethodSubtype"
     * }
     * This rule will produce the list of tokens:
     * ["Landroid", "view", "inputmethod", "InputMethodManager", "getCurrentInputMethodSubtype",
     *      "V", "Landroid/view/inputmethod/InputMethodSubtype"]
     *
     * A generalized rule, that matches all methods in the InputMethodManager class that
     * return an InputMethodSubtype object, will look like this:
     * {
     *      allow : false
     *      class_name : "Landroid/view/inputmethod/InputMethodManager"
     *      return_type: "Landroid/view/inputmethod/InputMethodSubtype"
     * }
     * This rule produces the list of tokens:
     * ["Landroid", "view", "inputmethod", "InputMethodManager",
     *      null, "Landroid/view/inputmethod/InputMethodSubtype"]
     *
     * Wildcards can be included in the class name to generalize to packages, for example:
     * "Landroid/view/inputmethod/*" matches all classes within the android.view.inputmethod.
     */
    @VisibleForTesting
    @Nullable
    String[] getApiTokens(AllowedApi apiRule) {
        ArrayList<String> tokens = new ArrayList<>();

        if (apiRule.getClassName().equals(EMPTY_STRING)) {
            // match unspecified class name
            tokens.add(WILDCARD);
        } else {
            List<String> classTokens = Arrays.asList(apiRule.getClassName().toString().split("/"));
            tokens.addAll(classTokens);
        }

        if (!apiRule.getMethodName().equals(EMPTY_STRING)) {
            tokens.add(apiRule.getMethodName());
        } else if (!WILDCARD.equals(tokens.get(tokens.size() - 1))) {
            // match unspecified method name
            tokens.add(WILDCARD);
        }

        if (apiRule.getParametersCount() != 0) {
            tokens.addAll(apiRule.getParametersList());
        } else if (!WILDCARD.equals(tokens.get(tokens.size() - 1))) {
            // match unspecified params
            tokens.add(WILDCARD);
        }

        if (!apiRule.getReturnType().equals(EMPTY_STRING)) {
            tokens.add(apiRule.getReturnType());
        } else if (!WILDCARD.equals(tokens.get(tokens.size() - 1))) {
            // match unspecified return type
            tokens.add(WILDCARD);
        }

        // To catch a malformed rule like "Landroid//com"
        if (tokens.contains(EMPTY_STRING)) {
            return null;
        }

        tokens.replaceAll(token -> token.equals(WILDCARD) ? null : token);

        return tokens.toArray(new String[tokens.size()]);
    }

    /** Returns a singleton instance of {@link SdkDexVerifier} */
    @NonNull
    public static SdkDexVerifier getInstance() {
        synchronized (SdkDexVerifier.class) {
            if (sSdkDexVerifier == null) {
                sSdkDexVerifier = new SdkDexVerifier();
            }
        }
        return sSdkDexVerifier;
    }

    /**
     * Initializes the allowlist for a given target sandbox sdk version
     *
     * @param targetSdkVersion declared in the manifest of the installed package, different from
     *     effectiveTargetSdkVersion.
     */
    private void initAllowlist(long targetSdkVersion) throws Exception {
        synchronized (mPlatformApiAllowlistsLock) {
            if (mPlatformApiAllowlists == null) {
                loadPlatformApiAllowlist();
            }

            if (!mPlatformApiAllowTries.containsKey(targetSdkVersion)) {
                buildAllowTrie(targetSdkVersion);
            }
        }
    }

    /**
     * Starts verification of the requested sdk
     *
     * @param sdkPath path to the sdk package to be verified
     * @param targetSdkVersion Android SDK target version of the package being verified, declared in
     *     the package manifest.
     */
    public void startDexVerification(String sdkPath, long targetSdkVersion) {
        long startTime = SystemClock.elapsedRealtime();

        try {
            initAllowlist(targetSdkVersion);
        } catch (Exception e) {
            Log.e(TAG, "Could not initialize allowlists", e);
            return;
        }

        File sdkPathFile = new File(sdkPath);

        if (!sdkPathFile.exists()) {
            Log.e(TAG, "Apk to verify not found: " + sdkPath);
        }

        // TODO(b/231441674): load apk dex data and, verify against allowtrie.

        long verificationTime = SystemClock.elapsedRealtime() - startTime;
        Log.d(TAG, "Verification time (ms): " + verificationTime);

        // TODO(b/231441674): cache and log verification result
    }
}
