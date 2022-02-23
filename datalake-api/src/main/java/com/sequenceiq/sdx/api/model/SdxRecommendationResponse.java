package com.sequenceiq.sdx.api.model;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.StackV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.VmTypeV4Response;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SdxRecommendationResponse {

    private StackV4Request defaultTemplate;

    private Map<String, VmTypeV4Response> defaultVmTypes;

    private Set<VmTypeV4Response> virtualMachines;

    public StackV4Request getDefaultTemplate() {
        return defaultTemplate;
    }

    public void setDefaultTemplate(StackV4Request defaultTemplate) {
        this.defaultTemplate = defaultTemplate;
    }

    public Map<String, VmTypeV4Response> getDefaultVmTypes() {
        return defaultVmTypes;
    }

    public void setDefaultVmTypes(Map<String, VmTypeV4Response> defaultVmTypes) {
        this.defaultVmTypes = defaultVmTypes;
    }

    public Set<VmTypeV4Response> getVirtualMachines() {
        return virtualMachines;
    }

    public void setVirtualMachines(Set<VmTypeV4Response> virtualMachines) {
        this.virtualMachines = virtualMachines;
    }

    @Override
    public String toString() {
        return "SdxRecommendationResponse{" +
                "defaultTempalte=" + defaultTemplate +
                '}';
    }
}
