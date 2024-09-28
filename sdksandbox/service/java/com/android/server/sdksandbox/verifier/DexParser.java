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

import com.android.server.sdksandbox.verifier.SerialDexLoader.DexSymbols;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Interface for DEX parsing, needed for SDK verification
 *
 * @hide
 */
public interface DexParser {

    /**
     * Returns a multimap of apkFile to a list of dexEntries, for all dex files in apks found in the
     * package path to verify.
     *
     * @param apkPathFile path to apks containing one or more dex files
     */
    List<DexEntry> getDexFilePaths(File apkPathFile) throws IOException;

    /**
     * Parses the dex file and reads the symbols in, populates data in the DexLoadResult.
     *
     * @param apkFile apk file to parse
     * @param dexEntry the specific .dex file to load
     * @param dexLoadResult sparse array to insert the methods loaded from the dex file
     */
    void loadDexSymbols(File apkFile, String dexEntry, DexSymbols dexSymbols) throws IOException;

    /**
     * Class that represents a dex entry in a given apk file.
     */
    public class DexEntry {
        private File apkFile;
        private String dexEntry;

        public DexEntry(File apkFile, String dexEntry) {
            this.apkFile = apkFile;
            this.dexEntry = dexEntry;
        }

        public File getApkFile() {
            return apkFile;
        }

        public String getDexEntry() {
            return dexEntry;
        }

        public String getEntryFilename() {
            return dexEntry.substring(dexEntry.lastIndexOf('/') + 1);
        }

        @Override
        public String toString() {
            return apkFile.getName() + "/" + dexEntry;
        }
    }
}
