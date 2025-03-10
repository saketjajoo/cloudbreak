package com.sequenceiq.cloudbreak.auth.altus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloudera.thunderhead.service.usermanagement.UserManagementProto.Account;
import com.cloudera.thunderhead.service.usermanagement.UserManagementProto.Entitlement;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    private static final String ENTITLEMENT_FOO = "FOO";

    private static final String ENTITLEMENT_BAR = "BAR";

    private static final Account ACCOUNT_ENTITLEMENTS_FOO_BAR = createAccountForEntitlements(ENTITLEMENT_FOO, ENTITLEMENT_BAR);

    @Mock
    private GrpcUmsClient umsClient;

    @InjectMocks
    private EntitlementService underTest;

    static Object[][] entitlementCheckDataProvider() {
        return new Object[][]{

                // entitlementName, function, enabled
                {"AUDIT_ARCHIVING_GCP", (EntitlementCheckFunction) EntitlementService::gcpAuditEnabled, false},
                {"AUDIT_ARCHIVING_GCP", (EntitlementCheckFunction) EntitlementService::gcpAuditEnabled, true},

                {"CDP_CB_AWS_NATIVE", (EntitlementCheckFunction) EntitlementService::awsNativeEnabled, false},
                {"CDP_CB_AWS_NATIVE", (EntitlementCheckFunction) EntitlementService::awsNativeEnabled, true},

                {"CDP_CB_AWS_NATIVE_DATALAKE", (EntitlementCheckFunction) EntitlementService::awsNativeDataLakeEnabled, false},
                {"CDP_CB_AWS_NATIVE_DATALAKE", (EntitlementCheckFunction) EntitlementService::awsNativeDataLakeEnabled, true},

                {"CDP_CB_AWS_NATIVE_FREEIPA", (EntitlementCheckFunction) EntitlementService::awsNativeFreeIpaEnabled, false},
                {"CDP_CB_AWS_NATIVE_FREEIPA", (EntitlementCheckFunction) EntitlementService::awsNativeFreeIpaEnabled, true},

                {"CDP_BASE_IMAGE", (EntitlementCheckFunction) EntitlementService::baseImageEnabled, false},
                {"CDP_BASE_IMAGE", (EntitlementCheckFunction) EntitlementService::baseImageEnabled, true},

                {"CDP_FREEIPA_REBUILD", (EntitlementCheckFunction) EntitlementService::isFreeIpaRebuildEnabled, false},
                {"CDP_FREEIPA_REBUILD", (EntitlementCheckFunction) EntitlementService::isFreeIpaRebuildEnabled, true},

                {"CLOUDERA_INTERNAL_ACCOUNT", (EntitlementCheckFunction) EntitlementService::internalTenant, false},
                {"CLOUDERA_INTERNAL_ACCOUNT", (EntitlementCheckFunction) EntitlementService::internalTenant, true},

                {"CDP_CLOUD_STORAGE_VALIDATION", (EntitlementCheckFunction) EntitlementService::cloudStorageValidationEnabled, false},
                {"CDP_CLOUD_STORAGE_VALIDATION", (EntitlementCheckFunction) EntitlementService::cloudStorageValidationEnabled, true},

                {"LOCAL_DEV", (EntitlementCheckFunction) EntitlementService::localDevelopment, false},
                {"LOCAL_DEV", (EntitlementCheckFunction) EntitlementService::localDevelopment, true},

                {"CDP_CLOUD_IDENTITY_MAPPING", (EntitlementCheckFunction) EntitlementService::cloudIdentityMappingEnabled, false},
                {"CDP_CLOUD_IDENTITY_MAPPING", (EntitlementCheckFunction) EntitlementService::cloudIdentityMappingEnabled, true},

                {"CDP_ALLOW_INTERNAL_REPOSITORY_FOR_UPGRADE", (EntitlementCheckFunction) EntitlementService::isInternalRepositoryForUpgradeAllowed, true},
                {"CDP_ALLOW_INTERNAL_REPOSITORY_FOR_UPGRADE", (EntitlementCheckFunction) EntitlementService::isInternalRepositoryForUpgradeAllowed, false},

                {"CDP_SDX_HBASE_CLOUD_STORAGE", (EntitlementCheckFunction) EntitlementService::sdxHbaseCloudStorageEnabled, false},
                {"CDP_SDX_HBASE_CLOUD_STORAGE", (EntitlementCheckFunction) EntitlementService::sdxHbaseCloudStorageEnabled, true},

                {"CDP_DATA_LAKE_AWS_EFS", (EntitlementCheckFunction) EntitlementService::dataLakeEfsEnabled, false},
                {"CDP_DATA_LAKE_AWS_EFS", (EntitlementCheckFunction) EntitlementService::dataLakeEfsEnabled, true},

                {"CDP_TRIAL", (EntitlementCheckFunction) EntitlementService::cdpTrialEnabled, false},
                {"CDP_TRIAL", (EntitlementCheckFunction) EntitlementService::cdpTrialEnabled, true},

                {"CDP_ALLOW_DIFFERENT_DATAHUB_VERSION_THAN_DATALAKE",
                        (EntitlementCheckFunction) EntitlementService::isDifferentDataHubAndDataLakeVersionAllowed, false},
                {"CDP_ALLOW_DIFFERENT_DATAHUB_VERSION_THAN_DATALAKE",
                        (EntitlementCheckFunction) EntitlementService::isDifferentDataHubAndDataLakeVersionAllowed, true},

                {"CDP_AZURE_SINGLE_RESOURCE_GROUP_DEDICATED_STORAGE_ACCOUNT",
                        (EntitlementCheckFunction) EntitlementService::azureSingleResourceGroupDedicatedStorageAccountEnabled, false},
                {"CDP_AZURE_SINGLE_RESOURCE_GROUP_DEDICATED_STORAGE_ACCOUNT",
                        (EntitlementCheckFunction) EntitlementService::azureSingleResourceGroupDedicatedStorageAccountEnabled, true},

                {"DATAHUB_AWS_AUTOSCALING", (EntitlementCheckFunction) EntitlementService::awsAutoScalingEnabled, false},
                {"DATAHUB_AWS_AUTOSCALING", (EntitlementCheckFunction) EntitlementService::awsAutoScalingEnabled, true},

                {"DATAHUB_AZURE_AUTOSCALING", (EntitlementCheckFunction) EntitlementService::azureAutoScalingEnabled, false},
                {"DATAHUB_AZURE_AUTOSCALING", (EntitlementCheckFunction) EntitlementService::azureAutoScalingEnabled, true},

                {"DATAHUB_GCP_AUTOSCALING", (EntitlementCheckFunction) EntitlementService::gcpAutoScalingEnabled, false},
                {"DATAHUB_GCP_AUTOSCALING", (EntitlementCheckFunction) EntitlementService::gcpAutoScalingEnabled, true},

                {"DATAHUB_AWS_STOP_START_SCALING", (EntitlementCheckFunction) EntitlementService::awsStopStartScalingEnabled, false},
                {"DATAHUB_AWS_STOP_START_SCALING", (EntitlementCheckFunction) EntitlementService::awsStopStartScalingEnabled, true},

                {"DATAHUB_AZURE_STOP_START_SCALING", (EntitlementCheckFunction) EntitlementService::azureStopStartScalingEnabled, false},
                {"DATAHUB_AZURE_STOP_START_SCALING", (EntitlementCheckFunction) EntitlementService::azureStopStartScalingEnabled, true},

                {"DATAHUB_GCP_STOP_START_SCALING", (EntitlementCheckFunction) EntitlementService::gcpStopStartScalingEnabled, false},
                {"DATAHUB_GCP_STOP_START_SCALING", (EntitlementCheckFunction) EntitlementService::gcpStopStartScalingEnabled, true},

                {"DATAHUB_STOP_START_SCALING_FAILURE_RECOVERY",
                        (EntitlementCheckFunction) EntitlementService::stopStartScalingFailureRecoveryEnabled, false},
                {"DATAHUB_STOP_START_SCALING_FAILURE_RECOVERY",
                        (EntitlementCheckFunction) EntitlementService::stopStartScalingFailureRecoveryEnabled, true},

                {"CDP_CB_DATABASE_WIRE_ENCRYPTION_DATAHUB", (EntitlementCheckFunction) EntitlementService::databaseWireEncryptionDatahubEnabled, false},
                {"CDP_CB_DATABASE_WIRE_ENCRYPTION_DATAHUB", (EntitlementCheckFunction) EntitlementService::databaseWireEncryptionDatahubEnabled, true},

                {"CDP_DATA_LAKE_LOAD_BALANCER", (EntitlementCheckFunction) EntitlementService::datalakeLoadBalancerEnabled, false},
                {"CDP_DATA_LAKE_LOAD_BALANCER", (EntitlementCheckFunction) EntitlementService::datalakeLoadBalancerEnabled, true},

                {"CDP_DATA_LAKE_LOAD_BALANCER_AZURE", (EntitlementCheckFunction) EntitlementService::azureDatalakeLoadBalancerEnabled, false},
                {"CDP_DATA_LAKE_LOAD_BALANCER_AZURE", (EntitlementCheckFunction) EntitlementService::azureDatalakeLoadBalancerEnabled, true},

                {"CDP_DATALAKE_RESIZE_RECOVERY", (EntitlementCheckFunction) EntitlementService::isDatalakeResizeRecoveryEnabled, false},
                {"CDP_DATALAKE_RESIZE_RECOVERY", (EntitlementCheckFunction) EntitlementService::isDatalakeResizeRecoveryEnabled, true},

                {"CDP_PUBLIC_ENDPOINT_ACCESS_GATEWAY_AZURE", (EntitlementCheckFunction) EntitlementService::azureEndpointGatewayEnabled, false},
                {"CDP_PUBLIC_ENDPOINT_ACCESS_GATEWAY_AZURE", (EntitlementCheckFunction) EntitlementService::azureEndpointGatewayEnabled, true},

                {"CDP_PUBLIC_ENDPOINT_ACCESS_GATEWAY_GCP", (EntitlementCheckFunction) EntitlementService::gcpEndpointGatewayEnabled, false},
                {"CDP_PUBLIC_ENDPOINT_ACCESS_GATEWAY_GCP", (EntitlementCheckFunction) EntitlementService::gcpEndpointGatewayEnabled, true},

                {"CDP_CB_AZURE_ENCRYPTION_AT_HOST", (EntitlementCheckFunction) EntitlementService::isAzureEncryptionAtHostEnabled, false},
                {"CDP_CB_AZURE_ENCRYPTION_AT_HOST", (EntitlementCheckFunction) EntitlementService::isAzureEncryptionAtHostEnabled, true},

                {"CDP_USER_SYNC_CREDENTIALS_UPDATE_OPTIMIZATION",
                        (EntitlementCheckFunction) EntitlementService::usersyncCredentialsUpdateOptimizationEnabled, false},
                {"CDP_USER_SYNC_CREDENTIALS_UPDATE_OPTIMIZATION",
                        (EntitlementCheckFunction) EntitlementService::usersyncCredentialsUpdateOptimizationEnabled, true},

                {"CDP_ENDPOINT_GATEWAY_SKIP_VALIDATION", (EntitlementCheckFunction) EntitlementService::endpointGatewaySkipValidation, false},
                {"CDP_ENDPOINT_GATEWAY_SKIP_VALIDATION", (EntitlementCheckFunction) EntitlementService::endpointGatewaySkipValidation, true},

                {"CDP_CM_HA", (EntitlementCheckFunction) EntitlementService::cmHAEnabled, false},
                {"CDP_CM_HA", (EntitlementCheckFunction) EntitlementService::cmHAEnabled, true},

                {"CDP_AWS_RESTRICTED_POLICY", (EntitlementCheckFunction) EntitlementService::awsRestrictedPolicy, false},
                {"CDP_AWS_RESTRICTED_POLICY", (EntitlementCheckFunction) EntitlementService::awsRestrictedPolicy, true},

                {"CDP_CONCLUSION_CHECKER_SEND_USER_EVENT", (EntitlementCheckFunction) EntitlementService::conclusionCheckerSendUserEventEnabled, false},
                {"CDP_CONCLUSION_CHECKER_SEND_USER_EVENT", (EntitlementCheckFunction) EntitlementService::conclusionCheckerSendUserEventEnabled, true},

                {"CDP_NODESTATUS_ENABLE_SALT_PING", (EntitlementCheckFunction) EntitlementService::nodestatusSaltPingEnabled, false},
                {"CDP_NODESTATUS_ENABLE_SALT_PING", (EntitlementCheckFunction) EntitlementService::nodestatusSaltPingEnabled, true},

                {"E2E_TEST_ONLY", (EntitlementCheckFunction) EntitlementService::isE2ETestOnlyEnabled, false},
                {"E2E_TEST_ONLY", (EntitlementCheckFunction) EntitlementService::isE2ETestOnlyEnabled, true},

                {"CDP_DATALAKE_ZDU_OS_UPGRADE", (EntitlementCheckFunction) EntitlementService::isDatalakeZduOSUpgradeEnabled, false},
                {"CDP_DATALAKE_ZDU_OS_UPGRADE", (EntitlementCheckFunction) EntitlementService::isDatalakeZduOSUpgradeEnabled, true},

                {"CDP_ENVIRONMENT_PRIVILEGED_USER", (EntitlementCheckFunction) EntitlementService::isEnvironmentPrivilegedUserEnabled, false},
                {"CDP_ENVIRONMENT_PRIVILEGED_USER", (EntitlementCheckFunction) EntitlementService::isEnvironmentPrivilegedUserEnabled, true},

                {"WORKLOAD_IAM_SYNC", (EntitlementCheckFunction) EntitlementService::isWorkloadIamSyncEnabled, false},
                {"WORKLOAD_IAM_SYNC", (EntitlementCheckFunction) EntitlementService::isWorkloadIamSyncEnabled, true},

                {"CDP_FMS_USERSYNC_THREAD_TIMEOUT", (EntitlementCheckFunction) EntitlementService::isUserSyncThreadTimeoutEnabled, false},
                {"CDP_FMS_USERSYNC_THREAD_TIMEOUT", (EntitlementCheckFunction) EntitlementService::isUserSyncThreadTimeoutEnabled, true},

                {"CDP_FMS_DELAYED_STOP_START", (EntitlementCheckFunction) EntitlementService::isFmsDelayedStopStartEnabled, false},
                {"CDP_FMS_DELAYED_STOP_START", (EntitlementCheckFunction) EntitlementService::isFmsDelayedStopStartEnabled, true},

                {"CDP_USERSYNC_ENFORCE_GROUP_MEMBER_LIMIT", (EntitlementCheckFunction) EntitlementService::isUserSyncEnforceGroupMembershipLimitEnabled, false},
                {"CDP_USERSYNC_ENFORCE_GROUP_MEMBER_LIMIT", (EntitlementCheckFunction) EntitlementService::isUserSyncEnforceGroupMembershipLimitEnabled, true},

                {"CDP_USERSYNC_SPLIT_FREEIPA_USER_RETRIEVAL", (EntitlementCheckFunction) EntitlementService::isUserSyncSplitFreeIPAUserRetrievalEnabled, false},
                {"CDP_USERSYNC_SPLIT_FREEIPA_USER_RETRIEVAL", (EntitlementCheckFunction) EntitlementService::isUserSyncSplitFreeIPAUserRetrievalEnabled, true},

                {"TARGETING_SUBNETS_FOR_ENDPOINT_ACCESS_GATEWAY",
                        (EntitlementCheckFunction) EntitlementService::isTargetingSubnetsForEndpointAccessGatewayEnabled, false},
                {"TARGETING_SUBNETS_FOR_ENDPOINT_ACCESS_GATEWAY",
                        (EntitlementCheckFunction) EntitlementService::isTargetingSubnetsForEndpointAccessGatewayEnabled, true},

                {"CDP_AZURE_IMAGE_MARKETPLACE", (EntitlementCheckFunction) EntitlementService::azureMarketplaceImagesEnabled, false},
                {"CDP_AZURE_IMAGE_MARKETPLACE", (EntitlementCheckFunction) EntitlementService::azureMarketplaceImagesEnabled, true},

                {"CDP_AZURE_IMAGE_MARKETPLACE_ONLY", (EntitlementCheckFunction) EntitlementService::azureOnlyMarketplaceImagesEnabled, false},
                {"CDP_AZURE_IMAGE_MARKETPLACE_ONLY", (EntitlementCheckFunction) EntitlementService::azureOnlyMarketplaceImagesEnabled, true},

                {"WORKLOAD_IAM_USERSYNC_ROUTING", (EntitlementCheckFunction) EntitlementService::isWiamUsersyncRoutingEnabled, false},
                {"WORKLOAD_IAM_USERSYNC_ROUTING", (EntitlementCheckFunction) EntitlementService::isWiamUsersyncRoutingEnabled, true},

                {"CDP_FEDRAMP_EXTERNAL_DATABASE_FORCE_DISABLED", (EntitlementCheckFunction) EntitlementService::isFedRampExternalDatabaseForceDisabled, false},
                {"CDP_FEDRAMP_EXTERNAL_DATABASE_FORCE_DISABLED", (EntitlementCheckFunction) EntitlementService::isFedRampExternalDatabaseForceDisabled, true},

                {"CDP_AZURE_DATABASE_FLEXIBLE_SERVER", (EntitlementCheckFunction) EntitlementService::isAzureDatabaseFlexibleServerEnabled, false},
                {"CDP_AZURE_DATABASE_FLEXIBLE_SERVER", (EntitlementCheckFunction) EntitlementService::isAzureDatabaseFlexibleServerEnabled, true},
        };
    }

    @ParameterizedTest(name = "{0} == {2}")
    @MethodSource("entitlementCheckDataProvider")
    void entitlementEnabledTestWhenNoOtherEntitlementsAreGranted(String entitlementName, EntitlementCheckFunction function, boolean enabled) {
        setUpUmsClient(entitlementName, enabled);
        assertThat(function.entitlementEnabled(underTest, ACCOUNT_ID)).isEqualTo(enabled);
    }

    @Test
    void getEntitlementsTest() {
        when(umsClient.getAccountDetails(eq(ACCOUNT_ID), any()))
                .thenReturn(ACCOUNT_ENTITLEMENTS_FOO_BAR);
        assertThat(underTest.getEntitlements(ACCOUNT_ID)).containsExactly(ENTITLEMENT_FOO, ENTITLEMENT_BAR);
    }

    private void setUpUmsClient(String entitlement, boolean entitled) {
        Account.Builder builder = Account.newBuilder();
        if (entitled) {
            builder.addEntitlements(
                    Entitlement.newBuilder()
                            .setEntitlementName(entitlement)
                            .build());
        }
        when(umsClient.getAccountDetails(eq(ACCOUNT_ID), any()))
                .thenReturn(builder.build());
    }

    private static Account createAccountForEntitlements(String... entitlementNames) {
        // Protobuf wrappers are all finals, so cannot be mocked
        Account.Builder builder = Account.newBuilder();
        Arrays.stream(entitlementNames).forEach(entitlementName -> builder.addEntitlements(createEntitlement(entitlementName)));
        return builder.build();
    }

    private static Entitlement createEntitlement(String entitlementName) {
        return Entitlement.newBuilder().setEntitlementName(entitlementName).build();
    }

    @FunctionalInterface
    private interface EntitlementCheckFunction {
        boolean entitlementEnabled(EntitlementService service, String accountId);
    }

}
