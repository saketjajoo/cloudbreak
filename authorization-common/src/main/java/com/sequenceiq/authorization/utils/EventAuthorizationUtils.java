package com.sequenceiq.authorization.utils;

import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.ADMIN_FREEIPA;
import static com.sequenceiq.authorization.resource.AuthorizationResourceAction.REGISTER_DATABASE;
import static com.sequenceiq.authorization.resource.AuthorizationResourceType.CUSTOM_CONFIGURATIONS;
import static com.sequenceiq.authorization.utils.GetAuthzActionTypeProvider.getActionsForResourceType;
import static com.sequenceiq.cloudbreak.util.NullUtil.throwIfNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.sequenceiq.authorization.resource.AuthorizationResourceAction;
import com.sequenceiq.authorization.resource.AuthorizationResourceType;
import com.sequenceiq.authorization.service.CommonPermissionCheckingUtils;
import com.sequenceiq.cloudbreak.auth.ThreadBasedUserCrnProvider;

@Component
public class EventAuthorizationUtils {

    private static final Map<AuthorizationResourceAction, List<AuthorizationResourceAction>> ALTERNATIVES = Map.of(ADMIN_FREEIPA, List.of(REGISTER_DATABASE));

    private static final Logger LOGGER = LoggerFactory.getLogger(EventAuthorizationUtils.class);

    private final Map<AuthorizationResourceType, AuthorizationResourceAction> specials;

    private CommonPermissionCheckingUtils permissionCheckingUtils;

    public EventAuthorizationUtils(CommonPermissionCheckingUtils permissionCheckingUtils) {
        this.permissionCheckingUtils = permissionCheckingUtils;
        specials = new HashMap<>();
        for (AuthorizationResourceAction resourceAction : Arrays.asList(AuthorizationResourceAction.values())) {
            if (resourceAction.getAuthorizationResourceType() == CUSTOM_CONFIGURATIONS && resourceAction.isDescribeAction()) {
                specials.put(CUSTOM_CONFIGURATIONS, resourceAction);
            }
        }
    }

    public void checkPermissionBasedOnResourceTypeAndCrn(Collection<EventAuthorizationDto> eventAuthorizationDtos) {
        throwIfNull(eventAuthorizationDtos,
                () -> new IllegalArgumentException("The collection of " + EventAuthorizationDto.class.getSimpleName() + "s should not be null!"));
        LOGGER.info("Checking permissions for events: {}",
                String.join(",", eventAuthorizationDtos.stream().map(EventAuthorizationDto::toString).collect(Collectors.toSet())));
        for (EventAuthorizationDto dto : eventAuthorizationDtos) {
            Set<AuthorizationResourceAction> act = getActionsForResourceType(findResourceTypeByString(dto.getResourceType()));
            checkMultipleActions(act, dto.getResourceCrn());
        }
    }

    private AuthorizationResourceType findResourceTypeByString(String resourceType) {
        try {
            return AuthorizationResourceType.valueOf(resourceType.toUpperCase());
        } catch (NullPointerException | IllegalArgumentException iae) {
            throw new IllegalStateException("Unable to find AuthZ action for resource: " + resourceType);
        }
    }

    private boolean checkSpecialActionHasPermission(AuthorizationResourceAction action, String resourceCrn) {
        for (AuthorizationResourceAction alternative : ALTERNATIVES.get(action)) {
            try {
                checkPermission(alternative, resourceCrn);
                return true;
            } catch (AccessDeniedException ade) {
                LOGGER.info(AccessDeniedException.class.getSimpleName() + " has caught during - alternative - permission check for " +
                        "[action: {}, resourceCrn: {}]", action.name(), resourceCrn);
            }
        }
        return false;
    }

    private void checkMultipleActions(Collection<AuthorizationResourceAction> actions, String resourceCrn) {
        for (AuthorizationResourceAction action : actions) {
            if (ALTERNATIVES.containsKey(action)) {
                if (checkSpecialActionHasPermission(action, resourceCrn)) {
                    continue;
                }
            } else {
                checkPermission(action, resourceCrn);
            }
        }
    }

    private void checkPermission(AuthorizationResourceAction action, String resourceCrn) {
        permissionCheckingUtils.checkPermissionForUserOnResource(
                action,
                ThreadBasedUserCrnProvider.getUserCrn(),
                resourceCrn);
    }

}
