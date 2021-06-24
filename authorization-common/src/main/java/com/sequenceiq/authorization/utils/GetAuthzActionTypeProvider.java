package com.sequenceiq.authorization.utils;

import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.ADMIN_FREEIPA;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_AUDIT_CREDENTIAL;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_CLUSTER_DEFINITION;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_CLUSTER_TEMPLATE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_CREDENTIAL;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_CREDENTIAL_ON_ENVIRONMENT;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_CUSTOM_CONFIGS;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_DATABASE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_DATABASE_SERVER;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_DATAHUB;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_DATALAKE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_ENVIRONMENT;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_IMAGE_CATALOG;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.DESCRIBE_RECIPE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.STRUCTURED_EVENTS_READ;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.AUDIT_CREDENTIAL;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.CLUSTER_DEFINITION;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.CLUSTER_TEMPLATE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.CREDENTIAL;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.CUSTOM_CONFIGURATIONS;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.DATABASE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.DATABASE_SERVER;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.DATAHUB;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.DATALAKE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.ENVIRONMENT;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.FREEIPA;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.IMAGE_CATALOG;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.RECIPE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.STRUCTURED_EVENT;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import com.sequenceiq.authorization.resource.AuthorizationResourceType;

public final class GetAuthzActionTypeProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetAuthzActionTypeProvider.class);

    private static final Map<AuthorizationResourceType, Set<AuthorizationResourceAction>> PAIRS = new HashMap<>(AuthorizationResourceType.values().length);

    static {
        PAIRS.put(CREDENTIAL, Set.of(DESCRIBE_CREDENTIAL, DESCRIBE_CREDENTIAL_ON_ENVIRONMENT));
        PAIRS.put(DATALAKE, Set.of(DESCRIBE_DATALAKE));
        PAIRS.put(ENVIRONMENT, Set.of(DESCRIBE_ENVIRONMENT));
        PAIRS.put(DATAHUB, Set.of(DESCRIBE_DATAHUB));
        PAIRS.put(IMAGE_CATALOG, Set.of(DESCRIBE_IMAGE_CATALOG));
        PAIRS.put(CLUSTER_DEFINITION, Set.of(DESCRIBE_CLUSTER_DEFINITION));
        PAIRS.put(CLUSTER_TEMPLATE, Set.of(DESCRIBE_CLUSTER_TEMPLATE));
        PAIRS.put(CUSTOM_CONFIGURATIONS, Set.of(DESCRIBE_CUSTOM_CONFIGS));
        PAIRS.put(DATABASE, Set.of(DESCRIBE_DATABASE));
        PAIRS.put(DATABASE_SERVER, Set.of(DESCRIBE_DATABASE_SERVER));
        PAIRS.put(RECIPE, Set.of(DESCRIBE_RECIPE));
        PAIRS.put(AUDIT_CREDENTIAL, Set.of(DESCRIBE_AUDIT_CREDENTIAL));
        PAIRS.put(STRUCTURED_EVENT, Set.of(STRUCTURED_EVENTS_READ));
        PAIRS.put(FREEIPA, Set.of(ADMIN_FREEIPA));
    }

    private GetAuthzActionTypeProvider() {
    }

    public static Set<AuthorizationResourceAction> getActionsForResourceType(AuthorizationResourceType resourceType) {
        if (resourceType != null) {
            if (PAIRS.containsKey(resourceType)) {
                return PAIRS.get(resourceType);
            }
            LOGGER.debug("No get/describe action has found for resource type: {}", resourceType.name());
        } else {
            LOGGER.debug("Unable to find get/describe action for resource type because it is null!");
        }
        return Collections.emptySet();
    }

}
