// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "Example",
    products: [.library(name: "App", targets: ["App"])],
    targets: [.target(name: "Core"), .target(name: "Other"), .target(name: "App", dependencies: ["Core"])]
)
