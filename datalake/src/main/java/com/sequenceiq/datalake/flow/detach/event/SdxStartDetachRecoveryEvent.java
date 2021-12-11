package com.sequenceiq.datalake.flow.detach.event;

import java.util.Objects;

import com.sequenceiq.cloudbreak.common.event.AcceptResult;
import com.sequenceiq.datalake.flow.SdxEvent;
import com.sequenceiq.datalake.flow.detach.SdxDetachEvent;

import reactor.rx.Promise;

public class SdxStartDetachRecoveryEvent extends SdxEvent {
    private final Exception exception;

    public SdxStartDetachRecoveryEvent(String selector, Long detachedSdxId, String userId) {
        super(selector, detachedSdxId, userId);
        exception = null;
    }

    public SdxStartDetachRecoveryEvent(String selector, Long detachedSdxId, String userId, Promise<AcceptResult> accepted) {
        super(selector, detachedSdxId, userId, accepted);
        exception = null;
    }

    public SdxStartDetachRecoveryEvent(Long detachedSdxId, String userId, Exception exception) {
        super(SdxDetachEvent.SDX_DETACH_RECOVERY_EVENT.event(), detachedSdxId, userId);
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public boolean isFailureEvent() {
        return exception != null;
    }

    @Override
    public String selector() {
        return SdxDetachEvent.SDX_DETACH_RECOVERY_EVENT.selector();
    }

    @Override
    public boolean equalsEvent(SdxEvent other) {
        return isClassAndEqualsEvent(SdxStartDetachRecoveryEvent.class, other,
                event -> Objects.equals(event.getResourceId(), other.getResourceId()));
    }
}
