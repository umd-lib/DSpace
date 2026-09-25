/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.app;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.Logger;

/**
 * The contents of a single ProQuest ETD Zip file, classified into the ProQuest
 * metadata file ("_DATA.xml"), the thesis PDF, and any supplementary files.
 *
 * A "normal" package contains exactly the ProQuest metadata file and one
 * ProQuest-named PDF. Anything else (supplementary files, a second
 * ProQuest-named PDF, a missing metadata file or thesis) is recorded as a
 * reason for routing the item to manual review, see {@link #getReviewReasons()}.
 */
public class EtdPackage {

    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(EtdPackage.class);

    /**
     * Pattern matching the first path component of the ProQuest-provided files
     * in an ETD Zip file, capturing the ProQuest item number.
     */
    public static final Pattern PROQUEST_FILE_PATTERN = Pattern.compile(".*_umd_0117._(\\d+)(.pdf|_DATA.xml)");

    /**
     * Configuration property for setting the maximum file size that can
     * be processed.
     */
    public static final String MAX_FILE_SIZE_CONFIG_PROP = "drum.etdloader.maxFileSize";

    private String itemNumber = null;

    private String metadataFileName = null;

    private ZipEntry metadataEntry = null;

    private String primaryFileName = null;

    private ZipEntry primaryEntry = null;

    private final List<String> supplementaryFileNames = new ArrayList<>();

    private final List<ZipEntry> supplementaryEntries = new ArrayList<>();

    private final List<String> reviewReasons = new ArrayList<>();

    private int proquestPdfCount = 0;

    /**
     * Reads and classifies the entries of the given ETD Zip file.
     *
     * Entries are classified exactly as they have always been: the first path
     * component of each entry is matched against
     * {@link #PROQUEST_FILE_PATTERN}; a matching entry whose file name ends in
     * "_DATA.xml" is the ProQuest metadata, a matching entry whose file name
     * ends in ".pdf" is the thesis, and everything else is a supplementary
     * file. When more than one ProQuest-named PDF is present the last one is
     * the thesis; earlier ones are retained as supplementary files (they were
     * previously discarded without notice) and the item is routed to review.
     *
     * @param zip the ETD Zip file to read
     * @param maxFileSizeInBytes the maximum allowed size, in bytes, of a
     * ProQuest-provided Zip entry. Use -1 to indicate unlimited file size.
     * @return the classified contents of the Zip file
     */
    public static EtdPackage read(ZipFile zip, long maxFileSizeInBytes) {
        EtdPackage etdPackage = new EtdPackage();

        log.info("Reading " + zip.size() + " zip file entries");

        for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
            ZipEntry ze = e.nextElement();
            String strName = ze.getName();

            log.debug("zip entry: " + strName);

            // skip directories
            if (ze.isDirectory()) {
                continue;
            }

            // split into path components
            String s[] = strName.split("/");

            String strFileName = s[s.length - 1];

            Matcher m = PROQUEST_FILE_PATTERN.matcher(s[0]);
            if (m.matches()) {
                if (!isFileSizeWithinLimit(ze, maxFileSizeInBytes)) {
                    long uncompressedSize = ze.getSize();
                    String msg = """
                        ===============================================
                        ERROR: Zip file entry too large

                        The file '%s' in '%s'
                        is too large at %d bytes, exceeding the limit
                        of %d bytes set in the '%s'
                        configuration property.
                        Skipping.
                        ===============================================
                        """.formatted(
                            strFileName, zip.getName(), uncompressedSize,
                            maxFileSizeInBytes, MAX_FILE_SIZE_CONFIG_PROP
                        );
                    throw new ZipEntryTooLarge(msg);
                }

                // Get the item number
                if (etdPackage.itemNumber == null) {
                    etdPackage.itemNumber = m.group(1);

                    log.debug("item number is " + etdPackage.itemNumber);
                }

                if (strFileName.endsWith("_DATA.xml")) {
                    etdPackage.setMetadata(strFileName, ze);
                } else if (strFileName.endsWith(".pdf")) {
                    etdPackage.setPrimary(strFileName, ze);
                }
            } else {
                etdPackage.supplementaryFileNames.add(strFileName);
                etdPackage.supplementaryEntries.add(ze);
            }
        }

        etdPackage.determineReviewReasons();
        return etdPackage;
    }

    /**
     * Returns true if the ZipEntry is less than or equal to the given
     * maximum file size limit, false otherwise.
     *
     * The maximum file size is typically controlled by the
     * MAX_FILE_SIZE_CONFIG_PROP configuration parameter.
     *
     * @param ze the ZipEntry to examine
     * @param maxFileSizeInBytes the maximum allows file size in bytes. Use
     * -1 to indicate unlimited file size.
     * @return true if the ZipEntry is within the given limit, false otherwise.
     */
    protected static boolean isFileSizeWithinLimit(ZipEntry ze, long maxFileSizeInBytes) {
        // Negative number indicates unlimited file size
        if (maxFileSizeInBytes < 0) {
            return true;
        }

        return ze.getSize() <= maxFileSizeInBytes;
    }

    /**
     * Records the ProQuest metadata file. The last metadata file in the Zip
     * file wins, as it always has.
     */
    private void setMetadata(String fileName, ZipEntry entry) {
        if (metadataFileName != null) {
            log.warn("Ignoring additional ProQuest metadata file: " + metadataFileName);
        }
        metadataFileName = fileName;
        metadataEntry = entry;
    }

    /**
     * Records the thesis PDF. The last ProQuest-named PDF in the Zip file is
     * the thesis; any earlier one becomes a supplementary file so that it is
     * preserved for the reviewer instead of being silently discarded.
     */
    private void setPrimary(String fileName, ZipEntry entry) {
        proquestPdfCount++;
        if (primaryFileName != null) {
            log.warn("Additional ProQuest-named PDF found; retaining the previous PDF as a supplementary file");
            supplementaryFileNames.add(primaryFileName);
            supplementaryEntries.add(primaryEntry);
        }
        primaryFileName = fileName;
        primaryEntry = entry;
    }

    /**
     * Determines why, if at all, this package should be routed to manual
     * review.
     */
    private void determineReviewReasons() {
        int supplementaryCount = supplementaryFileNames.size();
        if (supplementaryCount > 0) {
            reviewReasons.add(supplementaryCount + " supplementary file(s)");
        }
        if (proquestPdfCount > 1) {
            reviewReasons.add(proquestPdfCount + " ProQuest-named PDFs");
        }
        if (primaryEntry == null) {
            reviewReasons.add("no thesis PDF");
        }
        if (metadataEntry == null) {
            reviewReasons.add("no ProQuest metadata file");
        }
    }

    /**
     * Returns the reasons this package should be routed to manual review, or
     * an empty list if it should be installed without review.
     *
     * @return the reasons this package should be routed to manual review
     */
    public List<String> getReviewReasons() {
        return reviewReasons;
    }

    /**
     * Returns true if this package should be routed to manual review.
     *
     * @return true if this package should be routed to manual review, false
     * otherwise.
     */
    public boolean needsReview() {
        return !reviewReasons.isEmpty();
    }

    /**
     * Returns the ProQuest item number of this package, or null if the Zip
     * file contained no ProQuest-named files.
     *
     * @return the ProQuest item number of this package
     */
    public String getItemNumber() {
        return itemNumber;
    }

    /**
     * Returns the Zip entry of the ProQuest metadata file, or null if the Zip
     * file did not contain one.
     *
     * @return the Zip entry of the ProQuest metadata file
     */
    public ZipEntry getMetadataEntry() {
        return metadataEntry;
    }

    /**
     * Returns the file name of the thesis PDF, or null if the Zip file did not
     * contain one.
     *
     * @return the file name of the thesis PDF
     */
    public String getPrimaryFileName() {
        return primaryFileName;
    }

    /**
     * Returns the file names of the supplementary files, in Zip file order.
     *
     * @return the file names of the supplementary files
     */
    public List<String> getSupplementaryFileNames() {
        return supplementaryFileNames;
    }

    /**
     * Returns the file name/ZipEntry pairs in this package, in the order
     * expected by the rest of the loader: the ProQuest metadata file, the
     * thesis PDF, then any supplementary files.
     *
     * A placeholder object is used when the metadata file or the thesis PDF is
     * missing, preserving the structure of the list the loader has always
     * used.
     *
     * @return the file name/ZipEntry pairs in this package
     */
    public List getFileList() {
        List files = new ArrayList();

        files.add(metadataFileName != null ? metadataFileName : new Object());
        files.add(metadataEntry != null ? metadataEntry : new Object());
        files.add(primaryFileName != null ? primaryFileName : new Object());
        files.add(primaryEntry != null ? primaryEntry : new Object());

        for (int i = 0; i < supplementaryFileNames.size(); i++) {
            files.add(supplementaryFileNames.get(i));
            files.add(supplementaryEntries.get(i));
        }

        return files;
    }
}
