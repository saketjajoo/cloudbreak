package com.sequenceiq.freeipa.service.stack;

import javax.inject.Inject;
import javax.persistence.OptimisticLockException;

import org.hibernate.StaleObjectStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.DetailedStackStatus;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.StackStatus;

@Service
public class StackStatusUpdater {

    private static final Logger LOGGER = LoggerFactory.getLogger(StackStatusUpdater.class);

    @Inject
    private StackService stackService;

    @Retryable(value = {OptimisticLockException.class, StaleObjectStateException.class}, backoff = @Backoff(value = 1000), maxAttempts = 4)
    public Stack updateStatus(Stack stackOriginal, DetailedStackStatus newDetailedStatus, String newStatusReason, Status newStatus, StackStatus stackStatus) {
        Stack stack = stackService.getStackById(stackOriginal.getId());
        LOGGER.debug("Updated: status from {} to {} - detailed status from {} to {} - reason from {} to {}",
                stackStatus.getStatus(), newStatus, stackStatus.getDetailedStackStatus(), newDetailedStatus,
                stackStatus.getStatusReason(), newStatusReason);
        stack.setStackStatus(new StackStatus(stack, newStatus, newStatusReason, newDetailedStatus));
        stack = stackService.save(stack);
        return stack;
    }

}
