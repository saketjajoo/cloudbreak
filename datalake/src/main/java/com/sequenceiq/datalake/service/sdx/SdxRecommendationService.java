package com.sequenceiq.datalake.service.sdx;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.StackV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.instancegroup.InstanceGroupV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.VmTypeV4Response;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmTypes;
import com.sequenceiq.cloudbreak.cloud.model.VmType;
import com.sequenceiq.cloudbreak.cloud.service.CloudParameterService;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.common.api.type.CdpResourceType;
import com.sequenceiq.datalake.configuration.CDPConfigService;
import com.sequenceiq.datalake.converter.CredentialConverter;
import com.sequenceiq.datalake.converter.SdxRecommendationConverter;
import com.sequenceiq.datalake.service.EnvironmentClientService;
import com.sequenceiq.environment.api.v1.credential.model.response.CredentialResponse;
import com.sequenceiq.sdx.api.model.SdxClusterShape;
import com.sequenceiq.sdx.api.model.SdxRecommendationResponse;

@Service
public class SdxRecommendationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SdxRecommendationService.class);

    @Inject
    private EnvironmentClientService environmentClientService;

    @Inject
    private CDPConfigService cdpConfigService;

    @Inject
    private CloudParameterService cloudParameterService;

    @Inject
    private CredentialConverter credentialConverter;

    @Inject
    private SdxRecommendationConverter sdxRecommendationConverter;

    public SdxRecommendationResponse getRecommendation(String credentialCrn, String region, String platformVariant, String runtime,
            SdxClusterShape clusterShape, String availabilityZone) {
        if (clusterShape == null || StringUtils.isAnyBlank(credentialCrn, region, platformVariant, runtime)) {
            throw new BadRequestException(
                    "The following query params needs to be filled for this request: credentialCrn, region, platformVariant, runtime, clusterShape");
        }

        CredentialResponse credential = ThreadBasedUserCrnProvider.doAsInternalActor(() -> environmentClientService.getCredentialByCrn(credentialCrn));
        CloudPlatform cloudPlatform = CloudPlatform.valueOf(credential.getCloudPlatform());
        StackV4Request defaultTemplate = cdpConfigService.getConfigForKey(new CDPConfigKey(cloudPlatform, clusterShape, runtime));
        if (defaultTemplate == null) {
            LOGGER.error("Can't find template for cloudplatform: {}, shape {}, cdp version: {}", cloudPlatform, clusterShape, runtime);
            throw new BadRequestException("Can't find template for cloudplatform: " + cloudPlatform + ", shape: " + clusterShape +
                    ", runtime version: " + runtime);
        }

        Map<String, VmTypeV4Response> virtualMachineTypes = collectVirtualMachineTypes(region, platformVariant, availabilityZone, credential);
        Map<String, VmTypeV4Response> vmTypesForDefaultTemplate = collectVmTypesForDefaultTemplate(defaultTemplate, virtualMachineTypes);

        SdxRecommendationResponse response = new SdxRecommendationResponse();
        response.setDefaultTemplate(defaultTemplate);
        response.setDefaultVmTypes(vmTypesForDefaultTemplate);
        response.setVirtualMachines(new HashSet<>(virtualMachineTypes.values()));
        return response;
    }

    private Map<String, VmTypeV4Response> collectVirtualMachineTypes(String region, String platformVariant, String availabilityZone,
            CredentialResponse credential) {
        CloudVmTypes vmTypes = cloudParameterService.getVmTypesV2(credentialConverter.convert(credential), region, platformVariant,
                CdpResourceType.DATALAKE, Maps.newHashMap());

        Set<VmType> availableVmTypes = null;
        if (StringUtils.isNotBlank(availabilityZone) && vmTypes.getCloudVmResponses() != null) {
            availableVmTypes = vmTypes.getCloudVmResponses().get(availabilityZone);
        } else if (vmTypes.getCloudVmResponses() != null && !vmTypes.getCloudVmResponses().isEmpty()) {
            availableVmTypes = vmTypes.getCloudVmResponses().values().iterator().next();
        }
        if (availableVmTypes == null) {
            availableVmTypes = Collections.emptySet();
        }

        Map<String, VmTypeV4Response> virtualMachines = availableVmTypes.stream()
                .map(vmType -> sdxRecommendationConverter.convertVmTypeToVmTypV4Response(vmType))
                .collect(Collectors.toMap(VmTypeV4Response::getValue, Function.identity()));
        return virtualMachines;
    }

    private Map<String, VmTypeV4Response> collectVmTypesForDefaultTemplate(StackV4Request defaultTemplate, Map<String, VmTypeV4Response> virtualMachineTypes) {
        Map<String, VmTypeV4Response> vmTypesForDefaultTemplate = new HashMap<>();
        for (InstanceGroupV4Request instanceGroup : defaultTemplate.getInstanceGroups()) {
            String instanceGroupName = instanceGroup.getName();
            String instanceType = instanceGroup.getTemplate().getInstanceType();
            if (virtualMachineTypes.containsKey(instanceType)) {
                vmTypesForDefaultTemplate.put(instanceGroupName, virtualMachineTypes.get(instanceType));
            } else {
                LOGGER.debug("Missing default vm type for instance group: {}, instance type: {}", instanceGroupName, instanceType);
            }
        }
        return vmTypesForDefaultTemplate;
    }
}
