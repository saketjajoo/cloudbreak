package com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_FAILED;
import static com.sequenceiq.cloudbreak.cloud.model.AvailabilityZone.availabilityZone;
import static com.sequenceiq.cloudbreak.cloud.model.Location.location;
import static com.sequenceiq.cloudbreak.cloud.model.Region.region;
import static com.sequenceiq.cloudbreak.event.ResourceEvent.STACK_VERTICALSCALED_FAILED;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.Location;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.converter.spi.StackToCloudStackConverter;
import com.sequenceiq.cloudbreak.core.flow2.cluster.AbstractClusterAction;
import com.sequenceiq.cloudbreak.core.flow2.cluster.ClusterViewContext;
import com.sequenceiq.cloudbreak.core.flow2.event.StackVerticalScalingTriggerEvent;
import com.sequenceiq.cloudbreak.core.flow2.stack.AbstractStackFailureAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackFailureContext;
import com.sequenceiq.cloudbreak.domain.Resource;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.StackVerticalScaleRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.StackVerticalScaleResult;
import com.sequenceiq.cloudbreak.service.resource.ResourceService;
import com.sequenceiq.cloudbreak.service.stack.StackService;
import com.sequenceiq.cloudbreak.util.StackUtil;

@Configuration
public class ClusterVerticalScaleActions {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterVerticalScaleActions.class);

    //@Inject
    //private ClusterVerticalScaleService clusterVerticalScaleService;

    @Inject
    private CloudbreakFlowMessageService flowMessageService;

    @Inject
    private StackUtil stackUtil;

    @Inject
    private StackToCloudStackConverter cloudStackConverter;

    @Inject
    private StackService stackService;

    @Inject
    private ResourceService resourceService;

    @Bean(name = "CLUSTER_VERTICALSCALE_STATE")
    public Action<?, ?> stackVerticalScale() {
        return new AbstractClusterAction<>(StackVerticalScalingTriggerEvent.class) {
            @Override
            protected void doExecute(ClusterViewContext ctx, StackVerticalScalingTriggerEvent payload, Map<Object, Object> variables) {
                Stack stack = stackService.getByIdWithListsInTransaction(payload.getResourceId());
                List<Resource> resources = (List<Resource>) resourceService.getAllByStackId(payload.getResourceId());
                CloudCredential cloudCredential = stackUtil.getCloudCredential(stack);
                CloudStack cloudStack = cloudStackConverter.convert(stack);
                Location location = location(region(stack.getRegion()), availabilityZone(stack.getAvailabilityZone()));
                CloudContext cloudContext = CloudContext.Builder.builder()
                        .withId(stack.getId())
                        .withName(stack.getName())
                        .withCrn(stack.getResourceCrn())
                        .withPlatform(stack.cloudPlatform())
                        .withVariant(stack.getPlatformVariant())
                        .withLocation(location)
                        .withWorkspaceId(stack.getWorkspace().getId())
                        .withAccountId(stack.getTenant().getId())
                        .build();

                StackVerticalScaleRequest request = new StackVerticalScaleRequest(cloudContext, cloudCredential, cloudStack, resources);
                sendEvent(ctx, request);
            }
        };
    }

    @Bean(name = "CLUSTER_VERTICALSCALE_FINISHED_STATE")
    public Action<?, ?> stackVerticalScaleFinished() {
        return new AbstractClusterAction<>(StackVerticalScaleResult.class) {
            @Override
            protected void doExecute(ClusterViewContext context, StackVerticalScaleResult payload, Map<Object, Object> variables) {
                //clusterVerticalScaleService.finishCredentialUpdate(context.getStackId(), context.getClusterId(),
                //        payload.getRequest().getPassword());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterViewContext context) {
                return new StackEvent(ClusterVerticalScaleEvent.FINALIZED_EVENT.event(), context.getStack().getId());
            }
        };
    }

    @Bean(name = "CLUSTER_VERTICALSCALE_FAILED_STATE")
    public Action<?, ?> stackVerticalScaleFailedAction() {
        return new AbstractStackFailureAction<ClusterVerticalScaleState, ClusterVerticalScaleEvent>() {
            @Override
            protected void doExecute(StackFailureContext context, StackFailureEvent payload, Map<Object, Object> variables) {
                LOGGER.info("Exception during vertical scaling!: {}", payload.getException().getMessage());
                flowMessageService.fireEventAndLog(payload.getResourceId(), UPDATE_FAILED.name(), STACK_VERTICALSCALED_FAILED);
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(StackFailureContext context) {
                return new StackEvent(ClusterVerticalScaleEvent.FAIL_HANDLED_EVENT.event(), context.getStackView().getId());
            }
        };
    }
}
