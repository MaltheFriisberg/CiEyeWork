package org.netmelody.cii.witness.jenkins;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Collections2.transform;
import static org.netmelody.cii.domain.Build.buildAt;
import static org.netmelody.cii.domain.Percentage.percentageOf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.netmelody.cii.domain.Feature;
import org.netmelody.cii.domain.Percentage;
import org.netmelody.cii.domain.Sponsor;
import org.netmelody.cii.domain.Status;
import org.netmelody.cii.domain.Target;
import org.netmelody.cii.domain.TargetGroup;
import org.netmelody.cii.persistence.Detective;
import org.netmelody.cii.witness.Witness;
import org.netmelody.cii.witness.protocol.RestRequester;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JenkinsWitness implements Witness {
    
    private final Gson json = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").create();
    private final RestRequester restRequester = new RestRequester();
    private final String endpoint;

    public JenkinsWitness(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public TargetGroup statusOf(final Feature feature) {
        if (!endpoint.equals(feature.endpoint())) {
            return new TargetGroup();
        }
        
        final View view = filter(views(), new Predicate<View>() {
            @Override public boolean apply(View view) {
                return view.name.startsWith(feature.name());
            }
        }).iterator().next();
        
        final Collection<Target> targets = transform(jobsFor(view), new Function<Job, Target>() {
            @Override public Target apply(Job job) {
                return targetFrom(job);
            }
        });
        
        return new TargetGroup(targets);
    }
    
    @Override
    public long millisecondsUntilNextUpdate() {
        return 0L;
    }
    
    public Collection<String> users() {
        final JenkinsUserDetails detail = makeJenkinsRestCall(endpoint + "/people", JenkinsUserDetails.class);
        return transform(detail.users, new Function<UserDetail, String>() {
            @Override public String apply(UserDetail userDetail) { return userDetail.user.fullName; }
        });
    }
    
    private Collection<View> views() {
        return makeJenkinsRestCall(endpoint, JenkinsDetails.class).views;
    }

    private Collection<Job> jobsFor(View viewDigest) {
        return makeJenkinsRestCall(viewDigest.url, View.class).jobs;
    }
    
    private Target targetFrom(Job jobDigest) {
        if (!jobDigest.building() && Status.BROKEN != jobDigest.status()) {
            return new Target(jobDigest.name, jobDigest.status());
        }
        
        final Job job = makeJenkinsRestCall(jobDigest.url, Job.class);
        if (job.lastBuild == null || job.lastSuccessfulBuild == null) {
            return new Target(jobDigest.name, jobDigest.status(), buildAt(percentageOf(0)));
        }
        
        final Build lastBuild = fetchBuildData(job.lastBuild.url);
        
        
        final List<Sponsor> sponsors = sponsorsOf(lastBuild);
        
        if (!jobDigest.building()) {
            return new Target(jobDigest.name, jobDigest.name, jobDigest.status(), sponsors);
        }
        
        final Build lastSuccessfulBuild = fetchBuildData(job.lastSuccessfulBuild.url);
        
        return new Target(jobDigest.name,
                          jobDigest.name,
                          jobDigest.status(),
                          sponsors,
                          buildAt(Percentage.percentageOf(new Date().getTime() - lastBuild.timestamp,
                                                          lastSuccessfulBuild.duration)));
        
//        
//        if (!build.building || build.builtOn == null) {
//            return new Target(jobDigest.name, jobDigest.status(), buildAt(percentageOf(0)));
//        }
//        
//        Computer agentDetails = agentDetails(build.builtOn);
    }
    
    private List<Sponsor> sponsorsOf(Build build) {
        final Detective detective = new Detective();
        
        final List<Sponsor> sponsors = detective.sponsorsOf(analyseChanges(build));
        
        if (!sponsors.isEmpty()) {
            return sponsors;
        }
        
        for (String buildUrl :  build.upstreamBuildUrls()) {
            final Build upstreamBuild = fetchBuildData(buildUrl);
            if (null == upstreamBuild) {
                continue;
            }
            sponsors.addAll(sponsorsOf(upstreamBuild));
        }
        return sponsors;
    }

    private String analyseChanges(Build build) {
        if (null == build.changeSet || null == build.changeSet.items) {
            return "";
        }
        
        final StringBuilder result = new StringBuilder();
        for (ChangeSetItem changeSetItem : build.changeSet.items) {
            result.append(changeSetItem.user);
            result.append(changeSetItem.msg);
        }
        
        for (User user : build.culprits) {
            result.append(user.fullName);
        }
        
        return result.toString();
    }

//    private Computer agentDetails(String agentName) {
//        return makeJenkinsRestCall(endpoint + "/computer/" + agentName, Computer.class);
//    }
    
//    private void changeDescription(String jobName, String buildNumber, String newDescription) {
//        "/submitDescription?Submit=Submit&description=" + encodeURI(change.desc) + "&json={\"description\":\"" + change.desc + "\"}";
//    }
    
    private Build fetchBuildData(String buildUrl) {
        return makeJenkinsRestCall(endpoint + "/" + buildUrl, Build.class);
    }
    
    private <T> T makeJenkinsRestCall(String url, Class<T> type) {
        return json.fromJson(restRequester.makeRequest(url + "/api/json"), type);
    }
    
    static class JenkinsDetails {
        List<Label> assignedLabels;
        String mode;
        String nodeName;
        String nodeDescription;
        int numExecutors;
        String description;
        List<Job> jobs;
        Load overallLoad;
        View primaryView;
        int slaveAgentPort;
        boolean useCrumbs;
        boolean useSecurity;
        List<View> views;
    }
    
    static class JenkinsUserDetails {
        List<UserDetail> users;
    }

    static class UserDetail {
        long lastChange;
        Project project;
        User user;
    }
    
    static class Project {
        String name;
        String url;
    }
    
    static class User {
        String absoluteUrl;
        String fullName;
    }
    
    static class Job {
        String name;
        String url;
        String displayName;
        String description;
        String color;
        boolean buildable;
        boolean inQueue;
        boolean keepDependencies;
        boolean concurrentBuild;
        List<Build> builds;
        List<Action> actions;
        Build firstBuild;
        Build lastBuild;
        Build lastFailedBuild;
        Build lastStableBuild;
        Build lastSuccessfulBuild;
        Build lastUnstableBuild;
        Build lastUnsuccessfulBuild;
        int nextBuildNumber;
        List<Project> downstreamProjects;
        List<Project> upstreamProjects;
        List<HealthReport> healthReport;
        //scm
        //property
        //queueItem
        
        public Status status() {
            if (null == color || color.startsWith("blue")) {
                return Status.GREEN;
            }
            if ("disabled".equals(color)) {
                return Status.DISABLED;
            }
            return Status.BROKEN;
        }
        
        public boolean building() {
            return (null != color && color.endsWith("_anime"));
        }
    }
    
    static class Build {
        int number;
        String url;
        String description;
        boolean building;
        long duration;
        String fullDisplayName;
        String id;
        boolean keepLog;
        String result;
        long timestamp;
        String builtOn;
        List<User> culprits;
        List<Action> actions;
        ChangeSet changeSet;
        //artifacts
        
        public List<String> upstreamBuildUrls() {
            final List<String> result = new ArrayList<String>();
            
            if (null != actions) {
                for (Action action : actions) {
                    if (null != action) {
                        result.addAll(action.upstreamBuildUrls());
                    }
                }
            }
            return result;
        }
    }
    
    static class ChangeSet {
        String kind;
        List<ChangeSetItem> items;
        //revisions
    }
    
    static class ChangeSetItem {
        //Date date;
        String msg;
        long revision;
        String user;
        //paths
    }
    
    static class View {
        String name;
        String url;
        String description;
        List<Job> jobs;
    }
    
    static class HealthReport {
        String description;
        String iconUrl;
        int score;
    }
    
    static class Computer {
        String displayName;
        String icon;
        boolean idle;
        boolean jnlpAgent;
        boolean launchSupported;
        boolean manualLaunchAllowed;
        List<Action> actions;
        int numExecutors;
        boolean offline;
        boolean temporarilyOffline;
        //oneOffExecutors
        //offlineCause
        //loadStatistics
        //executors
        //monitorData
    }
    
    static class Action {
        List<Cause> causes;
        int failCount;
        int skipCount;
        int totalCount;
        String urlName;
        
        public List<String> upstreamBuildUrls() {
            final List<String> result = new ArrayList<String>();
            
            if (null != causes) {
                for (Cause cause : causes) {
                    if (null != cause) {
                        String upstreamBuildUrl = cause.upstreamBuildUrl();
                        if (null != upstreamBuildUrl) {
                            result.add(upstreamBuildUrl);
                        }
                    }
                }
            }
            return result;
        }
    }
    
    static class Cause {
        String shortDescription;
        long upstreamBuild;
        String upstreamProject;
        String upstreamUrl;
        
        public String upstreamBuildUrl() {
            return (null == upstreamUrl) ? "" : upstreamUrl + upstreamBuild;
        }
    }
    
    static class Label { }
    static class Load { }
}
