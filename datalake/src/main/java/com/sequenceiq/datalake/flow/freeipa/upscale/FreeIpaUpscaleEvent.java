package com.sequenceiq.datalake.flow.freeipa.upscale;

import com.sequenceiq.flow.core.FlowEvent;

public enum FreeIpaUpscaleEvent implements FlowEvent {
    FREEIPA_UPSCALE_START_EVENT("FREEIPAUPSCALESTARTEVENT"),
    FREEIPA_UPSCALE_IN_PROGRESS_EVENT,
    FREEIPA_UPSCALE_SUCCESS_EVENT("FREEIPAUPSCALESUCCESSEVENT"),
    FREEIPA_UPSCALE_FAILED_EVENT("FREEIPAUPSCALEFAILEDEVENT"),
    FREEIPA_UPSCALE_SKIPPED_EVENT,
    FREEIPA_UPSCALE_FAILED_HANDLED_EVENT,
    FREEIPA_UPSCALE_FINALIZED_EVENT;

    private final String event;

    FreeIpaUpscaleEvent(String event) {
        this.event = event;
    }

    FreeIpaUpscaleEvent() {
        this.event = name();
    }

    @Override
    public String event() {
        return event;
    }
}
