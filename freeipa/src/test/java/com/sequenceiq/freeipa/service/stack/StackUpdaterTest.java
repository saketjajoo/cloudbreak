package com.sequenceiq.freeipa.service.stack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.cloud.scheduler.PollGroup;
import com.sequenceiq.cloudbreak.cloud.store.InMemoryStateStore;
import com.sequenceiq.cloudbreak.message.StackStatusMessageTransformator;
import com.sequenceiq.common.api.type.Tunnel;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.DetailedStackStatus;
import com.sequenceiq.freeipa.entity.Stack;
import com.sequenceiq.freeipa.entity.StackStatus;

@ExtendWith(MockitoExtension.class)
public class StackUpdaterTest {

    private static final long STACK_ID = 1234L;

    private static final String REASON = "myReason";

    @Mock
    private StackService stackService;

    @Mock
    private StackStatusMessageTransformator stackStatusMessageTransformator;

    @Mock
    private ServiceStatusRawMessageTransformer serviceStatusRawMessageTransformer;

    @Mock
    private StackStatusUpdater stackStatusUpdater;

    @InjectMocks
    private StackUpdater underTest;

    @Test
    void testDoUpdateStackStatusWhenNoStatusChange() {
        Stack stack = getStack(DetailedStackStatus.AVAILABLE);
        DetailedStackStatus newStackStatus = DetailedStackStatus.AVAILABLE;
        InMemoryStateStore.putStack(stack.getId(), PollGroup.POLLABLE);
        when(serviceStatusRawMessageTransformer.transformMessage(eq(REASON), any(Tunnel.class))).thenReturn("transform1");
        when(stackStatusMessageTransformator.transformMessage("transform1")).thenReturn("transform2");
        when(stackStatusUpdater.updateStatus(any(), any(), anyString(), any(), any())).thenReturn(stack);

        underTest.doUpdateStackStatus(stack, newStackStatus, REASON);

        verify(stackStatusUpdater).updateStatus(any(), eq(newStackStatus), eq("transform2"), eq(newStackStatus.getStatus()), any());
        assertThat(InMemoryStateStore.getStack(STACK_ID)).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DetailedStackStatus.class, names = {"AVAILABLE", "DELETE_FAILED", "DELETE_COMPLETED", "STOPPED", "START_FAILED",
            "STOP_FAILED", "REPAIR_FAILED", "UPSCALE_FAILED", "DOWNSCALE_FAILED", "PROVISIONED", "SALT_STATE_UPDATE_FAILED", "REPAIR_COMPLETED",
            "DOWNSCALE_COMPLETED", "UPSCALE_COMPLETED", "STARTED", "PROVISION_FAILED"})
    void testDoUpdateStackStatusWhenRemovableState(DetailedStackStatus detailedStackStatus) {
        Stack stack = getStack(DetailedStackStatus.CREATING_INFRASTRUCTURE);
        InMemoryStateStore.putStack(stack.getId(), PollGroup.POLLABLE);
        when(serviceStatusRawMessageTransformer.transformMessage(eq(REASON), any(Tunnel.class))).thenReturn("transform1");
        when(stackStatusMessageTransformator.transformMessage("transform1")).thenReturn("transform2");
        when(stackStatusUpdater.updateStatus(any(), any(), anyString(), any(), any())).thenReturn(stack);

        underTest.doUpdateStackStatus(stack, detailedStackStatus, REASON);

        verify(stackStatusUpdater).updateStatus(any(), eq(detailedStackStatus), eq("transform2"), eq(detailedStackStatus.getStatus()), any());
        assertThat(InMemoryStateStore.getStack(STACK_ID)).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DetailedStackStatus.class, names = {"AVAILABLE", "DELETE_FAILED", "DELETE_COMPLETED", "STOPPED", "START_FAILED",
            "STOP_FAILED", "REPAIR_FAILED", "UPSCALE_FAILED", "DOWNSCALE_FAILED", "PROVISIONED", "SALT_STATE_UPDATE_FAILED", "REPAIR_COMPLETED",
            "DOWNSCALE_COMPLETED", "UPSCALE_COMPLETED", "STARTED", "PROVISION_FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    void testDoUpdateStackStatusWhenNotRemovableState(DetailedStackStatus detailedStackStatus) {
        Stack stack = getStack(DetailedStackStatus.AVAILABLE);
        when(serviceStatusRawMessageTransformer.transformMessage(eq(REASON), any(Tunnel.class))).thenReturn("transform1");
        when(stackStatusMessageTransformator.transformMessage("transform1")).thenReturn("transform2");
        when(stackStatusUpdater.updateStatus(any(), any(), anyString(), any(), any())).thenReturn(stack);

        underTest.doUpdateStackStatus(stack, detailedStackStatus, REASON);

        verify(stackStatusUpdater).updateStatus(any(), eq(detailedStackStatus), eq("transform2"), eq(detailedStackStatus.getStatus()), any());
        assertThat(InMemoryStateStore.getAllStackId()).contains(STACK_ID);
    }

    private Stack getStack(DetailedStackStatus originalStatus) {
        Stack stack = new Stack();
        stack.setId(STACK_ID);
        StackStatus stackStatus = new StackStatus();
        stackStatus.setDetailedStackStatus(originalStatus);
        stackStatus.setStatus(originalStatus.getStatus());
        stack.setStackStatus(stackStatus);
        return stack;
    }
}
