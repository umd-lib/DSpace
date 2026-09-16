package org.dspace.app.rest.converter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.builder.GroupBuilder;
import org.dspace.builder.ResourcePolicyBuilder;
import org.dspace.content.Bitstream;
import org.dspace.eperson.Group;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test for BitstreamConverter
 *
 * Using an integration test of ResourcePolicyService dependency
 */
public class BitstreamConverterIT extends AbstractControllerIntegrationTest {
    @Autowired
    private ConverterService converterService;

    private ResourcePolicy etdEmbargoPolicy;
    private ResourcePolicy otherPolicy;
    private Bitstream mockBitstream;
    private BitstreamConverter converter;
    private DateTimeFormatter asDate;

    @Before
    public void setup() throws Exception {
        //We turn off the authorization system in order to create the structure as defined below
        context.turnOffAuthorisationSystem();

        Group etdEmbargoGroup = GroupBuilder.createGroup(context).withName("ETD Embargo").build();
        etdEmbargoPolicy = ResourcePolicyBuilder.createResourcePolicy(context, null, etdEmbargoGroup).build();

        Group otherGroup = GroupBuilder.createGroup(context).withName("Other").build();
        otherPolicy = ResourcePolicyBuilder.createResourcePolicy(context, null, otherGroup).build();

        mockBitstream = mock(Bitstream.class);
        context.restoreAuthSystemState();

        // Need to retrieve BitstreamConverter via converter service so that
        // all the Autowired resources in the converter get initialized.
        // This seems to be due to ConverterService being responsible for
        // autowiring the converters (see comment in DSpaceObjectConverter)
        converter = (BitstreamConverter) ((DSpaceObjectConverter)
             converterService.getConverter(Bitstream.class));
        asDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    }

    @Test(expected = NullPointerException.class)
    public void getEmbargoRestriction_throwsNullPointerExceptionWhenBitstreamIsNull() {
        // Method assumes that provided DSpaceObject is always non-null.
        converter.getEmbargoRestriction(null);

    }

    @Test
    public void testgetEmbargoRestriction_ReturnsNone_WhenNoResourcePolicies() {
        assertEquals("NONE", converter.getEmbargoRestriction(mockBitstream));
    }

    @Test
    public void testgetEmbargoRestriction_ReturnsNone_WhenNoEtdEmbargoPolicy() {
        when(mockBitstream.getResourcePolicies()).thenReturn(List.of(otherPolicy));
        assertEquals("NONE", converter.getEmbargoRestriction(mockBitstream));
    }

    @Test
    public void testgetEmbargoRestriction_ReturnsNone_WhenEtdEmbargoPolicyWithEndDateInPast() throws Exception {
        etdEmbargoPolicy.setEndDate(LocalDate.parse("1972-12-03", asDate));
        when(mockBitstream.getResourcePolicies()).thenReturn(List.of(etdEmbargoPolicy));

        assertEquals("NONE", converter.getEmbargoRestriction(mockBitstream));
    }

    @Test
    public void testgetEmbargoRestriction_ReturnsEndDate_WhenEtdEmbargoPolicyWithEndDateInFuture() throws Exception {
        LocalDate futureDate = LocalDate.now().plusYears(1);

        String expectedDateStr = asDate.format(futureDate);
        etdEmbargoPolicy.setEndDate(futureDate);
        when(mockBitstream.getResourcePolicies()).thenReturn(List.of(etdEmbargoPolicy));

        assertEquals(expectedDateStr, converter.getEmbargoRestriction(mockBitstream));
    }

    @Test
    public void testgetEmbargoRestriction_ReturnsForever_WhenEtdEmbargoPolicyHasNullEndDate() throws Exception {
        etdEmbargoPolicy.setEndDate(null);
        when(mockBitstream.getResourcePolicies()).thenReturn(List.of(etdEmbargoPolicy));

        assertEquals("FOREVER", converter.getEmbargoRestriction(mockBitstream));
    }
}
