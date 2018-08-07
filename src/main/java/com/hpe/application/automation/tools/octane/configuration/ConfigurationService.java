/*
 * © Copyright 2013 EntIT Software LLC
 *  Certain versions of software and/or documents (“Material”) accessible here may contain branding from
 *  Hewlett-Packard Company (now HP Inc.) and Hewlett Packard Enterprise Company.  As of September 1, 2017,
 *  the Material is now offered by Micro Focus, a separately owned and operated company.  Any reference to the HP
 *  and Hewlett Packard Enterprise/HPE marks is historical in nature, and the HP and Hewlett Packard Enterprise/HPE
 *  marks are the property of their respective owners.
 * __________________________________________________________________
 * MIT License
 *
 * Copyright (c) 2018 Micro Focus Company, L.P.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ___________________________________________________________________
 *
 */

package com.hpe.application.automation.tools.octane.configuration;

import com.hpe.application.automation.tools.model.OctaneServerSettingsModel;
import com.hpe.application.automation.tools.settings.OctaneServerSettingsBuilder;
import hudson.Plugin;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import java.lang.reflect.Field;

/***
 * Octane plugin configuration service -
 * 1. helps to change Octane configuration
 * 2. helps to get Octane configuration and model
 * 3. helps to get RestClient based on some configuration
 */
public class ConfigurationService {

    /**
     * Get current {@see OctaneServerSettingsModel} model
     *
     * @return current configuration
     */
    public static OctaneServerSettingsModel getModel() {
        return getOctaneDescriptor().getModel();
    }

    public static String getSSCServer() {
        Descriptor sscDescriptor = getSSCDescriptor();
        return getServerFromDescriptor(sscDescriptor);
    }

    private static String getServerFromDescriptor(Descriptor sscDescriptor) {
        Object urlObj = getFieldValue(sscDescriptor, "url");
        if(urlObj != null) {
            return urlObj.toString();
        }
        return null;
    }
    public static Object getFieldValue(Object someObject, String fieldName) {
        for (Field field : someObject.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if(field.getName().equals(fieldName)) {
                Object value = null;
                try {
                    value = field.get(someObject);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Get current Octane server configuration (that is based on model)
     *
     * @return current configuration
     */
    public static ServerConfiguration getServerConfiguration() {
        if (getOctaneDescriptor() != null) {
            return getOctaneDescriptor().getServerConfiguration();
        }
        return null;
    }

    /**
     * Change model (used by tests)
     *
     * @param newModel new configuration
     */
    public static void configurePlugin(OctaneServerSettingsModel newModel) {
        if (getOctaneDescriptor() != null) {
            getOctaneDescriptor().setModel(newModel);
        }
    }

    private static OctaneServerSettingsBuilder.OctaneDescriptorImpl getOctaneDescriptor() {
        OctaneServerSettingsBuilder.OctaneDescriptorImpl octaneDescriptor = getJenkinsInstance().getDescriptorByType(OctaneServerSettingsBuilder.OctaneDescriptorImpl.class);
        if (octaneDescriptor == null) {
            throw new IllegalArgumentException("failed to obtain Octane plugin descriptor");
        }

        return octaneDescriptor;
    }
    private static Descriptor getSSCDescriptor(){
        return getJenkinsInstance().getDescriptorByName("com.fortify.plugin.jenkins.FPRPublisher");
    }

    /**
     * Get plugin version
     *
     * @return plugin version
     */
    public static String getPluginVersion() {
        Plugin plugin = getJenkinsInstance().getPlugin("hp-application-automation-tools-plugin");
        return plugin.getWrapper().getVersion();
    }


    private static Jenkins getJenkinsInstance() {
        Jenkins result = Jenkins.getInstance();
        if (result == null) {
            throw new IllegalStateException("failed to obtain Jenkins instance");
        }
        return result;
    }
}
