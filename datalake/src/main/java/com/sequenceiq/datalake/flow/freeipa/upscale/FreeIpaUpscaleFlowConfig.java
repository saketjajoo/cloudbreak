package com.sequenceiq.datalake.flow.freeipa.upscale;


import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_FAILED_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_FAILED_HANDLED_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_FINALIZED_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_IN_PROGRESS_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_SKIPPED_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_START_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_SUCCESS_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleState.FINAL_STATE;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleState.FREEIPA_UPSCALE_FAILED_STATE;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleState.FREEIPA_UPSCALE_FINISHED_STATE;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleState.FREEIPA_UPSCALE_IN_PROGRESS_STATE;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleState.FREEIPA_UPSCALE_START_STATE;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleState.INIT_STATE;

import java.util.List;

import com.sequenceiq.flow.core.config.AbstractFlowConfiguration;
import com.sequenceiq.flow.core.config.RetryableFlowConfiguration;

public class FreeIpaUpscaleFlowConfig extends AbstractFlowConfiguration<FreeIpaUpscaleState, FreeIpaUpscaleEvent>
        implements RetryableFlowConfiguration<FreeIpaUpscaleEvent> {

    private static final FlowEdgeConfig<FreeIpaUpscaleState, FreeIpaUpscaleEvent> EDGE_CONFIG =
            new FlowEdgeConfig<>(INIT_STATE, FINAL_STATE, FREEIPA_UPSCALE_FAILED_STATE, FREEIPA_UPSCALE_FAILED_HANDLED_EVENT);

    private final List<Transition<FreeIpaUpscaleState, FreeIpaUpscaleEvent>> transitions =
            new Transition.Builder<FreeIpaUpscaleState, FreeIpaUpscaleEvent>()
                    .defaultFailureEvent(FREEIPA_UPSCALE_FAILED_EVENT)

                    .from(INIT_STATE)
                    .to(FREEIPA_UPSCALE_START_STATE)
                    .event(FREEIPA_UPSCALE_START_EVENT)
                    .noFailureEvent()

                    .from(FREEIPA_UPSCALE_START_STATE)
                    .to(FREEIPA_UPSCALE_IN_PROGRESS_STATE)
                    .event(FREEIPA_UPSCALE_IN_PROGRESS_EVENT)
                    .defaultFailureEvent()

                    .from(FREEIPA_UPSCALE_START_STATE)
                    .to(FREEIPA_UPSCALE_FINISHED_STATE)
                    .event(FREEIPA_UPSCALE_SKIPPED_EVENT)
                    .noFailureEvent()

                    .from(FREEIPA_UPSCALE_IN_PROGRESS_STATE)
                    .to(FREEIPA_UPSCALE_FINISHED_STATE)
                    .event(FREEIPA_UPSCALE_SUCCESS_EVENT)
                    .defaultFailureEvent()

                    .from(FREEIPA_UPSCALE_FINISHED_STATE)
                    .to(FINAL_STATE)
                    .event(FREEIPA_UPSCALE_FINALIZED_EVENT)
                    .defaultFailureEvent()

                    .build();

    public FreeIpaUpscaleFlowConfig() {
        super(FreeIpaUpscaleState.class, FreeIpaUpscaleEvent.class);
    }

    @Override
    public FreeIpaUpscaleEvent[] getEvents() {
        return FreeIpaUpscaleEvent.values();
    }

    @Override
    public FreeIpaUpscaleEvent[] getInitEvents() {
        return new FreeIpaUpscaleEvent[]{
                FREEIPA_UPSCALE_START_EVENT
        };
    }

    @Override
    public String getDisplayName() {
        return "FreeIpa Upscale";
    }

    @Override
    protected List<Transition<FreeIpaUpscaleState, FreeIpaUpscaleEvent>> getTransitions() {
        return transitions;
    }

    @Override
    protected FlowEdgeConfig<FreeIpaUpscaleState, FreeIpaUpscaleEvent> getEdgeConfig() {
        return EDGE_CONFIG;
    }

    @Override
    public FreeIpaUpscaleEvent getRetryableEvent() {
        return FREEIPA_UPSCALE_FAILED_HANDLED_EVENT;
    }

}
