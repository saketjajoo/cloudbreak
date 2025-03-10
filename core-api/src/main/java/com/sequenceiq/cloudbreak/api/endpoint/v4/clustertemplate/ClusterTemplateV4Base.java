package com.sequenceiq.cloudbreak.api.endpoint.v4.clustertemplate;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sequenceiq.cloudbreak.doc.ModelDescriptions;
import com.sequenceiq.cloudbreak.doc.ModelDescriptions.ClusterTemplateModelDescription;
import com.sequenceiq.common.model.JsonEntity;
import com.sequenceiq.distrox.api.v1.distrox.model.DistroXV1Request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ClusterTemplateV4Base implements JsonEntity {

    @Size(max = 40, min = 5, message = "The length of name has to be in range of 5 to 40")
    @Pattern(regexp = "^[^;\\/%]*$",
            message = "Name should not contain semicolon, forward slash or percentage characters")
    @NotNull
    @ApiModelProperty(value = ModelDescriptions.NAME, required = true)
    private String name;

    @Size(max = 1000)
    @ApiModelProperty(ModelDescriptions.DESCRIPTION)
    private String description;

    @ApiModelProperty(value = ClusterTemplateModelDescription.TEMPLATE, required = true)
    @NotNull
    private DistroXV1Request distroXTemplate;

    @ApiModelProperty(required = true)
    private ClusterTemplateV4Type type = ClusterTemplateV4Type.OTHER;

    @ApiModelProperty(ClusterTemplateModelDescription.CLOUD_PLATFORM)
    private String cloudPlatform;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DistroXV1Request getDistroXTemplate() {
        return distroXTemplate;
    }

    public void setDistroXTemplate(DistroXV1Request distroXTemplate) {
        this.distroXTemplate = distroXTemplate;
    }

    public String getCloudPlatform() {
        return cloudPlatform;
    }

    public void setCloudPlatform(String cloudPlatform) {
        this.cloudPlatform = cloudPlatform;
    }

    public ClusterTemplateV4Type getType() {
        return type;
    }

    public void setType(ClusterTemplateV4Type type) {
        this.type = type;
    }
}
