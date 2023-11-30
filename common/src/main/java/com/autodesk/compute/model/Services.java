package com.autodesk.compute.model;

import com.autodesk.compute.model.cosv2.AppDefinition;
import com.google.common.collect.Multimap;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Services {
    private Multimap<String, AppDefinition> appDefinitions;
}
