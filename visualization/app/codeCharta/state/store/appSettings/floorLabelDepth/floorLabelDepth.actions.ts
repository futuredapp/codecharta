import { createAction, props } from "@ngrx/store"

export const setFloorLabelDepth = createAction("SET_FLOOR_LABEL_DEPTH", props<{ value: number }>())
