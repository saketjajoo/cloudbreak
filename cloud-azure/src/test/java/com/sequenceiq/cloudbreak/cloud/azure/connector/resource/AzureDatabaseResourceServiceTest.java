package com.sequenceiq.cloudbreak.cloud.azure.connector.resource;

import static com.sequenceiq.cloudbreak.cloud.azure.AzureResourceType.PRIVATE_DNS_ZONE_GROUP;
import static com.sequenceiq.cloudbreak.cloud.azure.AzureResourceType.PRIVATE_ENDPOINT;
import static com.sequenceiq.cloudbreak.cloud.azure.view.AzureDatabaseServerView.DB_VERSION;
import static com.sequenceiq.cloudbreak.cloud.model.ResourceStatus.DELETED;
import static com.sequenceiq.cloudbreak.cloud.model.ResourceStatus.IN_PROGRESS;
import static com.sequenceiq.common.api.type.ResourceType.AZURE_DATABASE;
import static com.sequenceiq.common.api.type.ResourceType.AZURE_DNS_ZONE_GROUP;
import static com.sequenceiq.common.api.type.ResourceType.AZURE_PRIVATE_ENDPOINT;
import static com.sequenceiq.common.api.type.ResourceType.AZURE_RESOURCE_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.azure.core.exception.AzureException;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import com.sequenceiq.cloudbreak.cloud.azure.AzureCloudResourceService;
import com.sequenceiq.cloudbreak.cloud.azure.AzureDatabaseTemplateBuilder;
import com.sequenceiq.cloudbreak.cloud.azure.AzureResourceGroupMetadataProvider;
import com.sequenceiq.cloudbreak.cloud.azure.AzureUtils;
import com.sequenceiq.cloudbreak.cloud.azure.ResourceGroupUsage;
import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.util.AzureExceptionHandler;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.exception.CloudConnectorException;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.DatabaseEngine;
import com.sequenceiq.cloudbreak.cloud.model.DatabaseServer;
import com.sequenceiq.cloudbreak.cloud.model.DatabaseStack;
import com.sequenceiq.cloudbreak.cloud.model.ExternalDatabaseStatus;
import com.sequenceiq.cloudbreak.cloud.model.Location;
import com.sequenceiq.cloudbreak.cloud.model.Region;
import com.sequenceiq.cloudbreak.cloud.notification.PersistenceNotifier;
import com.sequenceiq.cloudbreak.common.database.TargetMajorVersion;
import com.sequenceiq.cloudbreak.service.Retry;
import com.sequenceiq.common.api.type.CommonStatus;
import com.sequenceiq.common.api.type.ResourceType;

@ExtendWith(MockitoExtension.class)
class AzureDatabaseResourceServiceTest {

    private static final String RESOURCE_GROUP_NAME = "resource group name";

    private static final String STACK_NAME = "aStack";

    private static final String RESOURCE_REFERENCE = "aReference";

    private static final String TEMPLATE = "template is gonna do some templating";

    private static final String SERVER_NAME = "serverName";

    private static final String NEW_PASSWORD = "newPassword";

    @Mock
    private AzureDatabaseTemplateBuilder azureDatabaseTemplateBuilder;

    @Mock
    private AzureUtils azureUtils;

    @Mock
    private AzureExceptionHandler azureExceptionHandler;

    @Mock
    private AuthenticatedContext ac;

    @Mock
    private DatabaseStack databaseStack;

    @Mock
    private CloudContext cloudContext;

    @Mock
    private AzureClient client;

    @Mock
    private ResourceGroup resourceGroup;

    @Mock
    private AzureResourceGroupMetadataProvider azureResourceGroupMetadataProvider;

    @Mock
    private Deployment deployment;

    @Mock
    private AzureCloudResourceService azureCloudResourceService;

    @Mock
    private Retry retryService;

    @Mock
    private PersistenceNotifier persistenceNotifier;

    @InjectMocks
    private AzureDatabaseResourceService underTest;

    @BeforeEach
    void initTests() {
        when(ac.getCloudContext()).thenReturn(cloudContext);
        lenient().when(ac.getParameter(AzureClient.class)).thenReturn(client);
    }

    @Test
    void shouldReturnDeletedStatusInCaseOfMissingResourceGroup() {
        when(client.getResourceGroup(RESOURCE_GROUP_NAME)).thenReturn(null);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);

        ExternalDatabaseStatus actual = underTest.getDatabaseServerStatus(ac, databaseStack);

        assertEquals(ExternalDatabaseStatus.DELETED, actual);
    }

    @Test
    void shouldReturnStartedStatusInCaseOfExistingResourceGroup() {
        when(client.getResourceGroup(RESOURCE_GROUP_NAME)).thenReturn(resourceGroup);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);

        ExternalDatabaseStatus actual = underTest.getDatabaseServerStatus(ac, databaseStack);

        assertEquals(ExternalDatabaseStatus.STARTED, actual);
    }

    @Test
    void shouldReturnDeletedDbServerWhenTerminateDatabaseServerAndSingleResourceGroup() {
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(any(DatabaseStack.class))).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureUtils.deleteDatabaseServer(any(), anyString(), anyBoolean())).thenReturn(Optional.empty());
        List<CloudResource> cloudResources = List.of(buildResource(AZURE_DATABASE));

        List<CloudResourceStatus> resourceStatuses = underTest.terminateDatabaseServer(ac, databaseStack, cloudResources, false, persistenceNotifier);

        assertEquals(1, resourceStatuses.size());
        assertEquals(AZURE_DATABASE, resourceStatuses.get(0).getCloudResource().getType());
        assertEquals(DELETED, resourceStatuses.get(0).getStatus());
        verify(azureUtils).deleteDatabaseServer(any(), eq(RESOURCE_REFERENCE), anyBoolean());
        verify(client, never()).deleteResourceGroup(anyString());
        verify(persistenceNotifier).notifyDeletion(any(), any());
    }

    @Test
    void shouldReturnDeletedResourceGroupWhenTerminateDatabaseServerAndMultipleResourceGroups() {
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(any(DatabaseStack.class))).thenReturn(ResourceGroupUsage.MULTIPLE);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.deleteResourceGroup(any(), anyString(), anyBoolean())).thenReturn(Optional.empty());
        List<CloudResource> cloudResources = List.of(buildResource(AZURE_DATABASE));

        List<CloudResourceStatus> resourceStatuses = underTest.terminateDatabaseServer(ac, databaseStack, cloudResources, false, persistenceNotifier);

        assertEquals(1, resourceStatuses.size());
        assertEquals(AZURE_RESOURCE_GROUP, resourceStatuses.get(0).getCloudResource().getType());
        assertEquals(DELETED, resourceStatuses.get(0).getStatus());
        verify(azureUtils).deleteResourceGroup(any(), eq(RESOURCE_GROUP_NAME), eq(false));
        verify(azureUtils, never()).deleteDatabaseServer(any(), anyString(), anyBoolean());
        verify(persistenceNotifier).notifyDeletion(any(), any());
    }

    @Test
    void shouldReturnDeletedDbServerAndDeleteAccessPolicyWhenTerminateDatabaseServerAndSingleResourceGroup() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyVaultUrl", "dummyKeyVaultUrl");
        params.put("keyVaultResourceGroupName", "dummyKeyVaultResourceGroupName");
        when(databaseStack.getDatabaseServer()).thenReturn(DatabaseServer.builder().withParams(params).build());
        when(client.getServicePrincipalForResourceById(RESOURCE_REFERENCE)).thenReturn("dummyPrincipalId");
        when(client.getVaultNameFromEncryptionKeyUrl("dummyKeyVaultUrl")).thenReturn("dummyVaultName");
        when(client.keyVaultExists("dummyKeyVaultResourceGroupName", "dummyVaultName")).thenReturn(Boolean.TRUE);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(any(DatabaseStack.class))).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureUtils.deleteDatabaseServer(any(), anyString(), anyBoolean())).thenReturn(Optional.empty());
        List<CloudResource> cloudResources = List.of(buildResource(AZURE_DATABASE));
        initRetry();

        List<CloudResourceStatus> resourceStatuses = underTest.terminateDatabaseServer(ac, databaseStack, cloudResources, false, persistenceNotifier);

        assertEquals(1, resourceStatuses.size());
        assertEquals(AZURE_DATABASE, resourceStatuses.get(0).getCloudResource().getType());
        assertEquals(DELETED, resourceStatuses.get(0).getStatus());
        verify(azureUtils).deleteDatabaseServer(any(), eq(RESOURCE_REFERENCE), anyBoolean());
        verify(client).removeKeyVaultAccessPolicyForServicePrincipal("dummyKeyVaultResourceGroupName",
                "dummyVaultName", "dummyPrincipalId");
        verify(client, never()).deleteResourceGroup(anyString());
        verify(persistenceNotifier).notifyDeletion(any(), any());
    }

    @Test
    void shouldUpgradeDatabaseWhenUpgradeDatabaseServerAndPrivateEndpoint() {
        DatabaseServer databaseServer = buildDatabaseServer();

        CloudResource dbResource = buildResource(AZURE_DATABASE);
        CloudResource peResource = buildResource(AZURE_PRIVATE_ENDPOINT);
        CloudResource dzgResource = buildResource(AZURE_DNS_ZONE_GROUP);
        List<CloudResource> cloudResourceList = List.of(peResource, dzgResource, dbResource);

        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(cloudResourceList);
        when(azureCloudResourceService.getPrivateEndpointRdsResourceTypes()).thenReturn(List.of(AZURE_PRIVATE_ENDPOINT, AZURE_DNS_ZONE_GROUP));
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(databaseStack.getDatabaseServer()).thenReturn(databaseServer);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));
        ArgumentCaptor<DatabaseStack> databaseStackArgumentCaptor = ArgumentCaptor.forClass(DatabaseStack.class);
        when(azureDatabaseTemplateBuilder.build(eq(cloudContext), databaseStackArgumentCaptor.capture())).thenReturn(TEMPLATE);

        underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList);

        verify(azureUtils).getStackName(eq(cloudContext));

        InOrder inOrder = inOrder(azureUtils);
        inOrder.verify(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_ENDPOINT);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_DNS_ZONE_GROUP);

        inOrder = inOrder(persistenceNotifier);
        inOrder.verify(persistenceNotifier).notifyDeletion(dbResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(peResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(dzgResource, cloudContext);

        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        assertEquals("11", databaseStackArgumentCaptor.getValue().getDatabaseServer().getParameters().get(DB_VERSION));
        verify(persistenceNotifier).notifyAllocations(cloudResourceList, cloudContext);
        verify(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
    }

    @Test
    void testUpgradeThrowsMgmtExWithConflict() {
        DatabaseServer databaseServer = buildDatabaseServer();

        CloudResource dbResource = buildResource(AZURE_DATABASE);
        CloudResource peResource = buildResource(AZURE_PRIVATE_ENDPOINT);
        CloudResource dzgResource = buildResource(AZURE_DNS_ZONE_GROUP);
        List<CloudResource> cloudResourceList = List.of(peResource, dzgResource, dbResource);

        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(cloudResourceList);
        when(azureCloudResourceService.getPrivateEndpointRdsResourceTypes()).thenReturn(List.of(AZURE_PRIVATE_ENDPOINT, AZURE_DNS_ZONE_GROUP));
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(databaseStack.getDatabaseServer()).thenReturn(databaseServer);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));
        ArgumentCaptor<DatabaseStack> databaseStackArgumentCaptor = ArgumentCaptor.forClass(DatabaseStack.class);
        when(azureDatabaseTemplateBuilder.build(eq(cloudContext), databaseStackArgumentCaptor.capture())).thenReturn(TEMPLATE);
        ManagementException managementException = new ManagementException("asdf", mock(HttpResponse.class), new ManagementError("conflict", "asdf"));
        doThrow(managementException).when(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        when(azureExceptionHandler.isExceptionCodeConflict(managementException)).thenReturn(Boolean.TRUE);
        when(azureUtils.convertToCloudConnectorException(managementException, "Database stack upgrade")).thenReturn(new CloudConnectorException("fda"));

        assertThrows(CloudConnectorException.class,
                () -> underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList));

        verify(azureUtils).getStackName(eq(cloudContext));

        InOrder inOrder = inOrder(azureUtils);
        inOrder.verify(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_ENDPOINT);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_DNS_ZONE_GROUP);

        inOrder = inOrder(persistenceNotifier);
        inOrder.verify(persistenceNotifier).notifyDeletion(dbResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(peResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(dzgResource, cloudContext);

        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        assertEquals("11", databaseStackArgumentCaptor.getValue().getDatabaseServer().getParameters().get(DB_VERSION));
        verify(persistenceNotifier).notifyAllocations(cloudResourceList, cloudContext);
    }

    @Test
    void testUpgradeThrowsMgmtExWithNonConflict() {
        DatabaseServer databaseServer = buildDatabaseServer();

        CloudResource dbResource = buildResource(AZURE_DATABASE);
        CloudResource peResource = buildResource(AZURE_PRIVATE_ENDPOINT);
        CloudResource dzgResource = buildResource(AZURE_DNS_ZONE_GROUP);
        List<CloudResource> cloudResourceList = List.of(peResource, dzgResource, dbResource);

        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(cloudResourceList);
        when(azureCloudResourceService.getPrivateEndpointRdsResourceTypes()).thenReturn(List.of(AZURE_PRIVATE_ENDPOINT, AZURE_DNS_ZONE_GROUP));
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(databaseStack.getDatabaseServer()).thenReturn(databaseServer);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));
        ArgumentCaptor<DatabaseStack> databaseStackArgumentCaptor = ArgumentCaptor.forClass(DatabaseStack.class);
        when(azureDatabaseTemplateBuilder.build(eq(cloudContext), databaseStackArgumentCaptor.capture())).thenReturn(TEMPLATE);
        ManagementException managementException = new ManagementException("asdf", mock(HttpResponse.class), new ManagementError("not_conflict", "asdf"));
        doThrow(managementException).when(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        when(azureExceptionHandler.isExceptionCodeConflict(managementException)).thenReturn(Boolean.FALSE);
        when(azureUtils.convertToCloudConnectorException(managementException, "Database stack upgrade")).thenReturn(new CloudConnectorException("fda"));

        assertThrows(CloudConnectorException.class,
                () -> underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList));

        verify(azureUtils).getStackName(eq(cloudContext));

        InOrder inOrder = inOrder(azureUtils);
        inOrder.verify(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_ENDPOINT);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_DNS_ZONE_GROUP);

        inOrder = inOrder(persistenceNotifier);
        inOrder.verify(persistenceNotifier).notifyDeletion(dbResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(peResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(dzgResource, cloudContext);

        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        assertEquals("11", databaseStackArgumentCaptor.getValue().getDatabaseServer().getParameters().get(DB_VERSION));
        verify(persistenceNotifier).notifyAllocations(cloudResourceList, cloudContext);
        verify(azureUtils, never()).convertToActionFailedExceptionCausedByCloudConnectorException(managementException, "Database server deployment");
    }

    @Test
    void shouldUpgradeDatabaseAndDeleteAllResourcesWhenUpgradeDatabaseServerAndMultiplePrivateEndpointResourcesExist() {
        DatabaseServer databaseServer = buildDatabaseServer();

        CloudResource dbResource = buildResource(AZURE_DATABASE);
        CloudResource pe1Resource = buildResource(AZURE_PRIVATE_ENDPOINT, "pe1");
        CloudResource pe2Resource = buildResource(AZURE_PRIVATE_ENDPOINT, "pe2");
        CloudResource pe3Resource = buildResource(AZURE_PRIVATE_ENDPOINT, "pe3");
        CloudResource dzgResource = buildResource(AZURE_DNS_ZONE_GROUP);
        List<CloudResource> cloudResourceList = List.of(pe1Resource, pe2Resource, pe3Resource, dzgResource, dbResource);
        List<CloudResource> expectedCloudResourceList = List.of(pe3Resource, dzgResource, dbResource);

        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(expectedCloudResourceList);
        when(azureCloudResourceService.getPrivateEndpointRdsResourceTypes()).thenReturn(List.of(AZURE_PRIVATE_ENDPOINT, AZURE_DNS_ZONE_GROUP));
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(databaseStack.getDatabaseServer()).thenReturn(databaseServer);

        underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList);

        verify(azureUtils).getStackName(eq(cloudContext));

        InOrder inOrder = inOrder(azureUtils);
        inOrder.verify(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        inOrder.verify(azureUtils, times(3)).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_ENDPOINT);
        inOrder.verify(azureUtils).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_DNS_ZONE_GROUP);

        inOrder = inOrder(persistenceNotifier);
        inOrder.verify(persistenceNotifier).notifyDeletion(dbResource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(pe1Resource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(pe2Resource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(pe3Resource, cloudContext);
        inOrder.verify(persistenceNotifier).notifyDeletion(dzgResource, cloudContext);

        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        ArgumentCaptor<DatabaseStack> databaseStackArgumentCaptor = ArgumentCaptor.forClass(DatabaseStack.class);
        verify(azureDatabaseTemplateBuilder).build(eq(cloudContext), databaseStackArgumentCaptor.capture());
        assertEquals("11", databaseStackArgumentCaptor.getValue().getDatabaseServer().getParameters().get(DB_VERSION));
        verify(persistenceNotifier).notifyAllocations(expectedCloudResourceList, cloudContext);
    }

    @Test
    void shouldUpgradeDatabaseWhenUpgradeDatabaseServerAndNoPrivateEndpoint() {
        CloudResource dbResource = buildResource(AZURE_DATABASE);
        List<CloudResource> cloudResourceList = List.of(dbResource);
        DatabaseServer databaseServer = buildDatabaseServer();

        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(List.of(dbResource));
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(databaseStack.getDatabaseServer()).thenReturn(databaseServer);

        underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList);

        verify(azureUtils).getStackName(eq(cloudContext));
        verify(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        verify(azureUtils, never()).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_ENDPOINT);

        verify(persistenceNotifier).notifyDeletion(dbResource, cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        ArgumentCaptor<DatabaseStack> databaseStackArgumentCaptor = ArgumentCaptor.forClass(DatabaseStack.class);
        verify(azureDatabaseTemplateBuilder).build(eq(cloudContext), databaseStackArgumentCaptor.capture());
        assertEquals("11", databaseStackArgumentCaptor.getValue().getDatabaseServer().getParameters().get(DB_VERSION));
        verify(persistenceNotifier).notifyAllocations(List.of(dbResource), cloudContext);
    }

    @Test
    void shouldReturnExceptionWhenUpgradeDatabaseServerThrowsCloudException() {
        CloudResource dbResource = buildResource(AZURE_DATABASE);
        CloudResource peResource = buildResource(AZURE_PRIVATE_ENDPOINT);
        CloudResource dzgResource = buildResource(AZURE_DNS_ZONE_GROUP);
        List<CloudResource> cloudResourceList = List.of(peResource, dzgResource, dbResource);

        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(List.of(dbResource));

        doThrow(new RuntimeException("delete failed")).when(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);

        CloudConnectorException exception = assertThrows(CloudConnectorException.class,
                () -> underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList));

        assertEquals("Error in upgrading database stack aStack: delete failed", exception.getMessage());
        verify(azureUtils).getStackName(eq(cloudContext));
        verify(azureUtils).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureDatabaseTemplateBuilder, never()).build(eq(cloudContext), any(DatabaseStack.class));
        verify(persistenceNotifier, times(1)).notifyAllocations(List.of(dbResource), cloudContext);
    }

    @Test
    void shouldReturnExceptionWhenUpgradeDatabaseServerDbResourceIsNotFound() {
        CloudResource peResource = buildResource(AZURE_PRIVATE_ENDPOINT);
        CloudResource dzgResource = buildResource(AZURE_DNS_ZONE_GROUP);
        List<CloudResource> cloudResourceList = List.of(peResource, dzgResource);

        CloudConnectorException exception = assertThrows(CloudConnectorException.class,
                () -> underTest.upgradeDatabaseServer(ac, databaseStack, persistenceNotifier, TargetMajorVersion.VERSION_11, cloudResourceList));

        assertEquals("Azure database server cloud resource does not exist for stack, please contact Cloudera support!", exception.getMessage());
        verify(azureUtils).getStackName(eq(cloudContext));
        verify(azureUtils, never()).deleteDatabaseServer(client, RESOURCE_REFERENCE, false);
        verify(azureUtils, never()).deleteGenericResourceById(client, RESOURCE_REFERENCE, PRIVATE_ENDPOINT);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureDatabaseTemplateBuilder, never()).build(eq(cloudContext), any(DatabaseStack.class));
    }

    @Test
    void testBuildDatabaseResourcesForLaunch() {
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(databaseStack)).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureDatabaseTemplateBuilder.build(cloudContext, databaseStack)).thenReturn(TEMPLATE);
        when(client.resourceGroupExists(RESOURCE_GROUP_NAME)).thenReturn(true);
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(deployment.outputs()).thenReturn(Map.of("databaseServerFQDN", Map.of("value", "fqdn")));
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));

        List<CloudResourceStatus> actual =  underTest.buildDatabaseResourcesForLaunch(ac, databaseStack, persistenceNotifier);

        assertEquals(2, actual.size());
        verify(azureUtils).getStackName(cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureResourceGroupMetadataProvider).getResourceGroupUsage(databaseStack);
        verify(azureDatabaseTemplateBuilder).build(cloudContext, databaseStack);
        verify(client).resourceGroupExists(RESOURCE_GROUP_NAME);
        verify(persistenceNotifier, times(4)).notifyAllocation(any(CloudResource.class), eq(cloudContext));
        verify(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        verify(client).getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME);
    }

    @Test
    void testBuildDatabaseResourcesForLaunchShouldThrowExceptionWhenTheRGIsExistsAndTheTypeIsSingle() {
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(databaseStack)).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureDatabaseTemplateBuilder.build(cloudContext, databaseStack)).thenReturn(TEMPLATE);
        when(client.resourceGroupExists(RESOURCE_GROUP_NAME)).thenReturn(false);

        Exception exception =  assertThrows(CloudConnectorException.class,
                () -> underTest.buildDatabaseResourcesForLaunch(ac, databaseStack, persistenceNotifier));

        assertEquals("Resource group with name resource group name does not exist!", exception.getMessage());
        verify(azureUtils).getStackName(cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureResourceGroupMetadataProvider).getResourceGroupUsage(databaseStack);
        verify(azureDatabaseTemplateBuilder).build(cloudContext, databaseStack);
    }

    @Test
    void testBuildDatabaseResourcesForLaunchShouldCreateRGWhenTheExistingRGTypeIsMultiple() {
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(databaseStack)).thenReturn(ResourceGroupUsage.MULTIPLE);
        when(azureDatabaseTemplateBuilder.build(cloudContext, databaseStack)).thenReturn(TEMPLATE);
        when(client.resourceGroupExists(RESOURCE_GROUP_NAME)).thenReturn(false);
        when(cloudContext.getLocation()).thenReturn(Location.location(Region.region("region")));
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(deployment.outputs()).thenReturn(Map.of("databaseServerFQDN", Map.of("value", "fqdn")));
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));

        List<CloudResourceStatus> actual =  underTest.buildDatabaseResourcesForLaunch(ac, databaseStack, persistenceNotifier);

        assertEquals(2, actual.size());
        verify(azureUtils).getStackName(cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureResourceGroupMetadataProvider).getResourceGroupUsage(databaseStack);
        verify(azureDatabaseTemplateBuilder).build(cloudContext, databaseStack);
        verify(client).resourceGroupExists(RESOURCE_GROUP_NAME);
        verify(client).createResourceGroup(eq(RESOURCE_GROUP_NAME), any(), any());
        verify(persistenceNotifier, times(4)).notifyAllocation(any(CloudResource.class), eq(cloudContext));
        verify(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        verify(client).getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME);
    }

    @Test
    void testBuildDatabaseResourcesForLaunchWhenTheTemplateDeploymentIsAlreadyExists() {
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(databaseStack)).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureDatabaseTemplateBuilder.build(cloudContext, databaseStack)).thenReturn(TEMPLATE);
        when(client.resourceGroupExists(RESOURCE_GROUP_NAME)).thenReturn(true);
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(IN_PROGRESS);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        when(deployment.outputs()).thenReturn(Map.of("databaseServerFQDN", Map.of("value", "fqdn")));

        List<CloudResourceStatus> actual =  underTest.buildDatabaseResourcesForLaunch(ac, databaseStack, persistenceNotifier);

        assertEquals(2, actual.size());
        verify(azureUtils).getStackName(cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureResourceGroupMetadataProvider).getResourceGroupUsage(databaseStack);
        verify(azureDatabaseTemplateBuilder).build(cloudContext, databaseStack);
        verify(client).resourceGroupExists(RESOURCE_GROUP_NAME);
        verify(persistenceNotifier, times(4)).notifyAllocation(any(CloudResource.class), eq(cloudContext));
        verify(client).getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME);
    }

    @Test
    void testBuildDatabaseResourcesForLaunchShouldThrowExceptionWhenTheTemplateDeploymentThrowsManagementException() {
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(databaseStack)).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureDatabaseTemplateBuilder.build(cloudContext, databaseStack)).thenReturn(TEMPLATE);
        when(client.resourceGroupExists(RESOURCE_GROUP_NAME)).thenReturn(true);
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));
        ManagementException managementException = new ManagementException("Error", mock(HttpResponse.class));
        doThrow(managementException).when(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        String exceptionMessage = "Database stack provisioning";
        when(azureUtils.convertToCloudConnectorException(managementException, exceptionMessage))
                .thenReturn(new CloudConnectorException(exceptionMessage, managementException));
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(List.of(mock(CloudResource.class)));

        Exception exception =  assertThrows(CloudConnectorException.class,
                () -> underTest.buildDatabaseResourcesForLaunch(ac, databaseStack, persistenceNotifier));

        assertEquals(exceptionMessage, exception.getMessage());
        assertEquals(managementException, exception.getCause());
        verify(azureUtils).getStackName(cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureResourceGroupMetadataProvider).getResourceGroupUsage(databaseStack);
        verify(azureDatabaseTemplateBuilder).build(cloudContext, databaseStack);
        verify(client).resourceGroupExists(RESOURCE_GROUP_NAME);
        verify(persistenceNotifier, times(3)).notifyAllocation(any(CloudResource.class), eq(cloudContext));
        verify(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        verify(client).getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME);
        verify(azureUtils).convertToCloudConnectorException(managementException, exceptionMessage);
        verify(azureCloudResourceService).getDeploymentCloudResources(deployment);
    }

    @Test
    void testBuildDatabaseResourcesForLaunchShouldThrowExceptionWhenTheTemplateDeploymentThrowsException() {
        when(azureUtils.getStackName(cloudContext)).thenReturn(STACK_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, databaseStack)).thenReturn(RESOURCE_GROUP_NAME);
        when(azureResourceGroupMetadataProvider.getResourceGroupUsage(databaseStack)).thenReturn(ResourceGroupUsage.SINGLE);
        when(azureDatabaseTemplateBuilder.build(cloudContext, databaseStack)).thenReturn(TEMPLATE);
        when(client.resourceGroupExists(RESOURCE_GROUP_NAME)).thenReturn(true);
        when(client.getTemplateDeploymentStatus(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(DELETED);
        when(client.getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME)).thenReturn(deployment);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(retryService).testWith2SecDelayMax5Times(any(Runnable.class));
        AzureException azureException = new AzureException("Error");
        doThrow(azureException).when(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        when(azureCloudResourceService.getDeploymentCloudResources(deployment)).thenReturn(List.of(mock(CloudResource.class)));

        Exception exception =  assertThrows(CloudConnectorException.class,
                () -> underTest.buildDatabaseResourcesForLaunch(ac, databaseStack, persistenceNotifier));

        assertEquals("Error in provisioning database stack aStack: Error", exception.getMessage());
        assertEquals(azureException, exception.getCause());
        verify(azureUtils).getStackName(cloudContext);
        verify(azureResourceGroupMetadataProvider).getResourceGroupName(cloudContext, databaseStack);
        verify(azureResourceGroupMetadataProvider).getResourceGroupUsage(databaseStack);
        verify(azureDatabaseTemplateBuilder).build(cloudContext, databaseStack);
        verify(client).resourceGroupExists(RESOURCE_GROUP_NAME);
        verify(persistenceNotifier, times(3)).notifyAllocation(any(CloudResource.class), eq(cloudContext));
        verify(client).createTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME, TEMPLATE, "{}");
        verify(client).getTemplateDeployment(RESOURCE_GROUP_NAME, STACK_NAME);
        verify(azureCloudResourceService).getDeploymentCloudResources(deployment);
    }

    @Test
    void updateAdministratorLoginPasswordShouldSucceed() {
        when(databaseStack.getDatabaseServer()).thenReturn(DatabaseServer.builder().withServerId(SERVER_NAME).build());
        when(azureResourceGroupMetadataProvider.getResourceGroupName(eq(cloudContext), eq(databaseStack))).thenReturn(RESOURCE_GROUP_NAME);

        underTest.updateAdministratorLoginPassword(ac, databaseStack, NEW_PASSWORD);

        verify(azureResourceGroupMetadataProvider, times(1)).getResourceGroupName(eq(cloudContext), eq(databaseStack));
        verify(client, times(1)).updateAdministratorLoginPassword(eq(RESOURCE_GROUP_NAME), eq(SERVER_NAME), eq(NEW_PASSWORD));
    }

    @Test
    void updateAdministratorLoginPasswordShouldFailWhenClientThrowsException() {
        when(databaseStack.getDatabaseServer()).thenReturn(DatabaseServer.builder().withServerId(SERVER_NAME).build());
        when(azureResourceGroupMetadataProvider.getResourceGroupName(eq(cloudContext), eq(databaseStack))).thenReturn(RESOURCE_GROUP_NAME);
        doThrow(new RuntimeException("error")).when(client).updateAdministratorLoginPassword(eq(RESOURCE_GROUP_NAME), eq(SERVER_NAME), eq(NEW_PASSWORD));

        CloudConnectorException cloudConnectorException = assertThrows(CloudConnectorException.class,
                () -> underTest.updateAdministratorLoginPassword(ac, databaseStack, NEW_PASSWORD));

        assertEquals("error", cloudConnectorException.getMessage());
        verify(azureResourceGroupMetadataProvider, times(1)).getResourceGroupName(eq(cloudContext), eq(databaseStack));
        verify(client, times(1)).updateAdministratorLoginPassword(eq(RESOURCE_GROUP_NAME), eq(SERVER_NAME), eq(NEW_PASSWORD));
    }

    private CloudResource buildResource(ResourceType resourceType) {
        return buildResource(resourceType, "name");
    }

    private CloudResource buildResource(ResourceType resourceType, String name) {
        return CloudResource.builder()
                .withType(resourceType)
                .withReference(RESOURCE_REFERENCE)
                .withName(name)
                .withStatus(CommonStatus.CREATED)
                .withParameters(Map.of())
                .build();
    }

    private DatabaseServer buildDatabaseServer() {
        Map<String, Object> map = new HashMap<>();
        map.put("dbVersion", "10");

        return DatabaseServer.builder()
                .withConnectionDriver("driver")
                .withServerId("driver")
                .withConnectorJarUrl("driver")
                .withEngine(DatabaseEngine.POSTGRESQL)
                .withLocation("location")
                .withPort(99)
                .withStorageSize(50L)
                .withRootUserName("rootUserName")
                .withRootPassword("rootPassword")
                .withFlavor("flavor")
                .withUseSslEnforcement(true)
                .withParams(map)
                .build();
    }

    private void initRetry() {
        when(retryService.testWith2SecDelayMax15Times(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0, Supplier.class).get());
    }
}
