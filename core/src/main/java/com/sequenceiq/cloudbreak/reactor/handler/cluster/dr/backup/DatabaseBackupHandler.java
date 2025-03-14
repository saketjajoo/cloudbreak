package com.sequenceiq.cloudbreak.reactor.handler.cluster.dr.backup;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.api.endpoint.v4.common.DetailedStackStatus;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.cloudbreak.common.event.Selectable;
import com.sequenceiq.cloudbreak.core.bootstrap.service.ClusterDeletionBasedExitCriteriaModel;
import com.sequenceiq.cloudbreak.dto.StackDto;
import com.sequenceiq.cloudbreak.eventbus.Event;
import com.sequenceiq.cloudbreak.orchestrator.host.HostOrchestrator;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.cloudbreak.orchestrator.model.SaltConfig;
import com.sequenceiq.cloudbreak.orchestrator.state.ExitCriteriaModel;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.dr.backup.DatabaseBackupFailedEvent;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.dr.backup.DatabaseBackupRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.dr.backup.DatabaseBackupSuccess;
import com.sequenceiq.cloudbreak.reactor.handler.cluster.dr.BackupRestoreSaltConfigGenerator;
import com.sequenceiq.cloudbreak.reactor.handler.cluster.dr.RangerVirtualGroupService;
import com.sequenceiq.cloudbreak.service.GatewayConfigService;
import com.sequenceiq.cloudbreak.service.stack.StackDtoService;
import com.sequenceiq.cloudbreak.view.ClusterView;
import com.sequenceiq.cloudbreak.view.InstanceMetadataView;
import com.sequenceiq.cloudbreak.view.StackView;
import com.sequenceiq.flow.event.EventSelectorUtil;
import com.sequenceiq.flow.reactor.api.handler.ExceptionCatcherEventHandler;
import com.sequenceiq.flow.reactor.api.handler.HandlerEvent;

@Component
public class DatabaseBackupHandler extends ExceptionCatcherEventHandler<DatabaseBackupRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseBackupHandler.class);

    @Inject
    private BackupRestoreSaltConfigGenerator saltConfigGenerator;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private HostOrchestrator hostOrchestrator;

    @Inject
    private StackDtoService stackDtoService;

    @Inject
    private RangerVirtualGroupService rangerVirtualGroupService;

    @Inject
    private EntitlementService entitlementService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(DatabaseBackupRequest.class);
    }

    @Override
    protected Selectable defaultFailureEvent(Long resourceId, Exception e, Event<DatabaseBackupRequest> event) {
        return new DatabaseBackupFailedEvent(resourceId, e, DetailedStackStatus.DATABASE_BACKUP_FAILED);
    }

    @Override
    protected Selectable doAccept(HandlerEvent<DatabaseBackupRequest> event) {
        DatabaseBackupRequest request = event.getData();
        Selectable result;
        Long stackId = request.getResourceId();
        LOGGER.debug("Backing up database on stack {}, backup id {}", stackId, request.getBackupId());
        try {
            StackDto stackDto = stackDtoService.getById(stackId);
            ClusterView cluster = stackDto.getCluster();
            StackView stack = stackDto.getStack();
            InstanceMetadataView gatewayInstance = stackDto.getPrimaryGatewayInstance();
            GatewayConfig gatewayConfig = gatewayConfigService.getGatewayConfig(stack, stackDto.getSecurityConfig(), gatewayInstance, stackDto.hasGateway());
            Set<String> gatewayFQDN = Collections.singleton(gatewayInstance.getDiscoveryFQDN());
            ExitCriteriaModel exitModel = ClusterDeletionBasedExitCriteriaModel.clusterDeletionBasedModel(stackId, cluster.getId());
            String rangerAdminGroup = rangerVirtualGroupService.getRangerVirtualGroup(stack);

            boolean enableDbCompression = entitlementService.isDatalakeDatabaseBackupCompressionEnabled(ThreadBasedUserCrnProvider.getAccountId());
            LOGGER.info("Compression entitlement: {}", enableDbCompression);
            SaltConfig saltConfig = saltConfigGenerator.createSaltConfig(request.getBackupLocation(), request.getBackupId(), rangerAdminGroup,
                    request.isCloseConnections(), request.getSkipDatabaseNames(), enableDbCompression, stack);
            hostOrchestrator.backupDatabase(gatewayConfig, gatewayFQDN, saltConfig, exitModel, request.getDatabaseMaxDurationInMin());

            result = new DatabaseBackupSuccess(stackId);
        } catch (Exception e) {
            LOGGER.error("Database backup event failed", e);
            result = new DatabaseBackupFailedEvent(stackId, e, DetailedStackStatus.DATABASE_BACKUP_FAILED);
        }
        return result;
    }
}
