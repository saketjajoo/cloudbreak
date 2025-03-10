package com.sequenceiq.flow.rotation.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sequenceiq.cloudbreak.rotation.secret.RotationFlowExecutionType;
import com.sequenceiq.cloudbreak.rotation.secret.SecretType;
import com.sequenceiq.flow.event.EventSelectorUtil;

public class FinalizeRotationTriggerEvent extends RotationEvent {

    @JsonCreator
    public FinalizeRotationTriggerEvent(@JsonProperty("selector") String selector,
            @JsonProperty("resourceId") Long resourceId,
            @JsonProperty("resourceCrn") String resourceCrn,
            @JsonProperty("secretType") SecretType secretType,
            @JsonProperty("executionType") RotationFlowExecutionType executionType) {
        super(selector, resourceId, resourceCrn, secretType, executionType);
    }

    public static FinalizeRotationTriggerEvent fromPayload(RotationEvent payload) {
        return new FinalizeRotationTriggerEvent(EventSelectorUtil.selector(FinalizeRotationTriggerEvent.class),
                payload.getResourceId(), payload.getResourceCrn(), payload.getSecretType(), payload.getExecutionType());
    }
}
