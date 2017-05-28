/*
 *     Copyright 2017 Hewlett-Packard Development Company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.hp.application.automation.tools.octane.executor;

import com.google.inject.Inject;
import com.hp.application.automation.tools.common.HttpStatus;
import com.hp.application.automation.tools.octane.ResultQueue;
import com.hp.application.automation.tools.octane.actions.UftTestType;
import com.hp.application.automation.tools.octane.actions.dto.*;
import com.hp.application.automation.tools.octane.configuration.ConfigurationService;
import com.hp.application.automation.tools.octane.configuration.ServerConfiguration;
import com.hp.application.automation.tools.octane.tests.AbstractSafeLoggingAsyncPeriodWork;
import com.hp.mqm.client.MqmRestClient;
import com.hp.mqm.client.QueryHelper;
import com.hp.mqm.client.exception.RequestErrorException;
import com.hp.mqm.client.model.Entity;
import com.hp.mqm.client.model.ListItem;
import com.hp.mqm.client.model.PagedList;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import net.sf.json.processors.PropertyNameProcessor;
import net.sf.json.util.PropertyFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;


/**
 * This class is responsible to send discovered uft tests to Octane.
 * Class uses file-based queue so if octane or jenkins will be down before sending,
 * after connection is up - this dispatcher will send tests to Octane.
 * <p>
 * Actually list of discovered tests are persisted in job run directory. Queue contains only reference to that job run.
 */
@Extension
public class UftTestDiscoveryDispatcher extends AbstractSafeLoggingAsyncPeriodWork {

    private static Logger logger = LogManager.getLogger(UftTestDiscoveryDispatcher.class);
    private final static String TESTS_COLLECTION_NAME = "automated_tests";
    private final static String SCM_RESOURCE_FILES_COLLECTION_NAME = "scm_resource_files";

    private final static int BULK_SIZE = 100;

    private UftTestDiscoveryQueue queue;
    private ConfigurationService configurationService;

    public UftTestDiscoveryDispatcher() {
        super("Uft Test Discovery Dispatcher");
    }


    @Override
    protected void doExecute(TaskListener listener) throws IOException, InterruptedException {
        if (queue.peekFirst() == null) {
            return;
        }

        logger.warn("Queue size  " + queue.size());
        ServerConfiguration serverConfiguration = ConfigurationService.getServerConfiguration();
        MqmRestClient client = configurationService.createClient(serverConfiguration);

        if (client == null) {
            logger.warn("There are pending discovered UFT tests, but MQM server configuration is not valid, results can't be submitted");
            return;
        }

        ResultQueue.QueueItem item = null;
        try {
            while ((item = queue.peekFirst()) != null) {

                Job project = (Job) Jenkins.getInstance().getItemByFullName(item.getProjectName());
                if (project == null) {
                    logger.warn("Project [" + item.getProjectName() + "] no longer exists, pending discovered tests can't be submitted");
                    queue.remove();
                    continue;
                }

                Run build = project.getBuildByNumber(item.getBuildNumber());
                if (build == null) {
                    logger.warn("Build [" + item.getProjectName() + "#" + item.getBuildNumber() + "] no longer exists, pending discovered tests can't be submitted");
                    queue.remove();
                    continue;
                }

                UFTTestDetectionResult result = UFTTestDetectionService.readDetectionResults(build);
                if (result == null) {
                    logger.warn("Build [" + item.getProjectName() + "#" + item.getBuildNumber() + "] no longer contains valid detection result file");
                    queue.remove();
                    continue;
                }

                logger.warn("Persistence [" + item.getProjectName() + "#" + item.getBuildNumber() + "]");
                dispatchDetectionResults(item, client, result);
                queue.remove();
            }
        } catch (Exception e) {
            if (item != null) {
                item.incrementFailCount();
                int maxTrial = 5;
                if (item.incrementFailCount() > maxTrial) {
                    queue.remove();
                    logger.warn("Failed to  persist discovery of [" + item.getProjectName() + "#" + item.getBuildNumber() + "]  after " + maxTrial + " trials");
                }
            }
        }
    }

    private void dispatchDetectionResults(ResultQueue.QueueItem item, MqmRestClient client, UFTTestDetectionResult result) throws UnsupportedEncodingException {
        //post new tests
        if (!result.getNewTests().isEmpty()) {
            boolean posted = postTests(client, result.getNewTests(), result.getWorkspaceId(), result.getScmRepositoryId());
            logger.warn("Persistence [" + item.getProjectName() + "#" + item.getBuildNumber() + "] : " + result.getNewTests().size() + "  new tests posted successfully = " + posted);
        }

        //post test updated
        if (!result.getUpdatedTests().isEmpty()) {
            boolean updated = updateTests(client, result.getUpdatedTests(), result.getWorkspaceId(), result.getScmRepositoryId());
            logger.warn("Persistence [" + item.getProjectName() + "#" + item.getBuildNumber() + "] : " + result.getUpdatedTests().size() + "  updated tests posted successfully = " + updated);
        }

        //post test deleted
        if (!result.getDeletedTests().isEmpty()) {
            boolean updated = setTestsNotExecutable(client, result.getDeletedTests(), result.getWorkspaceId());
            logger.warn("Persistence [" + item.getProjectName() + "#" + item.getBuildNumber() + "] : " + result.getDeletedTests().size() + "  deleted tests set as not executable successfully = " + updated);
        }

        //post scm resources
        if (!result.getNewScmResourceFiles().isEmpty()) {
            boolean posted = postScmResources(client, result.getNewScmResourceFiles(), result.getWorkspaceId(), result.getScmRepositoryId());
            logger.warn("Persistence [" + item.getProjectName() + "#" + item.getBuildNumber() + "] : " + result.getNewScmResourceFiles().size() + "  new scmResources posted successfully = " + posted);
        }

        //delete scm resources
        if (!result.getDeletedScmResourceFiles().isEmpty()) {
            boolean posted = deleteScmResources(client, result.getDeletedScmResourceFiles(), result.getWorkspaceId(), result.getScmRepositoryId());
            logger.warn("Persistence [" + item.getProjectName() + "#" + item.getBuildNumber() + "] : " + result.getDeletedScmResourceFiles().size() + "  scmResources deleted successfully = " + posted);
        }

    }

    private static boolean postTests(MqmRestClient client, List<AutomatedTest> tests, String workspaceId, String scmRepositoryId) throws UnsupportedEncodingException {

        if (!tests.isEmpty()) {
            try {
                completeTestProperties(client, Long.parseLong(workspaceId), tests, scmRepositoryId);
            } catch (RequestErrorException e) {
                logger.error("Failed to completeTestProperties : " + e.getMessage());
                return false;
            }

            for (int i = 0; i < tests.size(); i += BULK_SIZE)
                try {
                    AutomatedTests data = AutomatedTests.createWithTests(tests.subList(i, Math.min(i + BULK_SIZE, tests.size())));
                    String uftTestJson = convertToJsonString(data);

                    client.postEntities(Long.parseLong(workspaceId), TESTS_COLLECTION_NAME, uftTestJson);

                } catch (RequestErrorException e) {
                    if (e.getStatusCode() != HttpStatus.CONFLICT.getCode()) {
                        logger.error("Failed to postTests to Octane : " + e.getMessage());
                        return false;
                    }

                    //else :  the test with the same hash code , so do nothing
                }
        }
        return true;
    }

    private static boolean postScmResources(MqmRestClient client, List<ScmResourceFile> resources, String workspaceId, String scmResourceId) throws UnsupportedEncodingException {

        if (!resources.isEmpty()) {
            try {
                completeScmResourceProperties(resources, scmResourceId);
            } catch (RequestErrorException e) {
                logger.error("Failed to completeTestProperties : " + e.getMessage());
                return false;
            }

            for (int i = 0; i < resources.size(); i += BULK_SIZE)
                try {
                    ScmResources data = ScmResources.createWithItems(resources.subList(i, Math.min(i + BULK_SIZE, resources.size())));
                    String uftTestJson = convertToJsonString(data);

                    client.postEntities(Long.parseLong(workspaceId), SCM_RESOURCE_FILES_COLLECTION_NAME, uftTestJson);

                } catch (RequestErrorException e) {
                    if (e.getStatusCode() != HttpStatus.CONFLICT.getCode()) {
                        logger.error("Failed to post scm resource files to Octane : " + e.getMessage());
                        return false;
                    }

                    //else :  the file with the same hash code , so do nothing
                }
        }
        return true;
    }

    private static String convertToJsonString(Object data) {
        JsonConfig config = getJsonConfig();
        return JSONObject.fromObject(data, config).toString();
    }

    private static JsonConfig getJsonConfig() {
        JsonConfig config = new JsonConfig();
        //override field names
        config.registerJsonPropertyNameProcessor(AutomatedTest.class, new PropertyNameProcessor() {

            @Override
            public String processPropertyName(Class className, String fieldName) {
                String result = fieldName;
                switch (fieldName) {
                    case "scmRepository":
                        result = "scm_repository";
                        break;
                    case "testingToolType":
                        result = "testing_tool_type";
                        break;
                    case "testTypes":
                        result = "test_type";
                        break;
                    default:
                        break;
                }
                return result;
            }
        });

        config.registerJsonPropertyNameProcessor(ScmResourceFile.class, new PropertyNameProcessor() {

            @Override
            public String processPropertyName(Class className, String fieldName) {
                String result = fieldName;
                switch (fieldName) {
                    case "relativePath":
                        result = "relative_path";
                        break;
                    case "scmRepository":
                        result = "scm_repository";
                        break;
                    default:
                        break;
                }
                return result;
            }
        });

        //filter empty fields
        PropertyFilter pf = new PropertyFilter() {
            public boolean apply(Object source, String name, Object value) {
                if (value != null) {
                    return false;
                }
                return true;
            }
        };
        config.setJsonPropertyFilter(pf);

        //skip fields
        config.registerPropertyExclusion(AutomatedTest.class, "uftTestType");
        return config;
    }

    /*private static void deleteTests(MqmRestClient client, Collection<AutomatedTest> removedTests, String workspaceId) throws UnsupportedEncodingException {
        List<Long> idsToDelete = new ArrayList<>();
        long workspaceIdAsLong = Long.parseLong(workspaceId);
        for (AutomatedTest test : removedTests) {
            Map<String, String> queryFields = new HashMap<>();
            queryFields.put("name", test.getName());
            queryFields.put("package", test.getPackage());
            PagedList<Test> foundTests = client.getTests(workspaceIdAsLong, queryFields, Arrays.asList("id"));
            if (foundTests.getItems().size() == 1) {
                idsToDelete.add(foundTests.getItems().get(0).getId());
            }
        }

        int BULK_SIZE = 100;
        for (int i = 0; i < idsToDelete.size(); i += BULK_SIZE) {
            client.deleteTests(workspaceIdAsLong, idsToDelete.subList(i, Math.min(i + BULK_SIZE, idsToDelete.size())));
        }
    }*/

    private static boolean setTestsNotExecutable(MqmRestClient client, Collection<AutomatedTest> deletedTest, String workspaceId) throws UnsupportedEncodingException {
        long workspaceIdAsLong = Long.parseLong(workspaceId);

        try {
            for (AutomatedTest test : deletedTest) {
                List<String> conditions = new ArrayList<>();
                conditions.add(QueryHelper.condition("name", test.getName()));
                conditions.add(QueryHelper.condition("package", test.getPackage()));
                PagedList<Entity> foundTests = client.getEntities(workspaceIdAsLong, TESTS_COLLECTION_NAME, conditions, Arrays.asList("id"));
                if (foundTests.getItems().size() == 1) {
                    Entity foundTest = foundTests.getItems().get(0);
                    AutomatedTest testForUpdate = new AutomatedTest();
                    testForUpdate.setExecutable(false);
                    testForUpdate.setId(foundTest.getId());
                    String json = convertToJsonString(testForUpdate);
                    client.updateEntity(Long.parseLong(workspaceId), TESTS_COLLECTION_NAME, foundTests.getItems().get(0).getId(), json);
                }
            }
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private static boolean updateTests(MqmRestClient client, Collection<AutomatedTest> updateTests, String workspaceId, String scmRepositoryId) throws UnsupportedEncodingException {
        long workspaceIdAsLong = Long.parseLong(workspaceId);

        try {
            List<AutomatedTest> createAsNewTests = new ArrayList<>();
            for (AutomatedTest test : updateTests) {
                if (StringUtils.isEmpty(test.getDescription())) {
                    continue;
                }

                List<String> conditions = new ArrayList<>();
                conditions.add(QueryHelper.condition("name", test.getName()));
                conditions.add(QueryHelper.condition("package", test.getPackage()));
                PagedList<Entity> foundTests = client.getEntities(workspaceIdAsLong, TESTS_COLLECTION_NAME, conditions, Arrays.asList("id"));
                if (foundTests.getItems().size() == 1) {
                    Entity foundTest = foundTests.getItems().get(0);
                    AutomatedTest testForUpdate = new AutomatedTest();
                    testForUpdate.setDescription(test.getDescription());
                    testForUpdate.setExecutable(test.getExecutable());
                    testForUpdate.setId(foundTest.getId());
                    String json = convertToJsonString(testForUpdate);
                    client.updateEntity(Long.parseLong(workspaceId), TESTS_COLLECTION_NAME, foundTests.getItems().get(0).getId(), json);
                } else if (foundTests.getItems().size() == 0) {
                    //test not exist in Octane, create it from scratch
                    logger.error(String.format("Test %s\\%s should be updated but wasn't found on Octane, creating test from scratch ", test.getPackage(), test.getName()));
                    createAsNewTests.add(test);

                }
            }


            if (!createAsNewTests.isEmpty()) {
                return postTests(client, createAsNewTests, workspaceId, scmRepositoryId);
            } else {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean deleteScmResources(MqmRestClient client, List<ScmResourceFile> deletedResourceFiles, String workspaceId, String scmRepositoryId) {

        long workspaceIdAsLong = Long.parseLong(workspaceId);
        Set<Long> deletedIds = new HashSet<>();
        try {
            for (ScmResourceFile scmResource : deletedResourceFiles) {
                List<String> conditions = new ArrayList<>();
                conditions.add(QueryHelper.condition("relative_path", scmResource.getRelativePath()));
                conditions.add(QueryHelper.conditionRef("scm_repository", Long.valueOf(scmRepositoryId)));

                PagedList<Entity> foundResources = client.getEntities(workspaceIdAsLong, SCM_RESOURCE_FILES_COLLECTION_NAME, conditions, Arrays.asList("id, name"));
                if (foundResources.getItems().size() == 1) {
                    Entity found = foundResources.getItems().get(0);
                    deletedIds.add(found.getId());
                }
            }

            client.deleteEntities(Long.parseLong(workspaceId), SCM_RESOURCE_FILES_COLLECTION_NAME, deletedIds);
            return true;

        } catch (Exception e) {
            return false;
        }

    }

    private static void completeTestProperties(MqmRestClient client, long workspaceId, Collection<AutomatedTest> tests, String scmRepositoryId) {
        ListNodeEntity uftTestingTool = getUftTestingTool(client, workspaceId);
        ListNodeEntity uftFramework = getUftFramework(client, workspaceId);
        ListNodeEntity guiTestType = hasTestsByType(tests, UftTestType.GUI) ? getGuiTestType(client, workspaceId) : null;
        ListNodeEntity apiTestType = hasTestsByType(tests, UftTestType.API) ? getApiTestType(client, workspaceId) : null;

        BaseRefEntity scmRepository = StringUtils.isEmpty(scmRepositoryId) ? null : BaseRefEntity.create("scm_repository", Long.valueOf(scmRepositoryId));
        for (AutomatedTest test : tests) {
            test.setTestingToolType(uftTestingTool);
            test.setFramework(uftFramework);
            test.setScmRepository(scmRepository);

            ListNodeEntity testType = guiTestType;
            if (UftTestType.API.equals(test.getUftTestType())) {
                testType = apiTestType;
            }
            test.setTestTypes(ListNodeEntityCollection.create(testType));
        }
    }

    private static void completeScmResourceProperties(List<ScmResourceFile> resources, String scmResourceId) {
        BaseRefEntity scmRepository = StringUtils.isEmpty(scmResourceId) ? null : BaseRefEntity.create("scm_repository", Long.valueOf(scmResourceId));
        for (ScmResourceFile resource : resources) {
            resource.setScmRepository(scmRepository);
        }
    }

    private static ListNodeEntity getUftTestingTool(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.testing_tool_type", null, workspaceId, 0, 100);
        String uftTestingToolLogicalName = "list_node.testing_tool_type.uft";

        for (ListItem item : testingTools.getItems()) {
            if (uftTestingToolLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    private static ListNodeEntity getUftFramework(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.je.framework", null, workspaceId, 0, 100);
        String uftTestingToolLogicalName = "list_node.je.framework.uft";

        for (ListItem item : testingTools.getItems()) {
            if (uftTestingToolLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    private static ListNodeEntity getGuiTestType(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.test_type", null, workspaceId, 0, 100);
        String guiLogicalName = "list_node.test_type.gui";

        for (ListItem item : testingTools.getItems()) {
            if (guiLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    private static ListNodeEntity getApiTestType(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.test_type", null, workspaceId, 0, 100);
        String guiLogicalName = "list_node.test_type.api";

        for (ListItem item : testingTools.getItems()) {
            if (guiLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    @Override
    public long getRecurrencePeriod() {
        String value = System.getProperty("UftTestDiscoveryDispatcher.Period"); // let's us config the recurrence period. default is 60 seconds.
        if (!StringUtils.isEmpty(value)) {
            return Long.valueOf(value);
        }
        return TimeUnit2.SECONDS.toMillis(30);
    }

    @Inject
    public void setTestResultQueue(UftTestDiscoveryQueue queue) {
        this.queue = queue;
    }

    @Inject
    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    private static boolean hasTestsByType(Collection<AutomatedTest> tests, UftTestType uftTestType) {
        for (AutomatedTest test : tests) {
            if (uftTestType.equals(test.getUftTestType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queue that current run contains discovered tests
     *
     * @param projectName
     * @param buildNumber
     */
    public void enqueueResult(String projectName, int buildNumber) {
        queue.add(projectName, buildNumber);
    }
}