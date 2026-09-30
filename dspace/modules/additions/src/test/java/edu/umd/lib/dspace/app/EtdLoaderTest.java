package edu.umd.lib.dspace.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.dspace.AbstractUnitTest;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.authorize.factory.AuthorizeServiceFactory;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.builder.AbstractBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.EtdUnit;
import org.dspace.content.EtdUnitTestUtils;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataSchemaEnum;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.EtdUnitService;
import org.dspace.content.service.InstallItemService;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.dspace.workflow.WorkflowService;
import org.dspace.workflow.factory.WorkflowServiceFactory;
import org.dspace.xmlworkflow.factory.XmlWorkflowServiceFactory;
import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;
import org.dspace.xmlworkflow.storedcomponents.service.XmlWorkflowItemService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for the EtdLoader
 */
public class EtdLoaderTest extends AbstractUnitTest {
    TestEtdLoaderConfiguration testEtdLoaderConfig = new TestEtdLoaderConfiguration();
    private TestLog4JLogger etdLogger;

    private final ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    private final InstallItemService installItemService = ContentServiceFactory.getInstance()
            .getInstallItemService();

    private final AuthorizeService authorizeService = AuthorizeServiceFactory.getInstance().getAuthorizeService();

    @BeforeClass
    public static void initTestEnvironment() {
        // Need to initialize AbstractBuilder so services for various builders
        // are properly initialized
        AbstractBuilder.init();
    }

    /**
     * This method will be run before every test as per @Before. It will
     * initialize resources required for the tests.
     *
     * Other methods can be annotated with @Before here or in subclasses
     * but no execution order is guaranteed
     */
    @Before
    @Override
    public void init() {
        super.init();

        etdLogger = new TestLog4JLogger("edu.umd.lib.dspace.app.EtdLoader", Level.INFO);
        etdLogger.setUp();

        testEtdLoaderConfig.initDspaceForEtdLoader(context);

        // Kludge to reset static counts in EtdLoader
        EtdLoader.lEmbargo = 0;
        EtdLoader.lRead = 0;
        EtdLoader.lWritten = 0;
        EtdLoader.lReview = 0;

        testEtdLoaderConfig.setReviewEnabled(false);
        testEtdLoaderConfig.captureExistingWorkflowItems(context);
    }

    @After
    @Override
    public void destroy() {
        etdLogger.tearDown();
        super.destroy();
    }

    @AfterClass
    public static void destroyTestEnvironment() throws SQLException {
        // Unload DSpace services
        AbstractBuilder.destroy();
    }

    @Test
    public void testMainOneItem() throws Exception {
        testEtdLoaderConfig.setEtdLoaderScriptProperties(
            "/edu/umd/lib/dspace/app/etdadmin_upload_test_one_item.zip", eperson);

        String[] args = new String[0];

        EtdLoader.main(args);

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("Records written: 1"));
        assertThat(logOutput, containsString("Embargoes:       0"));
    }

    @Test
    public void testMainEmbargoedItem() throws Exception {
        testEtdLoaderConfig.setEtdLoaderScriptProperties(
            "/edu/umd/lib/dspace/app/etdadmin_embargoed_item.zip", eperson);

        String[] args = new String[0];

        EtdLoader.main(args);

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("Records written: 1"));
        assertThat(logOutput, containsString("Embargoes:       1"));
        assertThat(logOutput, containsString("Embargoed until Tue Jun 26 3027"));
    }

    @Test
    public void testSupplementaryFileRoutedToReview() throws Exception {
        testEtdLoaderConfig.addWorkflowReviewers(context, eperson);
        testEtdLoaderConfig.setReviewEnabled(true);
        testEtdLoaderConfig.setEtdLoaderScriptProperties(
            createZipWithSupplementaryFile("appendix.wav"), eperson);

        assertFalse("The item should not be skipped", EtdLoader.run());

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("Records written: 0"));
        assertThat(logOutput, containsString("Routed to review: 1"));
        assertThat(logOutput, containsString("1 supplementary file(s)"));

        XmlWorkflowItem wfi = testEtdLoaderConfig.getOnlyWorkflowItem(context);
        Item item = wfi.getItem();

        assertFalse("The item should not be archived", item.isArchived());

        // The reason for the review is recorded in the item's provenance
        String provenance = itemService.getMetadataFirstValue(
            item, MetadataSchemaEnum.DC.getName(), "description", "provenance", Item.ANY);
        assertThat(provenance, containsString("Routed to ETD review"));
        assertThat(provenance, containsString("1 supplementary file(s)"));

        // The supplementary file is restricted to the review group, with a
        // custom policy so that installation on approval does not replace it
        Bitstream supplementary = testEtdLoaderConfig.getBitstream(item, "appendix.wav");
        List<ResourcePolicy> policies = groupPolicies(supplementary);
        assertEquals("The supplementary file should only be readable by the review group",
            1, policies.size());
        assertEquals(testEtdLoaderConfig.getReviewGroup(context), policies.get(0).getGroup());
        assertEquals(ResourcePolicy.TYPE_CUSTOM, policies.get(0).getRpType());

        // The workflow grants the reviewers their own (temporary) READ policy,
        // so that they can look at the file they are reviewing
        assertTrue("The reviewers should be able to read the supplementary file",
            authorizeService.getPoliciesActionFilter(context, supplementary, Constants.READ).stream()
                .anyMatch(p -> ResourcePolicy.TYPE_WORKFLOW.equals(p.getRpType())));

        // The thesis itself is not restricted
        Bitstream thesis = testEtdLoaderConfig.getBitstream(item, "Author_umd_0117N_12345.pdf");
        assertTrue("The thesis should not be restricted to the review group",
            groupPolicies(thesis).stream()
                .noneMatch(p -> testEtdLoaderConfig.isReviewGroup(p.getGroup())));
    }

    @Test
    public void testRestrictionSurvivesInstallationOnApproval() throws Exception {
        testEtdLoaderConfig.addWorkflowReviewers(context, eperson);
        testEtdLoaderConfig.setReviewEnabled(true);
        testEtdLoaderConfig.setEtdLoaderScriptProperties(
            createZipWithSupplementaryFile("appendix.wav"), eperson);

        assertFalse(EtdLoader.run());

        XmlWorkflowItem wfi = testEtdLoaderConfig.getOnlyWorkflowItem(context);

        // Approving the item installs it into the collection, which is where
        // the collection's default (Anonymous) READ policies are applied
        context.turnOffAuthorisationSystem();
        Item item = installItemService.installItem(context, wfi);
        context.restoreAuthSystemState();

        assertTrue("The item should be archived", item.isArchived());

        // The custom policy stops the collection's default READ policy from
        // being added to the supplementary file
        Bitstream supplementary = testEtdLoaderConfig.getBitstream(item, "appendix.wav");
        List<ResourcePolicy> policies = groupPolicies(supplementary);
        assertEquals("The supplementary file should still only be readable by the review group",
            1, policies.size());
        assertEquals(testEtdLoaderConfig.getReviewGroup(context), policies.get(0).getGroup());

        // The thesis inherits the collection's default READ policy
        Bitstream thesis = testEtdLoaderConfig.getBitstream(item, "Author_umd_0117N_12345.pdf");
        assertTrue("The thesis should be publicly readable",
            groupPolicies(thesis).stream()
                .anyMatch(p -> Group.ANONYMOUS.equals(p.getGroup().getName())));
    }

    /**
     * Returns the group READ policies of the given bitstream, ignoring the
     * policies the workflow grants to the submitter (TYPE_SUBMISSION, which
     * has no group) and to the reviewers (TYPE_WORKFLOW). Both are removed
     * when the item is installed.
     */
    private List<ResourcePolicy> groupPolicies(Bitstream bitstream) throws SQLException {
        return authorizeService.getPoliciesActionFilter(context, bitstream, Constants.READ).stream()
                .filter(p -> p.getGroup() != null)
                .filter(p -> !ResourcePolicy.TYPE_WORKFLOW.equals(p.getRpType()))
                .collect(Collectors.toList());
    }

    @Test
    public void testItemSkippedWhenCollectionHasNoReviewers() throws Exception {
        testEtdLoaderConfig.setReviewEnabled(true);
        testEtdLoaderConfig.setEtdLoaderScriptProperties(
            createZipWithSupplementaryFile("appendix.wav"), eperson);

        assertTrue("The item should be skipped, so the Zip file is retried", EtdLoader.run());

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("has no workflow reviewers"));
        assertThat(logOutput, containsString("Records written: 0"));
        assertThat(logOutput, containsString("Routed to review: 0"));

        assertTrue("No workflow item should have been created",
            testEtdLoaderConfig.getWorkflowItems(context).isEmpty());
    }

    @Test
    public void testSupplementaryFileInstalledWhenReviewDisabled() throws Exception {
        testEtdLoaderConfig.addWorkflowReviewers(context, eperson);
        testEtdLoaderConfig.setReviewEnabled(false);
        testEtdLoaderConfig.setEtdLoaderScriptProperties(
            createZipWithSupplementaryFile("appendix.wav"), eperson);

        assertFalse(EtdLoader.run());

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("Records written: 1"));
        assertThat(logOutput, containsString("Routed to review: 0"));

        assertTrue("No workflow item should have been created",
            testEtdLoaderConfig.getWorkflowItems(context).isEmpty());
    }

    @Test
    public void testNotificationFailureDoesNotRollBackRoutedItem() throws Exception {
        testEtdLoaderConfig.addWorkflowReviewers(context, eperson);
        testEtdLoaderConfig.setReviewEnabled(true);

        // An unknown department means no mapped collections, so the loader
        // sends a "missing collections" email, which fails because the
        // recipient is not a valid address
        File zipFile = EtdZipFileBuilder.createFromResource(
            testEtdLoaderConfig.newZipFile("etdadmin_upload_unknown_department.zip"),
            "/edu/umd/lib/dspace/app/etdadmin_upload_test_one_item.zip",
            xml -> xml.replace("<DISS_inst_contact>ETD Test Unit</DISS_inst_contact>",
                               "<DISS_inst_contact>Unknown Test Department</DISS_inst_contact>"),
            Map.of("appendix.wav", "placeholder".getBytes(StandardCharsets.ISO_8859_1)));
        testEtdLoaderConfig.setEtdLoaderScriptProperties(zipFile, eperson);

        testEtdLoaderConfig.useInvalidEtdMailRecipient();
        try {
            assertFalse(EtdLoader.run());
        } finally {
            testEtdLoaderConfig.restoreEtdMailRecipient();
        }

        // The item is still in the workflow, even though the email failed
        XmlWorkflowItem wfi = testEtdLoaderConfig.getOnlyWorkflowItem(context);
        assertFalse("The item should not be archived", wfi.getItem().isArchived());

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("Routed to review: 1"));
        assertThat(logOutput, containsString("Unable to send notifications"));
    }

    @Test
    public void testEmbargoedItemRoutedToReviewKeepsCustomPolicies() throws Exception {
        testEtdLoaderConfig.addWorkflowReviewers(context, eperson);
        testEtdLoaderConfig.setReviewEnabled(true);

        File zipFile = EtdZipFileBuilder.createFromResource(
            testEtdLoaderConfig.newZipFile("etdadmin_embargoed_supplementary.zip"),
            "/edu/umd/lib/dspace/app/etdadmin_embargoed_item.zip", null,
            Map.of("appendix.wav", "placeholder".getBytes(StandardCharsets.ISO_8859_1)));
        testEtdLoaderConfig.setEtdLoaderScriptProperties(zipFile, eperson);

        assertFalse(EtdLoader.run());

        String logOutput = etdLogger.getLog();
        assertThat(logOutput, containsString("Routed to review: 1"));
        assertThat(logOutput, containsString("Embargoes:       1"));

        XmlWorkflowItem wfi = testEtdLoaderConfig.getOnlyWorkflowItem(context);
        Item item = wfi.getItem();

        // The embargo policies are custom, so that they are not replaced by
        // the collection's default policies when the item is installed
        // The embargo policies are custom; the workflow's own policies, which
        // are removed when the item is installed, are not of interest here.
        Bitstream thesis = testEtdLoaderConfig.getBitstream(item, "Author_umd_0117E_98765.pdf");
        List<ResourcePolicy> policies = groupPolicies(thesis);
        assertFalse(policies.isEmpty());
        for (ResourcePolicy policy : policies) {
            assertEquals(ResourcePolicy.TYPE_CUSTOM, policy.getRpType());
        }
        assertTrue("The ETD Embargo group should be able to read the thesis",
            policies.stream().anyMatch(p -> "ETD Embargo".equals(p.getGroup().getName())));

        // The supplementary file keeps the stricter review group policy
        Bitstream supplementary = testEtdLoaderConfig.getBitstream(item, "appendix.wav");
        List<ResourcePolicy> supplementaryPolicies = groupPolicies(supplementary);
        assertEquals(1, supplementaryPolicies.size());
        assertEquals(testEtdLoaderConfig.getReviewGroup(context), supplementaryPolicies.get(0).getGroup());
    }

    /**
     * Returns an ETD Zip file containing the single item test resource, plus a
     * supplementary file with the given name.
     */
    private File createZipWithSupplementaryFile(String fileName) throws Exception {
        return EtdZipFileBuilder.createFromResource(
            testEtdLoaderConfig.newZipFile("etdadmin_upload_supplementary.zip"),
            "/edu/umd/lib/dspace/app/etdadmin_upload_test_one_item.zip", null,
            Map.of(fileName, "placeholder".getBytes(StandardCharsets.ISO_8859_1)));
    }
}

/**
 * Provides setup/cleanup for the DSpace configuration needed to run the
 * EtdLoader class.
 */
class TestEtdLoaderConfiguration {
    private final static ConfigurationService configurationService = DSpaceServicesFactory.getInstance()
            .getConfigurationService();

    private final static EtdUnitService etdUnitService = ContentServiceFactory.getInstance().getEtdUnitService();

    private final static GroupService groupService = EPersonServiceFactory.getInstance().getGroupService();

    private final static MetadataSchemaService metadataSchemaService = ContentServiceFactory.getInstance()
            .getMetadataSchemaService();

    private final static MetadataFieldService metadataFieldService = ContentServiceFactory.getInstance()
            .getMetadataFieldService();

    private final static WorkflowService workflowService = WorkflowServiceFactory.getInstance().getWorkflowService();

    private final static XmlWorkflowItemService xmlWorkflowItemService = XmlWorkflowServiceFactory.getInstance()
            .getXmlWorkflowItemService();

    /** Group that supplementary files are restricted to during review */
    public final static String REVIEW_GROUP_NAME = "ETD Review";

    /** Recipient of the ETD Loader's "missing collections" email */
    private final static String ETD_MAIL_RECIPIENT_PROP = "drum.mail.etd.recipient";

    private Group etdEmbargoGroup;
    private Group etdReviewGroup;
    private Community testCommunity;
    private Collection testCollection;
    private EtdUnit etdUnit;
    private File zipFileDirectory;
    private Set<Integer> existingWorkflowItemIds = Collections.emptySet();
    private String savedEtdMailRecipient;

    /**
     * Sets up the ETD group, metadata field entries, community, collection, and
     * ETD Unit needed for the ETD Loader.
     *
     * This method is typically called from an @Before test method.
     *
     * @param context the DSpace context
     */
    public void initDspaceForEtdLoader(Context context) {
        context.turnOffAuthorisationSystem();
        try {
            if (groupService.findByName(context, "ETD Embargo") == null) {
                etdEmbargoGroup = groupService.create(context);
                groupService.setName(etdEmbargoGroup, "ETD Embargo");
                groupService.update(context, etdEmbargoGroup);
            }

            etdReviewGroup = groupService.findByName(context, REVIEW_GROUP_NAME);
            if (etdReviewGroup == null) {
                etdReviewGroup = groupService.create(context);
                groupService.setName(etdReviewGroup, REVIEW_GROUP_NAME);
                groupService.update(context, etdReviewGroup);
            }

            addMetadataField(context, "dc", "contributor", "department");
            addMetadataField(context, "dc", "contributor", "publisher");
            addMetadataField(context, "dc", "subject", "pqcontrolled");
            addMetadataField(context, "dc", "subject", "pquncontrolled");

            testCommunity = CommunityBuilder.createCommunity(context)
                                            .withName("ETD Test Community")
                                            .build();
            testCollection = CollectionBuilder.createCollection(context, testCommunity)
                                            .withName("ETD Test Collection")
                                            .build();
            // testPerson = EPersonBuilder.createEPerson(context)
            //                                  .withEmail("test@test.com")
            //                                  .withPassword("test")
            //                                  .build();
            etdUnit = etdUnitService.findByName(context, "ETD Test Unit");
            if (etdUnit == null) {
                etdUnit = EtdUnitTestUtils.createEtdUnit(context, "ETD Test Unit", false);
            }
            etdUnitService.addCollection(context, etdUnit, testCollection);

            etdUnit = context.reloadEntity(etdUnit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Sets the properties provided to the EtdLoader by the "load-etd" script
     * and configuration properties.
     *
     * This method is typically called from within an @Test method
     * @param etdZipFile the file path to the ETD Zip file to load
     */
    public void setEtdLoaderScriptProperties(String etdZipFile, EPerson eperson) throws Exception {
        URL zipFileResourceUrl = getClass().getResource(etdZipFile);
        File zipFile = new File(zipFileResourceUrl.toURI());

        System.setProperty("etdloader.zipfile", zipFile.getCanonicalPath());
        configurationService.setProperty("drum.etdloader.eperson", eperson.getEmail());
        configurationService.setProperty("drum.etdloader.collection", testCollection.getID().toString());
        configurationService.setProperty("drum.etdloader.maxFileSize", "-1");
    }

    public void setEtdLoaderScriptProperties(String etdZipFile, EPerson eperson, int maxFileSize) throws Exception {
        setEtdLoaderScriptProperties(etdZipFile, eperson);
        configurationService.setProperty("drum.etdloader.maxFileSize", "" + maxFileSize);
    }

    /**
     * Sets the properties provided to the EtdLoader for a Zip file created by
     * the test, rather than one of the test resources.
     *
     * @param etdZipFile the ETD Zip file to load
     */
    public void setEtdLoaderScriptProperties(File etdZipFile, EPerson eperson) throws Exception {
        System.setProperty("etdloader.zipfile", etdZipFile.getCanonicalPath());
        configurationService.setProperty("drum.etdloader.eperson", eperson.getEmail());
        configurationService.setProperty("drum.etdloader.collection", testCollection.getID().toString());
        configurationService.setProperty("drum.etdloader.maxFileSize", "-1");
    }

    /**
     * Returns a File, in a temporary directory, for a test-created ETD Zip
     * file with the given name.
     *
     * @param name the name of the Zip file
     * @return the File to write the Zip file to
     */
    public File newZipFile(String name) throws IOException {
        if (zipFileDirectory == null) {
            zipFileDirectory = Files.createTempDirectory("etdloader-test").toFile();
            zipFileDirectory.deleteOnExit();
        }

        File zipFile = new File(zipFileDirectory, name);
        zipFile.deleteOnExit();
        return zipFile;
    }

    /**
     * Makes the ETD Loader's email notifications fail, by giving them a
     * recipient that is not a valid email address. Email.send() parses the
     * recipients before checking "mail.server.disabled", so this fails without
     * a mail server. Call {@link #restoreEtdMailRecipient()} afterwards.
     */
    public void useInvalidEtdMailRecipient() {
        savedEtdMailRecipient = configurationService.getProperty(ETD_MAIL_RECIPIENT_PROP);
        configurationService.setProperty(ETD_MAIL_RECIPIENT_PROP, "not an email address");
    }

    /**
     * Restores the recipient changed by {@link #useInvalidEtdMailRecipient()}.
     */
    public void restoreEtdMailRecipient() {
        configurationService.setProperty(ETD_MAIL_RECIPIENT_PROP, savedEtdMailRecipient);
    }

    /**
     * Enables or disables the ETD Loader manual review workflow.
     *
     * @param enabled true to enable the review workflow, false to disable it
     */
    public void setReviewEnabled(boolean enabled) {
        configurationService.setProperty(EtdLoader.REVIEW_ENABLED_CONFIG_PROP, "" + enabled);
        configurationService.setProperty(EtdLoader.REVIEW_GROUP_CONFIG_PROP, REVIEW_GROUP_NAME);
    }

    /**
     * Creates the "Reviewer" workflow role group of the ETD collection, with
     * the given EPersons as members, so that submissions entering the workflow
     * are not immediately archived.
     *
     * @param context the DSpace context
     * @param members the EPersons to add to the group
     */
    public void addWorkflowReviewers(Context context, EPerson... members) throws Exception {
        context.turnOffAuthorisationSystem();
        try {
            // The role id is the Spring bean id from "workflow.xml", not the
            // display name of the role
            Group reviewers = workflowService.createWorkflowRoleGroup(context, testCollection, "reviewer");
            for (EPerson member : members) {
                groupService.addMember(context, reviewers, member);
            }
            groupService.update(context, reviewers);
        } finally {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Returns the group that supplementary files are restricted to during
     * review.
     *
     * @param context the DSpace context
     * @return the review group
     */
    public Group getReviewGroup(Context context) throws SQLException {
        return groupService.findByName(context, REVIEW_GROUP_NAME);
    }

    /**
     * Returns true if the given group is the review group.
     *
     * @param group the group to test
     * @return true if the given group is the review group, false otherwise.
     */
    public boolean isReviewGroup(Group group) {
        return (group != null) && REVIEW_GROUP_NAME.equals(group.getName());
    }

    /**
     * Returns all the workflow items in the repository.
     *
     * @param context the DSpace context
     * @return all the workflow items in the repository
     */
    public List<XmlWorkflowItem> getWorkflowItems(Context context) throws SQLException {
        // The ETD Loader commits its own context, so items created by earlier
        // tests are still in the database
        return xmlWorkflowItemService.findAll(context, null, null).stream()
                .filter(wfi -> !existingWorkflowItemIds.contains(wfi.getID()))
                .collect(Collectors.toList());
    }

    /**
     * Records the workflow items that already exist, so that
     * {@link #getWorkflowItems(Context)} returns only those created by the
     * test.
     *
     * @param context the DSpace context
     */
    public void captureExistingWorkflowItems(Context context) {
        try {
            existingWorkflowItemIds = xmlWorkflowItemService.findAll(context, null, null).stream()
                    .map(XmlWorkflowItem::getID)
                    .collect(Collectors.toSet());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the only workflow item in the repository, failing if there is
     * not exactly one.
     *
     * @param context the DSpace context
     * @return the only workflow item in the repository
     */
    public XmlWorkflowItem getOnlyWorkflowItem(Context context) throws SQLException {
        List<XmlWorkflowItem> workflowItems = getWorkflowItems(context);
        assertEquals("Expected a single workflow item", 1, workflowItems.size());
        return workflowItems.get(0);
    }

    /**
     * Returns the bitstream of the given Item's ORIGINAL bundle with the given
     * name, failing if there is no such bitstream.
     *
     * @param item the Item containing the bitstream
     * @param name the name of the bitstream
     * @return the bitstream with the given name
     */
    public Bitstream getBitstream(Item item, String name) throws SQLException {
        for (Bundle bundle : item.getBundles("ORIGINAL")) {
            for (Bitstream bitstream : bundle.getBitstreams()) {
                if (name.equals(bitstream.getName())) {
                    return bitstream;
                }
            }
        }

        throw new AssertionError("No '" + name + "' bitstream in the ORIGINAL bundle");
    }


    protected void addMetadataField(Context context, String metadataSchemaName, String element, String qualifier)
        throws Exception {
        MetadataSchema metadataSchema = metadataSchemaService.find(context, metadataSchemaName);
        if (metadataFieldService.findByElement(context, metadataSchemaName, element, qualifier) == null) {
            metadataFieldService.create(context, metadataSchema, element, qualifier, null);
        }
    }
}

/**
 * Replaces the logger for the given class, enabling the log output to be
 * examined.
 */
class TestLog4JLogger {
    private String loggerName;
    private Level logLevel;
    private LoggerContext logContext;
    private Configuration config;
    private StringWriter logOutput;
    private Appender appender;

    /**
     * Creates a TestLog4JLogger instance
     * @param loggerName the name of the logger (typically a class name) of
     * the logger to replace
     * @param logLevel the Level to log at (Level.INFO, Level.DEBUG, etc.)
     */
    public TestLog4JLogger(String loggerName, Level logLevel) {
        this.loggerName = loggerName;
        this.logLevel = logLevel;
    }

    /**
     * Sets up the logger. Should be called by an @Before method in the test
     * (i.e., the JUnit "setUp" method, or equivalent).
     */
    public void setUp() {
        logContext = LoggerContext.getContext(false);
        config = logContext.getConfiguration();

        logOutput = new StringWriter();

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%msg%n")
                .build();

        appender = WriterAppender.newBuilder()
                .setName("stringWriterAppender")
                .setTarget(logOutput)
                .setLayout(layout)
                .build();

        appender.start();

        logContext.getConfiguration().addAppender(appender);


        LoggerConfig loggerConfig = LoggerConfig.newBuilder()
                                        .withLevel(logLevel)
                                        .withLoggerName(loggerName)
                                        .withConfig(config).build();

        loggerConfig.addAppender(appender, null, null);
        config.addLogger(loggerName, loggerConfig);
        logContext.updateLoggers();
    }

    /**
     * Tears down the logger. Should be called by an @After method in the test
     * (i.e., the JUnit "tearDown" method, or equivalent).
     */
    public void tearDown() {
        // Clean up: remove the logger config and stop the appender
        config.removeLogger(loggerName);
        appender.stop();
        logContext.updateLoggers();

    }

    /**
     * Returns a String containing the messages sent to the log.
     * @return a String containing the messages sent to the log.
     */
    public String getLog() {
        return logOutput.toString();
    }
}