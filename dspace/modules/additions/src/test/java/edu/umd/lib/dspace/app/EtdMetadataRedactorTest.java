/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.junit.Test;

/**
 * Tests for the redaction of the ProQuest ETD metadata by EtdMetadataRedactor.
 */
public class EtdMetadataRedactorTest {

    private static final String METADATA =
        "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n"
        + "<DISS_submission publishing_option=\"0\" embargo_code=\"0\">\n"
        + "  <DISS_authorship>\n"
        + "    <DISS_author type=\"primary\">\n"
        + "      <DISS_name>\n"
        + "        <DISS_surname>Testauthor</DISS_surname>\n"
        + "        <DISS_fname>René</DISS_fname>\n"
        + "      </DISS_name>\n"
        + "      <DISS_contact type=\"current\">\n"
        + "        <DISS_contact_effdt>01/01/2020</DISS_contact_effdt>\n"
        + "        <DISS_address>\n"
        + "          <DISS_addrline>1 Current Street</DISS_addrline>\n"
        + "          <DISS_city>Current City</DISS_city>\n"
        + "        </DISS_address>\n"
        + "        <DISS_email>current@example.com</DISS_email>\n"
        + "      </DISS_contact>\n"
        + "      <DISS_contact type=\"future\">\n"
        + "        <DISS_address>\n"
        + "          <DISS_addrline>1 Future Street</DISS_addrline>\n"
        + "        </DISS_address>\n"
        + "        <DISS_email>future@example.com</DISS_email>\n"
        + "      </DISS_contact>\n"
        + "      <DISS_citizenship>Testland</DISS_citizenship>\n"
        + "      <DISS_orcid>0000-0000-0000-0000</DISS_orcid>\n"
        + "    </DISS_author>\n"
        + "  </DISS_authorship>\n"
        + "  <DISS_description>\n"
        + "    <DISS_title>A Test Thesis</DISS_title>\n"
        + "    <DISS_institution>\n"
        + "      <DISS_inst_contact>ETD Test Unit</DISS_inst_contact>\n"
        + "    </DISS_institution>\n"
        + "  </DISS_description>\n"
        + "  <DISS_restriction>\n"
        + "    <DISS_sales_restriction code=\"1\" remove=\"06/26/3027\"/>\n"
        + "  </DISS_restriction>\n"
        + "</DISS_submission>\n";

    private static Document parse(String xml) throws Exception {
        return new SAXReader().read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void testContactAndCitizenshipAreRemoved() throws Exception {
        Document meta = parse(METADATA);

        assertEquals(3, EtdMetadataRedactor.redact(meta));

        String redacted = new String(EtdMetadataRedactor.toBytes(meta), StandardCharsets.ISO_8859_1);
        assertFalse(redacted.contains("DISS_contact "));
        assertFalse(redacted.contains("<DISS_contact>"));
        assertFalse(redacted.contains("DISS_citizenship"));
        assertFalse(redacted.contains("Street"));
        assertFalse(redacted.contains("example.com"));
        assertFalse(redacted.contains("Testland"));
    }

    @Test
    public void testOtherElementsAreKept() throws Exception {
        Document meta = parse(METADATA);

        EtdMetadataRedactor.redact(meta);

        assertNotNull(meta.selectSingleNode("/DISS_submission/DISS_authorship/DISS_author/DISS_name/DISS_surname"));
        assertNotNull(meta.selectSingleNode("/DISS_submission/DISS_authorship/DISS_author/DISS_orcid"));
        assertNotNull(meta.selectSingleNode("/DISS_submission/DISS_description/DISS_title"));
        assertNotNull(meta.selectSingleNode(
            "/DISS_submission/DISS_description/DISS_institution/DISS_inst_contact"));
        assertNotNull(meta.selectSingleNode(
            "/DISS_submission/DISS_restriction/DISS_sales_restriction[@code='1']/@remove"));
    }

    @Test
    public void testNothingToRedact() throws Exception {
        Document meta = parse("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<DISS_submission/>");

        assertEquals(0, EtdMetadataRedactor.redact(meta));
    }

    @Test
    public void testSerializedAsUtf8() throws Exception {
        Document meta = parse(METADATA);

        EtdMetadataRedactor.redact(meta);
        byte[] bytes = EtdMetadataRedactor.toBytes(meta);

        // The ISO-8859-1 input is written as UTF-8, with a matching declaration
        String redacted = new String(bytes, StandardCharsets.UTF_8);
        assertThat(redacted, containsString("encoding=\"UTF-8\""));
        assertThat(redacted, containsString("<DISS_fname>Ren\u00e9</DISS_fname>"));

        // The serialized document can be read back
        Document reread = new SAXReader().read(new ByteArrayInputStream(bytes));
        assertEquals("Ren\u00e9", reread.selectSingleNode("//DISS_fname").getText());
    }

    @Test
    public void testNoBlankLinesAreLeft() throws Exception {
        Document meta = parse(METADATA);

        EtdMetadataRedactor.redact(meta);

        String redacted = new String(EtdMetadataRedactor.toBytes(meta), StandardCharsets.UTF_8);
        assertThat(redacted, containsString("</DISS_name>\n      <DISS_orcid>"));
    }
}
