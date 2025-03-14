package com.sequenceiq.datalake.flow.delete;

import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.RDS_WAIT_SUCCESS_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_DELETE_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_DELETE_FAILED_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_DELETE_FINALIZED_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_STACK_DELETION_IN_PROGRESS_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_STACK_DELETION_SUCCESS_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.STORAGE_CONSUMPTION_COLLECTION_UNSCHEDULING_SUCCESS_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.FINAL_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.INIT_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.SDX_DELETION_FAILED_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.SDX_DELETION_FINISHED_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.SDX_DELETION_START_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.SDX_DELETION_STORAGE_CONSUMPTION_COLLECTION_UNSCHEDULING_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.SDX_DELETION_WAIT_RDS_STATE;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteState.SDX_STACK_DELETION_IN_PROGRESS_STATE;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sequenceiq.flow.core.config.AbstractFlowConfiguration;
import com.sequenceiq.flow.core.config.RetryableFlowConfiguration;

@Component
public class SdxDeleteFlowConfig extends AbstractFlowConfiguration<SdxDeleteState, SdxDeleteEvent> implements RetryableFlowConfiguration<SdxDeleteEvent> {

    private static final List<Transition<SdxDeleteState, SdxDeleteEvent>> TRANSITIONS =
            new Transition.Builder<SdxDeleteState, SdxDeleteEvent>()
                .defaultFailureEvent(SDX_DELETE_FAILED_EVENT)

                .from(INIT_STATE)
                .to(SDX_DELETION_START_STATE)
                .event(SDX_DELETE_EVENT)
                .noFailureEvent()

                .from(SDX_DELETION_START_STATE)
                .to(SDX_STACK_DELETION_IN_PROGRESS_STATE)
                .event(SDX_STACK_DELETION_IN_PROGRESS_EVENT)
                .defaultFailureEvent()

                .from(SDX_STACK_DELETION_IN_PROGRESS_STATE)
                .to(SDX_DELETION_STORAGE_CONSUMPTION_COLLECTION_UNSCHEDULING_STATE)
                .event(SDX_STACK_DELETION_SUCCESS_EVENT)
                .defaultFailureEvent()

                .from(SDX_DELETION_STORAGE_CONSUMPTION_COLLECTION_UNSCHEDULING_STATE)
                .to(SDX_DELETION_WAIT_RDS_STATE)
                .event(STORAGE_CONSUMPTION_COLLECTION_UNSCHEDULING_SUCCESS_EVENT)
                .defaultFailureEvent()

                .from(SDX_DELETION_WAIT_RDS_STATE)
                .to(SDX_DELETION_FINISHED_STATE)
                .event(RDS_WAIT_SUCCESS_EVENT)
                .defaultFailureEvent()

                .from(SDX_DELETION_FINISHED_STATE)
                .to(FINAL_STATE)
                .event(SDX_DELETE_FINALIZED_EVENT)
                .defaultFailureEvent()

            .build();

    private static final FlowEdgeConfig<SdxDeleteState, SdxDeleteEvent> EDGE_CONFIG =
            new FlowEdgeConfig<>(INIT_STATE, FINAL_STATE, SDX_DELETION_FAILED_STATE, SdxDeleteEvent.SDX_DELETE_FAILED_HANDLED_EVENT);

    public SdxDeleteFlowConfig() {
        super(SdxDeleteState.class, SdxDeleteEvent.class);
    }

    @Override
    public SdxDeleteEvent[] getEvents() {
        return SdxDeleteEvent.values();
    }

    @Override
    public SdxDeleteEvent[] getInitEvents() {
        return new SdxDeleteEvent[]{
                SDX_DELETE_EVENT
        };
    }

    @Override
    public String getDisplayName() {
        return "Delete SDX";
    }

    @Override
    protected List<Transition<SdxDeleteState, SdxDeleteEvent>> getTransitions() {
        return TRANSITIONS;
    }

    @Override
    protected FlowEdgeConfig<SdxDeleteState, SdxDeleteEvent> getEdgeConfig() {
        return EDGE_CONFIG;
    }

    @Override
    public SdxDeleteEvent getRetryableEvent() {
        return SdxDeleteEvent.SDX_DELETE_FAILED_HANDLED_EVENT;
    }
}
