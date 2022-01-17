package com.sequenceiq.cloudbreak.core.flow2.cluster;

public class ClusterScaleFailedPayload {

    private final Long stackId;

    private final String hostGroupName;

    private final Exception errorDetails;

    public ClusterScaleFailedPayload(Long stackId, String hostGroupName, Exception errorDetails) {
        this.stackId = stackId;
        this.hostGroupName = hostGroupName;
        this.errorDetails = errorDetails;
    }

    public Long getResourceId() {
        return stackId;
    }

    public Exception getErrorDetails() {
        return errorDetails;
    }
}
