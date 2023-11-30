package com.autodesk.compute.auth.vault;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.*;

@NameBinding
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalVaultAuth {
}
