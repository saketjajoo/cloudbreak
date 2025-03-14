package com.sequenceiq.distrox.api.v1.distrox.model.tags;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sequenceiq.cloudbreak.doc.ModelDescriptions.StackModelDescription;
import com.sequenceiq.common.model.JsonEntity;

import io.swagger.annotations.ApiModelProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TagsV1Request implements JsonEntity {

    @ApiModelProperty(StackModelDescription.APPLICATION_TAGS)
    private Map<String, String> application = new HashMap<>();

    @ApiModelProperty(StackModelDescription.USERDEFINED_TAGS)
    private Map<String, String> userDefined = new HashMap<>();

    @ApiModelProperty(StackModelDescription.DEFAULT_TAGS)
    private Map<String, String> defaults = new HashMap<>();

    public Map<String, String> getApplication() {
        return application;
    }

    public void setApplication(Map<String, String> application) {
        this.application = application;
    }

    public Map<String, String> getUserDefined() {
        return userDefined;
    }

    public void setUserDefined(Map<String, String> userDefined) {
        this.userDefined = userDefined;
    }

    public Map<String, String> getDefaults() {
        return defaults;
    }

    public void setDefaults(Map<String, String> defaults) {
        this.defaults = defaults;
    }
}
