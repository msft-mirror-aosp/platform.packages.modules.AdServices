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

package com.android.adservices.data.signals;

import static com.android.adservices.data.signals.EncoderPersistenceManager.ADSERVICES_PREFIX;
import static com.android.adservices.data.signals.EncoderPersistenceManager.ENCODERS_DIR;
import static com.android.adservices.data.signals.EncoderPersistenceManager.ENCODER_FILE_SUFFIX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class EncoderPersistenceManagerTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    private static final String ENCODER =
            "function hello() {\n" + "  console.log(\"Hello World!\");\n" + "}";
    private static final String ENCODER_2 =
            "function bye() {\n" + "  console.log(\"Goodbye World!\");\n" + "}";

    private EncoderPersistenceManager mEncoderPersistenceManager;

    @Before
    public void setup() {
        mEncoderPersistenceManager = EncoderPersistenceManager.getInstance(CONTEXT);
    }

    @After
    public void tearDown() {
        mEncoderPersistenceManager.deleteAllEncoders();
    }

    @Test
    public void testPersistenceIsSingleton() {
        assertEquals(
                "Both objects should have been the same instance",
                mEncoderPersistenceManager,
                EncoderPersistenceManager.getInstance(CONTEXT.getApplicationContext()));
    }

    @Test
    public void testGenerateFileName() {
        assertEquals(
                ADSERVICES_PREFIX + BUYER_1.toString() + ENCODER_FILE_SUFFIX,
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1));
    }

    @Test
    public void testCreateEncoderDirectory() {
        File encoderDir = new File(CONTEXT.getFilesDir(), ENCODERS_DIR);
        assertFalse("Directory should not have existed so far", encoderDir.exists());

        encoderDir = mEncoderPersistenceManager.createEncodersDirectoryIfDoesNotExist();
        assertTrue("Directory should have been created", encoderDir.exists());
        assertEquals(
                "Created Directory name does not match expected",
                ENCODERS_DIR,
                encoderDir.getName());
    }

    @Test
    public void testCreateFileInDirectory() {
        File encoderDir = mEncoderPersistenceManager.createEncodersDirectoryIfDoesNotExist();
        String fileName = mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);
        File encoderFile = new File(encoderDir, fileName);

        assertFalse("File should not have existed", encoderFile.exists());

        encoderFile = mEncoderPersistenceManager.createFileInDirectory(encoderDir, fileName);
        assertTrue("File should have been created", encoderFile.exists());
        assertEquals(
                "Created name does not match expected",
                ADSERVICES_PREFIX + BUYER_1 + ENCODER_FILE_SUFFIX,
                encoderFile.getName());
    }

    @Test
    public void testPersistAndGetEncoder() {
        assertTrue(mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
        assertEquals(
                "Encoder read should have matched the encoder persisted",
                ENCODER,
                mEncoderPersistenceManager.getEncoder(BUYER_1));
    }

    @Test
    public void testPersistWipeAndThenGetEmpty() {
        assertNull(
                "Persisted encoder should have been null",
                mEncoderPersistenceManager.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
        assertEquals(
                "Persisted encoder should have matched the encoder read back",
                ENCODER,
                mEncoderPersistenceManager.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceManager.deleteAllEncoders());
        assertNull(
                "Persisted encoder should have been null again",
                mEncoderPersistenceManager.getEncoder(BUYER_1));
    }

    @Test
    public void testPersistOverwrites() {
        assertTrue(mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
        assertEquals(
                "Persisted encoder should have matched the encoder read back",
                ENCODER,
                mEncoderPersistenceManager.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER_2));
        assertEquals(
                "Persisted encoder should have been replaced",
                ENCODER_2,
                mEncoderPersistenceManager.getEncoder(BUYER_1));
    }

    @Test
    public void testWipeAllEncoders() {
        File encoderDir = mEncoderPersistenceManager.createEncodersDirectoryIfDoesNotExist();
        String fileName = mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);
        File encoderFile = new File(encoderDir, fileName);

        assertFalse("File should not have existed", encoderFile.exists());

        encoderFile = mEncoderPersistenceManager.createFileInDirectory(encoderDir, fileName);
        assertTrue("File should have been created", encoderFile.exists());

        assertTrue(
                "All encoders and directory should have been deleted",
                mEncoderPersistenceManager.deleteAllEncoders());
        encoderDir = new File(CONTEXT.getFilesDir(), ENCODERS_DIR);
        assertFalse("Directory should have been wiped", encoderDir.exists());
    }

    @Test
    public void testDeleteEmptyDirectory() {
        File encoderDir = new File(CONTEXT.getFilesDir(), ENCODERS_DIR);
        assertFalse("Directory should not have existed", encoderDir.exists());

        assertTrue(
                "The deletion of non-existing directory should have been true",
                mEncoderPersistenceManager.deleteAllEncoders());
    }

    @Test
    public void testWriteAndReadForFile() {
        File tempFile = new File(CONTEXT.getFilesDir(), "temp_file");
        mEncoderPersistenceManager.writeDataToFile(tempFile, ENCODER);
        String readData =
                mEncoderPersistenceManager.readDataFromFile(
                        CONTEXT.getFilesDir(), tempFile.getName());
        assertEquals(
                "Data written to the file should have matched data read from file",
                ENCODER,
                readData);
        tempFile.delete();
    }
}
