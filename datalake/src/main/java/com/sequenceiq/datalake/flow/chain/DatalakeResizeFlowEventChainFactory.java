package com.sequenceiq.datalake.flow.chain;

import static com.sequenceiq.datalake.flow.create.SdxCreateEvent.STORAGE_VALIDATION_WAIT_EVENT;
import static com.sequenceiq.datalake.flow.datahub.StartDatahubFlowEvent.START_DATAHUB_EVENT;
import static com.sequenceiq.datalake.flow.delete.SdxDeleteEvent.SDX_DELETE_EVENT;
import static com.sequenceiq.datalake.flow.detach.SdxDetachEvent.SDX_DETACH_EVENT;
import static com.sequenceiq.datalake.flow.detach.event.DatalakeResizeFlowChainStartEvent.SDX_RESIZE_FLOW_CHAIN_START_EVENT;
import static com.sequenceiq.datalake.flow.dr.backup.DatalakeBackupEvent.DATALAKE_TRIGGER_BACKUP_EVENT;
import static com.sequenceiq.datalake.flow.dr.restore.DatalakeRestoreEvent.DATALAKE_TRIGGER_RESTORE_EVENT;
import static com.sequenceiq.datalake.flow.stop.SdxStopEvent.SDX_STOP_EVENT;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.datalake.flow.SdxEvent;
import com.sequenceiq.datalake.flow.delete.event.SdxDeleteStartEvent;
import com.sequenceiq.datalake.flow.detach.event.DatalakeResizeFlowChainStartEvent;
import com.sequenceiq.datalake.flow.detach.event.SdxStartDetachEvent;
import com.sequenceiq.datalake.flow.dr.backup.DatalakeBackupFailureReason;
import com.sequenceiq.datalake.flow.dr.backup.event.DatalakeTriggerBackupEvent;
import com.sequenceiq.datalake.flow.dr.restore.DatalakeRestoreFailureReason;
import com.sequenceiq.datalake.flow.dr.restore.event.DatalakeTriggerRestoreEvent;
import com.sequenceiq.datalake.flow.freeipa.upscale.event.FreeIpaUpscaleStartEvent;
import com.sequenceiq.datalake.flow.stop.event.SdxStartStopEvent;
import com.sequenceiq.datalake.service.FreeipaService;
import com.sequenceiq.flow.core.chain.FlowEventChainFactory;
import com.sequenceiq.flow.core.chain.config.FlowTriggerEventQueue;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.AvailabilityType;

@Component
public class DatalakeResizeFlowEventChainFactory implements FlowEventChainFactory<DatalakeResizeFlowChainStartEvent> {

    @Inject
    private FreeipaService freeipaService;

    @Override
    public String initEvent() {
        return SDX_RESIZE_FLOW_CHAIN_START_EVENT;
    }

    @Override
    public FlowTriggerEventQueue createFlowTriggerEventQueue(DatalakeResizeFlowChainStartEvent event) {
        Queue<Selectable> chain = new ConcurrentLinkedQueue<>();

        boolean accept = true;
        String envCrn = event.getSdxCluster().getEnvCrn();
        if (AvailabilityType.HA.getInstanceCount() > freeipaService.getNodeCount(envCrn)) {
            addFreeIpaUpscaleToChain(chain, event, envCrn, accept);
            accept = false;
        }
        if (event.shouldTakeBackup()) {
            addBackupToChain(chain, event, accept);
            accept = false;
        }

        addStopToChain(chain, event, accept);

        // De-attach sdx from environment
        chain.add(new SdxStartDetachEvent(SDX_DETACH_EVENT.event(), event.getResourceId(), event.getSdxCluster(), event.getUserId()));

        // Create new
        chain.add(new SdxEvent(STORAGE_VALIDATION_WAIT_EVENT.event(), event.getResourceId(), event.getSdxCluster().getClusterName(), event.getUserId()));

        if (event.shouldTakeBackup() && !event.getSdxCluster().isRangerRazEnabled()) {
            //restore the new cluster
            chain.add(new DatalakeTriggerRestoreEvent(DATALAKE_TRIGGER_RESTORE_EVENT.event(), event.getResourceId(), event.getSdxCluster().getClusterName(),
                    event.getUserId(), null, event.getBackupLocation(), null, DatalakeRestoreFailureReason.RESTORE_ON_RESIZE));
        }
        // Delete  De-attached Sdx
        chain.add(new SdxDeleteStartEvent(SDX_DELETE_EVENT.event(), event.getResourceId(), event.getUserId(), true));

        //Start any existing datahubs
        chain.add(new SdxEvent(START_DATAHUB_EVENT.event(), event.getResourceId(), event.getUserId()));

        return new FlowTriggerEventQueue(getName(), event, chain);
    }

    private void addFreeIpaUpscaleToChain(Queue<Selectable> chain, DatalakeResizeFlowChainStartEvent event, String envCrn, boolean accept) {
        if (accept) {
            chain.add(new FreeIpaUpscaleStartEvent(null, event.getResourceId(), event.getUserId(), envCrn, event.accepted()));
        } else {
            chain.add(new FreeIpaUpscaleStartEvent(event.getResourceId(), event.getUserId(), envCrn));
        }
    }

    private void addBackupToChain(Queue<Selectable> chain, DatalakeResizeFlowChainStartEvent event, boolean accept) {
        if (accept) {
            chain.add(new DatalakeTriggerBackupEvent(DATALAKE_TRIGGER_BACKUP_EVENT.event(),
                    event.getResourceId(), event.getUserId(), event.getBackupLocation(), "resize" + System.currentTimeMillis(),
                    DatalakeBackupFailureReason.BACKUP_ON_RESIZE, event.accepted()));
        } else {
            chain.add(new DatalakeTriggerBackupEvent(DATALAKE_TRIGGER_BACKUP_EVENT.event(),
                    event.getResourceId(), event.getUserId(), event.getBackupLocation(), "resize" + System.currentTimeMillis(),
                    DatalakeBackupFailureReason.BACKUP_ON_RESIZE));
        }
    }

    private void addStopToChain(Queue<Selectable> chain, DatalakeResizeFlowChainStartEvent event, boolean accept) {
        if (accept) {
            chain.add(new SdxStartStopEvent(SDX_STOP_EVENT.event(), event.getResourceId(), event.getUserId(), event.accepted()));
        } else {
            chain.add(new SdxStartStopEvent(SDX_STOP_EVENT.event(), event.getResourceId(), event.getUserId()));
        }
    }
}
