/*
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
 */

package com.microfocus.application.automation.tools.octane.events;

import com.hp.octane.integrations.dto.events.CIEvent;
import hudson.model.InvisibleAction;

/**
 * The class is intended to hold scm event that should be sent later to Octane.
 * For most jobs, scm listener is called after job is start to run.
 * In multibranch job, scm listener is called before job is started to run
 * Therefore scm event that is sent to Octane before job start event is ignored in Octane.
 *
 *
 */
public class ScmEventHolderAction extends InvisibleAction {

    private CIEvent scmEvent;

    public static ScmEventHolderAction create(CIEvent scmEvent) {
        ScmEventHolderAction action = new ScmEventHolderAction();
        action.scmEvent = scmEvent;
        return action;
    }


    public CIEvent getScmEvent() {
        return scmEvent;
    }


}
