import { createSelector } from "@ngrx/store"
import { appSettingsSelector } from "../appSettings.selector"

export const floorLabelDepthSelector = createSelector(appSettingsSelector, appSettings => appSettings.floorLabelDepth)
