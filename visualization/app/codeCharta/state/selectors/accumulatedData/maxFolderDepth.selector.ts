import { createSelector } from "@ngrx/store"
import { CodeMapNode } from "../../../codeCharta.model"
import { accumulatedDataSelector } from "./accumulatedData.selector"

function computeMaxFolderDepth(node: CodeMapNode, depth: number): number {
    if (!node.children || node.children.length === 0) {
        return depth - 1
    }
    let max = depth
    for (const child of node.children) {
        const childDepth = computeMaxFolderDepth(child, depth + 1)
        if (childDepth > max) {
            max = childDepth
        }
    }
    return max
}

export const maxFolderDepthSelector = createSelector(accumulatedDataSelector, accumulatedData => {
    const root = accumulatedData.unifiedMapNode
    if (!root) {
        return undefined
    }
    return Math.max(1, computeMaxFolderDepth(root, 0) + 1)
})
