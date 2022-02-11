package com.sequenceiq.datalake.flow.freeipa.upscale;

import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_FAILED_HANDLED_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_FINALIZED_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_IN_PROGRESS_EVENT;
import static com.sequenceiq.datalake.flow.freeipa.upscale.FreeIpaUpscaleEvent.FREEIPA_UPSCALE_SKIPPED_EVENT;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

import com.sequenceiq.cloudbreak.event.ResourceEvent;
import com.sequenceiq.datalake.entity.DatalakeStatusEnum;
import com.sequenceiq.datalake.entity.SdxCluster;
import com.sequenceiq.datalake.flow.SdxContext;
import com.sequenceiq.datalake.flow.SdxFailedEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleFailedEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleStartEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleSuccessEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleWaitRequest;
import com.sequenceiq.datalake.metric.MetricType;
import com.sequenceiq.datalake.metric.SdxMetricService;
import com.sequenceiq.datalake.service.AbstractSdxAction;
import com.sequenceiq.datalake.service.FreeipaService;
import com.sequenceiq.datalake.service.sdx.status.SdxStatusService;
import com.sequenceiq.flow.core.Flow;
import com.sequenceiq.flow.core.FlowEvent;
import com.sequenceiq.flow.core.FlowParameters;
import com.sequenceiq.flow.core.FlowState;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.AvailabilityType;

@Configuration
public class FreeIpaUpscaleActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeIpaUpscaleActions.class);

    @Inject
    private FreeipaService freeipaService;

    @Inject
    private SdxStatusService sdxStatusService;

    @Inject
    private SdxMetricService metricService;

    @Bean(name = "FREEIPA_UPSCALE_START_STATE")
    public Action<?, ?> freeIpaUpscale() {
        return new AbstractSdxAction<>(FreeIpaUpscaleStartEvent.class) {
            @Override
            protected SdxContext createFlowContext(FlowParameters flowParameters, StateContext<FlowState, FlowEvent> stateContext,
                    FreeIpaUpscaleStartEvent payload) {
                return SdxContext.from(flowParameters, payload);
            }

            @Override
            protected void doExecute(SdxContext context, FreeIpaUpscaleStartEvent payload, Map<Object, Object> variables) {

                int maxCount = AvailabilityType.HA.getInstanceCount();
                if (maxCount > freeipaService.getNodeCount(payload.getEnvCrn())) {
                    LOGGER.info("FreeIpa Upscale has been started for {}", payload.getResourceId());
                    String operationId = freeipaService.upscale(payload.getEnvCrn(), AvailabilityType.HA);
                    payload.setOperationId(operationId);
                    sdxStatusService.setStatusForDatalakeAndNotify(DatalakeStatusEnum.RUNNING,
                            ResourceEvent.DATALAKE_FREEIPA_UPSCALE_IN_PROGRESS,
                            "Datalake is running, freeIpa upscale in progress", payload.getResourceId());
                    sendEvent(context, FREEIPA_UPSCALE_IN_PROGRESS_EVENT.event(), payload);
                } else {
                    LOGGER.info("FreeIpa Upscale being skipped for {}. Already has {} instances", payload.getResourceId(), maxCount);
                    sendEvent(context, FREEIPA_UPSCALE_SKIPPED_EVENT.event(), payload);
                }
            }

            @Override
            protected Object getFailurePayload(FreeIpaUpscaleStartEvent payload, Optional<SdxContext> flowContext, Exception ex) {
                return FreeIpaUpscaleFailedEvent.from(payload, ex);
            }
        };
    }

    @Bean(name = "FREEIPA_UPSCALE_IN_PROGRESS_STATE")
    public Action<?, ?> freeIpaUpscaleInProgress() {
        return new AbstractSdxAction<>(FreeIpaUpscaleStartEvent.class) {

            @Override
            protected SdxContext createFlowContext(FlowParameters flowParameters, StateContext<FlowState, FlowEvent> stateContext,
                    FreeIpaUpscaleStartEvent payload) {
                return SdxContext.from(flowParameters, payload);
            }

            @Override
            protected void doExecute(SdxContext context, FreeIpaUpscaleStartEvent payload, Map<Object, Object> variables) {
                LOGGER.info("FreeIpa Upscale in progress: {}", payload.getResourceId());
                sendEvent(context, FreeIpaUpscaleWaitRequest.from(context, payload));
            }

            @Override
            protected Object getFailurePayload(FreeIpaUpscaleStartEvent payload, Optional<SdxContext> flowContext, Exception ex) {
                return FreeIpaUpscaleFailedEvent.from(payload, ex);
            }
        };
    }

    @Bean(name = "FREEIPA_UPSCALE_FINISHED_STATE")
    public Action<?, ?> finishedAction() {
        return new AbstractSdxAction<>(FreeIpaUpscaleSuccessEvent.class) {
            @Override
            protected SdxContext createFlowContext(FlowParameters flowParameters, StateContext<FlowState, FlowEvent> stateContext,
                    FreeIpaUpscaleSuccessEvent payload) {
                return SdxContext.from(flowParameters, payload);
            }

            @Override
            protected void doExecute(SdxContext context, FreeIpaUpscaleSuccessEvent payload, Map<Object, Object> variables) {
                Long datalakeId = payload.getResourceId();
                LOGGER.info("FreeIpa Upscale finalized: {}", datalakeId);
                SdxCluster sdxCluster = sdxStatusService.setStatusForDatalakeAndNotify(DatalakeStatusEnum.RUNNING,
                        ResourceEvent.DATALAKE_FREEIPA_UPSCALE_FINISHED,
                        "Datalake is running, freeIpa upscale finished", payload.getResourceId());
                metricService.incrementMetricCounter(MetricType.UPSCALE_FREEIPA_FINISHED, sdxCluster);
                sendEvent(context, FREEIPA_UPSCALE_FINALIZED_EVENT.event(), payload);
            }

            @Override
            protected Object getFailurePayload(FreeIpaUpscaleSuccessEvent payload, Optional<SdxContext> flowContext, Exception ex) {
                return null;
            }
        };
    }

    @Bean(name = "FREEIPA_UPSCALE_FAILED_STATE")
    public Action<?, ?> failedAction() {
        return new AbstractSdxAction<>(SdxFailedEvent.class) {
            @Override
            protected SdxContext createFlowContext(FlowParameters flowParameters, StateContext<FlowState, FlowEvent> stateContext,
                    SdxFailedEvent payload) {
                return SdxContext.from(flowParameters, payload);
            }

            @Override
            protected void doExecute(SdxContext context, SdxFailedEvent payload, Map<Object, Object> variables) {
                Exception exception = payload.getException();
                LOGGER.error("FreeIpa failed to upscale {}", payload.getResourceId(), exception);
                Flow flow = getFlow(context.getFlowParameters().getFlowId());
                flow.setFlowFailed(payload.getException());
                SdxCluster sdxCluster = sdxStatusService.setStatusForDatalakeAndNotify(DatalakeStatusEnum.RUNNING,
                        ResourceEvent.DATALAKE_FREEIPA_UPSCALE_FAILED,
                        "Datalake is running, freeIpa upscale failed", payload.getResourceId());
                metricService.incrementMetricCounter(MetricType.UPSCALE_FREEIPA_FAILED, sdxCluster);

                sendEvent(context, FREEIPA_UPSCALE_FAILED_HANDLED_EVENT.event(), payload);
            }

            @Override
            protected Object getFailurePayload(SdxFailedEvent payload, Optional<SdxContext> flowContext, Exception ex) {
                return null;
            }
        };
    }

}
