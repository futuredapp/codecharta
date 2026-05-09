import { setFloorLabelDepth } from "./floorLabelDepth.actions"
import { floorLabelDepth } from "./floorLabelDepth.reducer"

describe("floorLabelDepth", () => {
    it("should set new floor label depth", () => {
        expect(floorLabelDepth(3, setFloorLabelDepth({ value: 4 }))).toEqual(4)
    })
})
