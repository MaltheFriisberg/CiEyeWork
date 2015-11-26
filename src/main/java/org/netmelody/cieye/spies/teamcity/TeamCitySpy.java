package org.netmelody.cieye.spies.teamcity;

import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static org.netmelody.cieye.core.domain.Status.UNKNOWN;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.netmelody.cieye.core.domain.Feature;
import org.netmelody.cieye.core.domain.Status;
import org.netmelody.cieye.core.domain.TargetDetail;
import org.netmelody.cieye.core.domain.TargetDigest;
import org.netmelody.cieye.core.domain.TargetDigestGroup;
import org.netmelody.cieye.core.domain.TargetId;
import org.netmelody.cieye.core.observation.CiSpy;
import org.netmelody.cieye.core.observation.Contact;
import org.netmelody.cieye.core.observation.KnownOffendersDirectory;
import org.netmelody.cieye.spies.teamcity.jsondomain.Build;
import org.netmelody.cieye.spies.teamcity.jsondomain.BuildType;
import org.netmelody.cieye.spies.teamcity.jsondomain.BuildTypeDetail;

import com.google.common.base.Predicate;
import java.util.ArrayList;

public final class TeamCitySpy implements CiSpy {

    private final TeamCityCommunicator communicator;
    private final BuildTypeAnalyser buildTypeAnalyser;

    private final Map<TargetId, BuildType> recognisedBuildTypes = newHashMap();
    
    public TeamCitySpy(String endpoint, KnownOffendersDirectory detective, Contact contact) {
        this.communicator = new TeamCityCommunicator(contact, endpoint);
        this.buildTypeAnalyser = new BuildTypeAnalyser(this.communicator, detective);
    }

    @Override
    public TargetDigestGroup targetsConstituting(Feature feature) {
        try {
        final Collection<BuildType> buildTypes = buildTypesFor(feature);
        final List<TargetDigest> digests = newArrayList();
        
        for (BuildType buildType : buildTypes) {
            final TargetDigest targetDigest = new TargetDigest(communicator.endpoint() + buildType.href, buildType.webUrl(), buildType.name, UNKNOWN);
            digests.add(targetDigest);
            recognisedBuildTypes.put(targetDigest.id(), buildType);
            
        }
        
        return new TargetDigestGroup(digests);
        } catch(Exception e) {
            e.printStackTrace(System.out);
        }
        return new TargetDigestGroup();
    }

    @Override
    public TargetDetail statusOf(final TargetId target) {
        try {
        BuildType buildType = recognisedBuildTypes.get(target);
        if (null == buildType) {
            return null;
        }
        return buildTypeAnalyser.targetFrom(buildType);
        } catch(Exception e) {
            e.printStackTrace(System.out);
        }
        return null;
    }
    
    @Override
    public boolean takeNoteOf(TargetId target, String note) {
        try {
        if (!recognisedBuildTypes.containsKey(target)) {
            return false;
        }
        
        final BuildTypeDetail buildTypeDetail = communicator.detailsFor(recognisedBuildTypes.get(target));
        final Build lastCompletedBuild = communicator.lastCompletedBuildFor(buildTypeDetail);
        if (null != lastCompletedBuild && Status.BROKEN.equals(lastCompletedBuild.status())) {
            communicator.commentOn(lastCompletedBuild, note);
        }

        return true;
        } catch(Exception e) {
            System.out.print("takeNoteOf");
            e.printStackTrace(System.out);
        }
        return false;
    }

    private Collection<BuildType> buildTypesFor(final Feature feature) {
        try {
        if (!communicator.canSpeakFor(feature)) {
            return newArrayList();
        }
        
        final Collection<BuildType> buildTypes = communicator.buildTypes();
        
        for(BuildType bt : buildTypes) {
            
            List<Build> list = communicator.runningBuildsFor(bt);
               
            
            if(list.size() == 1 && list.get(0) != null) {
                String number = list.get(0).number;
                String branchName = list.get(0).branchName;
                if(branchName == null) {
                    branchName = "";
                }
                String toAdd = " #"+number+" "+ branchName;
                
                if(!bt.name.contains(toAdd)) {
                    bt.name+=toAdd;
                }
            }
            
        }
        if (feature.name().isEmpty()) {
            return buildTypes;
        }
        
        return filter(buildTypes, withFeatureName(feature.name()));
        } catch(Exception e) {
            System.out.print("BuildTypesFor");
            e.printStackTrace(System.out);
            
        }
        ArrayList<BuildType> b = new ArrayList<BuildType>();
            return b;
    }

    private Predicate<BuildType> withFeatureName(final String featureName) {
        
        return new Predicate<BuildType>() {
            @Override public boolean apply(BuildType buildType) {
                return buildType.projectName.trim().equals(featureName.trim());
            }
        };
    }
}
