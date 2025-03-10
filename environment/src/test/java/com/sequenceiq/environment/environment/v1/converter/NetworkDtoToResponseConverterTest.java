package com.sequenceiq.environment.environment.v1.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.sequenceiq.cloudbreak.cloud.model.CloudSubnet;
import com.sequenceiq.common.api.type.LoadBalancerCreation;
import com.sequenceiq.common.api.type.ServiceEndpointCreation;
import com.sequenceiq.common.api.type.Tunnel;
import com.sequenceiq.environment.api.v1.environment.model.base.PrivateSubnetCreation;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentNetworkResponse;
import com.sequenceiq.environment.network.dao.domain.RegistrationType;
import com.sequenceiq.environment.network.dto.AwsParams;
import com.sequenceiq.environment.network.dto.AzureParams;
import com.sequenceiq.environment.network.dto.MockParams;
import com.sequenceiq.environment.network.dto.NetworkDto;
import com.sequenceiq.environment.network.dto.YarnParams;
import com.sequenceiq.environment.network.service.SubnetIdProvider;
import com.sequenceiq.environment.network.service.domain.ProvidedSubnetIds;

@ExtendWith(SpringExtension.class)
public class NetworkDtoToResponseConverterTest {

    private static final Tunnel TUNNEL = Tunnel.CCM;

    private static final String PREFERRED_SUBNET_ID = "preferred-subnet-id";

    @InjectMocks
    private NetworkDtoToResponseConverter underTest;

    @Mock
    private SubnetIdProvider subnetIdProvider;

    @Test
    void testConvertWithAwsParams() {
        NetworkDto network = createNetworkDto().withAws(createAwsParams()).build();
        ProvidedSubnetIds providedSubnetIds = new ProvidedSubnetIds(PREFERRED_SUBNET_ID, Set.of(PREFERRED_SUBNET_ID));
        when(subnetIdProvider.subnets(network, TUNNEL, network.getCloudPlatform(), true)).thenReturn(providedSubnetIds);

        EnvironmentNetworkResponse actual = underTest.convert(network, TUNNEL, true);

        assertCommonFields(network, actual);
        assertEquals(network.getAws().getVpcId(), actual.getAws().getVpcId());
        assertNull(actual.getAzure());
        assertNull(actual.getYarn());
        assertNull(actual.getMock());
    }

    @Test
    void testConvertWithAzureParams() {
        NetworkDto network = createNetworkDto().withAzure(createAzureParams()).build();
        ProvidedSubnetIds providedSubnetIds = new ProvidedSubnetIds(PREFERRED_SUBNET_ID, Set.of(PREFERRED_SUBNET_ID));
        when(subnetIdProvider.subnets(network, TUNNEL, network.getCloudPlatform(), true)).thenReturn(providedSubnetIds);

        EnvironmentNetworkResponse actual = underTest.convert(network, TUNNEL, true);

        assertCommonFields(network, actual);
        assertEquals(network.getAzure().isNoPublicIp(), actual.getAzure().getNoPublicIp());
        assertEquals(network.getAzure().getNetworkId(), actual.getAzure().getNetworkId());
        assertEquals(network.getAzure().getResourceGroupName(), actual.getAzure().getResourceGroupName());
        assertEquals(network.getAzure().getDatabasePrivateDnsZoneId(), actual.getAzure().getDatabasePrivateDnsZoneId());
        assertEquals(network.getAzure().getAksPrivateDnsZoneId(), actual.getAzure().getAksPrivateDnsZoneId());
        assertEquals(network.getAzure().isNoOutboundLoadBalancer(), actual.getAzure().getNoOutboundLoadBalancer());
        assertEquals(network.getAzure().getAvailabilityZones(), actual.getAzure().getAvailabilityZones());
        assertNull(actual.getAws());
        assertNull(actual.getYarn());
        assertNull(actual.getMock());
    }

    @Test
    void testConvertWithMockParams() {
        NetworkDto network = createNetworkDto().withMock(createMockParams()).build();
        ProvidedSubnetIds providedSubnetIds = new ProvidedSubnetIds(PREFERRED_SUBNET_ID, Set.of(PREFERRED_SUBNET_ID));
        when(subnetIdProvider.subnets(network, TUNNEL, network.getCloudPlatform(), true)).thenReturn(providedSubnetIds);

        EnvironmentNetworkResponse actual = underTest.convert(network, TUNNEL, true);

        assertCommonFields(network, actual);
        assertEquals(network.getMock().getVpcId(), actual.getMock().getVpcId());
        assertEquals(network.getMock().getInternetGatewayId(), actual.getMock().getInternetGatewayId());
        assertNull(actual.getAws());
        assertNull(actual.getYarn());
        assertNull(actual.getAzure());
    }

    @Test
    void testConvertWithLoadBalancerEnabled() {
        NetworkDto network = createNetworkDto().withAws(createAwsParams()).withLoadBalancerCreation(LoadBalancerCreation.ENABLED).build();
        ProvidedSubnetIds providedSubnetIds = new ProvidedSubnetIds(PREFERRED_SUBNET_ID, Set.of(PREFERRED_SUBNET_ID));
        when(subnetIdProvider.subnets(network, TUNNEL, network.getCloudPlatform(), true)).thenReturn(providedSubnetIds);

        EnvironmentNetworkResponse actual = underTest.convert(network, TUNNEL, true);

        assertEquals(network.getAws().getVpcId(), actual.getAws().getVpcId());
        assertEquals(LoadBalancerCreation.ENABLED, actual.getLoadBalancerCreation());
        assertNull(actual.getAzure());
        assertNull(actual.getYarn());
        assertNull(actual.getMock());
    }

    @Test
    void testConvertWithYarnParams() {
        NetworkDto network = createNetworkDto().withYarn(createYarnParams()).build();

        ProvidedSubnetIds providedSubnetIds = new ProvidedSubnetIds(PREFERRED_SUBNET_ID, Set.of(PREFERRED_SUBNET_ID));
        when(subnetIdProvider.subnets(network, TUNNEL, network.getCloudPlatform(), true)).thenReturn(providedSubnetIds);

        EnvironmentNetworkResponse actual = underTest.convert(network, TUNNEL, true);

        assertCommonFields(network, actual);
        assertEquals(network.getYarn().getQueue(), actual.getYarn().getQueue());
        assertEquals(network.getYarn().getLifetime(), actual.getYarn().getLifetime());
        assertNull(actual.getAws());
        assertNull(actual.getMock());
        assertNull(actual.getAzure());
    }

    private void assertCommonFields(NetworkDto network, EnvironmentNetworkResponse actual) {
        verify(subnetIdProvider).subnets(network, TUNNEL, network.getCloudPlatform(), true);
        assertEquals(network.getResourceCrn(), actual.getCrn());
        assertEquals(network.getSubnetIds(), actual.getSubnetIds());
        assertEquals(network.getNetworkCidr(), actual.getNetworkCidr());
        assertEquals(network.getSubnetMetas(), actual.getSubnetMetas());
        assertEquals(network.getCbSubnets(), actual.getCbSubnets());
        assertEquals(network.getDwxSubnets(), actual.getDwxSubnets());
        assertEquals(network.getMlxSubnets(), actual.getMlxSubnets());
        assertEquals(network.getMlxSubnets(), actual.getLiftieSubnets());
        assertEquals(PREFERRED_SUBNET_ID, actual.getPreferedSubnetId());
        assertEquals(network.getPrivateSubnetCreation(), actual.getPrivateSubnetCreation());
        assertFalse(actual.isExistingNetwork());
    }

    private NetworkDto.Builder createNetworkDto() {
        return NetworkDto.builder()
                .withResourceCrn("resource crn")
                .withSubnetMetas(Map.of("subnet-id", new CloudSubnet()))
                .withNetworkCidr("10.0.0.0/16")
                .withCbSubnets(Map.of("cb-subnetId", new CloudSubnet()))
                .withDwxSubnets(Map.of("dvx-subnetId", new CloudSubnet()))
                .withMlxSubnets(Map.of("mlx-subnetId", new CloudSubnet()))
                .withLiftieSubnets(Map.of("mlx-subnetId", new CloudSubnet()))
                .withPrivateSubnetCreation(PrivateSubnetCreation.ENABLED)
                .withServiceEndpointCreation(ServiceEndpointCreation.ENABLED)
                .withRegistrationType(RegistrationType.CREATE_NEW);
    }

    private AzureParams createAzureParams() {
        return AzureParams.builder()
                .withNetworkId("azure-network")
                .withNoPublicIp(true)
                .withResourceGroupName("resource-group")
                .withNetworkId("network-id")
                .withDatabasePrivateDnsZoneId("database-private-dns-zone-id")
                .withAksPrivateDnsZoneId("aks-private-dns-zone-id")
                .withNoOutboundLoadBalancer(true)
                .withAvailabilityZones(Set.of("1", "2"))
                .build();
    }

    private YarnParams createYarnParams() {
        return YarnParams.builder()
                .withQueue("yarn-queue")
                .withLifetime(1000)
                .build();
    }

    private MockParams createMockParams() {
        return MockParams.builder()
                .withInternetGatewayId("internet-gateway-id")
                .withVpcId("vpc-id")
                .build();
    }

    private AwsParams createAwsParams() {
        return AwsParams.builder()
                .withVpcId("aws-vpc-id")
                .build();
    }

}
