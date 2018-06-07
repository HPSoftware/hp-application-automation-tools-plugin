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

package com.hpe.application.automation.tools.octane.actions.dto;

import com.hpe.application.automation.tools.octane.actions.UftTestType;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * This file represents automated test for sending to Octane
 */
@XmlRootElement(name = "test")
@XmlAccessorType(XmlAccessType.FIELD)
public class AutomatedTest implements SupportsMoveDetection, SupportsOctaneStatus {

    @XmlAttribute
    private String id;
    @XmlAttribute
    private String changeSetSrc;
    @XmlAttribute
    private String changeSetDst;
    @XmlAttribute
    private String oldName;
    @XmlAttribute
    private String oldPackageName;
    @XmlAttribute
    private Boolean isMoved;
    @XmlAttribute
    private UftTestType uftTestType;
    @XmlAttribute
    private OctaneStatus octaneStatus;


    @XmlAttribute
    private String name;
    @XmlAttribute
    private String packageName;
    @XmlAttribute
    private Boolean executable;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackage() {
        return packageName;
    }

    public void setPackage(String packageName) {
        this.packageName = packageName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUftTestType(UftTestType uftTestType) {
        this.uftTestType = uftTestType;
    }

    public UftTestType getUftTestType() {
        return uftTestType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getExecutable() {
        return executable;
    }

    public void setExecutable(Boolean executable) {
        this.executable = executable;
    }

    @Override
    public String toString() {
        return "#" + getId() == null ? "0" : getId() + " - " + getPackage() + "@" + getName();
    }

    @Override
    public String getChangeSetSrc() {
        return changeSetSrc;
    }

    @Override
    public void setChangeSetSrc(String changeSetSrc) {
        this.changeSetSrc = changeSetSrc;
    }

    @Override
    public String getChangeSetDst() {
        return changeSetDst;
    }

    @Override
    public void setChangeSetDst(String changeSetDst) {
        this.changeSetDst = changeSetDst;
    }

    public String getOldName() {
        return oldName;
    }

    public void setOldName(String oldName) {
        this.oldName = oldName;
    }

    public String getOldPackage() {
        return oldPackageName;
    }

    public void setOldPackage(String oldPackageName) {
        this.oldPackageName = oldPackageName;
    }

    public Boolean getIsMoved() {
        return isMoved == null ? false : isMoved;
    }

    public void setIsMoved(Boolean moved) {
        isMoved = moved;
    }

    @Override
    public OctaneStatus getOctaneStatus() {
        return octaneStatus;
    }

    public void setOctaneStatus(OctaneStatus octaneStatus) {
        this.octaneStatus = octaneStatus;
    }
}
