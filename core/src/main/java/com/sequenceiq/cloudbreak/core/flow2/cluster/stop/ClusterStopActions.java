package com.sequenceiq.cloudbreak.core.flow2.cluster.stop;

import java.util.Map;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.core.flow2.cluster.AbstractClusterAction;
import com.sequenceiq.cloudbreak.core.flow2.cluster.ClusterViewContext;
import com.sequenceiq.cloudbreak.core.flow2.stack.AbstractStackFailureAction;
import com.sequenceiq.cloudbreak.core.flow2.stack.StackFailureContext;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.StackFailureEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterStopFailedRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterStopRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterStopResult;
import com.sequenceiq.cloudbreak.service.metrics.MetricType;
import com.sequenceiq.cloudbreak.service.stack.StackDtoService;
import com.sequenceiq.cloudbreak.view.StackView;
import com.sequenceiq.flow.core.FlowParameters;

@Configuration
public class ClusterStopActions {

    @Inject
    private ClusterStopService clusterStopService;

    @Inject
    private StackDtoService stackDtoService;

    @Bean(name = "CLUSTER_STOPPING_STATE")
    public Action<?, ?> stoppingCluster() {
        return new AbstractClusterAction<>(StackEvent.class) {
            @Override
            protected void doExecute(ClusterViewContext context, StackEvent payload, Map<Object, Object> variables) {
                clusterStopService.stoppingCluster(context.getStackId());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterViewContext context) {
                return new ClusterStopRequest(context.getStackId());
            }
        };
    }

    @Bean(name = "CLUSTER_STOP_FINISHED_STATE")
    public Action<?, ?> clusterStopFinished() {
        return new AbstractClusterAction<>(ClusterStopResult.class) {
            @Override
            protected void doExecute(ClusterViewContext context, ClusterStopResult payload, Map<Object, Object> variables) {
                clusterStopService.clusterStopFinished(context.getStackId());
                getMetricService().incrementMetricCounter(MetricType.CLUSTER_STOP_SUCCESSFUL, context.getStack());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(ClusterViewContext context) {
                return new StackEvent(ClusterStopEvent.FINALIZED_EVENT.event(), context.getStackId());
            }
        };
    }

    @Bean(name = "CLUSTER_STOP_FAILED_STATE")
    public Action<?, ?> clusterStopFailedAction() {
        return new AbstractStackFailureAction<ClusterStopState, ClusterStopEvent>() {
            @Override
            protected void doExecute(StackFailureContext context, StackFailureEvent payload, Map<Object, Object> variables) {
                clusterStopService.handleClusterStopFailureAndContinue(context.getStackId(), payload.getException().getMessage());
                getMetricService().incrementMetricCounter(MetricType.CLUSTER_STOP_FAILED, context.getStack(), payload.getException());
                sendEvent(context);
            }

            @Override
            protected Selectable createRequest(StackFailureContext context) {
                return new ClusterStopFailedRequest(context.getStackId());
            }

            @Override
            protected StackFailureContext createFlowContext(FlowParameters flowParameters, StateContext<ClusterStopState, ClusterStopEvent> stateContext,
                                                            StackFailureEvent payload) {
                StackView stack = stackDtoService.getStackViewById(payload.getResourceId());
                MDCBuilder.buildMdcContext(stack);
                return new StackFailureContext(flowParameters, stack, stack.getId());
            }
        };
    }
}
