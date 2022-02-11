package com.sequenceiq.datalake.flow.freeipa.upscale.event;

import com.sequenceiq.datalake.flow.SdxEvent;

public class FreeIpaUpscaleSuccessEvent extends SdxEvent {

    public FreeIpaUpscaleSuccessEvent(Long sdxId, String userId) {
        super(sdxId, userId);
    }
}
