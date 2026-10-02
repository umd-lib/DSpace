/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.app;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Creates ETD Zip files for tests, either from scratch or from one of the
 * test resource Zip files.
 */
public class EtdZipFileBuilder {

    // Suppress default constructor
    private EtdZipFileBuilder() {
    }

    /**
     * Creates a Zip file containing an entry for each of the given file names.
     *
     * @param target the file to write the Zip file to
     * @param fileEntryNames the names of the entries to create
     * @return the created Zip file
     */
    public static File createZipFile(File target, String... fileEntryNames) throws IOException {
        return createZipFile(target, fileEntryNames, new String[0]);
    }

    /**
     * Creates a Zip file containing an entry for each of the given file names,
     * and a directory entry for each of the given directory names.
     *
     * @param target the file to write the Zip file to
     * @param fileEntryNames the names of the file entries to create
     * @param directoryEntryNames the names of the directory entries to create
     * @return the created Zip file
     */
    public static File createZipFile(File target, String[] fileEntryNames, String[] directoryEntryNames)
            throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            for (String directoryEntryName : directoryEntryNames) {
                zos.putNextEntry(new ZipEntry(directoryEntryName));
                zos.closeEntry();
            }

            for (String fileEntryName : fileEntryNames) {
                zos.putNextEntry(new ZipEntry(fileEntryName));
                zos.write(contentFor(fileEntryName));
                zos.closeEntry();
            }
        }

        return target;
    }

    /**
     * Creates a Zip file from the given test resource Zip file, optionally
     * modifying the ProQuest metadata and adding entries.
     *
     * @param target the file to write the Zip file to
     * @param resourcePath the classpath location of the Zip file to copy
     * @param metadataEditor applied to the contents of the "_DATA.xml" entry,
     * or null to copy it unchanged
     * @param additionalEntries entries to add, keyed by entry name
     * @return the created Zip file
     */
    public static File createFromResource(File target, String resourcePath,
            UnaryOperator<String> metadataEditor, Map<String, byte[]> additionalEntries) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();

        URL resourceUrl = EtdZipFileBuilder.class.getResource(resourcePath);
        if (resourceUrl == null) {
            throw new IOException("Unable to find the '" + resourcePath + "' resource");
        }

        try (ZipFile zip = new ZipFile(new File(resourceUrl.toURI()))) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                ZipEntry ze = e.nextElement();
                if (ze.isDirectory()) {
                    continue;
                }

                byte[] content;
                try (InputStream is = zip.getInputStream(ze)) {
                    content = readAllBytes(is);
                }

                if ((metadataEditor != null) && ze.getName().endsWith("_DATA.xml")) {
                    content = metadataEditor.apply(new String(content, StandardCharsets.ISO_8859_1))
                            .getBytes(StandardCharsets.ISO_8859_1);
                }

                entries.put(ze.getName(), content);
            }
        }

        if (additionalEntries != null) {
            entries.putAll(additionalEntries);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }

        return target;
    }

    /**
     * Returns placeholder content appropriate to the given entry name.
     */
    private static byte[] contentFor(String entryName) {
        if (entryName.endsWith(".pdf")) {
            return "%PDF-1.4\n%placeholder\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
        } else if (entryName.endsWith("_DATA.xml")) {
            return "<DISS_submission/>".getBytes(StandardCharsets.ISO_8859_1);
        }

        return "placeholder content".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        OutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return ((ByteArrayOutputStream) buffer).toByteArray();
    }
}
