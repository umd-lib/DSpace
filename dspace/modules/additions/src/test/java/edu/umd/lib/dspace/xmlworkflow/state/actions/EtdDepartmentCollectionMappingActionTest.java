/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.xmlworkflow.state.actions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.List;

import org.dspace.AbstractUnitTest;
import org.dspace.builder.AbstractBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.WorkflowItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.EtdUnit;
import org.dspace.content.EtdUnitTestUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.EtdUnitService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for the EtdDepartmentCollectionMappingAction
 */
public class EtdDepartmentCollectionMappingActionTest extends AbstractUnitTest {

    private static final String ETD_UNIT_NAME = "ETD Mapping Test Unit";

    private final ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
            .getConfigurationService();

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private final EtdUnitService etdUnitService = ContentServiceFactory.getInstance().getEtdUnitService();

    private final MetadataSchemaService metadataSchemaService = ContentServiceFactory.getInstance()
            .getMetadataSchemaService();

    private final MetadataFieldService metadataFieldService = ContentServiceFactory.getInstance()
            .getMetadataFieldService();

    private Collection etdCollection;
    private Collection departmentCollection;
    private Collection otherCollection;

    private EtdDepartmentCollectionMappingAction action;

    @BeforeClass
    public static void initTestEnvironment() {
        AbstractBuilder.init();
    }

    @AfterClass
    public static void destroyTestEnvironment() throws SQLException {
        AbstractBuilder.destroy();
    }

    @Before
    @Override
    public void init() {
        super.init();

        context.turnOffAuthorisationSystem();
        try {
            MetadataSchema dc = metadataSchemaService.find(context, MetadataSchemaEnum.DC.getName());
            if (metadataFieldService.findByElement(context, MetadataSchemaEnum.DC.getName(),
                    "contributor", "department") == null) {
                metadataFieldService.create(context, dc, "contributor", "department", null);
            }

            Community community = CommunityBuilder.createCommunity(context)
                                                  .withName("ETD Mapping Test Community")
                                                  .build();

            // The collection the ETD Loader submits to. It needs reviewers, so
            // that a submission stays in the workflow instead of being
            // archived immediately.
            etdCollection = CollectionBuilder.createCollection(context, community)
                                             .withName("ETD Mapping Test Collection")
                                             .withWorkflowGroup("reviewer", eperson)
                                             .build();

            departmentCollection = CollectionBuilder.createCollection(context, community)
                                                    .withName("Department Collection")
                                                    .build();

            otherCollection = CollectionBuilder.createCollection(context, community)
                                               .withName("Non ETD Collection")
                                               .withWorkflowGroup("reviewer", eperson)
                                               .build();

            EtdUnit etdUnit = etdUnitService.findByName(context, ETD_UNIT_NAME);
            if (etdUnit == null) {
                etdUnit = EtdUnitTestUtils.createEtdUnit(context, ETD_UNIT_NAME, false);
            }
            etdUnitService.addCollection(context, etdUnit, departmentCollection);

            configurationService.setProperty(
                EtdDepartmentCollectionMappingAction.ETD_COLLECTION_PROPERTY,
                etdCollection.getID().toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }

        // Use the configured bean, so that the Spring wiring in
        // "workflow-actions.xml" is exercised along with the action
        action = DSpaceServicesFactory.getInstance().getServiceManager().getServiceByName(
            "etdDepartmentCollectionMappingAPI", EtdDepartmentCollectionMappingAction.class);
    }

    /**
     * Creates a workflow item in the given collection, with the given
     * "dc.contributor.department" value.
     */
    private XmlWorkflowItem createWorkflowItem(Collection collection, String department) throws Exception {
        context.turnOffAuthorisationSystem();
        try {
            XmlWorkflowItem wfi = WorkflowItemBuilder.createWorkflowItem(context, collection)
                                                     .withTitle("Test Thesis")
                                                     .withSubmitter(eperson)
                                                     .build();

            if (department != null) {
                itemService.addMetadata(context, wfi.getItem(), MetadataSchemaEnum.DC.getName(),
                        "contributor", "department", null, department);
                itemService.update(context, wfi.getItem());
            }

            return wfi;
        } finally {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Runs the action against the given workflow item.
     */
    private ActionResult execute(XmlWorkflowItem wfi) throws Exception {
        action.activate(context, wfi);
        return action.execute(context, wfi, null, null);
    }

    @Test
    public void testItemIsMappedToDepartmentCollection() throws Exception {
        XmlWorkflowItem wfi = createWorkflowItem(etdCollection, ETD_UNIT_NAME);
        Item item = wfi.getItem();

        assertFalse("The item should not start out in the department collection",
            item.getCollections().contains(departmentCollection));

        ActionResult result = execute(wfi);

        assertEquals(ActionResult.TYPE.TYPE_OUTCOME, result.getType());
        assertEquals(ActionResult.OUTCOME_COMPLETE, result.getResult());
        // Only the department collection is added; the collection the item was
        // submitted to is assigned when the item is installed on approval
        assertEquals("The item should be mapped to the department collection",
            List.of(departmentCollection), context.reloadEntity(item).getCollections());
    }

    @Test
    public void testItemInAnotherCollectionIsNotMapped() throws Exception {
        XmlWorkflowItem wfi = createWorkflowItem(otherCollection, ETD_UNIT_NAME);

        execute(wfi);

        assertFalse("Only submissions to the ETD collection should be mapped",
            context.reloadEntity(wfi.getItem()).getCollections().contains(departmentCollection));
    }

    @Test
    public void testUnknownDepartmentIsIgnored() throws Exception {
        XmlWorkflowItem wfi = createWorkflowItem(etdCollection, "No Such Department");

        ActionResult result = execute(wfi);

        assertEquals(ActionResult.OUTCOME_COMPLETE, result.getResult());
        assertTrue("An unknown department should not map the item anywhere",
            context.reloadEntity(wfi.getItem()).getCollections().isEmpty());
    }

    @Test
    public void testItemWithoutDepartmentIsIgnored() throws Exception {
        XmlWorkflowItem wfi = createWorkflowItem(etdCollection, null);

        ActionResult result = execute(wfi);

        assertEquals(ActionResult.OUTCOME_COMPLETE, result.getResult());
        assertTrue("An item without a department should not be mapped anywhere",
            context.reloadEntity(wfi.getItem()).getCollections().isEmpty());
    }
}
