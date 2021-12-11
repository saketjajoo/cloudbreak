package com.sequenceiq.datalake.flow.chain;

import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_DELETE_EVENT;
import static com.sequenceiq.datalake.flow.detach.SdxDetachEvent.SDX_DETACH_RECOVERY_EVENT;
import static com.sequenceiq.datalake.flow.detach.event.DatalakeResizeRecoveryFlowChainStartEvent.SDX_RESIZE_RECOVERY_FLOW_CHAIN_START_EVENT;
import static com.sequenceiq.datalake.flow.start.SdxStartEvent.SDX_START_EVENT;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.datalake.flow.delete.event.SdxDeleteStartEvent;
import com.sequenceiq.datalake.flow.detach.event.DatalakeResizeRecoveryFlowChainStartEvent;
import com.sequenceiq.datalake.flow.detach.event.SdxStartDetachRecoveryEvent;
import com.sequenceiq.datalake.flow.start.event.SdxStartStartEvent;
import com.sequenceiq.flow.core.chain.FlowEventChainFactory;
import com.sequenceiq.flow.core.chain.config.FlowTriggerEventQueue;

@Component
public class DatalakeResizeRecoveryFlowEventChainFactory implements FlowEventChainFactory<DatalakeResizeRecoveryFlowChainStartEvent> {
    @Override
    public String initEvent() {
        return SDX_RESIZE_RECOVERY_FLOW_CHAIN_START_EVENT;
    }

    @Override
    public FlowTriggerEventQueue createFlowTriggerEventQueue(DatalakeResizeRecoveryFlowChainStartEvent event) {
        Queue<Selectable> flowChain = new ConcurrentLinkedQueue<>();

        // Forced deletion of the resized datalake.
        boolean firstEventAdded = false;
        if (event.getNewCluster().getDeleted() == null) {
            flowChain.add(new SdxDeleteStartEvent(
                    SDX_DELETE_EVENT.event(), event.getNewCluster().getId(), event.getUserId(), true, event.accepted()
            ));
            firstEventAdded = true;
        }

        // Reattach of the old datalake.
        if (event.getOldCluster().isDetached()) {
            if (firstEventAdded) {
                flowChain.add(new SdxStartDetachRecoveryEvent(
                        SDX_DETACH_RECOVERY_EVENT.event(), event.getOldCluster().getId(), event.getUserId()
                ));
            } else {
                flowChain.add(new SdxStartDetachRecoveryEvent(
                        SDX_DETACH_RECOVERY_EVENT.event(), event.getOldCluster().getId(), event.getUserId(), event.accepted()
                ));
                firstEventAdded = true;
            }
        }

        // Restart of the old datalake.
        if (firstEventAdded) {
            flowChain.add(new SdxStartStartEvent(
                    SDX_START_EVENT.event(), event.getOldCluster().getId(), event.getUserId()
            ));
        } else {
            flowChain.add(new SdxStartStartEvent(
                    SDX_START_EVENT.event(), event.getOldCluster().getId(), event.getUserId(), event.accepted()
            ));
        }

        return new FlowTriggerEventQueue(getName(), event, flowChain);
    }
}
