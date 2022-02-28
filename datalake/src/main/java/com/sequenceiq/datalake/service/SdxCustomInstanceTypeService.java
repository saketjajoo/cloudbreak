package com.sequenceiq.datalake.service;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.StackV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.instancegroup.InstanceGroupV4Request;
import com.sequenceiq.cloudbreak.common.service.TransactionService;

@Service
public class SdxCustomInstanceTypeService {

    @Inject
    private TransactionService transactionService;

    public void validateCustomInstanceTypes() {

    }

    public StackV4Request modifyInstanceTypesIfNeeded(StackV4Request stackV4Request, Map<String, String> customInstanceTypes) {
        if (MapUtils.isEmpty(customInstanceTypes)) {
            return stackV4Request;
        }
        return modifyStackRequestByCustomInstanceTypes(stackV4Request, customInstanceTypes);
    }

    private StackV4Request modifyStackRequestByCustomInstanceTypes(StackV4Request stackV4Request, Map<String, String> customInstanceTypes) {
        customInstanceTypes.entrySet().stream().forEach(entry -> {
            Optional<InstanceGroupV4Request> igByName = stackV4Request.getInstanceGroups().stream().filter(ig ->
                    StringUtils.equals(ig.getName(), entry.getKey())).findFirst();
            if (igByName.isPresent()) {
                igByName.get().getTemplate().setInstanceType(entry.getValue());
            }
        });
        return stackV4Request;
    }
}
