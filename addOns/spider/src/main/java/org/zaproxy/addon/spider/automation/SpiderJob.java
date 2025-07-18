/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2021 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.spider.automation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.httpclient.URI;
import org.apache.commons.lang3.StringUtils;
import org.parosproxy.paros.CommandLine;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.extension.history.ExtensionHistory;
import org.parosproxy.paros.model.HistoryReference;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpSender;
import org.parosproxy.paros.network.HttpStatusCode;
import org.zaproxy.addon.automation.AutomationData;
import org.zaproxy.addon.automation.AutomationEnvironment;
import org.zaproxy.addon.automation.AutomationJob;
import org.zaproxy.addon.automation.AutomationProgress;
import org.zaproxy.addon.automation.ContextWrapper;
import org.zaproxy.addon.automation.jobs.JobData;
import org.zaproxy.addon.automation.jobs.JobUtils;
import org.zaproxy.addon.automation.tests.AbstractAutomationTest;
import org.zaproxy.addon.automation.tests.AutomationStatisticTest;
import org.zaproxy.addon.commonlib.Constants;
import org.zaproxy.addon.network.common.ZapUnknownHostException;
import org.zaproxy.addon.spider.ExtensionSpider2;
import org.zaproxy.addon.spider.SpiderParam;
import org.zaproxy.addon.spider.SpiderParam.HandleParametersOption;
import org.zaproxy.addon.spider.SpiderScan;
import org.zaproxy.zap.model.Target;
import org.zaproxy.zap.users.User;
import org.zaproxy.zap.utils.Stats;
import org.zaproxy.zap.utils.ThreadUtils;

public class SpiderJob extends AutomationJob {

    public static final String JOB_NAME = "spider";
    private static final String OPTIONS_METHOD_NAME = "getSpiderParam";

    private static final String URLS_ADDED_STATS_KEY = "automation.spider.urls.added";

    private static final String PARAM_CONTEXT = "context";
    private static final String PARAM_URL = "url";
    private static final String PARAM_USER = "user";
    private static final String PARAM_FAIL_IF_LESS_URLS = "failIfFoundUrlsLessThan";
    private static final String PARAM_WARN_IF_LESS_URLS = "warnIfFoundUrlsLessThan";

    private ExtensionSpider2 extSpider;

    private Data data;
    private Parameters parameters = new Parameters();

    private UrlRequester urlRequester =
            new UrlRequester(this.getName(), new HttpSender(HttpSender.SPIDER_INITIATOR));

    public SpiderJob() {
        this.data = new Data(this, parameters);
    }

    @Override
    public String getTemplateDataMin() {
        return getResourceAsString(getType() + "-min.yaml");
    }

    private static String getResourceAsString(String fileName) {
        try (InputStream in = SpiderJob.class.getResourceAsStream(fileName)) {
            return new BufferedReader(new InputStreamReader(in))
                            .lines()
                            .collect(Collectors.joining("\n"))
                    + "\n";
        } catch (Exception e) {
            CommandLine.error(
                    Constant.messages.getString("spider.automation.error.nofile", fileName));
        }
        return "";
    }

    @Override
    public String getTemplateDataMax() {
        return getResourceAsString(getType() + "-max.yaml");
    }

    private ExtensionSpider2 getExtSpider() {
        if (extSpider == null) {
            extSpider =
                    Control.getSingleton()
                            .getExtensionLoader()
                            .getExtension(ExtensionSpider2.class);
        }
        return extSpider;
    }

    @Override
    public void verifyParameters(AutomationProgress progress) {
        Map<?, ?> jobData = this.getJobData();
        if (jobData == null) {
            return;
        }
        JobUtils.applyParamsToObject(
                (LinkedHashMap<?, ?>) jobData.get("parameters"),
                this.parameters,
                this.getName(),
                null,
                progress);

        this.verifyUser(this.getParameters().getUser(), progress);

        if (this.getParameters().getWarnIfFoundUrlsLessThan() != null
                || this.getParameters().getFailIfFoundUrlsLessThan() != null) {
            progress.warn(
                    Constant.messages.getString(
                            "spider.automation.error.failIfUrlsLessThan.deprecated",
                            getName(),
                            URLS_ADDED_STATS_KEY));
        }

        if (getParameters().getRequestWaitTime() != null) {
            progress.warn(
                    Constant.messages.getString(
                            "spider.automation.error.requestWaitTime.deprecated", getName()));
        }
    }

    @Override
    public void applyParameters(AutomationProgress progress) {
        JobUtils.applyObjectToObject(
                this.parameters,
                JobUtils.getJobOptions(this, progress),
                this.getName(),
                new String[] {
                    PARAM_CONTEXT,
                    PARAM_URL,
                    PARAM_USER,
                    PARAM_FAIL_IF_LESS_URLS,
                    PARAM_WARN_IF_LESS_URLS
                },
                progress,
                this.getPlan().getEnv());
    }

    @Override
    public Map<String, String> getCustomConfigParameters() {
        Map<String, String> map = super.getCustomConfigParameters();
        map.put(PARAM_CONTEXT, "");
        map.put(PARAM_URL, "");
        return map;
    }

    @Override
    public boolean supportsMonitorTests() {
        return true;
    }

    @Override
    public void runJob(AutomationEnvironment env, AutomationProgress progress) {

        getExtSpider().setPanelSwitch(false);

        ContextWrapper context;
        if (StringUtils.isNotEmpty(parameters.getContext())) {
            context = env.getContextWrapper(parameters.getContext());
            if (context == null) {
                progress.error(
                        Constant.messages.getString(
                                "automation.error.context.unknown", parameters.getContext()));
                return;
            }
        } else {
            context = env.getDefaultContextWrapper();
        }
        URI uri = null;
        String urlStr = parameters.getUrl();
        try {
            if (StringUtils.isNotEmpty(urlStr)) {
                urlStr = env.replaceVars(urlStr);
                uri = new URI(urlStr, true);
            }
        } catch (Exception e1) {
            progress.error(Constant.messages.getString("automation.error.context.badurl", urlStr));
            return;
        }
        User user = this.getUser(this.getParameters().getUser(), progress);

        // Request all specified URLs
        for (String u : context.getUrls()) {
            urlStr = env.replaceVars(u);
            progress.info(
                    Constant.messages.getString("automation.info.requrl", this.getName(), urlStr));
            this.urlRequester.requestUrl(urlStr, user, progress);
        }

        if (env.isTimeToQuit()) {
            // Failed to access one of the URLs
            return;
        }

        Target target = new Target(context.getContext());
        target.setRecurse(true);
        List<Object> contextSpecificObjects = new ArrayList<>();
        if (uri != null) {
            contextSpecificObjects.add(uri);
        }

        int scanId = this.getExtSpider().startScan(target, user, contextSpecificObjects.toArray());

        long endTime = Long.MAX_VALUE;
        if (parameters.getMaxDuration() != null && parameters.getMaxDuration() > 0) {
            // The spider should stop, if it doesnt we will stop it (after a few seconds leeway)
            endTime =
                    System.currentTimeMillis()
                            + TimeUnit.MINUTES.toMillis(parameters.getMaxDuration())
                            + TimeUnit.SECONDS.toMillis(5);
        }

        // Wait for the spider to finish
        SpiderScan scan;
        boolean forceStop = false;
        int numUrlsFound = 0;
        int lastCount = 0;

        while (true) {
            this.sleep(500);

            scan = this.getExtSpider().getScan(scanId);
            numUrlsFound = scan.getNumberOfURIsFound();
            Stats.incCounter(URLS_ADDED_STATS_KEY, numUrlsFound - lastCount);
            lastCount = numUrlsFound;

            if (scan.isStopped()) {
                break;
            }
            if (!this.runMonitorTests(progress) || System.currentTimeMillis() > endTime) {
                forceStop = true;
                break;
            }
        }
        if (forceStop) {
            this.getExtSpider().stopScan(scanId);
            progress.info(Constant.messages.getString("automation.info.jobstopped", getType()));
        }
        numUrlsFound = scan.getNumberOfURIsFound();

        progress.info(
                Constant.messages.getString(
                        "automation.info.urlsfound", this.getName(), numUrlsFound));

        getExtSpider().setPanelSwitch(true);
    }

    /**
     * Only for use by unit tests
     *
     * @param urlRequester the UrlRequester to use
     */
    protected void setUrlRequester(UrlRequester urlRequester) {
        this.urlRequester = urlRequester;
    }

    @Override
    public boolean isExcludeParam(String param) {
        switch (param) {
            case "confirmRemoveDomainAlwaysInScope":
            case "confirmRemoveIrrelevantParameter":
            case "maxScansInUI":
            case "showAdvancedDialog":
            case "skipURLString":
                return true;
            default:
                return false;
        }
    }

    @Override
    public Parameters getParameters() {
        return parameters;
    }

    @Override
    public void showDialog() {
        new SpiderJobDialog(this).setVisible(true);
    }

    @Override
    public String getSummary() {
        String context = this.getParameters().getContext();
        if (StringUtils.isEmpty(context)) {
            context = Constant.messages.getString("automation.dialog.default");
        }
        return Constant.messages.getString(
                "spider.automation.dialog.summary",
                context,
                JobUtils.unBox(this.getParameters().getUrl(), "''"));
    }

    @Override
    public String getType() {
        return JOB_NAME;
    }

    @Override
    public Order getOrder() {
        return Order.LAST_EXPLORE;
    }

    @Override
    public Object getParamMethodObject() {
        return this.getExtSpider();
    }

    @Override
    public String getParamMethodName() {
        return OPTIONS_METHOD_NAME;
    }

    @Override
    public int addDefaultTests(AutomationProgress progress) {
        AutomationStatisticTest test =
                new AutomationStatisticTest(
                        URLS_ADDED_STATS_KEY,
                        Constant.messages.getString(
                                "spider.automation.dialog.tests.stats.defaultname", 100),
                        AutomationStatisticTest.Operator.GREATER_OR_EQUAL.getSymbol(),
                        100,
                        AbstractAutomationTest.OnFail.INFO.name(),
                        this,
                        progress);
        this.addTest(test);
        return 1;
    }

    public static class UrlRequester {

        private final HttpSender httpSender;
        private final String requester;

        public UrlRequester(String requester, HttpSender httpSender) {
            this.requester = requester;
            this.httpSender = httpSender;
        }

        public void requestUrl(String url, User user, AutomationProgress progress) {
            // Request the URL
            try {
                final HttpMessage msg = new HttpMessage(new URI(url, true));

                if (user != null) {
                    msg.setRequestingUser(user);
                }

                httpSender.sendAndReceive(msg, true);

                if (msg.getResponseHeader().getStatusCode() != HttpStatusCode.OK) {
                    progress.warn(
                            Constant.messages.getString(
                                    "spider.automation.error.url.notok",
                                    requester,
                                    url,
                                    msg.getResponseHeader().getStatusCode()));
                }

                ExtensionHistory extHistory =
                        Control.getSingleton()
                                .getExtensionLoader()
                                .getExtension(ExtensionHistory.class);
                extHistory.addHistory(msg, HistoryReference.TYPE_SPIDER);

                ThreadUtils.invokeAndWait(
                        () ->
                                // Needs to be done on the EDT
                                Model.getSingleton()
                                        .getSession()
                                        .getSiteTree()
                                        .addPath(msg.getHistoryRef()));
            } catch (ZapUnknownHostException e1) {
                if (e1.isFromOutgoingProxy()) {
                    progress.error(
                            Constant.messages.getString(
                                    "spider.automation.error.url.badhost.proxychain",
                                    requester,
                                    url,
                                    e1.getMessage()));
                } else {
                    progress.error(
                            Constant.messages.getString(
                                    "spider.automation.error.url.badhost",
                                    requester,
                                    url,
                                    e1.getMessage()));
                }
            } catch (Exception e1) {
                progress.error(
                        Constant.messages.getString(
                                "spider.automation.error.url.failed",
                                requester,
                                url,
                                e1.getMessage()));
            }
        }
    }

    @Override
    public Data getData() {
        return data;
    }

    @Getter
    public static class Data extends JobData {
        private Parameters parameters;

        public Data(AutomationJob job, Parameters parameters) {
            super(job);
            this.parameters = parameters;
        }
    }

    @Getter
    @Setter
    public static class Parameters extends AutomationData {
        private String context = "";
        private String user = "";
        private String url = "";
        private Integer maxDuration = 0;
        private Integer maxDepth = 5;
        private Integer maxChildren = 0;
        private Boolean acceptCookies = true;
        private Boolean handleODataParametersVisited = false;
        private HandleParametersOption handleParameters = HandleParametersOption.USE_ALL;
        private Integer maxParseSizeBytes = SpiderParam.DEFAULT_MAX_PARSE_SIZE_BYTES;
        private Boolean parseComments = true;
        private Boolean parseGit = false;
        private Boolean parseDsStore = false;
        private Boolean parseRobotsTxt = true;
        private Boolean parseSitemapXml = true;
        private Boolean parseSVNEntries = false;
        private Boolean postForm = true;
        private Boolean processForm = true;
        private Boolean sendRefererHeader = true;
        private Integer threadCount = Constants.getDefaultThreadCount();
        private String userAgent = "";
        private Boolean logoutAvoidance = SpiderParam.DEFAULT_LOGOUT_AVOIDANCE;
        // These 2 fields are deprecated
        private Boolean failIfFoundUrlsLessThan;
        private Boolean warnIfFoundUrlsLessThan;
        private Integer requestWaitTime;
    }
}
