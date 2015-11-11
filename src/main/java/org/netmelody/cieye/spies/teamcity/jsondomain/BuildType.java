package org.netmelody.cieye.spies.teamcity.jsondomain;

public class BuildType {
    public String id;
    public String name;
    public String notRunningName;
    public String href;
    public String projectName;
    public String projectId;
    public String webUrl;
    public Boolean runningBuild;
    
    public String webUrl() {
        return webUrl + "&guest=1";
    }
}