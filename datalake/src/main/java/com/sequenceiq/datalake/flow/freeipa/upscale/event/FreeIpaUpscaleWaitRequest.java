package com.sequenceiq.datalake.flow.freeipa.upscale.event;

import com.sequenceiq.datalake.flow.SdxContext;
import com.sequenceiq.datalake.flow.SdxEvent;

public class FreeIpaUpscaleWaitRequest extends SdxEvent {
    private final String envCrn;

    private final String operationId;

    public FreeIpaUpscaleWaitRequest(Long sdxId, String userId, String envCrn, String operationId) {

        super(sdxId, userId);
        this.envCrn = envCrn;
        this.operationId = operationId;
    }

    public static FreeIpaUpscaleWaitRequest from(SdxContext context, FreeIpaUpscaleStartEvent payload) {
        return new FreeIpaUpscaleWaitRequest(context.getSdxId(), context.getUserId(), payload.getEnvCrn(), payload.getOperationId());
    }

    public String getEnvCrn() {
        return envCrn;
    }

    public String getOperationId() {
        return operationId;
    }
}
