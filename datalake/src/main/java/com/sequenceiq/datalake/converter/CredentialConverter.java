package com.sequenceiq.datalake.converter;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.ExtendedCloudCredential;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.cloudbreak.service.secret.service.SecretService;
import com.sequenceiq.cloudbreak.util.NullUtil;
import com.sequenceiq.environment.api.v1.credential.model.response.CredentialResponse;

@Component
public class CredentialConverter {

    @Inject
    private SecretService secretService;

    public ExtendedCloudCredential convert(CredentialResponse credential) {
        CloudCredential cloudCredential = convertToCloudCredential(credential);
        String accountId = ThreadBasedUserCrnProvider.getAccountId();
        String userCrn = ThreadBasedUserCrnProvider.getUserCrn();
        return new ExtendedCloudCredential(cloudCredential, credential.getCloudPlatform(), credential.getDescription(), userCrn, accountId);
    }

    public CloudCredential convertToCloudCredential(CredentialResponse credential) {
        return NullUtil.getIfNotNull(credential, c -> {
            String attributes = secretService.getByResponse(credential.getAttributes());
            Map<String, Object> fields = isEmpty(attributes) ? new HashMap<>() : new Json(attributes).getMap();
            return new CloudCredential(credential.getCrn(), credential.getName(), fields, false);
        });
    }

}
