package com.microfocus.application.automation.tools.octane.testrunner;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;

import javax.annotation.CheckForNull;
import java.util.HashMap;
import java.util.Map;

public class VariableInjectionAction implements EnvironmentContributingAction {

    private Map<String, String> variables;

    public VariableInjectionAction(String key, String value) {
        variables = new HashMap<>();
        variables.put(key, value);
    }

    public VariableInjectionAction(Map<String, String> variables) {
        variables = new HashMap<>(variables);
    }


    @Override
    public void buildEnvVars(AbstractBuild<?, ?> abstractBuild, EnvVars envVars) {
        if (envVars != null && variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                envVars.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return "VariableInjectionAction";
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
