package com.autodesk.compute.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class VersionedSecret {
    private long lastModifiedTime;
    private String version;
    private String secret;
    private String label;
}
