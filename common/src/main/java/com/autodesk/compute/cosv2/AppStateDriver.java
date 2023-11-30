package com.autodesk.compute.cosv2;

import com.autodesk.compute.model.cosv2.AppState;

/**
 * @author Nusli Vakil (vakiln)
 * <p>
 * AppStateDriver is responsible for interfacing with the application state.
 * All interactions with the application state should go through this driver.
 */
public interface AppStateDriver {

    /**
     * Load the state from dynamoDB. If there is currently no state present for the application,
     * an empty state will be returned
     *
     * @param appName Name of the application (used as the primary key)
     * @return State file
     */
    AppState load(String appName);
}
