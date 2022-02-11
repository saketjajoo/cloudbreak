package com.sequenceiq.datalake.flow.freeipa.upscale.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleFailedEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleWaitRequest;
import com.sequenceiq.datalake.service.FreeipaService;
import com.sequenceiq.flow.reactor.api.handler.HandlerEvent;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationState;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationStatus;

import reactor.bus.Event;

@ExtendWith(MockitoExtension.class)

public class FreeIpaUpscaleWaitHandlerTest {
    private static final long SDX_ID = 1L;

    private static final int SLEEP_TIME_IN_SEC = 10;

    private static final int DURATION_IN_MINUTES = 1;

    private static final String USER_ID = "userId";

    private static final String ENV_CRN = "crn:cdp:environments:us-west-1:accountId:environment:4c5ba74b-c35e-45e9-9f47-123456789876";

    private static final String OPERATION_ID = "op_id";

    private static final String FAILED_EVENT = "FREEIPAUPSCALEFAILEDEVENT";

    private static final String SUCCESS_EVENT = "FREEIPAUPSCALESUCCESSEVENT";

    @Mock
    private FreeipaService freeipaService;

    @InjectMocks
    private FreeIpaUpscaleWaitHandler underTest;

    @Test
    void testSelector() {
        assertEquals("FreeIpaUpscaleWaitRequest", underTest.selector());
    }

    @Test
    void testDefaultFailureEvent() {
        RuntimeException exception = new RuntimeException();
        FreeIpaUpscaleWaitRequest freeIpaUpscaleWaitRequest = mock(FreeIpaUpscaleWaitRequest.class);
        when(freeIpaUpscaleWaitRequest.getUserId()).thenReturn(USER_ID);

        Selectable failureSelectable = underTest.defaultFailureEvent(SDX_ID, exception, new Event<>(freeIpaUpscaleWaitRequest));

        assertEquals(FAILED_EVENT, failureSelectable.selector());
        assertEquals(SDX_ID, failureSelectable.getResourceId());
        assertEquals(exception, ((FreeIpaUpscaleFailedEvent) failureSelectable).getException());
    }

    @Test
    void testAcceptWhenFreeIpaServiceReturnsFailure() {
        ReflectionTestUtils.setField(underTest, "sleepTimeInSec", SLEEP_TIME_IN_SEC);
        ReflectionTestUtils.setField(underTest, "durationInMinutes", DURATION_IN_MINUTES);
        OperationStatus status = new OperationStatus();
        status.setStatus(OperationState.FAILED);
        when(freeipaService.getFreeIpaOperation(any(), any())).thenReturn(status);

        Selectable nextEvent = underTest.doAccept(getEvent());

        assertEquals(FAILED_EVENT, nextEvent.selector());
        assertEquals(SDX_ID, nextEvent.getResourceId());
    }

    @Test
    void testExitOnNotFound() {
        ReflectionTestUtils.setField(underTest, "sleepTimeInSec", SLEEP_TIME_IN_SEC);
        ReflectionTestUtils.setField(underTest, "durationInMinutes", DURATION_IN_MINUTES);
        RuntimeException error = new RuntimeException("Critical Error");
        when(freeipaService.getFreeIpaOperation(any(), any())).thenThrow(error);
        Selectable nextEvent = underTest.doAccept(getEvent());

        assertEquals(FAILED_EVENT, nextEvent.selector());
        assertEquals(SDX_ID, nextEvent.getResourceId());

    }

    @Test
    void testAcceptWhenFreeIpaServiceReturnsSuccess() {
        ReflectionTestUtils.setField(underTest, "sleepTimeInSec", SLEEP_TIME_IN_SEC);
        ReflectionTestUtils.setField(underTest, "durationInMinutes", DURATION_IN_MINUTES);
        OperationStatus status = new OperationStatus();
        status.setStatus(OperationState.COMPLETED);
        when(freeipaService.getFreeIpaOperation(any(), any())).thenReturn(status);

        Selectable nextEvent = underTest.doAccept(getEvent());

        assertEquals(SUCCESS_EVENT, nextEvent.selector());
        assertEquals(SDX_ID, nextEvent.getResourceId());
    }

    private HandlerEvent<FreeIpaUpscaleWaitRequest> getEvent() {
        FreeIpaUpscaleWaitRequest freeIpaUpscaleWaitRequest = mock(FreeIpaUpscaleWaitRequest.class);

        when(freeIpaUpscaleWaitRequest.getResourceId()).thenReturn(SDX_ID);
        when(freeIpaUpscaleWaitRequest.getEnvCrn()).thenReturn(ENV_CRN);
        when(freeIpaUpscaleWaitRequest.getOperationId()).thenReturn(OPERATION_ID);
        return new HandlerEvent<>(new Event<>(freeIpaUpscaleWaitRequest));
    }
}
