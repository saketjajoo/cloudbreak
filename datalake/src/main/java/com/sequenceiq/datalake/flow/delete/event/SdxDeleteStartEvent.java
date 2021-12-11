package com.sequenceiq.datalake.flow.delete.event;

import com.sequenceiq.cloudbreak.common.event.AcceptResult;
import com.sequenceiq.datalake.flow.SdxEvent;

import reactor.rx.Promise;

public class SdxDeleteStartEvent extends SdxEvent {

    private final boolean forced;

    public SdxDeleteStartEvent(String selector, Long sdxId, String userId, boolean forced) {
        super(selector, sdxId, userId);
        this.forced = forced;
    }

    public SdxDeleteStartEvent(String selector, Long sdxId, String userId, boolean forced, Promise<AcceptResult> accepted) {
        super(selector, sdxId, userId, accepted);
        this.forced = forced;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    public boolean equalsEvent(SdxEvent other) {
        return isClassAndEqualsEvent(SdxDeleteStartEvent.class, other,
                event -> forced == event.forced);
    }
}
