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

package com.android.adservices.ohttp;

import com.android.internal.annotations.VisibleForTesting;

/** Holds the reference to the HPKE context */
public class HpkeContextNativeRef extends NativeRef {

    private HpkeContextNativeRef(ReferenceManager referenceManager) {
        super(referenceManager);
    }

    /** Returns reference to a newly-allocated EVP_HPKE_CTX BoringSSL object */
    public static HpkeContextNativeRef createHpkeContextReference() {
        return createHpkeContextReference(OhttpJniWrapper.getInstance());
    }

    /**
     * Returns reference to a EVP_HPKE_CTX BoringSSL object previously allocated at the given
     * address.
     */
    public static HpkeContextNativeRef fromNativeRefAddress(long nativeRefAddress) {
        IOhttpJniWrapper ohttpJniWrapper = OhttpJniWrapper.getInstance();
        ReferenceManager referenceManager =
                new ReferenceManager() {
                    @Override
                    public long getOrCreate() {
                        return nativeRefAddress;
                    }

                    @Override
                    public void doRelease(long address) {
                        ohttpJniWrapper.hpkeCtxFree(address);
                    }
                };

        return new HpkeContextNativeRef(referenceManager);
    }

    /** Serialized the HPKE Context Native Ref. */
    public long serialize() {
        return getAddress();
    }

    @VisibleForTesting
    static HpkeContextNativeRef createHpkeContextReference(IOhttpJniWrapper ohttpJniWrapper) {
        ReferenceManager referenceManager =
                new ReferenceManager() {
                    @Override
                    public long getOrCreate() {
                        return ohttpJniWrapper.hpkeCtxNew();
                    }

                    @Override
                    public void doRelease(long address) {
                        ohttpJniWrapper.hpkeCtxFree(address);
                    }
                };

        return new HpkeContextNativeRef(referenceManager);
    }
}
