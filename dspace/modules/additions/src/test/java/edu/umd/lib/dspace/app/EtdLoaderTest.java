package edu.umd.lib.dspace.app;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import java.io.File;
import java.io.StringWriter;
import java.net.URL;
import java.sql.SQLException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.dspace.AbstractUnitTest;
import org.dspace.builder.AbstractBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.EtdUnit;
import org.dspace.content.EtdUnitTestUtils;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.EtdUnitService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.content.service.MetadataSchemaService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.GroupService;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
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
        assertThat(logOutput, containsString("Embargoed until Tue Jun 26 00:00:00 IST 3027"));
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

    private Group etdEmbargoGroup;
    private Community testCommunity;
    private Collection testCollection;
    private EtdUnit etdUnit;

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