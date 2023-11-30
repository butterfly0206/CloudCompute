package com.autodesk.compute.auth.vault;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.*;

/**
 * Created by wattd on 6/1/17.
 */
@NameBinding
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface VaultAuth {
}
