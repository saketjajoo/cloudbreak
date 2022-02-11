package com.sequenceiq.datalake.flow.freeipa.upscale.event;

import com.sequenceiq.datalake.flow.SdxEvent;
import com.sequenceiq.datalake.flow.SdxFailedEvent;

public class FreeIpaUpscaleFailedEvent  extends SdxFailedEvent {

    public FreeIpaUpscaleFailedEvent(Long sdxId, String userId, Exception exception) {
        super(sdxId, userId, exception);
    }

    public static FreeIpaUpscaleFailedEvent from(SdxEvent event, Exception exception) {
        return new FreeIpaUpscaleFailedEvent(event.getResourceId(), event.getUserId(), exception);
    }
}
