/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

/**
 * Removes the known personal information that DRUM does not use from the
 * ProQuest "_DATA.xml" metadata of an ETD, before the metadata is logged or
 * stored in the "METADATA" bundle of the item.
 *
 * Each redacted element is removed entirely, with all of its children, so
 * that any child element ProQuest adds later is removed too. See the
 * "Redaction of the ProQuest Metadata XML" section of
 * "dspace/docs/DrumEtdLoader.md".
 */
public class EtdMetadataRedactor {

    /**
     * XPath expressions of the redacted elements. They match anywhere in the
     * document, although ProQuest only sends them under
     * "/DISS_submission/DISS_authorship/DISS_author".
     */
    public static final List<String> REDACTED_ELEMENTS = List.of(
        // The student's current and future address, email, phone, etc.
        "//DISS_contact",
        "//DISS_citizenship"
    );

    private EtdMetadataRedactor() {
    }

    /**
     * Removes the redacted elements from the given metadata document.
     *
     * @param meta the ProQuest metadata document, which is modified in place
     * @return the number of elements removed
     */
    public static int redact(Document meta) {
        int count = 0;

        for (String xpath : REDACTED_ELEMENTS) {
            for (Object node : meta.selectNodes(xpath)) {
                remove((Node) node);
                count++;
            }
        }

        return count;
    }

    /**
     * Removes the given node, along with the whitespace indenting it, so
     * that no blank line is left in its place.
     */
    private static void remove(Node node) {
        Element parent = node.getParent();
        if (parent != null) {
            List content = parent.content();
            int index = content.indexOf(node);
            if (index > 0 && content.get(index - 1) instanceof Text
                    && ((Text) content.get(index - 1)).getText().trim().isEmpty()) {
                ((Node) content.get(index - 1)).detach();
            }
        }

        node.detach();
    }

    /**
     * Serializes the given metadata document as UTF-8, whatever the encoding
     * of the original file.
     *
     * @param meta the ProQuest metadata document
     * @return the serialized document
     * @throws IOException if the document cannot be serialized
     */
    public static byte[] toBytes(Document meta) throws IOException {
        OutputFormat format = new OutputFormat();
        format.setEncoding("UTF-8");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(out, format);
        writer.write(meta);
        writer.close();

        return out.toByteArray();
    }
}
