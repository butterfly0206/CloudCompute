package com.autodesk.compute.util;

import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import jakarta.ws.rs.core.SecurityContext;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Slf4j
@UtilityClass
public class AuthCheckHelper {

    public static boolean isServiceAuthorized(final String moniker, final SecurityContext securityContext) {
            if(securityContext == null){
                log.error("Security context is null. Should not have reached this part");
                return false;
            }
            final BasicAuthSecurityContext callerSecurityContext = BasicAuthSecurityContext.get(securityContext);
            if (callerSecurityContext.getUserPrincipal() != null) {
                if (!Objects.equals(moniker, callerSecurityContext.getUserPrincipal().getName())) { //NOPMD
                    log.error("The moniker {} is not authorized for performing any action on the jobs for moniker {}", callerSecurityContext.getUserPrincipal().getName(), moniker);
                    return false;
                }
            } else {
                //Case where authentication is not enforced. isSecure() is true
                return true;
            }
        return true;
    }
}
