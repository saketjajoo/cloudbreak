package com.sequenceiq.distrox.v1.distrox.service;

import static com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus.AVAILABLE;
import static com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus.START_DATAHUB_STARTED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.StackV4Request;
import com.sequenceiq.cloudbreak.common.exception.BadRequestException;
import com.sequenceiq.cloudbreak.common.user.CloudbreakUser;
import com.sequenceiq.cloudbreak.saas.sdx.PlatformAwareSdxConnector;
import com.sequenceiq.cloudbreak.saas.sdx.status.StatusCheckResult;
import com.sequenceiq.cloudbreak.service.environment.EnvironmentClientService;
import com.sequenceiq.cloudbreak.service.freeipa.FreeipaClientService;
import com.sequenceiq.cloudbreak.service.workspace.WorkspaceService;
import com.sequenceiq.cloudbreak.structuredevent.CloudbreakRestRequestThreadLocalService;
import com.sequenceiq.cloudbreak.workspace.model.Tenant;
import com.sequenceiq.cloudbreak.workspace.model.Workspace;
import com.sequenceiq.distrox.api.v1.distrox.model.DistroXV1Request;
import com.sequenceiq.distrox.api.v1.distrox.model.image.DistroXImageV1Request;
import com.sequenceiq.distrox.v1.distrox.StackOperations;
import com.sequenceiq.distrox.v1.distrox.converter.DistroXV1RequestToStackV4RequestConverter;
import com.sequenceiq.distrox.v1.distrox.fedramp.FedRampModificationService;
import com.sequenceiq.environment.api.v1.environment.model.response.DetailedEnvironmentResponse;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.AvailabilityStatus;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.describe.DescribeFreeIpaResponse;

@ExtendWith(MockitoExtension.class)
class DistroXServiceTest {

    private static final Long USER_ID = 123456L;

    private static final String DATALAKE_CRN = "crn:cdp:datalake:us-west-1:acc1:datalake:cluster1";

    @Mock
    private DistroXV1RequestToStackV4RequestConverter stackRequestConverter;

    @Mock
    private EnvironmentClientService environmentClientService;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private FedRampModificationService fedRampModificationService;

    @Mock
    private StackOperations stackOperations;

    @Mock
    private FreeipaClientService freeipaClientService;

    @Mock
    private CloudbreakRestRequestThreadLocalService restRequestThreadLocalService;

    @Mock
    private PlatformAwareSdxConnector platformAwareSdxConnector;

    @InjectMocks
    private DistroXService underTest;

    @BeforeEach
    void setUp() {
        Workspace workspace = new Workspace();
        workspace.setId(USER_ID);
        Tenant tenant = new Tenant();
        tenant.setName("test");
        workspace.setTenant(tenant);
        lenient().when(workspaceService.getForCurrentUser()).thenReturn(workspace);
    }

    @Test
    @DisplayName("When request doesn't contains a valid environment name then BadRequestException should come")
    void testWithInvalidEnvironmentNameValue() {
        String invalidEnvNameValue = "somethingInvalidStuff";
        DistroXV1Request r = new DistroXV1Request();
        r.setEnvironmentName(invalidEnvNameValue);
        when(environmentClientService.getByName(invalidEnvNameValue)).thenReturn(null);

        BadRequestException err = assertThrows(BadRequestException.class, () -> underTest.post(r));

        assertEquals("No environment name provided hence unable to obtain some important data", err.getMessage());

        verify(environmentClientService, calledOnce()).getByName(any());
        verify(environmentClientService, calledOnce()).getByName(invalidEnvNameValue);
        verify(stackOperations, never()).post(any(), any(), any(), anyBoolean());
        verify(stackRequestConverter, never()).convert(any(DistroXV1Request.class));
    }

    @Test
    void testWithNotAvailableEnvironmentButFreeipaAvailableAndRunWithoutException() throws IllegalAccessException {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request request = new DistroXV1Request();
        request.setEnvironmentName(envName);
        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(START_DATAHUB_STARTED);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        doNothing().when(fedRampModificationService).prepare(any(), any());
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));

        underTest.post(request);

        verify(platformAwareSdxConnector).listSdxCrns(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.DetailedStackStatus.class)
    @DisplayName("When request contains a valid environment name but that environment is not in the AVAILABLE state then BadRequestException should come")
    void testWithValidEnvironmentNameValueButTheActualEnvIsNotAvailableBadRequestExceptionShouldCome(
            com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.DetailedStackStatus detailedStackStatus) {
        if (!detailedStackStatus.getAvailabilityStatus().isAvailable()) {
            String envName = "someAwesomeExistingButNotAvailableEnvironment";
            DistroXV1Request r = new DistroXV1Request();
            r.setEnvironmentName(envName);

            DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
            envResponse.setCrn("crn");
            envResponse.setEnvironmentStatus(AVAILABLE);
            envResponse.setName(envName);

            DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
            freeipa.setAvailabilityStatus(detailedStackStatus.getAvailabilityStatus());
            freeipa.setStatus(detailedStackStatus.getStatus());
            when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);

            when(environmentClientService.getByName(envName)).thenReturn(envResponse);

            BadRequestException err = assertThrows(BadRequestException.class, () -> underTest.post(r));

            assertEquals(String.format("If you want to provision a Data Hub then the FreeIPA instance must be running in the '%s' Environment.", envName),
                    err.getMessage());

            verify(environmentClientService, calledOnce()).getByName(any());
            verify(environmentClientService, calledOnce()).getByName(eq(envName));
            verify(stackOperations, never()).post(any(), any(), any(), anyBoolean());
            verify(stackRequestConverter, never()).convert(any(DistroXV1Request.class));
        }
    }

    @Test
    @DisplayName("When the environment that has the given name is exist and also in the state DELETE_IN_PROGRESS then no exception should come")
    void testWhenEnvDeleteInProgressExistsThenShouldThrowBadRequest() throws IllegalAccessException {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request r = new DistroXV1Request();
        r.setEnvironmentName(envName);

        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(EnvironmentStatus.DELETE_INITIATED);
        envResponse.setName(envName);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);

        lenient().when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        lenient().when(environmentClientService.getByName(envName)).thenReturn(envResponse);

        StackV4Request converted = new StackV4Request();
        CloudbreakUser cloudbreakUser = mock(CloudbreakUser.class);
        lenient().when(stackRequestConverter.convert(r)).thenReturn(converted);
        lenient().when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));
        lenient().when(restRequestThreadLocalService.getCloudbreakUser()).thenReturn(cloudbreakUser);

        BadRequestException err = assertThrows(BadRequestException.class, () -> underTest.post(r));

        assertEquals(String.format("'someAwesomeEnvironment' Environment can not be delete in progress state."),
                err.getMessage());
    }

    @Test
    @DisplayName("When the environment that has the given name is exist and also in the state AVAILABLE then no exception should come")
    void testWhenEnvExistsAndItIsAvailable() throws IllegalAccessException {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request r = new DistroXV1Request();
        r.setEnvironmentName(envName);

        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(AVAILABLE);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        doNothing().when(fedRampModificationService).prepare(any(), any());
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);

        StackV4Request converted = new StackV4Request();
        CloudbreakUser cloudbreakUser = mock(CloudbreakUser.class);
        when(stackRequestConverter.convert(r)).thenReturn(converted);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));
        when(restRequestThreadLocalService.getCloudbreakUser()).thenReturn(cloudbreakUser);

        underTest.post(r);

        verify(environmentClientService, calledOnce()).getByName(any());
        verify(environmentClientService, calledOnce()).getByName(envName);
        verify(stackOperations, calledOnce()).post(any(), any(), any(), anyBoolean());
        verify(stackOperations, calledOnce()).post(eq(USER_ID), eq(cloudbreakUser), eq(converted), eq(true));
        verify(workspaceService, calledOnce()).getForCurrentUser();
        verify(stackRequestConverter, calledOnce()).convert(any(DistroXV1Request.class));
        verify(stackRequestConverter, calledOnce()).convert(r);
    }

    @Test
    public void testIfDlIsNotExists() {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request request = new DistroXV1Request();
        request.setEnvironmentName(envName);
        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(AVAILABLE);
        envResponse.setCrn("crn");
        envResponse.setName(envName);
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of());

        assertThatThrownBy(() -> underTest.post(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Data Lake stack cannot be found for environment CRN: %s (%s)", envName, envResponse.getCrn());

        verify(platformAwareSdxConnector).listSdxCrns(any(), any());
        verifyNoMoreInteractions(platformAwareSdxConnector);
    }

    @Test
    public void testIfDlIsNotRunning() {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request request = new DistroXV1Request();
        request.setEnvironmentName(envName);
        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(AVAILABLE);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));
        when(platformAwareSdxConnector.listSdxCrnsWithAvailability(any(), any(), any()))
                .thenReturn(Set.of(Pair.of(DATALAKE_CRN, StatusCheckResult.NOT_AVAILABLE)));

        assertThatThrownBy(() -> underTest.post(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Data Lake stacks of environment should be available.");

        verify(platformAwareSdxConnector).listSdxCrns(any(), any());
        verify(platformAwareSdxConnector).listSdxCrnsWithAvailability(any(), any(), any());
    }

    @Test
    public void testIfDlIsRunning() {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request request = new DistroXV1Request();
        request.setEnvironmentName(envName);
        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(AVAILABLE);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        Workspace workspace = new Workspace();
        Tenant tenant = new Tenant();
        tenant.setName("test");
        workspace.setTenant(tenant);
        doNothing().when(fedRampModificationService).prepare(any(), any());
        when(workspaceService.getForCurrentUser()).thenReturn(workspace);
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));
        when(platformAwareSdxConnector.listSdxCrnsWithAvailability(any(), any(), any()))
                .thenReturn(Set.of(Pair.of(DATALAKE_CRN, StatusCheckResult.AVAILABLE)));

        underTest.post(request);

        verify(platformAwareSdxConnector).listSdxCrns(any(), any());
        verify(platformAwareSdxConnector).listSdxCrnsWithAvailability(any(), any(), any());
    }

    @Test
    public void testIfDlRollingUpgradeInProgress() {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request request = new DistroXV1Request();
        request.setEnvironmentName(envName);
        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(AVAILABLE);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        Workspace workspace = new Workspace();
        Tenant tenant = new Tenant();
        tenant.setName("test");
        workspace.setTenant(tenant);
        doNothing().when(fedRampModificationService).prepare(any(), any());
        when(workspaceService.getForCurrentUser()).thenReturn(workspace);
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));
        when(platformAwareSdxConnector.listSdxCrnsWithAvailability(any(), any(), any()))
                .thenReturn(Set.of(Pair.of(DATALAKE_CRN, StatusCheckResult.ROLLING_UPGRADE_IN_PROGRESS)));

        underTest.post(request);

        verify(platformAwareSdxConnector).listSdxCrns(any(), any());
        verify(platformAwareSdxConnector).listSdxCrnsWithAvailability(any(), any(), any());
    }

    @Test
    void testIfImageIdAndOsBothSet() {
        String envName = "someAwesomeEnvironment";
        DistroXV1Request request = new DistroXV1Request();
        request.setEnvironmentName(envName);
        DistroXImageV1Request imageRequest = new DistroXImageV1Request();
        imageRequest.setId("id");
        imageRequest.setOs("os");
        request.setImage(imageRequest);
        DetailedEnvironmentResponse envResponse = new DetailedEnvironmentResponse();
        envResponse.setEnvironmentStatus(AVAILABLE);
        envResponse.setCrn("crn");
        DescribeFreeIpaResponse freeipa = new DescribeFreeIpaResponse();
        freeipa.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        freeipa.setStatus(com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status.AVAILABLE);
        Workspace workspace = new Workspace();
        Tenant tenant = new Tenant();
        tenant.setName("test");
        workspace.setTenant(tenant);
        when(workspaceService.getForCurrentUser()).thenReturn(workspace);
        when(freeipaClientService.getByEnvironmentCrn("crn")).thenReturn(freeipa);
        when(environmentClientService.getByName(envName)).thenReturn(envResponse);
        when(platformAwareSdxConnector.listSdxCrns(any(), any())).thenReturn(Set.of(DATALAKE_CRN));
        when(platformAwareSdxConnector.listSdxCrnsWithAvailability(any(), any(), any()))
                .thenReturn(Set.of(Pair.of(DATALAKE_CRN, StatusCheckResult.AVAILABLE)));

        assertThatThrownBy(() -> underTest.post(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Image request can not have both image id and os parameters set.");

        verify(platformAwareSdxConnector).listSdxCrns(any(), any());
        verify(platformAwareSdxConnector).listSdxCrnsWithAvailability(any(), any(), any());
    }

    private static VerificationMode calledOnce() {
        return times(1);
    }

}