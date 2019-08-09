package com.microfocus.application.automation.tools.octane.tests.reflectionTest;

import hudson.util.Secret;

public class MyObject {
    private Integer integer = 67666;

    private String str2="6";


    public String getStr1() {
        return "1111";
    }



    public String getStr2() {
        return str2;
    }

    public void setStr2(String str2) {
        this.str2 = str2;
    }
}
