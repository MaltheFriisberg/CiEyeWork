package org.netmelody.cieye.spies.teamcity.jsondomain;

public class BuildType {
    public String id;
    public String name;
    public String href;
    public String projectName;
    public String projectId;
    public String webUrl;
    
    public String webUrl() {
        return webUrl + "&guest=1";
    }
    public void addBranchName(String name) {
        this.name+= " " + name;
    }
}