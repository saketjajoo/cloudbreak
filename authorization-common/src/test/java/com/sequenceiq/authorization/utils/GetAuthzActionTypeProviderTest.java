package com.sequenceiq.authorization.utils;

import static com.sequenceiq.authorization.utils.GetAuthzActionTypeProvider.getActionsForResourceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import com.sequenceiq.authorization.resource.AuthorizationResourceType;

class GetAuthzActionTypeProviderTest {

    @Test
    void testAllAuthorizationResourceTypeShouldHaveAnEntryInTheResultMap() {
        List<AuthorizationResourceType> resourceTypeQueue = Arrays.asList(AuthorizationResourceType.values());
        List<String> missingTypesFromPairs = new ArrayList<>();
        for (AuthorizationResourceType resourceType : resourceTypeQueue) {
            if (getActionsForResourceType(resourceType).isEmpty()) {
                missingTypesFromPairs.add(resourceType.name());
            }
        }

        Assertions.assertTrue(missingTypesFromPairs.isEmpty(),
                String.format("The following %s(s) has no get/fetch action pair: [%s]", AuthorizationResourceAction.class.getSimpleName(),
                        String.join(", ", missingTypesFromPairs)));
    }

}