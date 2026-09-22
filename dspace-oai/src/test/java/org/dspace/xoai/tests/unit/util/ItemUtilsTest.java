/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xoai.tests.unit.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;

import com.lyncode.xoai.dataprovider.xml.xoai.Element;
import com.lyncode.xoai.dataprovider.xml.xoai.Metadata;
import org.dspace.AbstractUnitTest;
import org.dspace.app.util.factory.UtilServiceFactory;
import org.dspace.app.util.service.MetadataExposureService;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.BitstreamService;
import org.dspace.content.service.BundleService;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.CommunityService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.WorkspaceItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.eperson.service.GroupService;
import org.dspace.xoai.util.ItemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link ItemUtils}.
 *
 * @author mohideen
 */
public class ItemUtilsTest extends AbstractUnitTest {

    private Community community;
    private Collection collection;
    private Item item;
    private Bundle originalBundle;
    private Bundle metadataBundle;
    private Bitstream originalBitstream;
    private Bitstream metadataBitstream;

    private CommunityService communityService = ContentServiceFactory.getInstance().getCommunityService();
    private CollectionService collectionService = ContentServiceFactory.getInstance().getCollectionService();
    private ItemService itemService = ContentServiceFactory.getInstance().getItemService();
    private BundleService bundleService = ContentServiceFactory.getInstance().getBundleService();
    private BitstreamService bitstreamService = ContentServiceFactory.getInstance().getBitstreamService();
    private WorkspaceItemService workspaceItemService = ContentServiceFactory.getInstance().getWorkspaceItemService();
    private InstallItemService installItemService = ContentServiceFactory.getInstance().getInstallItemService();
    private GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();
    private EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    private AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();
    private ResourcePolicyService resourcePolicyService = AuthorizeServiceFactory.getInstance()
            .getResourcePolicyService();
    private MetadataExposureService metadataExposureService =
            UtilServiceFactory.getInstance().getMetadataExposureService();

    /**
     * Spy of AuthorizeService to use for tests
     */
    private AuthorizeService authorizeServiceSpy;

    @Before
    @Override
    public void init() {
        super.init();

        try {
            context.turnOffAuthorisationSystem();

            // Create community and collection
            community = communityService.create(null, context);
            collection = collectionService.create(context, community);

            // Create workspace item and install it
            WorkspaceItem workspaceItem = workspaceItemService.create(context, collection, true);
            item = installItemService.installItem(context, workspaceItem);

            // Create ORIGINAL bundle with a bitstream
            originalBundle = bundleService.create(context, item, Constants.DEFAULT_BUNDLE_NAME);
            // Use BitstreamService.create with InputStream (null for empty bitstream in tests)
            originalBitstream = bitstreamService.create(context, originalBundle, null);
            originalBitstream.setName(context, "test.pdf");
            originalBitstream.setSource(context, "test.pdf");
            itemService.update(context, item);

            // Create METADATA bundle (non-ORIGINAL) with a bitstream containing PII
            metadataBundle = bundleService.create(context, item, "METADATA");
            metadataBitstream = bitstreamService.create(context, metadataBundle, null);
            metadataBitstream.setName(context, "proquest.xml");
            metadataBitstream.setSource(context, "proquest.xml");
            itemService.update(context, item);

            context.restoreAuthSystemState();

            // Initialize spy for authorization checks
            authorizeServiceSpy = spy(authorizeService);
            ReflectionTestUtils.setField(bundleService, "authorizeService", authorizeServiceSpy);
            ReflectionTestUtils.setField(bitstreamService, "authorizeService", authorizeServiceSpy);
            ReflectionTestUtils.setField(itemService, "authorizeService", authorizeServiceSpy);
            ReflectionTestUtils.setField(AuthorizeServiceFactory.getInstance(), "authorizeService",
                    authorizeServiceSpy);
        } catch (Exception e) {
            throw new RuntimeException("Error in test setup", e);
        }
    }

    @After
    @Override
    public void destroy() {
        try {
            context.turnOffAuthorisationSystem();
            if (item != null) {
                itemService.delete(context, item);
            }
            if (community != null) {
                communityService.delete(context, community);
            }
            context.restoreAuthSystemState();
        } catch (Exception e) {
            throw new RuntimeException("Error in test cleanup", e);
        }
        super.destroy();
    }

    /**
     * Helper method to create an unauthenticated Context (READ_ONLY mode like OAI indexer uses).
     */
    private Context createAnonymousContext() throws SQLException {
        Context anonymousContext = new Context(Context.Mode.READ_ONLY);
        anonymousContext.turnOffAuthorisationSystem();
        // Ensure no authenticated user - this simulates OAI indexer behavior
        anonymousContext.setCurrentUser(null);
        anonymousContext.restoreAuthSystemState();
        return anonymousContext;
    }

    /**
     * Test that an authenticated Context still works for retrieveMetadata.
     */
    @Test
    public void testRetrieveMetadataWithAuthenticatedContext() throws Exception {
        context.turnOffAuthorisationSystem();
        context.setCurrentUser(eperson);
        context.restoreAuthSystemState();

        Metadata metadata = ItemUtils.retrieveMetadata(context, item);

        assertNotNull("Metadata should not be null", metadata);
        Element bundles = ElementUtils.findElement(metadata.getElement(), "bundles");
        assertNotNull("Bundles element should exist", bundles);
    }

    /**
     * Test that ORIGINAL bundle bitstreams are included when Anonymous has READ access.
     */
    @Test
    public void testAnonymousCanReadOriginalBundle() throws Exception {
        context.turnOffAuthorisationSystem();
        // Give Anonymous READ access to the ORIGINAL bundle and its bitstream
        authorizeService.addPolicy(context, originalBundle, Constants.READ,
                groupService.findByName(context, Group.ANONYMOUS));
        authorizeService.addPolicy(context, originalBitstream, Constants.READ,
                groupService.findByName(context, Group.ANONYMOUS));
        context.restoreAuthSystemState();

        Context anonymousContext = createAnonymousContext();
        try {
            Metadata metadata = ItemUtils.retrieveMetadata(anonymousContext, item);

            Element bundles = ElementUtils.findElement(metadata.getElement(), "bundles");
            assertNotNull("Bundles element should exist", bundles);

            // Find the ORIGINAL bundle
            Element originalBundleElement = findBundleByName(bundles, Constants.DEFAULT_BUNDLE_NAME);
            assertNotNull("ORIGINAL bundle should be present", originalBundleElement);

            // Verify the bitstream is included
            Element bitstreams = ElementUtils.findElement(originalBundleElement.getElement(), "bitstreams");
            assertNotNull("Bitstreams element should exist", bitstreams);
            assertThat("Should have 1 bitstream", getBitstreamCount(bitstreams), equalTo(1));

            // Verify the bitstream name is included
            Element bitstreamElement = findBitstreamByName(bitstreams, "test.pdf");
            assertNotNull("Bitstream element should exist", bitstreamElement);
            assertThat("Bitstream name should be included",
                    ElementUtils.getFieldValue(bitstreamElement, "name"), equalTo("test.pdf"));
            assertThat("Original filename should be included",
                    ElementUtils.getFieldValue(bitstreamElement, "originalName"), equalTo("test.pdf"));
        } finally {
            anonymousContext.abort();
        }
    }

    /**
     * Test that non-ORIGINAL bundles (like METADATA) are excluded when Anonymous
     * does not have READ access - this is the main fix for LIBDRUM-1042.
     */
    @Test
    public void testAnonymousCannotReadNonOriginalBundle_Excluded() throws Exception {
        context.turnOffAuthorisationSystem();
        // Do NOT give Anonymous READ access to the METADATA bundle
        context.restoreAuthSystemState();

        Context anonymousContext = createAnonymousContext();
        try {
            Metadata metadata = ItemUtils.retrieveMetadata(anonymousContext, item);

            Element bundles = ElementUtils.findElement(metadata.getElement(), "bundles");
            assertNotNull("Bundles element should exist", bundles);

            // METADATA bundle should NOT be present
            Element metadataBundleElement = findBundleByName(bundles, "METADATA");
            assertNull("METADATA bundle should NOT be present for Anonymous",
                    metadataBundleElement);

            // ORIGINAL bundle should still be present
            Element originalBundleElement = findBundleByName(bundles, Constants.DEFAULT_BUNDLE_NAME);
            assertNotNull("ORIGINAL bundle should be present", originalBundleElement);
        } finally {
            anonymousContext.abort();
        }
    }

    /**
     * Test that individual bitstreams within a readable non-ORIGINAL bundle
     * are excluded if they don't have individual READ permission.
     */
    @Test
    public void testIndividualBitstreamInNonOriginalBundle_Excluded() throws Exception {
        context.turnOffAuthorisationSystem();
        // Give Anonymous READ access to the METADATA bundle itself
        authorizeService.addPolicy(context, metadataBundle, Constants.READ,
                groupService.findByName(context, Group.ANONYMOUS));
        // But DO NOT give Anonymous READ access to the specific bitstream
        context.restoreAuthSystemState();

        Context anonymousContext = createAnonymousContext();
        try {
            Metadata metadata = ItemUtils.retrieveMetadata(anonymousContext, item);

            Element bundles = ElementUtils.findElement(metadata.getElement(), "bundles");
            assertNotNull("Bundles element should exist", bundles);

            // METADATA bundle should be present (bundle-level READ allowed)
            Element metadataBundleElement = findBundleByName(bundles, "METADATA");
            assertNotNull("METADATA bundle should be present (bundle readable)", metadataBundleElement);

            // But the bitstream inside should NOT be present (bitstream-level READ denied)
            Element bitstreams = ElementUtils.findElement(metadataBundleElement.getElement(), "bitstreams");
            assertNotNull("Bitstreams element should exist", bitstreams);
            assertThat("Bitstream should be excluded (bitstream not readable)",
                    getBitstreamCount(bitstreams), equalTo(0));
        } finally {
            anonymousContext.abort();
        }
    }

    /**
     * Test that the ORIGINAL bundle is NOT filtered even when it has no explicit
     * Anonymous READ policy (should inherit from item).
     */
    @Test
    public void testOriginalBundleAlwaysIncluded() throws Exception {
        context.turnOffAuthorisationSystem();
        // Give Anonymous READ access to the item itself, but NOT explicitly to bundles
        authorizeService.addPolicy(context, item, Constants.READ,
                groupService.findByName(context, Group.ANONYMOUS));
        context.restoreAuthSystemState();

        Context anonymousContext = createAnonymousContext();
        try {
            Metadata metadata = ItemUtils.retrieveMetadata(anonymousContext, item);

            Element bundles = ElementUtils.findElement(metadata.getElement(), "bundles");
            assertNotNull("Bundles element should exist", bundles);

            // ORIGINAL bundle should be present (not filtered by our customization)
            Element originalBundleElement = findBundleByName(bundles, Constants.DEFAULT_BUNDLE_NAME);
            assertNotNull("ORIGINAL bundle should always be included", originalBundleElement);
        } finally {
            anonymousContext.abort();
        }
    }

    /**
     * Test that ResourcePolicy information is included for embargoed bitstreams.
     */
    @Test
    public void testResourcePolicyInformationForEmbargoedBitstream() throws Exception {
        context.turnOffAuthorisationSystem();
        // Create an embargoed bitstream
        ResourcePolicy rp = resourcePolicyService.create(context, eperson, groupService.findByName(context,
                Group.ANONYMOUS));
        rp.setAction(Constants.READ);
        rp.setStartDate(java.time.LocalDate.now().plusDays(365)); // Future start date = embargo
        resourcePolicyService.update(context, rp);

        context.restoreAuthSystemState();

        Metadata metadata = ItemUtils.retrieveMetadata(context, item);

        Element bundles = ElementUtils.findElement(metadata.getElement(), "bundles");
        Element originalBundleElement = findBundleByName(bundles, Constants.DEFAULT_BUNDLE_NAME);
        Element bitstreams = ElementUtils.findElement(originalBundleElement.getElement(), "bitstreams");
        Element bitstreamElement = findBitstreamByName(bitstreams, "test.pdf");

        // Verify resource policies are present
        Element resourcePolicies = ElementUtils.findElement(bitstreamElement.getElement(), "resourcePolicies");
        assertNotNull("Resource policies should be present", resourcePolicies);
    }

    /**
     * Utility class to help with XML element navigation in tests.
     */
    private static class ElementUtils {
        static Element findElement(List<Element> elements, String name) {
            for (Element e : elements) {
                if (name.equals(e.getName())) {
                    return e;
                }
            }
            return null;
        }

        static String getFieldValue(Element element, String fieldName) {
            for (Element.Field f : element.getField()) {
                if (fieldName.equals(f.getName())) {
                    return f.getValue();
                }
            }
            return null;
        }
    }

    private Element findBundleByName(Element bundlesElement, String bundleName) {
        for (Element bundle : bundlesElement.getElement()) {
            if (bundleName.equals(ElementUtils.getFieldValue(bundle, "name"))) {
                return bundle;
            }
        }
        return null;
    }

    private int getBitstreamCount(Element bitstreamsElement) {
        return bitstreamsElement.getElement().size();
    }

    private Element findBitstreamByName(Element bitstreamsElement, String bitstreamName) {
        for (Element bitstream : bitstreamsElement.getElement()) {
            if (bitstreamName.equals(ElementUtils.getFieldValue(bitstream, "name"))) {
                return bitstream;
            }
        }
        return null;
    }
}
