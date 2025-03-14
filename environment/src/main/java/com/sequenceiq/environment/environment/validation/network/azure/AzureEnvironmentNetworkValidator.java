package com.sequenceiq.environment.environment.validation.network.azure;

import static com.sequenceiq.cloudbreak.common.mappable.CloudPlatform.AZURE;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.cloud.model.CloudSubnet;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.cloudbreak.validation.ValidationResult.ValidationResultBuilder;
import com.sequenceiq.environment.environment.domain.Region;
import com.sequenceiq.environment.environment.dto.EnvironmentDto;
import com.sequenceiq.environment.environment.dto.EnvironmentValidationDto;
import com.sequenceiq.environment.environment.validation.ValidationType;
import com.sequenceiq.environment.environment.validation.network.EnvironmentNetworkValidator;
import com.sequenceiq.environment.network.CloudNetworkService;
import com.sequenceiq.environment.network.dto.AzureParams;
import com.sequenceiq.environment.network.dto.NetworkDto;

@Component
public class AzureEnvironmentNetworkValidator implements EnvironmentNetworkValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureEnvironmentNetworkValidator.class);

    private final CloudNetworkService cloudNetworkService;

    private final AzurePrivateEndpointValidator azurePrivateEndpointValidator;

    @Value("${cb.multiaz.azure.availabilityZones}")
    private Set<String> azureAvailabilityZones;

    public AzureEnvironmentNetworkValidator(CloudNetworkService cloudNetworkService,
            AzurePrivateEndpointValidator azurePrivateEndpointValidator) {
        this.cloudNetworkService = cloudNetworkService;
        this.azurePrivateEndpointValidator = azurePrivateEndpointValidator;
    }

    @Override
    public void validateDuringFlow(EnvironmentValidationDto environmentValidationDto, NetworkDto networkDto, ValidationResultBuilder resultBuilder) {
        if (environmentValidationDto == null || environmentValidationDto.getEnvironmentDto() == null || networkDto == null) {
            LOGGER.warn("Neither EnvironmentDto nor NetworkDto could be null!");
            resultBuilder.error("Internal validation error");
            return;
        }
        EnvironmentDto environmentDto = environmentValidationDto.getEnvironmentDto();
        Map<String, CloudSubnet> cloudNetworks = cloudNetworkService.retrieveSubnetMetadata(environmentDto, networkDto);
        Region region = environmentDto.getRegions()
                .stream().findFirst()
                .orElseThrow();
        checkSubnetsProvidedWhenExistingNetwork(resultBuilder, networkDto, networkDto.getAzure(), cloudNetworks, region);
        if (environmentValidationDto.getValidationType() == ValidationType.ENVIRONMENT_CREATION) {
            azurePrivateEndpointValidator.checkNetworkPoliciesWhenExistingNetwork(networkDto, cloudNetworks, resultBuilder);
            azurePrivateEndpointValidator.checkMultipleResourceGroup(resultBuilder, environmentDto, networkDto);
            azurePrivateEndpointValidator.checkExistingManagedPrivateDnsZone(resultBuilder, environmentDto, networkDto);
            azurePrivateEndpointValidator.checkNewPrivateDnsZone(resultBuilder, environmentDto, networkDto);
            azurePrivateEndpointValidator.checkExistingRegisteredOnlyPrivateDnsZone(resultBuilder, environmentDto, networkDto);
        } else {
            LOGGER.debug("Skipping Private Endpoint related validations as they have been validated before during env creation");
        }
    }

    @Override
    public void validateDuringRequest(NetworkDto networkDto, ValidationResultBuilder resultBuilder) {
        if (networkDto == null) {
            return;
        }

        checkEitherNetworkCidrOrNetworkIdIsPresent(networkDto, resultBuilder);

        AzureParams azureParams = networkDto.getAzure();
        if (azureParams != null) {
            checkSubnetsProvidedWhenExistingNetwork(resultBuilder, azureParams, networkDto.getSubnetMetas());
            checkExistingNetworkParamsProvidedWhenSubnetsPresent(networkDto, resultBuilder);
            checkResourceGroupNameWhenExistingNetwork(resultBuilder, azureParams);
            checkNetworkIdWhenExistingNetwork(resultBuilder, azureParams);
            checkNetworkIdIsSpecifiedWhenSubnetIdsArePresent(resultBuilder, azureParams, networkDto);
            validateAvailabilityZones(resultBuilder, azureParams);
        } else if (StringUtils.isEmpty(networkDto.getNetworkCidr())) {
            resultBuilder.error(missingParamsErrorMsg(AZURE));
        }
    }

    private void checkEitherNetworkCidrOrNetworkIdIsPresent(NetworkDto networkDto, ValidationResultBuilder resultBuilder) {
        if (StringUtils.isEmpty(networkDto.getNetworkCidr()) && StringUtils.isEmpty(networkDto.getNetworkId())) {
            String message = "Either the AZURE network id or cidr needs to be defined!";
            LOGGER.info(message);
            resultBuilder.error(message);
        }
    }

    private void checkSubnetsProvidedWhenExistingNetwork(ValidationResultBuilder resultBuilder, NetworkDto network,
            AzureParams azureParams, Map<String, CloudSubnet> subnetMetas, Region region) {
        if (StringUtils.isNotEmpty(azureParams.getNetworkId()) && StringUtils.isNotEmpty(azureParams.getResourceGroupName())) {
            if (CollectionUtils.isEmpty(network.getSubnetIds())) {
                String message = String.format("If networkId (%s) and resourceGroupName (%s) are specified then subnet ids must be specified as well.",
                        azureParams.getNetworkId(), azureParams.getResourceGroupName());
                LOGGER.info(message);
                resultBuilder.error(message);
            } else if (subnetMetas.size() != network.getSubnetIds().size()) {
                String message = String.format("If networkId (%s) and resourceGroupName (%s) are specified then subnet ids must be specified and should exist " +
                                "on azure as well. Given subnetids: [%s], existing ones: [%s], in region: [%s]", azureParams.getNetworkId(),
                        azureParams.getResourceGroupName(), network.getSubnetIds().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")),
                        subnetMetas.keySet().stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")), region.getDisplayName());
                LOGGER.info(message);
                resultBuilder.error(message);
            }
        }
    }

    private void checkResourceGroupNameWhenExistingNetwork(ValidationResultBuilder resultBuilder, AzureParams azureParams) {
        if (StringUtils.isNotEmpty(azureParams.getNetworkId()) && StringUtils.isEmpty(azureParams.getResourceGroupName())) {
            resultBuilder.error("If networkId is specified, then resourceGroupName must be specified too.");
        }
    }

    private void checkNetworkIdWhenExistingNetwork(ValidationResultBuilder resultBuilder, AzureParams azureParams) {
        if (StringUtils.isEmpty(azureParams.getNetworkId()) && StringUtils.isNotEmpty(azureParams.getResourceGroupName())) {
            resultBuilder.error("If resourceGroupName is specified, then networkId must be specified too.");
        }
    }

    private void checkExistingNetworkParamsProvidedWhenSubnetsPresent(NetworkDto networkDto, ValidationResultBuilder resultBuilder) {
        if (!networkDto.getSubnetIds().isEmpty()
                && StringUtils.isEmpty(networkDto.getAzure().getNetworkId())
                && StringUtils.isEmpty(networkDto.getAzure().getResourceGroupName())) {
            String message =
                    String.format("If %s subnet ids were provided then network id and resource group name have to be specified, too.", AZURE.name());
            LOGGER.info(message);
            resultBuilder.error(message);
        }
    }

    private void checkNetworkIdIsSpecifiedWhenSubnetIdsArePresent(ValidationResultBuilder resultBuilder,
            AzureParams azureParams, NetworkDto networkDto) {
        if (StringUtils.isEmpty(azureParams.getNetworkId()) && CollectionUtils.isNotEmpty(networkDto.getSubnetIds())) {
            resultBuilder.error("If subnetIds are specified, then networkId must be specified too.");
        }
    }

    private void checkSubnetsProvidedWhenExistingNetwork(ValidationResultBuilder resultBuilder,
            AzureParams azureParams, Map<String, CloudSubnet> subnetMetas) {
        if (StringUtils.isNotEmpty(azureParams.getNetworkId()) && StringUtils.isNotEmpty(azureParams.getResourceGroupName())
                && MapUtils.isEmpty(subnetMetas)) {
            String message = String.format("If networkId (%s) and resourceGroupName (%s) are specified then subnet ids must be specified as well.",
                    azureParams.getNetworkId(), azureParams.getResourceGroupName());
            LOGGER.info(message);
            resultBuilder.error(message);
        }
    }

    private void validateAvailabilityZones(ValidationResultBuilder resultBuilder, AzureParams azureParams) {
        if (CollectionUtils.isNotEmpty(azureParams.getAvailabilityZones())) {
            Set<String> invalidAvailabilityZones = azureParams.getAvailabilityZones().stream().filter(az -> !azureAvailabilityZones.contains(az))
                    .collect(Collectors.toSet());
            if (CollectionUtils.isNotEmpty(invalidAvailabilityZones)) {
                String message = String.format("Availability zones %s are not valid. Valid availability zones are %s.",
                        invalidAvailabilityZones.stream().sorted().collect(Collectors.joining(",")),
                        azureAvailabilityZones.stream().sorted().collect(Collectors.joining(",")));
                LOGGER.error(message);
                resultBuilder.error(message);
            }
        }
    }

    @Override
    public CloudPlatform getCloudPlatform() {
        return AZURE;
    }

}
