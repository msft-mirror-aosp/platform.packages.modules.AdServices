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
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EncoderPersistenceManagerTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    private static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;
    private static final String ENCODER =
            "function hello() {\n" + "  console.log(\"Hello World!\");\n" + "}";
    private static final String ENCODER_2 =
            "function bye() {\n" + "  console.log(\"Goodbye World!\");\n" + "}";

    private EncoderPersistenceManager mEncoderPersistenceManager;

    private ExecutorService mService = Executors.newFixedThreadPool(5);

    @Before
    public void setup() {
        mEncoderPersistenceManager = EncoderPersistenceManager.getInstance(CONTEXT);
    }

    @After
    public void tearDown() {
        // Added safety check to clear any dangling locks from tests
        mEncoderPersistenceManager.mFileLocks.clear();
        mEncoderPersistenceManager.deleteAllEncoders();
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
        mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER);
        assertEquals(
                "Encoder read should have matched the encoder persisted",
                ENCODER,
                mEncoderPersistenceManager.getEncoder(BUYER_1));
    }

    @Test
    public void testPersistWipeAndThenGetEmpty() {
        assertEquals(
                "Persisted encoder should have been empty",
                "",
                mEncoderPersistenceManager.getEncoder(BUYER_1));

        mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER);
        assertEquals(
                "Persisted encoder should have matched the encoder read back",
                ENCODER,
                mEncoderPersistenceManager.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceManager.deleteAllEncoders());
        assertEquals(
                "Persisted encoder should have been empty again",
                "",
                mEncoderPersistenceManager.getEncoder(BUYER_1));
    }

    @Test
    public void testPersistOverwrites() {
        mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER);
        assertEquals(
                "Persisted encoder should have matched the encoder read back",
                ENCODER,
                mEncoderPersistenceManager.getEncoder(BUYER_1));

        mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER_2);
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

    // Following tests are added for validating Concurrency

    @Test
    public void testPersistenceIsSingleton() {
        assertEquals(
                "Both objects should have been the same instance",
                mEncoderPersistenceManager,
                EncoderPersistenceManager.getInstance(CONTEXT.getApplicationContext()));
    }

    @Test
    public void testSameLockIsGenerated() {
        String testFileName = "test.encoder";
        ReentrantReadWriteLock lock = mEncoderPersistenceManager.getFileLock(testFileName);
        ReentrantReadWriteLock lock2 = mEncoderPersistenceManager.getFileLock(testFileName);

        assertEquals("Both objects should have been same", lock, lock2);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testLockPreventsOverWrites() throws InterruptedException {
        String uniqueFileNamePerBuyer =
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);

        ReentrantReadWriteLock lock =
                mEncoderPersistenceManager.getFileLock(uniqueFileNamePerBuyer);

        CountDownLatch writeWhileLockedLatch = new CountDownLatch(1);
        lock.writeLock().lock();
        try {
            mService.submit(
                    () -> {
                        writeWhileLockedLatch.countDown();
                        assertFalse(
                                "Lock should have prevented this write",
                                mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
                    });
            assertTrue(writeWhileLockedLatch.await(5, TimeUnit.SECONDS));
        } finally {
            lock.writeLock().unlock();
        }

        CountDownLatch writeWhileUnLockedLatch = new CountDownLatch(1);
        mService.submit(
                () -> {
                    writeWhileUnLockedLatch.countDown();
                    assertTrue(
                            "Open Lock should have allowed this write",
                            mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
                });
        assertTrue(writeWhileUnLockedLatch.await(5, TimeUnit.SECONDS));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testLockAllowsOtherWrites() throws InterruptedException {
        String uniqueFileNamePerBuyer =
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);

        ReentrantReadWriteLock lock =
                mEncoderPersistenceManager.getFileLock(uniqueFileNamePerBuyer);

        CountDownLatch writeWhileLockedLatch = new CountDownLatch(1);

        lock.writeLock().lock();
        try {
            mService.submit(
                    () -> {
                        writeWhileLockedLatch.countDown();
                        assertTrue(
                                "Lock should have allowed this write for another buyer",
                                mEncoderPersistenceManager.persistEncoder(BUYER_2, ENCODER));
                    });
            assertTrue(writeWhileLockedLatch.await(5, TimeUnit.SECONDS));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testWriteLockPreventsReads() throws InterruptedException {
        String uniqueFileNamePerBuyer =
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);

        ReentrantReadWriteLock lock =
                mEncoderPersistenceManager.getFileLock(uniqueFileNamePerBuyer);

        CountDownLatch readWhileLockedLatch = new CountDownLatch(1);

        lock.writeLock().lock();
        try {
            mService.submit(
                    () -> {
                        readWhileLockedLatch.countDown();
                        assertEquals(
                                "Lock should have prevented this read",
                                "",
                                mEncoderPersistenceManager.getEncoder(BUYER_1));
                    });
            assertTrue(readWhileLockedLatch.await(5, TimeUnit.SECONDS));
        } finally {
            lock.writeLock().unlock();
        }

        CountDownLatch readWhileUnLockedLatch = new CountDownLatch(1);
        mService.submit(
                () -> {
                    readWhileUnLockedLatch.countDown();
                    assertEquals(
                            "Open Lock should have allowed this read",
                            ENCODER,
                            mEncoderPersistenceManager.getEncoder(BUYER_1));
                });
        assertTrue(readWhileUnLockedLatch.await(5, TimeUnit.SECONDS));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testReadLockAllowsMultipleReadsLock() throws InterruptedException {
        String uniqueFileNamePerBuyer =
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);

        mService.submit(
                () -> {
                    assertTrue(
                            "Open Lock should have allowed this write",
                            mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
                });

        ReentrantReadWriteLock lock =
                mEncoderPersistenceManager.getFileLock(uniqueFileNamePerBuyer);

        lock.readLock().lock();
        try {
            mService.submit(
                    () -> {
                        assertFalse(
                                "Read Lock should have prevented this write",
                                mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER));
                    });

            CountDownLatch readWhileLockedLatch = new CountDownLatch(2);
            mService.submit(
                    () -> {
                        readWhileLockedLatch.countDown();
                        assertEquals(
                                "Read Lock should have allowed this read",
                                ENCODER,
                                mEncoderPersistenceManager.getEncoder(BUYER_1));
                    });

            mService.submit(
                    () -> {
                        readWhileLockedLatch.countDown();
                        assertEquals(
                                "Read Lock should have allowed this another read",
                                ENCODER,
                                mEncoderPersistenceManager.getEncoder(BUYER_1));
                    });

            assertTrue(readWhileLockedLatch.await(10, TimeUnit.SECONDS));
        } finally {
            lock.readLock().unlock();
        }
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testWriteLockPreventsDeletes() throws InterruptedException {
        String uniqueFileNamePerBuyer =
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);
        mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER);

        ReentrantReadWriteLock lock =
                mEncoderPersistenceManager.getFileLock(uniqueFileNamePerBuyer);

        CountDownLatch deleteWhileLockedLatch = new CountDownLatch(1);

        lock.writeLock().lock();
        try {

            mService.submit(
                    () -> {
                        deleteWhileLockedLatch.countDown();
                        assertFalse(
                                "Lock should have prevented this delete",
                                mEncoderPersistenceManager.deleteEncoder(BUYER_1));
                    });
            assertTrue(deleteWhileLockedLatch.await(5, TimeUnit.SECONDS));
        } finally {
            lock.writeLock().unlock();
        }

        CountDownLatch deleteWhileUnLockedLatch = new CountDownLatch(1);
        mService.submit(
                () -> {
                    deleteWhileUnLockedLatch.countDown();
                    assertTrue(
                            "Open Lock should have allowed this delete",
                            mEncoderPersistenceManager.deleteEncoder(BUYER_1));
                });
        assertTrue(deleteWhileUnLockedLatch.await(5, TimeUnit.SECONDS));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testWriteLockPreventsWipeAllEncoders() throws InterruptedException {
        String uniqueFileNamePerBuyer =
                mEncoderPersistenceManager.generateFileNameForBuyer(BUYER_1);
        mEncoderPersistenceManager.persistEncoder(BUYER_1, ENCODER);

        ReentrantReadWriteLock lock =
                mEncoderPersistenceManager.getFileLock(uniqueFileNamePerBuyer);

        CountDownLatch deleteWhileLockedLatch = new CountDownLatch(1);

        lock.writeLock().lock();
        try {
            mService.submit(
                    () -> {
                        deleteWhileLockedLatch.countDown();
                        assertFalse(
                                "Lock should have prevented this delete",
                                mEncoderPersistenceManager.deleteAllEncoders());
                    });
            assertTrue(deleteWhileLockedLatch.await(5, TimeUnit.SECONDS));
        } finally {
            lock.writeLock().unlock();
        }

        CountDownLatch deleteWhileUnLockedLatch = new CountDownLatch(1);
        mService.submit(
                () -> {
                    deleteWhileUnLockedLatch.countDown();
                    assertTrue(
                            "Open Lock should have allowed this delete",
                            mEncoderPersistenceManager.deleteAllEncoders());
                });
        assertTrue(deleteWhileUnLockedLatch.await(5, TimeUnit.SECONDS));
    }
}
