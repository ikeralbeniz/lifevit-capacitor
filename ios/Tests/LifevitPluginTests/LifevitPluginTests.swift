import XCTest
@testable import LifevitPlugin

final class LifevitPluginTests: XCTestCase {
    func testPluginLoads() {
        XCTAssertNotNil(LifevitPlugin())
    }
}
