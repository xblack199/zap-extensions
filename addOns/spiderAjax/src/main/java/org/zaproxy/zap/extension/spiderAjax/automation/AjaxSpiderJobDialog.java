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
package org.zaproxy.zap.extension.spiderAjax.automation;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.JTextField;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.view.View;
import org.zaproxy.addon.automation.jobs.JobUtils;
import org.zaproxy.addon.commonlib.Constants;
import org.zaproxy.zap.extension.selenium.ExtensionSelenium;
import org.zaproxy.zap.extension.selenium.ProvidedBrowserUI;
import org.zaproxy.zap.extension.spiderAjax.AjaxSpiderMultipleOptionsPanel;
import org.zaproxy.zap.extension.spiderAjax.AjaxSpiderParam;
import org.zaproxy.zap.extension.spiderAjax.AjaxSpiderParamElem;
import org.zaproxy.zap.extension.spiderAjax.OptionsAjaxSpiderTableModel;
import org.zaproxy.zap.extension.spiderAjax.automation.AjaxSpiderJob.Parameters;
import org.zaproxy.zap.extension.spiderAjax.internal.ExcludedElementsPanel;
import org.zaproxy.zap.extension.spiderAjax.internal.ScopeCheckComponent;
import org.zaproxy.zap.utils.DisplayUtils;
import org.zaproxy.zap.view.StandardFieldsDialog;

@SuppressWarnings("serial")
public class AjaxSpiderJobDialog extends StandardFieldsDialog {

    private static final long serialVersionUID = 1L;

    private static final String[] TAB_LABELS = {
        "spiderajax.automation.dialog.tab.params",
        "spiderajax.automation.dialog.tab.elems",
        "spiderajax.automation.dialog.ajaxspider.tab.adv"
    };

    private static final String TITLE = "spiderajax.automation.dialog.ajaxspider.title";
    private static final String NAME_PARAM = "spiderajax.automation.dialog.ajaxspider.name";
    private static final String CONTEXT_PARAM = "spiderajax.automation.dialog.ajaxspider.context";
    private static final String USER_PARAM = "automation.dialog.all.user";
    private static final String URL_PARAM = "spiderajax.automation.dialog.ajaxspider.url";
    private static final String MAX_DURATION_PARAM =
            "spiderajax.automation.dialog.ajaxspider.maxduration";
    private static final String MAX_CRAWL_DEPTH_PARAM =
            "spiderajax.automation.dialog.ajaxspider.maxcrawldepth";
    private static final String NUM_BROWSERS_PARAM =
            "spiderajax.automation.dialog.ajaxspider.numbrowsers";
    private static final String IN_SCOPE_ONLY =
            "spiderajax.automation.dialog.ajaxspider.inScopeOnly";
    private static final String ONLY_IF_MODERN =
            "spiderajax.automation.dialog.ajaxspider.runOnlyIfModern";
    private static final String FIELD_ADVANCED = "spiderajax.automation.dialog.ajaxspider.advanced";

    private static final String BROWSER_ID_PARAM =
            "spiderajax.automation.dialog.ajaxspider.browserid";
    private static final String MAX_CRAWL_STATES_PARAM =
            "spiderajax.automation.dialog.ajaxspider.maxcrawlstates";
    private static final String EVENT_WAIT_PARAM =
            "spiderajax.automation.dialog.ajaxspider.eventwait";
    private static final String RELOAD_WAIT_PARAM =
            "spiderajax.automation.dialog.ajaxspider.reloadwait";
    private static final String CLICK_DEFAULT_ELEMS_PARAM =
            "spiderajax.automation.dialog.ajaxspider.clickdefaultelems";
    private static final String CLICK_ELEMS_ONCE_PARAM =
            "spiderajax.automation.dialog.ajaxspider.clickelemsonce";
    private static final String ENABLE_EXTENSIONS_PARAM =
            "spiderajax.automation.dialog.ajaxspider.enableexts";
    private static final String RANDOM_INPUTS_PARAM =
            "spiderajax.automation.dialog.ajaxspider.randominputs";
    private static final String CLICK_ELEMS_HEADER =
            "spiderajax.automation.dialog.ajaxspider.clickelems";
    private static final String LOGOUT_AVOIDANCE_PARAM =
            "spiderajax.scandialog.label.logoutAvoidance";

    private AjaxSpiderMultipleOptionsPanel elemsOptionsPanel;

    private OptionsAjaxSpiderTableModel ajaxSpiderClickModel;

    private ScopeCheckComponent scopeCheckComponent;

    private ExcludedElementsPanel excludedElementsPanel;

    private AjaxSpiderJob job;
    private ExtensionSelenium extSel = null;

    public AjaxSpiderJobDialog(AjaxSpiderJob job) {
        super(
                View.getSingleton().getMainFrame(),
                TITLE,
                DisplayUtils.getScaledDimension(450, 450),
                TAB_LABELS);
        this.job = job;

        // Parameters tab
        this.addTextField(0, NAME_PARAM, this.job.getName());
        List<String> contextNames = this.job.getEnv().getContextNames();
        // Add blank option
        contextNames.add(0, "");
        this.addComboField(0, CONTEXT_PARAM, contextNames, this.job.getParameters().getContext());

        List<String> users = job.getEnv().getAllUserNames();
        // Add blank option
        users.add(0, "");
        this.addComboField(0, USER_PARAM, users, this.job.getParameters().getUser());

        // Cannot select the node as it might not be present in the Sites tree
        this.addNodeSelectField(0, URL_PARAM, null, true, false);
        Component urlField = this.getField(URL_PARAM);
        if (urlField instanceof JTextField) {
            ((JTextField) urlField).setText(this.job.getParameters().getUrl());
        }
        this.addNumberField(
                0,
                MAX_DURATION_PARAM,
                1,
                Integer.MAX_VALUE,
                getInt(
                        this.job.getParameters().getMaxDuration(),
                        AjaxSpiderParam.DEFAULT_MAX_DURATION));
        this.addNumberField(
                0,
                MAX_CRAWL_DEPTH_PARAM,
                1,
                Integer.MAX_VALUE,
                getInt(
                        this.job.getParameters().getMaxCrawlDepth(),
                        AjaxSpiderParam.DEFAULT_MAX_CRAWL_DEPTH));
        this.addNumberField(
                0,
                NUM_BROWSERS_PARAM,
                1,
                Integer.MAX_VALUE,
                getInt(
                        this.job.getParameters().getNumberOfBrowsers(),
                        Constants.getDefaultThreadCount() / 2));
        List<ProvidedBrowserUI> browserList = getExtSelenium().getProvidedBrowserUIList();
        List<String> browserNames = new ArrayList<>();
        String defaultBrowser = "";
        browserNames.add(""); // Default to empty
        for (ProvidedBrowserUI browser : browserList) {
            browserNames.add(browser.getName());
            if (browser.getBrowser().getId().equals(this.job.getParameters().getBrowserId())) {
                defaultBrowser = browser.getName();
            }
        }
        this.addComboField(0, BROWSER_ID_PARAM, browserNames, defaultBrowser);

        this.addCheckBoxField(
                0, IN_SCOPE_ONLY, JobUtils.unBox(this.job.getParameters().getInScopeOnly()));
        this.addCheckBoxField(
                0, ONLY_IF_MODERN, JobUtils.unBox(this.job.getParameters().getRunOnlyIfModern()));
        this.addCheckBoxField(0, FIELD_ADVANCED, advOptionsSet());

        this.addFieldListener(
                FIELD_ADVANCED,
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        setAdvancedTabs(getBoolValue(FIELD_ADVANCED));
                    }
                });

        this.addPadding(0);

        // Elements tab
        boolean clickDefaultElements =
                JobUtils.unBox(this.job.getParameters().getClickDefaultElems())
                        || this.job.getParameters().getElements() == null;

        this.addCheckBoxField(
                1,
                CLICK_ELEMS_ONCE_PARAM,
                getBool(
                        this.job.getParameters().getClickElemsOnce(),
                        AjaxSpiderParam.DEFAULT_CLICK_ELEMS_ONCE));
        this.addCheckBoxField(
                1,
                RANDOM_INPUTS_PARAM,
                getBool(
                        this.job.getParameters().getRandomInputs(),
                        AjaxSpiderParam.DEFAULT_RANDOM_INPUTS));
        this.addCheckBoxField(1, CLICK_DEFAULT_ELEMS_PARAM, clickDefaultElements);

        this.addReadOnlyField(1, CLICK_ELEMS_HEADER, "", true);

        this.addFieldListener(
                CLICK_DEFAULT_ELEMS_PARAM,
                e -> setClickElemsEnabled(!this.getBoolValue(CLICK_DEFAULT_ELEMS_PARAM)));

        this.getAjaxSpiderClickModel().setElems(this.getElems());
        this.addCustomComponent(1, getAjaxSpiderClickPanel());

        getExcludedElementsPanel()
                .setElements(JobMapper.INSTANCE.toModel(job.getParameters().getExcludedElements()));

        addCustomComponent(1, getExcludedElementsPanel().getPanel());

        this.addPadding(1);

        // Advanced tab
        getScopeCheckComponent().setScopeCheck(job.getParameters().getScopeCheck());
        addCustomComponent(2, getScopeCheckComponent().getComponent());

        addCheckBoxField(
                2,
                LOGOUT_AVOIDANCE_PARAM,
                getBool(
                        job.getParameters().getLogoutAvoidance(),
                        AjaxSpiderParam.DEFAULT_LOGOUT_AVOIDANCE));

        this.addNumberField(
                2,
                MAX_CRAWL_STATES_PARAM,
                0,
                Integer.MAX_VALUE,
                getInt(
                        this.job.getParameters().getMaxCrawlStates(),
                        AjaxSpiderParam.DEFAULT_CRAWL_STATES));
        this.addNumberField(
                2,
                EVENT_WAIT_PARAM,
                1,
                Integer.MAX_VALUE,
                getInt(
                        this.job.getParameters().getEventWait(),
                        AjaxSpiderParam.DEFAULT_EVENT_WAIT_TIME));
        this.addNumberField(
                2,
                RELOAD_WAIT_PARAM,
                1,
                Integer.MAX_VALUE,
                getInt(
                        this.job.getParameters().getReloadWait(),
                        AjaxSpiderParam.DEFAULT_RELOAD_WAIT_TIME));

        this.addCheckBoxField(
                2,
                ENABLE_EXTENSIONS_PARAM,
                getBool(
                        this.job.getParameters().getEnableExtensions(),
                        AjaxSpiderParam.DEFAULT_ENABLE_EXTENSIONS));

        this.addPadding(2);

        setClickElemsEnabled(!clickDefaultElements);
        setAdvancedTabs(getBoolValue(FIELD_ADVANCED));
    }

    private int getInt(Integer i, int defaultValue) {
        if (i == null) {
            return defaultValue;
        }
        return i.intValue();
    }

    private boolean getBool(Boolean b, boolean defaultValue) {
        if (b == null) {
            return defaultValue;
        }
        return b.booleanValue();
    }

    private List<AjaxSpiderParamElem> getElems() {

        List<AjaxSpiderParamElem> elems = new ArrayList<>();
        List<String> enabledElems;
        if (this.job.getParameters().getElements() != null) {
            enabledElems =
                    this.job.getParameters().getElements().stream()
                            .map(e -> e.toLowerCase(Locale.ROOT))
                            .collect(Collectors.toList());
        } else {
            enabledElems = new ArrayList<>();
        }

        List<String> defaultElementList = Arrays.asList(AjaxSpiderParam.DEFAULT_ELEMS_NAMES);

        defaultElementList.forEach(
                e -> {
                    elems.add(new AjaxSpiderParamElem(e, enabledElems.contains(e)));
                });
        for (String elem : enabledElems) {
            if (!defaultElementList.contains(elem)) {
                elems.add(new AjaxSpiderParamElem(elem, true));
            }
        }

        return elems;
    }

    private OptionsAjaxSpiderTableModel getAjaxSpiderClickModel() {
        if (ajaxSpiderClickModel == null) {
            ajaxSpiderClickModel = new OptionsAjaxSpiderTableModel();
        }
        return ajaxSpiderClickModel;
    }

    private AjaxSpiderMultipleOptionsPanel getAjaxSpiderClickPanel() {
        if (elemsOptionsPanel == null) {
            elemsOptionsPanel = new AjaxSpiderMultipleOptionsPanel(getAjaxSpiderClickModel());
        }
        return elemsOptionsPanel;
    }

    private ScopeCheckComponent getScopeCheckComponent() {
        if (scopeCheckComponent == null) {
            scopeCheckComponent = new ScopeCheckComponent();
        }
        return scopeCheckComponent;
    }

    private ExcludedElementsPanel getExcludedElementsPanel() {
        if (excludedElementsPanel == null) {
            excludedElementsPanel = new ExcludedElementsPanel(this, false);
        }
        return excludedElementsPanel;
    }

    private void setClickElemsEnabled(boolean isEnabled) {
        getAjaxSpiderClickPanel().setComponentEnabled(isEnabled);
    }

    private boolean advOptionsSet() {
        Parameters params = this.job.getParameters();
        return params.getBrowserId() != null
                || params.getMaxCrawlStates() != null
                || params.getEventWait() != null
                || params.getReloadWait() != null
                || params.getClickDefaultElems() != null
                || params.getClickElemsOnce() != null
                || params.getRandomInputs() != null;
    }

    private void setAdvancedTabs(boolean visible) {
        // Show/hide all except from the first tab
        this.setTabsVisible(
                new String[] {
                    "spiderajax.automation.dialog.tab.elems",
                    "spiderajax.automation.dialog.ajaxspider.tab.adv"
                },
                visible);
    }

    private ExtensionSelenium getExtSelenium() {
        if (extSel == null) {
            extSel =
                    Control.getSingleton()
                            .getExtensionLoader()
                            .getExtension(ExtensionSelenium.class);
        }
        return extSel;
    }

    @Override
    public void save() {
        this.job.setName(this.getStringValue(NAME_PARAM));
        this.job.getParameters().setContext(this.getStringValue(CONTEXT_PARAM));
        this.job.getParameters().setUser(this.getStringValue(USER_PARAM));
        this.job.getParameters().setUrl(this.getStringValue(URL_PARAM));
        this.job.getParameters().setMaxDuration(this.getIntValue(MAX_DURATION_PARAM));
        this.job.getParameters().setMaxCrawlDepth(this.getIntValue(MAX_CRAWL_DEPTH_PARAM));
        this.job.getParameters().setNumberOfBrowsers(this.getIntValue(NUM_BROWSERS_PARAM));
        this.job.getParameters().setInScopeOnly(this.getBoolValue(IN_SCOPE_ONLY));
        this.job.getParameters().setRunOnlyIfModern(this.getBoolValue(ONLY_IF_MODERN));
        String browserName = this.getStringValue(BROWSER_ID_PARAM);
        if (browserName.isEmpty()) {
            this.job.getParameters().setBrowserId(null);
        } else {
            List<ProvidedBrowserUI> browserList = getExtSelenium().getProvidedBrowserUIList();
            for (ProvidedBrowserUI bui : browserList) {
                if (browserName.equals(bui.getName())) {
                    this.job.getParameters().setBrowserId(bui.getBrowser().getId());
                    break;
                }
            }
        }

        if (this.getBoolValue(FIELD_ADVANCED)) {
            job.getParameters().setScopeCheck(getScopeCheckComponent().getScopeCheck().toString());
            job.getParameters().setLogoutAvoidance(getBoolValue(LOGOUT_AVOIDANCE_PARAM));
            this.job.getParameters().setMaxCrawlStates(this.getIntValue(MAX_CRAWL_STATES_PARAM));
            this.job.getParameters().setEventWait(this.getIntValue(EVENT_WAIT_PARAM));
            this.job.getParameters().setReloadWait(this.getIntValue(RELOAD_WAIT_PARAM));
            this.job
                    .getParameters()
                    .setClickDefaultElems(this.getBoolValue(CLICK_DEFAULT_ELEMS_PARAM));
            this.job.getParameters().setClickElemsOnce(this.getBoolValue(CLICK_ELEMS_ONCE_PARAM));
            this.job.getParameters().setRandomInputs(this.getBoolValue(RANDOM_INPUTS_PARAM));
            this.job
                    .getParameters()
                    .setEnableExtensions(this.getBoolValue(ENABLE_EXTENSIONS_PARAM));

            if (!this.getBoolValue(CLICK_DEFAULT_ELEMS_PARAM)) {
                this.job
                        .getParameters()
                        .setElements(
                                this.getAjaxSpiderClickModel().getElements().stream()
                                        .filter(e -> e.isEnabled())
                                        .map(e -> e.getName())
                                        .collect(Collectors.toList()));
            }

            job.getParameters()
                    .setExcludedElements(
                            JobMapper.INSTANCE.toDto(getExcludedElementsPanel().getElements()));

        } else {
            this.job.getParameters().setMaxCrawlStates(null);
            this.job.getParameters().setEventWait(null);
            this.job.getParameters().setReloadWait(null);
            this.job.getParameters().setClickDefaultElems(null);
            this.job.getParameters().setClickElemsOnce(null);
            this.job.getParameters().setRandomInputs(null);
            this.job.getParameters().setEnableExtensions(null);
            this.job.getParameters().setElements(null);
            this.job.getParameters().setExcludedElements(null);
        }
        this.job.setChanged();
    }

    @Override
    public String validateFields() {
        // TODO validate url - coping with envvars :O
        return null;
    }
}
