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

import static org.junit.Assert.assertEquals;

import com.android.server.sdksandbox.proto.Verifier.AllowedApi;

import org.junit.Test;

public class SdkDexVerifierUnitTest {

    private final SdkDexVerifier mVerifier = SdkDexVerifier.getInstance();

    @Test
    public void fullyQualifiedRule() {
        final AllowedApi apiRuleFull =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getCurrentInputMethodSubtype")
                        .addParameters("V")
                        .setReturnType("Landroid/view/inputmethod/InputMethodSubtype")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getCurrentInputMethodSubtype",
            "V",
            "Landroid/view/inputmethod/InputMethodSubtype"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleFull);

        assertEquals(keys, expectedKeys);
    }

    @Test
    public void classAndMethodRule() {
        final AllowedApi apiRuleClassMethod =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getCurrentInputMethodSubtype")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getCurrentInputMethodSubtype",
            null
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassMethod);

        assertEquals(keys, expectedKeys);
    }

    @Test
    public void multiParam() {
        final AllowedApi apiRuleMultiParam =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getInputMethodListAsUser")
                        .addParameters("I") // int, according to DEX TypeDescriptor Semantics
                        .addParameters("I")
                        .setReturnType("Ljava/util/List")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getInputMethodListAsUser",
            "I",
            "I",
            "Ljava/util/List"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleMultiParam);

        assertEquals(keys, expectedKeys);
    }

    @Test
    public void classReturn() {
        final AllowedApi apiRuleClassReturn =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setReturnType("Ljava/util/List")
                        .build();
        String[] expectedKeys = {
            "Landroid", "view", "inputmethod", "InputMethodManager", null, "Ljava/util/List"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassReturn);

        assertEquals(keys, expectedKeys);
    }

    @Test
    public void classAndParams() {
        final AllowedApi apiRuleClassParam =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .addParameters("V")
                        .build();
        String[] expectedKeys = {
            "Landroid", "view", "inputmethod", "InputMethodManager", null, "V", null
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassParam);

        assertEquals(keys, expectedKeys);
    }
}
