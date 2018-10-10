/*
 *
 *  Certain versions of software and/or documents (“Material”) accessible here may contain branding from
 *  Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 *  the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 *  and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 *  marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * © Copyright 2012-2018 Micro Focus or one of its affiliates.
 *
 * The only warranties for products and services of Micro Focus and its affiliates
 * and licensors (“Micro Focus”) are set forth in the express warranty statements
 * accompanying such products and services. Nothing herein should be construed as
 * constituting an additional warranty. Micro Focus shall not be liable for technical
 * or editorial errors or omissions contained herein.
 * The information contained herein is subject to change without notice.
 * ___________________________________________________________________
 *
 */

package com.microfocus.application.automation.tools.octane.configuration;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.microfocus.application.automation.tools.octane.PlugInAbstractTest;
import hudson.util.Secret;
import net.jcip.annotations.NotThreadSafe;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

// import com.gargoylesoftware.htmlunit.WebRequestSettings;
@SuppressWarnings({"squid:S2699","squid:S3658","squid:S2259","squid:S1872","squid:S2925","squid:S109","squid:S1607","squid:S2701","squid:S2698"})
@NotThreadSafe
public class ConfigApiTest extends PlugInAbstractTest {

	@Test
	public void testRead() throws Exception {
		Page page = client.goTo("nga/configuration/read", "application/json");
		String configAsString = page.getWebResponse().getContentAsString();
		JSONObject config = JSONObject.fromObject(configAsString);
		Assert.assertEquals("http://localhost:8008", config.getString("location"));
		Assert.assertEquals(PlugInAbstractTest.ssp, config.getString("sharedSpace"));
		Assert.assertEquals("username", config.getString("username"));
		Assert.assertEquals(ConfigurationService.getModel().getIdentity(), config.getString("serverIdentity"));
	}

	@Test
	public void testSave() throws Exception {
		//URL url = client.createCrumbedUrl("nga/configuration/save");
		//WebRequest request = new WebRequest(url,HttpMethod.POST);

		// basic scenario: location, shared space and credentials
		JSONObject config = new JSONObject();
		config.put("location", "http://localhost:8088");
		String sharedSP = UUID.randomUUID().toString();
		config.put("sharedSpace", sharedSP);
		config.put("username", "username1");
		config.put("password", "password1");
		WebRequest req = new WebRequest(client.createCrumbedUrl("nga/configuration/save"), HttpMethod.POST);
		req.setEncodingType(null);
		req.setRequestBody(config.toString());
		Page page = client.getPage(req);
		config = JSONObject.fromObject(page.getWebResponse().getContentAsString());
		checkConfig(config, "http://localhost:8088", sharedSP, "username1", Secret.fromString("password1"));
		Assert.assertEquals(ConfigurationService.getModel().getIdentity(), config.getString("serverIdentity"));


		// location, shared space, no credentials
		config = new JSONObject();
		config.put("location", "http://localhost:8888");
		String sharedSP1 = UUID.randomUUID().toString();
		config.put("sharedSpace", sharedSP1);
		req.setRequestBody(config.toString());
		page = client.getPage(req);
		config = JSONObject.fromObject(page.getWebResponse().getContentAsString());
		checkConfig(config, "http://localhost:8888", sharedSP1, "username1", Secret.fromString("password1"));
		Assert.assertEquals(ConfigurationService.getModel().getIdentity(), config.getString("serverIdentity"));
//
		// location, shared space and username without password
		config = new JSONObject();
		config.put("location", "http://localhost:8882");
		String sharedSP2 = UUID.randomUUID().toString();
		config.put("sharedSpace", sharedSP2);
		config.put("username", "username3");
		req.setRequestBody(config.toString());
		page = client.getPage(req);
		config = JSONObject.fromObject(page.getWebResponse().getContentAsString());
		checkConfig(config, "http://localhost:8882", sharedSP2, "username3", Secret.fromString(""));
		Assert.assertEquals(ConfigurationService.getModel().getIdentity(), config.getString("serverIdentity"));

		// uiLocation and identity
		config = new JSONObject();
		String sharedSP3 = UUID.randomUUID().toString();
		config.put("uiLocation", "http://localhost:8881/ui?p=" + sharedSP3 + "/1002");
		config.put("serverIdentity", "2d2fa955-1d13-4d8c-947f-ab11c72bf850");
		req.setRequestBody(config.toString());
		page = client.getPage(req);
		config = JSONObject.fromObject(page.getWebResponse().getContentAsString());
		checkConfig(config, "http://localhost:8881", sharedSP3, "username3", Secret.fromString(""));
		Assert.assertEquals("2d2fa955-1d13-4d8c-947f-ab11c72bf850", config.getString("serverIdentity"));
		Assert.assertEquals("2d2fa955-1d13-4d8c-947f-ab11c72bf850", ConfigurationService.getModel().getIdentity());

		// requires POST
		req.setHttpMethod(HttpMethod.GET);
		try {
			client.getPage(req);
			Assert.fail("Only POST should be allowed");
		} catch (FailingHttpStatusCodeException ex) {
			// expected
		}
	}

	private void checkConfig(JSONObject config, String location, String sharedSpace, String username, Secret password) {
		// check values returned
		Assert.assertEquals(location, config.getString("location"));
		Assert.assertEquals(sharedSpace, config.getString("sharedSpace"));
		Assert.assertEquals(username, config.getString("username"));
		// check values stored
		ServerConfiguration serverConfiguration = ConfigurationService.getServerConfiguration();
		Assert.assertEquals(location, serverConfiguration.location);
		Assert.assertEquals(sharedSpace, serverConfiguration.sharedSpace);
		Assert.assertEquals(username, serverConfiguration.username);
		Assert.assertEquals(password, serverConfiguration.password);
	}
}
