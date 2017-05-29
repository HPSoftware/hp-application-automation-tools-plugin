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
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import net.sf.json.processors.PropertyNameProcessor;
import net.sf.json.util.PropertyFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
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

    private final static Logger logger = LogManager.getLogger(UftTestDiscoveryDispatcher.class);
    private final static String DUPLICATE_ERROR_CODE = "platform.duplicate_entity_error";
    private final static int POST_BULK_SIZE = 100;
    private final static int MAX_DISPATCH_TRIALS = 5;

    private UftTestDiscoveryQueue queue;

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
        MqmRestClient client = ConfigurationService.createClient(serverConfiguration);

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
                if (item.incrementFailCount() > MAX_DISPATCH_TRIALS) {
                    queue.remove();
                    logger.warn("Failed to  persist discovery of [" + item.getProjectName() + "#" + item.getBuildNumber() + "]  after " + MAX_DISPATCH_TRIALS + " trials");
                }
            }
        }
    }

    private static void dispatchDetectionResults(ResultQueue.QueueItem item, MqmRestClient client, UFTTestDetectionResult result) {
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
            boolean updated = updateTests(client, result.getDeletedTests(), result.getWorkspaceId(), result.getScmRepositoryId());
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

    private static boolean postTests(MqmRestClient client, List<AutomatedTest> tests, String workspaceId, String scmRepositoryId) {

        if (!tests.isEmpty()) {
            try {
                completeTestProperties(client, Long.parseLong(workspaceId), tests, scmRepositoryId);
            } catch (RequestErrorException e) {
                logger.error("Failed to completeTestProperties : " + e.getMessage());
                return false;
            }

            for (int i = 0; i < tests.size(); i += POST_BULK_SIZE) {
                try {
                    AutomatedTests data = AutomatedTests.createWithTests(tests.subList(i, Math.min(i + POST_BULK_SIZE, tests.size())));
                    String uftTestJson = convertToJsonString(data);

                    client.postEntities(Long.parseLong(workspaceId), OctaneConstants.Tests.COLLECTION_NAME, uftTestJson);

                } catch (RequestErrorException e) {
                    return checkIfExceptionCanBeIgnoredInPOST(e, "Failed to post tests");
                }
            }
        }
        return true;
    }

    private static boolean postScmResources(MqmRestClient client, List<ScmResourceFile> resources, String workspaceId, String scmResourceId) {

        if (!resources.isEmpty()) {
            try {
                completeScmResourceProperties(resources, scmResourceId);
            } catch (RequestErrorException e) {
                logger.error("Failed to completeTestProperties : " + e.getMessage());
                return false;
            }

            for (int i = 0; i < resources.size(); i += POST_BULK_SIZE)
                try {
                    ScmResources data = ScmResources.createWithItems(resources.subList(i, Math.min(i + POST_BULK_SIZE, resources.size())));
                    String uftTestJson = convertToJsonString(data);

                    client.postEntities(Long.parseLong(workspaceId), OctaneConstants.DataTables.COLLECTION_NAME, uftTestJson);

                } catch (RequestErrorException e) {
                    return checkIfExceptionCanBeIgnoredInPOST(e, "Failed to post scm resource files");
                }
        }
        return true;
    }

    /**
     * Entities might be posted while they already exist in Octane, such POST request will fail with general error code will be 409.
     * The same error code might be received on other validation error.
     * In this method we check whether exist other exception than duplicate
     *
     * @param e
     * @param errorPrefix
     * @return
     */
    private static boolean checkIfExceptionCanBeIgnoredInPOST(RequestErrorException e, String errorPrefix) {
        if (e.getStatusCode() == HttpStatus.CONFLICT.getCode() && e.getJsonObject() != null && e.getJsonObject().containsKey("errors")) {
            JSONObject error = findFirstErrorDifferThan(e.getJsonObject().getJSONArray("errors"), DUPLICATE_ERROR_CODE);
            String errorMessage = null;
            if (error != null) {
                errorMessage = error.getString("description");
                logger.error(errorPrefix + " : " + errorMessage);
            }
            return errorMessage == null;
        }

        logger.error(errorPrefix + "  :  " + e.getMessage());
        return false;
    }

    /**
     * Search for error code that differ from supplied errorCode.
     */
    private static JSONObject findFirstErrorDifferThan(JSONArray errors, String excludeErrorCode) {
        for (int errorIndex = 0; errorIndex < errors.size(); errorIndex++) {
            JSONObject error = errors.getJSONObject(errorIndex);
            String errorCode = error.getString("error_code");
            if (errorCode.equals(excludeErrorCode)) {
                continue;
            } else {
                return error;
            }
        }
        return null;
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
                        result = OctaneConstants.Tests.SCM_REPOSITORY_FIELD;
                        break;
                    case "testingToolType":
                        result = OctaneConstants.Tests.TESTING_TOOL_TYPE_FIELD;
                        break;
                    case "testTypes":
                        result = OctaneConstants.Tests.TEST_TYPE_FIELD;
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
                        result = OctaneConstants.DataTables.RELATIVE_PATH_FIELD;
                        break;
                    case "scmRepository":
                        result = OctaneConstants.DataTables.SCM_REPOSITORY_FIELD;
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

    private static boolean updateTests(MqmRestClient client, Collection<AutomatedTest> tests, String workspaceId, String scmRepositoryId) {
        long workspaceIdAsLong = Long.parseLong(workspaceId);

        try {
            List<AutomatedTest> testsForCreate = new ArrayList<>();
            List<AutomatedTest> testsForUpdate = new ArrayList<>();
            for (AutomatedTest test : tests) {
                Entity foundTest = fetchTestFromOctane(client, workspaceIdAsLong, test);
                if (foundTest != null) {
                    AutomatedTest testForUpdate = new AutomatedTest();
                    if (test.getDescription() != null) {
                        testForUpdate.setDescription(test.getDescription());
                    }
                    testForUpdate.setExecutable(test.getExecutable());
                    testForUpdate.setId(foundTest.getId());
                    testsForUpdate.add(testForUpdate);
                } else if (foundTest == null && test.getExecutable()) {
                    //test is executable but does not exist in Octane, create it from scratch
                    logger.error(String.format("Test %s\\%s should be updated but wasn't found on Octane, creating test from scratch ", test.getPackage(), test.getName()));
                    testsForCreate.add(test);
                }
            }

            if (!testsForUpdate.isEmpty()) {
                for (int i = 0; i < tests.size(); i += POST_BULK_SIZE) {
                    AutomatedTests data = AutomatedTests.createWithTests(testsForUpdate.subList(i, Math.min(i + POST_BULK_SIZE, tests.size())));
                    String uftTestJson = convertToJsonString(data);
                    client.updateEntities(Long.parseLong(workspaceId), OctaneConstants.Tests.COLLECTION_NAME, uftTestJson);
                }
            }

            boolean successful = true;
            if (!testsForCreate.isEmpty()) {
                successful = postTests(client, testsForCreate, workspaceId, scmRepositoryId);
            }
            return successful;

        } catch (Exception e) {
            logger.error("Failed to update tests : " + e.getMessage());
            return false;
        }
    }

    private static Entity fetchTestFromOctane(MqmRestClient client, long workspaceIdAsLong, AutomatedTest test) {
        List<String> conditions = new ArrayList<>();
        conditions.add(QueryHelper.condition(OctaneConstants.Tests.NAME_FIELD, test.getName()));
        conditions.add(QueryHelper.condition(OctaneConstants.Tests.PACKAGE_FIELD, test.getPackage()));
        List<Entity> tests = client.getEntities(workspaceIdAsLong, OctaneConstants.Tests.COLLECTION_NAME, conditions, Arrays.asList(OctaneConstants.Tests.ID_FIELD));
        return tests.size() == 1 ? tests.get(0) : null;
    }

    private static boolean deleteScmResources(MqmRestClient client, List<ScmResourceFile> deletedResourceFiles, String workspaceId, String scmRepositoryId) {

        long workspaceIdAsLong = Long.parseLong(workspaceId);
        Set<Long> deletedIds = new HashSet<>();
        try {
            for (ScmResourceFile scmResource : deletedResourceFiles) {
                List<String> conditions = new ArrayList<>();
                conditions.add(QueryHelper.condition(OctaneConstants.DataTables.RELATIVE_PATH_FIELD, scmResource.getRelativePath()));
                conditions.add(QueryHelper.conditionRef(OctaneConstants.DataTables.SCM_REPOSITORY_FIELD, Long.valueOf(scmRepositoryId)));

                List<Entity> foundResources = client.getEntities(workspaceIdAsLong, OctaneConstants.DataTables.COLLECTION_NAME, conditions, Arrays.asList("id, name"));
                if (foundResources.size() == 1) {
                    Entity found = foundResources.get(0);
                    deletedIds.add(found.getId());
                }
            }

            client.deleteEntities(Long.parseLong(workspaceId), OctaneConstants.DataTables.COLLECTION_NAME, deletedIds);
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

        BaseRefEntity scmRepository = StringUtils.isEmpty(scmRepositoryId) ? null : BaseRefEntity.create(OctaneConstants.Tests.SCM_REPOSITORY_FIELD, Long.valueOf(scmRepositoryId));
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
        BaseRefEntity scmRepository = StringUtils.isEmpty(scmResourceId) ? null : BaseRefEntity.create(OctaneConstants.DataTables.SCM_REPOSITORY_FIELD, Long.valueOf(scmResourceId));
        for (ScmResourceFile resource : resources) {
            resource.setScmRepository(scmRepository);
        }
    }

    private static ListNodeEntity getUftTestingTool(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.testing_tool_type", null, workspaceId, 0, POST_BULK_SIZE);
        String uftTestingToolLogicalName = "list_node.testing_tool_type.uft";

        for (ListItem item : testingTools.getItems()) {
            if (uftTestingToolLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    private static ListNodeEntity getUftFramework(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.je.framework", null, workspaceId, 0, POST_BULK_SIZE);
        String uftTestingToolLogicalName = "list_node.je.framework.uft";

        for (ListItem item : testingTools.getItems()) {
            if (uftTestingToolLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    private static ListNodeEntity getGuiTestType(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.test_type", null, workspaceId, 0, POST_BULK_SIZE);
        String guiLogicalName = "list_node.test_type.gui";

        for (ListItem item : testingTools.getItems()) {
            if (guiLogicalName.equals(item.getLogicalName())) {
                return ListNodeEntity.create(item.getId());
            }
        }
        return null;
    }

    private static ListNodeEntity getApiTestType(MqmRestClient client, long workspaceId) {
        PagedList<ListItem> testingTools = client.queryListItems("list_node.test_type", null, workspaceId, 0, POST_BULK_SIZE);
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
