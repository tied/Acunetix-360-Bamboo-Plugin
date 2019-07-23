package com.acunetix.tasks;

import com.acunetix.ConfigManager;
import com.acunetix.model.ScanRequest;
import com.acunetix.model.ScanRequestResult;
import com.acunetix.model.VCSCommit;
import com.acunetix.utility.AppCommon;
import com.atlassian.bamboo.author.AuthorContext;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.configuration.AdministrationConfiguration;
import com.atlassian.bamboo.configuration.ConfigurationMap;
import com.atlassian.bamboo.task.*;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.spring.container.ContainerManager;
import org.apache.http.HttpResponse;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Acunetix360ScanTask implements TaskType {
    private BuildLogger buildLogger;
    private ConfigManager configManager = new ConfigManager();

    public AdministrationConfiguration getAdministrationConfiguration() {
        return (AdministrationConfiguration) ContainerManager.getComponent("administrationConfiguration");
    }

    public Map<String, String> getCustomVariables(final TaskContext taskContext) {
        final CustomVariableContext customVariableContext = (CustomVariableContext) ContainerManager.getComponent("customVariableContext");

        return customVariableContext.getVariables(taskContext.getCommonContext());
    }

    @Override
    public TaskResult execute(final TaskContext taskContext) throws TaskException {
        buildLogger = taskContext.getBuildLogger();
        final TaskResult taskResult = ScanRequestHandler(taskContext);
        buildLogger = null;

        return taskResult;
    }


    private TaskResult ScanRequestHandler(final TaskContext taskContext) throws TaskException {
        final TaskResultBuilder builder = TaskResultBuilder.newBuilder(taskContext).failed(); //Initially set to Failed.
        final Map<String, String> customVariables = getCustomVariables(taskContext);
        final ConfigurationMap configurationMap = taskContext.getConfigurationMap();

        final String serverURL = configManager.getApiUrl();
        final String apiToken = configManager.getApiToken();
        final String scanType = configurationMap.get(ScanRequest.SCAN_TYPE_Literal);
        final String websiteId = configurationMap.get(ScanRequest.WEBSITE_ID_Literal);
        final String profileId = configurationMap.get(ScanRequest.PROFILE_ID_Literal);

        logScanParams(scanType, websiteId, profileId);
        VCSCommit vcsCommit = getVCSCommit(taskContext, customVariables);
        //logCustomParams(customVariables);
        logScanInfoBeginning();
        logInfo("Requesting scan...");

        try {
            ScanRequest scanRequest = new ScanRequest(
                    serverURL, apiToken, scanType, websiteId, profileId, vcsCommit);

            HttpResponse scanRequestResponse = scanRequest.scanRequest();
            logInfo("Response status code: " + scanRequestResponse.getStatusLine().getStatusCode());

            ScanRequestResult scanRequestResult = new ScanRequestResult(scanRequestResponse, serverURL, apiToken);
            // HTTP status code 201 refers to created. This means our request added to queue. Otherwise it is failed.
            if (scanRequestResult.getHttpStatusCode() == 201 && !scanRequestResult.isError()) {
                ScanRequestSuccessHandler(taskContext, builder, customVariables, scanRequestResult);
            } else {
                ScanRequestFailureHandler(builder, scanRequestResult);
            }
        } catch (Exception ex) {
            throw new TaskException(ex.getMessage());
        }

        logScanInfoEnd();
        final TaskResult result = builder.build();

        return result;
    }

    private void ScanRequestSuccessHandler(final TaskContext taskContext,
                                           final TaskResultBuilder builder,
                                           final Map<String, String> customVariables,
                                           final ScanRequestResult scanRequestResult) throws IOException {
        builder.success();
        final BuildContext buildContext = taskContext.getBuildContext();
        final String planKey = customVariables.get("planKey");
        final String buildKey = customVariables.get("buildKey");
        final String buildNumber = String.valueOf(buildContext.getBuildNumber());

        configManager.setScanTaskID(planKey, buildNumber, scanRequestResult.getScanTaskID());
        configManager.setScanTaskID(buildKey, buildNumber, scanRequestResult.getScanTaskID());

        logInfo("Scan requested successfully.");
    }

    private void ScanRequestFailureHandler(final TaskResultBuilder builder, final ScanRequestResult scanRequestResult) throws Exception {
        builder.failed();
        logError("Scan request failed. Error Message: " + scanRequestResult.getErrorMessage());
    }

    private VCSCommit getVCSCommit(final TaskContext taskContext, final Map<String, String> customVariables) {
        final BuildContext buildContext = taskContext.getBuildContext();
        final AdministrationConfiguration administrationConfiguration = getAdministrationConfiguration();
        final BuildChanges buildChanges = buildContext.getBuildChanges();
        final List<CommitContext> changes = buildChanges.getChanges();
        boolean buildHasChange = !changes.isEmpty();

        String buildId = String.valueOf(buildContext.getBuildNumber());
        String buildConfigurationName = buildContext.getPlanName();
        String buildURL = administrationConfiguration.getBaseUrl() + "/browse/" + buildContext.getPlanResultKey().toString();
        String bambooVersion = customVariables.get("version");
        if (AppCommon.IsNullOrEmpty(bambooVersion)) {
            bambooVersion = "Not found.";
        }
        //ISO 8601-compliant date and time format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        String dateString = dateFormat.format(new Date());

        VCSCommit vcsCommit;
        if (buildHasChange) {
            final CommitContext commitContext = changes.get(0);
            final AuthorContext authorContext = commitContext.getAuthorContext();

            Date date = commitContext.getDate();
            dateString = dateFormat.format(date);
            String changeSetId = commitContext.getChangeSetId();

            if (AppCommon.IsNullOrEmpty(changeSetId)) {
                changeSetId = "Not Found.";
            }

            String userName = authorContext.getName();
            if (AppCommon.isValidEmailAddress(authorContext.getEmail())) {
                userName = authorContext.getEmail();
            }
            if (AppCommon.IsNullOrEmpty(userName)) {
                userName = "Not Found.";
            }
            String vcsName = customVariables.get("planRepository.type");
            if (AppCommon.IsNullOrEmpty(vcsName)) {
                userName = "Not Found.";
            }

            vcsCommit = new VCSCommit(bambooVersion, buildId, buildConfigurationName, buildURL, buildHasChange, vcsName, userName, changeSetId, dateString);
        } else {
            vcsCommit = new VCSCommit(bambooVersion, buildId, buildConfigurationName, buildURL, buildHasChange, "", "", "", dateString);
        }

        logBuildParams(vcsCommit);

        return vcsCommit;
    }

    private void logScanParams(final String scanType, final String websiteId, final String profileId) {
        buildLogger.addBuildLogEntry("***************************************************************************" + "\n");
        buildLogger.addBuildLogEntry("********************  Acunetix 360 Scan Parameters  *******************" + "\n");
        buildLogger.addBuildLogEntry("***************************************************************************" + "\n");
        logIfNotNull("Scan Type" + ": " + scanType);
        logIfNotNull("Website Id" + ": " + websiteId);
        logIfNotNull("Website Profile Id" + ": " + profileId);
        buildLogger.addBuildLogEntry("***************************************************************************" + "\n");
    }

    private void logBuildParams(final VCSCommit commit) {
        buildLogger.addBuildLogEntry("********************  Acunetix 360 Build Parameters  ******************" + "\n");
        buildLogger.addBuildLogEntry("***************************************************************************" + "\n");
        logIfNotNull("Build Id: " + commit.getBuildId());
        logIfNotNull("Build configuration name: " + commit.getBuildConfigurationName());
        logIfNotNull("Build url: " + commit.getBuildURL());
        logIfNotNull("Build has change: " + commit.BuildHasChange());
        logIfNotNull("VCS Name: " + commit.getVersionControlName());
        logIfNotNull("Committer: " + commit.getCommitter());
        logIfNotNull("VCS Version: " + commit.getVcsVersion());
        logIfNotNull("Time stamp: " + commit.getCiTimestamp());
        logIfNotNull("Build server version: " + commit.getCiBuildServerVersion());
        logIfNotNull("PluginVersion: " + commit.getCiNcPluginVersion());
        logIfNotNull("***************************************************************************" + "\n");
    }

    private void logScanInfoBeginning() {
        buildLogger.addBuildLogEntry("***********************  Acunetix 360 Scan Info  **********************" + "\n");
        buildLogger.addBuildLogEntry("***************************************************************************" + "\n");
    }

    private void logScanInfoEnd() {
        buildLogger.addBuildLogEntry("***************************************************************************" + "\n");
    }

    private void logCustomParams(final Map<String, String> customVariables) {
        buildLogger.addBuildLogEntry("**************************************************************************" + "\n");
        buildLogger.addBuildLogEntry("*******************       Bamboo Custom Variables       ******************" + "\n");
        buildLogger.addBuildLogEntry("**************************************************************************" + "\n");
        final Iterator<String> iterator = customVariables.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            final String value = customVariables.get(key);
            buildLogger.addBuildLogEntry(key + ": " + value + "\n");
        }
        buildLogger.addBuildLogEntry("**************************************************************************" + "\n");
    }

    private void logIfNotNull(final String message) {
        if (!AppCommon.IsNullOrEmpty(message)) {
            buildLogger.addBuildLogEntry(message);
        }
    }

    private void logInfo(final String message) {
        buildLogger.addBuildLogEntry("> Info: " + message);
    }

    private void logError(final String message) {
        buildLogger.addErrorLogEntry("> Error: " + message);
    }

}