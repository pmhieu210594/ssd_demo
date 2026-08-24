package com.sdd.platform.web.security;

import com.sdd.platform.domain.model.AuthUserContext;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.sdd.platform.domain.model.AppUser;

/**
 * Resolves a controller parameter of type {@link AppUser} annotated with
 * {@link CurrentUser} from the authenticated bearer-token principal.
 */
@Component
public class CurrentAppUserResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthUserContext.class)
                || (parameter.getParameterType().equals(AppUser.class)
                && parameter.hasParameterAnnotation(CurrentUser.class));
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (parameter.getParameterType().equals(AuthUserContext.class)) {
            if (auth != null && auth.getPrincipal() instanceof AuthUserContext principal) {
                return principal;
            }
            Object testPrincipal = webRequest.getAttribute("testUserContext", NativeWebRequest.SCOPE_REQUEST);
            return testPrincipal instanceof AuthUserContext principal ? principal : null;
        }
        if (auth == null || !(auth.getPrincipal() instanceof AuthUserContext principal)) {
            return null;
        }
        return AppUser.builder()
                .provider("internal")
                .providerUid(principal.getUserAccountId().toString())
                .email(principal.getEmail())
                .displayName(principal.getDisplayName())
                .role("ADMIN".equalsIgnoreCase(principal.getRole())
                        ? AppUser.Role.ADMIN
                        : AppUser.Role.EDITOR)
                .active(true)
                .build();
    }
}
