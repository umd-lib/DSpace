/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.xmlworkflow.storedcomponents;

// UMD Customization
import java.util.Collections;
import java.util.List;
// End UMD Customization

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.workflow.WorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;

/**
 * Class representing an item going through the workflow process in DSpace
 *
 * @author Bram De Schouwer (bram.deschouwer at dot com)
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 */
@Entity
@Table(name = "cwf_workflowitem")
public class XmlWorkflowItem implements WorkflowItem {

    @Id
    @Column(name = "workflowitem_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cwf_workflowitem_seq")
    @SequenceGenerator(name = "cwf_workflowitem_seq", sequenceName = "cwf_workflowitem_seq", allocationSize = 1)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collection_id")
    private Collection collection;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", unique = true)
    private Item item;

    @Column(name = "multiple_titles")
    private boolean multipleTitles = false;

    @Column(name = "published_before")
    private boolean publishedBefore = false;

    @Column(name = "multiple_files")
    private boolean multipleFiles = false;

    /**
     * Protected constructor, create object using:
     * {@link XmlWorkflowItemService#create(Context, Item, Collection)}
     */
    protected XmlWorkflowItem() {

    }

    /**
     * Get the internal ID of this workflow item
     *
     * @return the internal identifier
     */
    @Override
    public Integer getID() {
        return id;
    }


    @Override
    public Collection getCollection() {
        return this.collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    @Override
    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    @Override
    public EPerson getSubmitter() {
        return item.getSubmitter();
    }

    @Override
    public boolean hasMultipleFiles() {
        return multipleFiles;
    }

    @Override
    public void setMultipleFiles(boolean b) {
        this.multipleFiles = b;
    }

    @Override
    public boolean hasMultipleTitles() {
        return this.multipleTitles;
    }

    @Override
    public void setMultipleTitles(boolean b) {
        this.multipleTitles = b;
    }

    @Override
    public boolean isPublishedBefore() {
        return this.publishedBefore;
    }

    @Override
    public void setPublishedBefore(boolean b) {
        this.publishedBefore = b;
    }

    // UMD Customization
    // Stub implementation for InProgressSubmission interface compliance.
    /**
     * Unimplemented stub
     */
    @Override
    public void addMappedCollections(List<Collection> collections) {
    }

    /**
     * Unimplemented stub
     */
    @Override
    public List<Collection> getMappedCollections() {
        return Collections.emptyList();
    }

    /**
     * Unimplemented stub
     */
    @Override
    public void removeMappedCollections() {
    }
    // End UMD Customization
}
