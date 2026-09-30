/*
 * Copyright (c) 2006 The University of Maryland. All Rights Reserved.
 *
 */

package edu.umd.lib.dspace.xmlworkflow.state.actions;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.EtdUnit;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.MetadataValue;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.CollectionService;
import org.dspace.content.service.EtdUnitService;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.util.UUIDUtils;
import org.dspace.workflow.WorkflowException;
import org.dspace.xmlworkflow.state.Step;
import org.dspace.xmlworkflow.state.actions.ActionResult;
import org.dspace.xmlworkflow.state.actions.processingaction.ProcessingAction;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * Processing action mapping ETD items into the collections of the ETD units
 * given by their "dc.contributor.department" metadata values.
 *
 * The ETD Loader maps items into the department collections when it creates
 * the workspace item, but XmlWorkflowItem does not retain mapped collections,
 * so the mapping is lost for items routed to the review workflow. This action
 * restores it as the item leaves the workflow.
 *
 * Only items submitted to the collection given by the
 * "drum.etdloader.collection" configuration property are mapped.
 */
public class EtdDepartmentCollectionMappingAction extends ProcessingAction {
    private static Logger log = org.apache.logging.log4j.LogManager.getLogger(
        EtdDepartmentCollectionMappingAction.class);

    /**
     * Configuration property containing the id of the collection the ETD
     * Loader submits to.
     */
    public static final String ETD_COLLECTION_PROPERTY = "drum.etdloader.collection";

    protected ConfigurationService configurationService;
    protected CollectionService collectionService;
    protected EtdUnitService etdUnitService;

    @Override
    public void activate(Context c, XmlWorkflowItem wf)
            throws SQLException, IOException, AuthorizeException, WorkflowException {
        configurationService = DSpaceServicesFactory.getInstance().getConfigurationService();
        collectionService = ContentServiceFactory.getInstance().getCollectionService();
        etdUnitService = ContentServiceFactory.getInstance().getEtdUnitService();
    }

    @Override
    public ActionResult execute(Context c, XmlWorkflowItem wfi, Step step, HttpServletRequest request)
            throws SQLException, AuthorizeException, IOException, WorkflowException {
        Item item = wfi.getItem();

        if (isEtdSubmission(c, wfi)) {
            log.info("Mapping ETD item '{}' to its department collections", item.getID());
            mapToDepartmentCollections(c, wfi);
        } else {
            log.debug("Item '{}' is not an ETD submission", item.getID());
        }

        return new ActionResult(ActionResult.TYPE.TYPE_OUTCOME,
                ActionResult.OUTCOME_COMPLETE);
    }

    @Override
    public List<String> getOptions() {
        return Collections.emptyList();
    }

    /**
     * Returns true if the given workflow item was submitted to the collection
     * the ETD Loader submits to.
     *
     * @param context the current Context
     * @param wfi the XmlWorkflowItem to test
     * @return true if the given workflow item is an ETD submission, false
     * otherwise.
     */
    protected boolean isEtdSubmission(Context context, XmlWorkflowItem wfi) {
        String strCollection = configurationService.getProperty(ETD_COLLECTION_PROPERTY);

        if (strCollection == null) {
            log.warn("Property '{}' not set, skipping ETD department mapping for item '{}'",
                ETD_COLLECTION_PROPERTY, wfi.getItem().getID());
            return false;
        }

        Collection collection = wfi.getCollection();

        return (collection != null) && collection.getID().equals(UUIDUtils.fromString(strCollection));
    }

    /**
     * Maps the given workflow item's Item into the collections of the ETD
     * units named by its "dc.contributor.department" metadata values.
     *
     * @param context the current Context
     * @param wfi the XmlWorkflowItem to map
     */
    protected void mapToDepartmentCollections(Context context, XmlWorkflowItem wfi)
            throws SQLException, AuthorizeException {
        context.turnOffAuthorisationSystem();
        try {
            Item item = wfi.getItem();
            Set<Collection> collections = new HashSet<>();

            List<MetadataValue> departments = itemService.getMetadata(
                item, MetadataSchemaEnum.DC.getName(), "contributor", "department", Item.ANY);

            for (MetadataValue department : departments) {
                String strDepartment = department.getValue().trim().replaceAll(" +", " ");

                EtdUnit etdunit = etdUnitService.findByName(context, strDepartment);

                if (etdunit == null) {
                    log.error("Unable to lookup mapped collection: {}", strDepartment);
                } else {
                    collections.addAll(etdunit.getCollections());
                }
            }

            for (Collection collection : collections) {
                if (collection.equals(wfi.getCollection())) {
                    // The item is submitted to this collection
                    continue;
                }

                log.info("Mapping item '{}' to collection '{}'", item.getID(), collection.getID());
                collectionService.addItem(context, collection, item);
                collectionService.update(context, collection);
                itemService.update(context, item);
            }
        } finally {
            context.restoreAuthSystemState();
        }
    }
}
