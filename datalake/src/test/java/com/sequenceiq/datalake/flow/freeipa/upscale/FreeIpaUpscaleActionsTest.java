package com.sequenceiq.datalake.flow.freeipa.upscale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.statemachine.action.Action;
import org.springframework.test.util.ReflectionTestUtils;

import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.flow.SdxContext;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleStartEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleWaitRequest;
import com.sequenceiq.datalake.service.FreeipaService;
import com.sequenceiq.datalake.service.sdx.status.SdxStatusService;
import com.sequenceiq.flow.core.AbstractAction;
import com.sequenceiq.flow.core.AbstractActionTestSupport;
import com.sequenceiq.flow.core.FlowParameters;
import com.sequenceiq.flow.core.FlowRegister;
import com.sequenceiq.flow.reactor.ErrorHandlerAwareReactorEventFactory;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.AvailabilityType;
import com.sequenceiq.sdx.api.model.SdxClusterShape;

import reactor.bus.Event;
import reactor.bus.EventBus;

@ExtendWith(MockitoExtension.class)
public class FreeIpaUpscaleActionsTest {

    private static final Long SDX_ID = 2L;

    private static final String FLOW_ID = "flow_id";

    private static final String USER_CRN = "crn:cdp:iam:us-west-1:1234:user:1";

    private static final String ENV_CRN = "crn:cdp:environments:us-west-1:accountId:environment:4c5ba74b-c35e-45e9-9f47-123456789876";

    private static final String OPERATION_ID = "op_id";

    private static final String DATALAKE_NAME = "test_dl";

    @InjectMocks
    private final FreeIpaUpscaleActions underTest = new FreeIpaUpscaleActions();

    @Mock
    private FreeipaService freeipaService;

    @Mock
    private FlowRegister runningFlows;

    @Mock
    private EventBus eventBus;

    @Mock
    private ErrorHandlerAwareReactorEventFactory reactorEventFactory;

    @Mock
    private SdxStatusService sdxStatusService;

    @Test
    public void testGetOpIdfromService() throws Exception {
        when(freeipaService.upscale(eq(ENV_CRN), eq(AvailabilityType.HA))).thenReturn(OPERATION_ID);
        FreeIpaUpscaleStartEvent event = new FreeIpaUpscaleStartEvent(SDX_ID, USER_CRN, ENV_CRN);
        AbstractAction action = (AbstractAction) underTest.freeIpaUpscale();
        initActionPrivateFields(action);
        AbstractActionTestSupport testSupport = new AbstractActionTestSupport(action);
        SdxContext context = SdxContext.from(new FlowParameters(FLOW_ID, FLOW_ID, null), event);
        testSupport.doExecute(context, event, new HashMap());

        ArgumentCaptor<FreeIpaUpscaleStartEvent> captor = ArgumentCaptor.forClass(FreeIpaUpscaleStartEvent.class);

        verify(reactorEventFactory, times(1)).createEvent(any(), captor.capture());
        FreeIpaUpscaleStartEvent captorValue = captor.getValue();
        Assertions.assertEquals(SDX_ID, captorValue.getResourceId());
        Assertions.assertEquals(ENV_CRN, captorValue.getEnvCrn());
        Assertions.assertEquals(OPERATION_ID, captorValue.getOperationId());
    }

    @Test
    public void testPassOpIdtoWaiter() throws Exception {
        FreeIpaUpscaleStartEvent event = new FreeIpaUpscaleStartEvent(SDX_ID, USER_CRN, ENV_CRN);
        event.setOperationId(OPERATION_ID);
        AbstractAction action = (AbstractAction) underTest.freeIpaUpscaleInProgress();
        initActionPrivateFields(action);
        AbstractActionTestSupport testSupport = new AbstractActionTestSupport(action);
        SdxContext context = SdxContext.from(new FlowParameters(FLOW_ID, FLOW_ID, null), event);
        testSupport.doExecute(context, event, new HashMap());

        ArgumentCaptor<FreeIpaUpscaleWaitRequest> captor = ArgumentCaptor.forClass(FreeIpaUpscaleWaitRequest.class);

        verify(reactorEventFactory, times(1)).createEvent(any(), captor.capture());
        FreeIpaUpscaleWaitRequest captorValue = captor.getValue();
        Assertions.assertEquals(SDX_ID, captorValue.getResourceId());
        Assertions.assertEquals(ENV_CRN, captorValue.getEnvCrn());
        Assertions.assertEquals(OPERATION_ID, captorValue.getOperationId());
    }

    @Test
    public void testAlreadyHaSkips() throws Exception {
        when(freeipaService.getNodeCount(eq(ENV_CRN))).thenReturn((long) AvailabilityType.HA.getInstanceCount());
        FreeIpaUpscaleStartEvent event = new FreeIpaUpscaleStartEvent(SDX_ID, USER_CRN, ENV_CRN);
        AbstractAction action = (AbstractAction) underTest.freeIpaUpscale();
        initActionPrivateFields(action);
        AbstractActionTestSupport testSupport = new AbstractActionTestSupport(action);
        SdxContext context = SdxContext.from(new FlowParameters(FLOW_ID, FLOW_ID, null), event);
        testSupport.doExecute(context, event, new HashMap());

        ArgumentCaptor<FreeIpaUpscaleStartEvent> captor = ArgumentCaptor.forClass(FreeIpaUpscaleStartEvent.class);
        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

        verify(eventBus, times(1)).notify(stringCaptor.capture(), (Event<?>) isNull());
        verify(reactorEventFactory, times(1)).createEvent(any(), captor.capture());
        FreeIpaUpscaleStartEvent captorValue = captor.getValue();
        Assertions.assertEquals(SDX_ID, captorValue.getResourceId());
        Assertions.assertEquals(ENV_CRN, captorValue.getEnvCrn());
    }

    private void initActionPrivateFields(Action<?, ?> action) {
        ReflectionTestUtils.setField(action, null, runningFlows, FlowRegister.class);
        ReflectionTestUtils.setField(action, null, eventBus, EventBus.class);
        ReflectionTestUtils.setField(action, null, reactorEventFactory, ErrorHandlerAwareReactorEventFactory.class);
    }

    private SdxCluster genCluster() {
        SdxCluster sdxCluster = new SdxCluster();
        sdxCluster.setId(SDX_ID);
        sdxCluster.setClusterShape(SdxClusterShape.LIGHT_DUTY);
        sdxCluster.setEnvName("env");
        sdxCluster.setEnvCrn("crn");
        sdxCluster.setClusterName(DATALAKE_NAME);
        return sdxCluster;
    }

}
