/*
 * CloudOS Compute API
 * CloudOS Compute API for batch style workloads in Autodesk. Developers can register compute worker types by following the cloudOS2.0 onboarding process. Users can submit jobs against registered worker types. The system treats input and output as opaque JSON payloads that must confirm to the JSON schema specified by the worker documentation outside this system. All APIs are used with the /api/v1 prefix.
 *
 * The version of the OpenAPI document: 1.0.19
 * Contact: cloudos-compute@autodesk.com
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.autodesk.compute.testlambda.gen;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class Pair {
    private String name = "";
    private String value = "";

    public Pair(String name, String value) {
        setName(name);
        setValue(value);
    }

    private void setName(String name) {
        if (!isValidString(name)) {
            return;
        }

        this.name = name;
    }

    private void setValue(String value) {
        if (!isValidString(value)) {
            return;
        }

        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public String getValue() {
        return this.value;
    }

    private boolean isValidString(String arg) {
        if (arg == null) {
            return false;
        }

        if (arg.trim().isEmpty()) {
            return false;
        }

        return true;
    }
}
