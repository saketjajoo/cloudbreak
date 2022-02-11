package com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale;

import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleEvent.FAILURE_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleEvent.FAIL_HANDLED_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleEvent.FINALIZED_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleEvent.STACK_VERTICALSCALE_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleEvent.STACK_VERTICALSCALE_FINISHED_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleEvent.STACK_VERTICALSCALE_FINISHED_FAILURE_EVENT;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleState.FINAL_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleState.INIT_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleState.STACK_VERTICALSCALE_FAILED_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleState.STACK_VERTICALSCALE_FINISHED_STATE;
import static com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale.ClusterVerticalScaleState.STACK_VERTICALSCALE_STATE;

import java.util.List;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.core.flow2.StackStatusFinalizer;
import com.sequenceiq.flow.core.config.AbstractFlowConfiguration;
import com.sequenceiq.flow.core.config.AbstractFlowConfiguration.Transition.Builder;
import com.sequenceiq.flow.core.config.FlowFinalizerCallback;

@Component
public class ClusterVerticalScaleFlowConfig extends AbstractFlowConfiguration<ClusterVerticalScaleState, ClusterVerticalScaleEvent> {
    private static final List<Transition<ClusterVerticalScaleState, ClusterVerticalScaleEvent>> TRANSITIONS =
            new Builder<ClusterVerticalScaleState, ClusterVerticalScaleEvent>()
                    .from(INIT_STATE)
                    .to(STACK_VERTICALSCALE_STATE)
                    .event(STACK_VERTICALSCALE_EVENT)
                        .noFailureEvent()
                    .from(STACK_VERTICALSCALE_STATE)
                    .to(STACK_VERTICALSCALE_FINISHED_STATE)
                    .event(STACK_VERTICALSCALE_FINISHED_EVENT)
                            .failureEvent(STACK_VERTICALSCALE_FINISHED_FAILURE_EVENT)
                    .from(STACK_VERTICALSCALE_FINISHED_STATE)
                    .to(FINAL_STATE)
                    .event(FINALIZED_EVENT)
                        .failureEvent(FAILURE_EVENT)
                    .build();

    private static final FlowEdgeConfig<ClusterVerticalScaleState, ClusterVerticalScaleEvent> EDGE_CONFIG = new FlowEdgeConfig<>(
            INIT_STATE,
            FINAL_STATE,
            STACK_VERTICALSCALE_FAILED_STATE,
            FAIL_HANDLED_EVENT);

    @Inject
    private StackStatusFinalizer stackStatusFinalizer;

    public ClusterVerticalScaleFlowConfig() {
        super(ClusterVerticalScaleState.class, ClusterVerticalScaleEvent.class);
    }

    @Override
    protected List<Transition<ClusterVerticalScaleState, ClusterVerticalScaleEvent>> getTransitions() {
        return TRANSITIONS;
    }

    @Override
    protected FlowEdgeConfig<ClusterVerticalScaleState, ClusterVerticalScaleEvent> getEdgeConfig() {
        return EDGE_CONFIG;
    }

    @Override
    public ClusterVerticalScaleEvent[] getEvents() {
        return ClusterVerticalScaleEvent.values();
    }

    @Override
    public ClusterVerticalScaleEvent[] getInitEvents() {
        return new ClusterVerticalScaleEvent[] {
                STACK_VERTICALSCALE_EVENT
        };
    }

    @Override
    public String getDisplayName() {
        return "Vertical scaling on the stack";
    }

    @Override
    public FlowFinalizerCallback getFinalizerCallBack() {
        return stackStatusFinalizer;
    }
}
