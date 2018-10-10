package com.microfocus.application.automation.tools.octane;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.hp.octane.integrations.dto.DTOFactory;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.UUID;

public class PlugInAbstractTest {
    protected static final DTOFactory dtoFactory = DTOFactory.getInstance();
    protected static String ssp;

    @ClassRule
    public static final JenkinsRule rule = new JenkinsRule();
    public static final JenkinsRule.WebClient client = rule.createWebClient();


    @BeforeClass
    public static void init() throws Exception {
        HtmlPage configPage = client.goTo("configure");
        HtmlForm form = configPage.getFormByName("config");
        ssp = UUID.randomUUID().toString();
        form.getInputByName("_.uiLocation").setValueAttribute("http://localhost:8008/ui/?p=" + ssp + "/1002");
        form.getInputByName("_.username").setValueAttribute("username");
        form.getInputByName("_.password").setValueAttribute("password");
        rule.submit(form);
    }
}
