/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for the classification of ETD Zip file entries by EtdPackage.
 */
public class EtdPackageTest {

    private static final String PREFIX = "Surname_umd_0117D_12345";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Returns an EtdPackage for a Zip file containing the given entries, with
     * no file size limit.
     */
    private EtdPackage read(String... entryNames) throws IOException {
        File zipFile = EtdZipFileBuilder.createZipFile(
            temporaryFolder.newFile("etdadmin_upload_test.zip"), entryNames);

        try (ZipFile zip = new ZipFile(zipFile)) {
            return EtdPackage.read(zip, -1L);
        }
    }

    @Test
    public void testNormalPackageDoesNotNeedReview() throws Exception {
        EtdPackage etdPackage = read(PREFIX + "_DATA.xml", PREFIX + ".pdf");

        assertEquals("12345", etdPackage.getItemNumber());
        assertEquals(PREFIX + ".pdf", etdPackage.getPrimaryFileName());
        assertTrue(etdPackage.getSupplementaryFileNames().isEmpty());
        assertFalse(etdPackage.needsReview());
        assertTrue(etdPackage.getReviewReasons().isEmpty());

        List files = etdPackage.getFileList();
        assertEquals(4, files.size());
        assertEquals(PREFIX + "_DATA.xml", files.get(0));
        assertEquals(PREFIX + ".pdf", files.get(2));
    }

    @Test
    public void testSupplementaryFileNeedsReview() throws Exception {
        EtdPackage etdPackage = read(PREFIX + "_DATA.xml", PREFIX + ".pdf", "appendix.wav");

        assertEquals(List.of("appendix.wav"), etdPackage.getSupplementaryFileNames());
        assertTrue(etdPackage.needsReview());
        assertEquals(List.of("1 supplementary file(s)"), etdPackage.getReviewReasons());

        // The supplementary file is the last file name/ZipEntry pair
        List files = etdPackage.getFileList();
        assertEquals(6, files.size());
        assertEquals("appendix.wav", files.get(4));
    }

    @Test
    public void testSupplementaryFileInSubdirectoryNeedsReview() throws Exception {
        // Entries are classified on their first path component, so even a
        // ProQuest-named file in a subdirectory is a supplementary file.
        EtdPackage etdPackage = read(PREFIX + "_DATA.xml", PREFIX + ".pdf", "extra/" + PREFIX + ".pdf");

        assertEquals(List.of(PREFIX + ".pdf"), etdPackage.getSupplementaryFileNames());
        assertTrue(etdPackage.needsReview());
    }

    @Test
    public void testMultipleProQuestPdfsAreRetainedForReview() throws Exception {
        EtdPackage etdPackage = read(PREFIX + "_DATA.xml", "First_umd_0117D_12345.pdf", PREFIX + ".pdf");

        // The last ProQuest-named PDF is the thesis; the earlier one is kept
        // as a supplementary file instead of being silently discarded.
        assertEquals(PREFIX + ".pdf", etdPackage.getPrimaryFileName());
        assertEquals(List.of("First_umd_0117D_12345.pdf"), etdPackage.getSupplementaryFileNames());
        assertTrue(etdPackage.needsReview());
        assertEquals(List.of("1 supplementary file(s)", "2 ProQuest-named PDFs"),
                etdPackage.getReviewReasons());
    }

    @Test
    public void testMissingThesisNeedsReview() throws Exception {
        EtdPackage etdPackage = read(PREFIX + "_DATA.xml");

        assertNull(etdPackage.getPrimaryFileName());
        assertTrue(etdPackage.needsReview());
        assertEquals(List.of("no thesis PDF"), etdPackage.getReviewReasons());

        // Placeholders keep the structure of the file list
        List files = etdPackage.getFileList();
        assertEquals(4, files.size());
    }

    @Test
    public void testMissingMetadataNeedsReview() throws Exception {
        EtdPackage etdPackage = read(PREFIX + ".pdf");

        assertNull(etdPackage.getMetadataEntry());
        assertTrue(etdPackage.needsReview());
        assertEquals(List.of("no ProQuest metadata file"), etdPackage.getReviewReasons());
    }

    @Test
    public void testDirectoryEntriesAreIgnored() throws Exception {
        File zipFile = EtdZipFileBuilder.createZipFile(
            temporaryFolder.newFile("etdadmin_upload_directory.zip"),
            new String[] { PREFIX + "_DATA.xml", PREFIX + ".pdf" },
            new String[] { "extra/" });

        try (ZipFile zip = new ZipFile(zipFile)) {
            EtdPackage etdPackage = EtdPackage.read(zip, -1L);
            assertFalse(etdPackage.needsReview());
        }
    }

    @Test
    public void testFileSizeLimitIsEnforced() throws Exception {
        File zipFile = EtdZipFileBuilder.createZipFile(
            temporaryFolder.newFile("etdadmin_upload_large.zip"),
            PREFIX + "_DATA.xml", PREFIX + ".pdf");

        try (ZipFile zip = new ZipFile(zipFile)) {
            assertThrows(ZipEntryTooLarge.class, () -> EtdPackage.read(zip, 1L));
        }
    }
}
