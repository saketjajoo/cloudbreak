package com.sequenceiq.cloudbreak.core.flow2.cluster.verticalscale;

import com.sequenceiq.cloudbreak.reactor.api.event.resource.StackVerticalScaleRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.StackVerticalScaleResult;
import com.sequenceiq.flow.core.FlowEvent;
import com.sequenceiq.flow.event.EventSelectorUtil;

public enum ClusterVerticalScaleEvent implements FlowEvent {
    STACK_VERTICALSCALE_EVENT("STACK_VERTICAL_SCALE_TRIGGER_EVENT"),
    STACK_VERTICALSCALE_FINISHED_EVENT(EventSelectorUtil.selector(StackVerticalScaleResult.class)),
    STACK_VERTICALSCALE_FINISHED_FAILURE_EVENT(EventSelectorUtil.failureSelector(StackVerticalScaleRequest.class)),

    FINALIZED_EVENT("STACKVERTICALSCALEFINALIZEDEVENT"),
    FAILURE_EVENT("STACKVERTICALSCALEFAILUREEVENT"),
    FAIL_HANDLED_EVENT("STACKVERTICALSCALEFAILHANDLEDEVENT");

    private final String event;

    ClusterVerticalScaleEvent(String event) {
        this.event = event;
    }

    @Override
    public String event() {
        return event;
    }
}
