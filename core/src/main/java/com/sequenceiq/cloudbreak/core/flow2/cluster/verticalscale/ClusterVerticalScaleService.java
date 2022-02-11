package com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale;

import static com.sequenceiq.cloudbreak.event.ResourceEvent.STACK_VERTICALSCALED;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.STACK_VERTICALSCALING;

import javax.inject.Inject;

import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.DetailedStackStatus;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;

@Service
public class ClusterVerticalScaleService {
    @Inject
    private ClusterService clusterService;

    @Inject
    private CloudbreakFlowMessageService flowMessageService;

    public void credentialChange(Long stackId) {
        flowMessageService.fireEventAndLog(stackId, Status.UPDATE_IN_PROGRESS.name(), STACK_VERTICALSCALING);
    }

    public void finishVerticalScaleReplace(Long stackId, Long clusterId, String user, String password) {
        Cluster cluster = clusterService.getById(clusterId);
        cluster.setUserName(user);
        cluster.setPassword(password);
        finishVerticalScaleChange(stackId, cluster);
    }

    public void finishVerticalScaleUpdate(Long stackId, Long clusterId, String password) {
        Cluster cluster = clusterService.getById(clusterId);
        cluster.setPassword(password);
        finishVerticalScaleChange(stackId, cluster);
    }

    private void finishVerticalScaleChange(Long stackId, Cluster cluster) {
        clusterService.updateCluster(cluster);
        clusterService.updateClusterStatusByStackId(stackId, DetailedStackStatus.AVAILABLE);
        flowMessageService.fireEventAndLog(stackId, Status.AVAILABLE.name(), STACK_VERTICALSCALED);
    }
}
