package com.sequenceiq.datalake.flow.freeipa.upscale.event;

import com.sequenceiq.cloudbreak.common.event.AcceptResult;
import com.sequenceiq.datalake.flow.SdxEvent;

import reactor.rx.Promise;

public class FreeIpaUpscaleStartEvent extends SdxEvent {
    private final String envCrn;

    private String operationId;

    public FreeIpaUpscaleStartEvent(Long sdxId, String userId, String envCrn) {
        super(sdxId, userId);
        this.envCrn = envCrn;
    }

    public FreeIpaUpscaleStartEvent(String selector, Long sdxId, String userId, String envCrn, Promise<AcceptResult> accepted) {
        super(selector, sdxId, userId, accepted);
        this.envCrn = envCrn;
    }

    public String getEnvCrn() {
        return envCrn;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }
}
