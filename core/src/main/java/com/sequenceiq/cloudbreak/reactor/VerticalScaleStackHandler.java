package com.sequenceiq.cloudbreak.reactor;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.CloudConnector;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.event.CloudPlatformResult;
import com.sequenceiq.cloudbreak.cloud.handler.CloudPlatformEventHandler;
import com.sequenceiq.cloudbreak.cloud.init.CloudPlatformConnectors;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.ResourceStatus;
import com.sequenceiq.cloudbreak.core.flow2.stack.upscale.StackUpscaleService;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.StackVerticalScaleRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.StackVerticalScaleResult;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class VerticalScaleStackHandler implements CloudPlatformEventHandler<StackVerticalScaleRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerticalScaleStackHandler.class);

    @Inject
    private CloudPlatformConnectors cloudPlatformConnectors;

    @Inject
    private EventBus eventBus;

    @Inject
    private StackUpscaleService stackUpscaleService;

    @Override
    public Class<StackVerticalScaleRequest> type() {
        return StackVerticalScaleRequest.class;
    }

    @Override
    public void accept(Event<StackVerticalScaleRequest> stackVerticalScaleRequestEvent) {
        LOGGER.debug("Received event: {}", stackVerticalScaleRequestEvent);
        StackVerticalScaleRequest<StackVerticalScaleResult> request = stackVerticalScaleRequestEvent.getData();
        CloudContext cloudContext = request.getCloudContext();
        try {
            CloudConnector<?> connector = cloudPlatformConnectors.get(cloudContext.getPlatformVariant());
            AuthenticatedContext ac = getAuthenticatedContext(request, cloudContext, connector);
            List<CloudResourceStatus> resourceStatus = stackUpscaleService.verticalScale(ac, request, connector);
            LOGGER.info("Vertical scaling resource statuses: {}", resourceStatus);
            StackVerticalScaleResult result = new StackVerticalScaleResult(request.getResourceId(), ResourceStatus.UPDATED, resourceStatus);
            request.getResult().onNext(result);
            eventBus.notify(result.selector(), new Event<>(stackVerticalScaleRequestEvent.getHeaders(), result));
            LOGGER.debug("Vertical scaling successfully finished for {}, and the result is: {}", cloudContext, result);
        } catch (Exception e) {
            LOGGER.error("Vertical scaling stack failed", e);
            StackVerticalScaleResult result = new StackVerticalScaleResult(e.getMessage(), e, request.getResourceId());
            request.getResult().onNext(result);
            eventBus.notify(CloudPlatformResult.failureSelector(StackVerticalScaleResult.class),
                    new Event<>(stackVerticalScaleRequestEvent.getHeaders(), result));
        }
    }

    private AuthenticatedContext getAuthenticatedContext(StackVerticalScaleRequest<StackVerticalScaleResult> request,
            CloudContext cloudContext,
            CloudConnector<?> connector) {
        return connector.authentication().authenticate(cloudContext, request.getCloudCredential());
    }

}
