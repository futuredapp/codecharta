import { createReducer, on } from "@ngrx/store"
import { setState } from "../../util/setState.reducer.factory"
import { setFloorLabelDepth } from "./floorLabelDepth.actions"

export const defaultFloorLabelDepth = 3
export const floorLabelDepth = createReducer(defaultFloorLabelDepth, on(setFloorLabelDepth, setState(defaultFloorLabelDepth)))
