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

package com.android.adservices.cobalt;

import com.google.cobalt.CobaltRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;

/** Loads the Cobalt registry from a Java resource. */
final class CobaltRegistryLoader {
    private static final String REGISTRY_FILE = "cobalt_registry.binarypb";

    private CobaltRegistryLoader() {}

    /**
     * Get the Cobalt registry from the JAR's resource file.
     *
     * @return the CobaltRegistry
     */
    public static CobaltRegistry getRegistry() throws IOException, InvalidProtocolBufferException {
        final ClassLoader loader = CobaltRegistryLoader.class.getClassLoader();
        return CobaltRegistry.parseFrom(loader.getResourceAsStream(REGISTRY_FILE));
    }
}
