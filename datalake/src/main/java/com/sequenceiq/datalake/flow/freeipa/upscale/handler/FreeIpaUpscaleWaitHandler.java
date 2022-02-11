package com.sequenceiq.datalake.flow.freeipa.upscale.handler;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dyngr.Polling;
import com.dyngr.core.AttemptResults;
import com.dyngr.exception.PollerException;
import com.dyngr.exception.PollerStoppedException;
import com.dyngr.exception.UserBreakException;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.crn.Crn;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.common.json.JsonUtil;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleFailedEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleSuccessEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleWaitRequest;
import com.sequenceiq.datalake.service.FreeipaService;
import com.sequenceiq.datalake.service.sdx.PollingConfig;
import com.sequenceiq.flow.reactor.api.handler.ExceptionCatcherEventHandler;
import com.sequenceiq.flow.reactor.api.handler.HandlerEvent;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationState;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationStatus;

import reactor.bus.Event;

@Component
public class FreeIpaUpscaleWaitHandler extends ExceptionCatcherEventHandler<FreeIpaUpscaleWaitRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeIpaUpscaleWaitHandler.class);

    @Value("${sdx.freeipa.upscale.sleeptime_sec:10}")
    private int sleepTimeInSec;

    @Value("${sdx.freeipa.upscale.duration_min:60}")
    private int durationInMinutes;

    @Inject
    private FreeipaService freeipaService;

    @Override
    public String selector() {
        return "FreeIpaUpscaleWaitRequest";
    }

    @Override
    protected Selectable defaultFailureEvent(Long resourceId, Exception e, Event<FreeIpaUpscaleWaitRequest> event) {
        return new FreeIpaUpscaleFailedEvent(resourceId, event.getData().getUserId(), e);
    }

    @Override
    protected Selectable doAccept(HandlerEvent<FreeIpaUpscaleWaitRequest> event) {

        FreeIpaUpscaleWaitRequest freeIpaUpscaleWaitRequest = event.getData();
        Long sdxId = freeIpaUpscaleWaitRequest.getResourceId();
        String userId = freeIpaUpscaleWaitRequest.getUserId();
        Selectable response;
        try {
            LOGGER.debug("Start polling freeIpa upscale process for id: {}", sdxId);
            PollingConfig pollingConfig = new PollingConfig(sleepTimeInSec, TimeUnit.SECONDS, durationInMinutes, TimeUnit.MINUTES);
            waitFreeIpaUpscale(freeIpaUpscaleWaitRequest.getEnvCrn(), freeIpaUpscaleWaitRequest.getOperationId(), pollingConfig);
            response = new FreeIpaUpscaleSuccessEvent(sdxId, userId);
        } catch (UserBreakException userBreakException) {
            LOGGER.error("Upscale polling exited before timeout. Cause: ", userBreakException);
            response = new FreeIpaUpscaleFailedEvent(sdxId, userId, userBreakException);
        } catch (PollerStoppedException pollerStoppedException) {
            LOGGER.error("Upscale poller stopped for stack: {}", sdxId);
            response = new FreeIpaUpscaleFailedEvent(sdxId, userId,
                    new PollerStoppedException("FreeIpa Upscale timed out after " + durationInMinutes + "minutes"));
        } catch (PollerException exception) {
            LOGGER.error("Upscale polling failed for stack: {}", sdxId);
            response = new FreeIpaUpscaleFailedEvent(sdxId, userId, exception);
        }
        return response;
    }

    public void waitFreeIpaUpscale(String envCrn, String operationId, PollingConfig pollingConfig) {
        String accountId = Crn.safeFromString(envCrn).getAccountId();
        Polling.waitPeriodly(pollingConfig.getSleepTime(), pollingConfig.getSleepTimeUnit())
                .stopIfException(pollingConfig.getStopPollingIfExceptionOccurred())
                .stopAfterDelay(pollingConfig.getDuration(), pollingConfig.getDurationTimeUnit())
                .run(() -> {
                    LOGGER.info("Polling freeIpa for operation status: '{}' in '{}' env", operationId, envCrn);
                    OperationStatus operationStatus;
                    try {
                        operationStatus = ThreadBasedUserCrnProvider.doAsInternalActor(() ->
                                freeipaService.getFreeIpaOperation(operationId, accountId));
                    } catch (RuntimeException e) {
                        LOGGER.info("Status of operation {} by response from freeIpa: ERROR {}", operationId, e.getMessage());
                        return AttemptResults.breakFor(e);
                    }
                    LOGGER.info("Status of operation {} by response from freeIpa: {}", operationId,
                            operationStatus.getStatus().name());
                    LOGGER.debug("Response from freeIpa: {}", JsonUtil.writeValueAsString(operationStatus));

                    if (OperationState.COMPLETED == operationStatus.getStatus()) {
                        return AttemptResults.justFinish();
                    }
                    if (OperationState.FAILED == operationStatus.getStatus()) {
                        LOGGER.error("FreeIpa Upscale failed {} with: {}", operationStatus.getOperationId(), operationStatus.getError());
                        return AttemptResults.breakFor(String.format("FreeIpa Upscale failed with : %s", operationStatus.getError()));
                    }
                    return AttemptResults.justContinue();
                });
    }

}
