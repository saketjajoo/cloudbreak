package com.sequenceiq.cloudbreak.reactor.api.event.resource;

import java.util.List;
import java.util.StringJoiner;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.event.resource.CloudStackRequest;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;

public class StackVerticalScaleRequest<T> extends CloudStackRequest<T> {

    private final List<CloudResource> resourceList;

    public StackVerticalScaleRequest(CloudContext cloudContext,
        CloudCredential cloudCredential,
        CloudStack stack,
        List<CloudResource> resourceList) {
        super(cloudContext, cloudCredential, stack);
        this.resourceList = resourceList;
    }

    public List<CloudResource> getResourceList() {
        return resourceList;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", StackVerticalScaleRequest.class.getSimpleName() + "[", "]")
                .add("resourceList=" + resourceList)
                .toString();
    }
}
