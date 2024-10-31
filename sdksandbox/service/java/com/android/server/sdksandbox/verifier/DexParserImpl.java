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

import com.android.server.sdksandbox.verifier.SerialDexLoader.DexSymbols;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.DexFileFactory.DexFileNotFoundException;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.reference.DexBackedMethodReference;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DEX parser for SDK verification
 *
 * @hide
 */
public class DexParserImpl implements DexParser {

    @Override
    public List<DexEntry> getDexFilePaths(@NonNull File apkPathFile) throws IOException {
        ArrayList<File> apkList = new ArrayList<>();

        // If multi-apk directory, find a base apk and zero or more split apks
        if (apkPathFile.isDirectory()) {
            for (File apkFile : apkPathFile.listFiles()) {
                if (apkFile.isFile() && apkFile.getName().endsWith(".apk")) {
                    apkList.add(apkFile);
                }
            }
        } else {
            apkList.add(apkPathFile);
        }

        List<DexEntry> dexList = new ArrayList<>();

        for (File apk : apkList) {
            try (ZipFile apkZipFile = new ZipFile(apk)) {
                Enumeration<? extends ZipEntry> entriesEnumeration = apkZipFile.entries();

                while (entriesEnumeration.hasMoreElements()) {
                    String entryName = entriesEnumeration.nextElement().getName();
                    if (entryName.endsWith(".dex")) {
                        dexList.add(new DexEntry(apk, entryName));
                    }
                }
            } catch (IOException ex) {
                throw new IOException(apk.getName() + " is not a valid DEX container file.", ex);
            }
        }

        return dexList;
    }

    @Override
    public void loadDexSymbols(File apkFile, String dexName, DexSymbols dexSymbols)
            throws IOException {
        MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry;
        try {
            dexEntry =
                    DexFileFactory.loadDexEntry(
                            apkFile, dexName, /* exactMatch */ true, Opcodes.getDefault());
        } catch (DexFileNotFoundException e) {
            throw new IOException(e);
        }
        dexSymbols.clearAndSetDexEntry(apkFile + "/" + dexName);

        DexBackedDexFile dexFile = dexEntry.getDexFile();

        for (DexBackedMethodReference method : dexFile.getMethodSection()) {
            String classname = method.getDefiningClass();
            String methodString = method.getName() + ";";
            for (String param : method.getParameterTypes()) {
                methodString = methodString + param;
            }
            methodString = methodString + method.getReturnType();
            // remove the ; suffix in the classname before adding it
            dexSymbols.addReferencedMethod(
                    classname.substring(0, classname.length() - 1), methodString);
        }
    }
}
