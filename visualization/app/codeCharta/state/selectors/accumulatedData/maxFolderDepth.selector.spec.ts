import { TestBed } from "@angular/core/testing"
import { provideMockStore, MockStore } from "@ngrx/store/testing"
import { CodeMapNode, NodeType } from "../../../codeCharta.model"
import { accumulatedDataSelector } from "./accumulatedData.selector"
import { maxFolderDepthSelector } from "./maxFolderDepth.selector"

function getValue() {
    let value: number | undefined
    maxFolderDepthSelector.release()
    const store = TestBed.inject(MockStore)
    store.select(maxFolderDepthSelector).subscribe(v => (value = v))
    return value
}

describe("maxFolderDepthSelector", () => {
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideMockStore()]
        })
    })

    it("should return undefined when no project is loaded", () => {
        const store = TestBed.inject(MockStore)
        store.overrideSelector(accumulatedDataSelector, { unifiedMapNode: undefined, unifiedFileMeta: undefined })

        expect(getValue()).toBeUndefined()
    })

    it("should return 1 for a root with only leaf children", () => {
        const root: CodeMapNode = {
            name: "root",
            type: NodeType.FOLDER,
            attributes: {},
            path: "/root",
            children: [
                { name: "a.ts", type: NodeType.FILE, attributes: {}, path: "/root/a.ts" },
                { name: "b.ts", type: NodeType.FILE, attributes: {}, path: "/root/b.ts" }
            ]
        }
        const store = TestBed.inject(MockStore)
        store.overrideSelector(accumulatedDataSelector, { unifiedMapNode: root, unifiedFileMeta: undefined })

        expect(getValue()).toBe(1)
    })

    it("should return deepest folder depth + 1 for a nested tree", () => {
        const root: CodeMapNode = {
            name: "root",
            type: NodeType.FOLDER,
            attributes: {},
            path: "/root",
            children: [
                {
                    name: "src",
                    type: NodeType.FOLDER,
                    attributes: {},
                    path: "/root/src",
                    children: [
                        {
                            name: "deep",
                            type: NodeType.FOLDER,
                            attributes: {},
                            path: "/root/src/deep",
                            children: [{ name: "x.ts", type: NodeType.FILE, attributes: {}, path: "/root/src/deep/x.ts" }]
                        }
                    ]
                },
                { name: "readme.md", type: NodeType.FILE, attributes: {}, path: "/root/readme.md" }
            ]
        }
        const store = TestBed.inject(MockStore)
        store.overrideSelector(accumulatedDataSelector, { unifiedMapNode: root, unifiedFileMeta: undefined })

        // deepest folder ("deep") is at depth 2 → max = 3
        expect(getValue()).toBe(3)
    })

    it("should clamp to at least 1 for a root with no children", () => {
        const root: CodeMapNode = {
            name: "root",
            type: NodeType.FOLDER,
            attributes: {},
            path: "/root",
            children: []
        }
        const store = TestBed.inject(MockStore)
        store.overrideSelector(accumulatedDataSelector, { unifiedMapNode: root, unifiedFileMeta: undefined })

        expect(getValue()).toBe(1)
    })
})
