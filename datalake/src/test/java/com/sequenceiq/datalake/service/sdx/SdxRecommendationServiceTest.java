package com.sequenceiq.datalake.service.sdx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.StackV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.instancegroup.InstanceGroupV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.instancegroup.template.InstanceTemplateV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.VmTypeV4Response;
import com.sequenceiq.cloudbreak.cloud.model.CloudVmTypes;
import com.sequenceiq.cloudbreak.cloud.model.VmType;
import com.sequenceiq.cloudbreak.cloud.model.VmTypeMeta;
import com.sequenceiq.cloudbreak.cloud.service.CloudParameterService;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.datalake.configuration.CDPConfigService;
import com.sequenceiq.datalake.converter.CredentialConverter;
import com.sequenceiq.datalake.converter.SdxRecommendationConverter;
import com.sequenceiq.datalake.service.EnvironmentClientService;
import com.sequenceiq.environment.api.v1.credential.model.response.CredentialResponse;
import com.sequenceiq.sdx.api.model.SdxClusterShape;
import com.sequenceiq.sdx.api.model.SdxRecommendationResponse;

@ExtendWith(MockitoExtension.class)
class SdxRecommendationServiceTest {

    @Mock
    private EnvironmentClientService environmentClientService;

    @Mock
    private CDPConfigService cdpConfigService;

    @Mock
    private CloudParameterService cloudParameterService;

    @Mock
    private CredentialConverter credentialConverter;

    @Spy
    private SdxRecommendationConverter sdxRecommendationConverter;

    @InjectMocks
    private SdxRecommendationService underTest;

    @Test
    public void testGetRecommendationShouldFailWhenMissingRequiredParameters() {
        assertThrows(BadRequestException.class, () -> underTest.getRecommendation(null, "eu-central-1", "AWS", "7.2.14", SdxClusterShape.LIGHT_DUTY, null));
        assertThrows(BadRequestException.class, () -> underTest.getRecommendation("credcrn", null, "AWS", "7.2.14", SdxClusterShape.LIGHT_DUTY, null));
        assertThrows(BadRequestException.class, () -> underTest.getRecommendation("credcrn", "eu-central-1", null, "7.2.14", SdxClusterShape.LIGHT_DUTY, null));
        assertThrows(BadRequestException.class, () -> underTest.getRecommendation("credcrn", "eu-central-1", "AWS", null, SdxClusterShape.LIGHT_DUTY, null));
        assertThrows(BadRequestException.class, () -> underTest.getRecommendation("credcrn", "eu-central-1", "AWS", "7.2.14", null, null));
    }

    @Test
    public void testGetRecommendationShouldFailWhenMissingDefaultTemplate() {
        CredentialResponse credentialResponse = new CredentialResponse();
        credentialResponse.setCloudPlatform("AWS");
        when(environmentClientService.getCredentialByCrn(anyString())).thenReturn(credentialResponse);
        when(cdpConfigService.getConfigForKey(any())).thenReturn(null);

        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> underTest.getRecommendation("credcrn", "eu-central-1", "AWS", "7.2.14", SdxClusterShape.LIGHT_DUTY, null));
        assertEquals("Can't find template for cloudplatform: AWS, shape: LIGHT_DUTY, runtime version: 7.2.14", badRequestException.getMessage());
    }

    @Test
    public void testGetRecommendationShouldReturnResponse() {
        CredentialResponse credentialResponse = new CredentialResponse();
        credentialResponse.setCloudPlatform("AWS");
        when(environmentClientService.getCredentialByCrn(anyString())).thenReturn(credentialResponse);
        StackV4Request defaultTemplate = new StackV4Request();
        InstanceGroupV4Request instanceGroup = new InstanceGroupV4Request();
        instanceGroup.setName("master");
        InstanceTemplateV4Request instanceTemplate = new InstanceTemplateV4Request();
        instanceTemplate.setInstanceType("r5.2xlarge");
        instanceGroup.setTemplate(instanceTemplate);
        defaultTemplate.getInstanceGroups().add(instanceGroup);
        when(cdpConfigService.getConfigForKey(any())).thenReturn(defaultTemplate);


        Map<String, Set<VmType>> cloudVmResponses = new HashMap<>();
        cloudVmResponses.put("eu-central-1a", Set.of(
                VmType.vmTypeWithMeta("r5.2xlarge", new VmTypeMeta(), false),
                VmType.vmTypeWithMeta("r5.xlarge", new VmTypeMeta(), false)));
        when(cloudParameterService.getVmTypesV2(any(), anyString(), anyString(), any(), any())).thenReturn(new CloudVmTypes(cloudVmResponses, new HashMap<>()));

        SdxRecommendationResponse recommendation = underTest.getRecommendation("credcrn", "eu-central-1", "AWS", "7.2.14", SdxClusterShape.LIGHT_DUTY, null);
        StackV4Request recommendationDefaultTemplate = recommendation.getDefaultTemplate();
        assertEquals(1, recommendationDefaultTemplate.getInstanceGroups().size());
        InstanceGroupV4Request recommendationInstanceGroup = recommendationDefaultTemplate.getInstanceGroups().iterator().next();
        assertEquals("master", recommendationInstanceGroup.getName());
        assertEquals("r5.2xlarge", recommendationInstanceGroup.getTemplate().getInstanceType());

        Set<VmTypeV4Response> virtualMachines = recommendation.getVirtualMachines();
        assertEquals(2, virtualMachines.size());
        Set<String> vmNames = virtualMachines.stream().map(vmType -> vmType.getValue()).collect(Collectors.toSet());
        Assertions.assertThat(vmNames).containsExactly("r5.xlarge", "r5.2xlarge");

        Map<String, VmTypeV4Response> defaultVmTypes = recommendation.getDefaultVmTypes();
        assertEquals(1, defaultVmTypes.size());
        assertTrue(defaultVmTypes.containsKey("master"));
        assertEquals("r5.2xlarge", defaultVmTypes.get("master").getValue());
    }

}