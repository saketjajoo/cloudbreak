package com.sequenceiq.cloudbreak.core.flow2.event;

import com.sequenceiq.cloudbreak.reactor.api.event.StackEvent;

public class StackVerticalScalingTriggerEvent extends StackEvent {

    public StackVerticalScalingTriggerEvent(String selector, Long stackId) {
        super(selector, stackId);
    }

}
