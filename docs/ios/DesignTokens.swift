import SwiftUI

struct ShadowToken {
    let color: Color
    let radius: CGFloat
    let y: CGFloat
}

enum DesignTokens {
    enum Colors {
        static let primary = Color(hex: 0x0E5CC7)
        static let secondary = Color(hex: 0x2F7FE6)
        static let accent = Color(hex: 0x0A4B9F)
        static let background = Color(hex: 0xF4F8FF)
        static let surface = Color.white
        static let border = Color(hex: 0xC5D3E7)
        static let textPrimary = Color(hex: 0x0B1730)
        static let textSecondary = Color(hex: 0x51627D)
        static let success = Color(hex: 0x14804A)
        static let warning = Color(hex: 0xC98112)
        static let error = Color(hex: 0xBA1A1A)
    }

    enum Typography {
        static let display = Font.system(size: 32, weight: .bold, design: .rounded)
        static let title = Font.system(size: 24, weight: .bold, design: .rounded)
        static let subtitle = Font.system(size: 18, weight: .semibold, design: .rounded)
        static let body = Font.system(size: 15, weight: .medium, design: .rounded)
        static let caption = Font.system(size: 12, weight: .semibold, design: .rounded)
        static let button = Font.system(size: 14, weight: .bold, design: .rounded)
    }

    enum Spacing {
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
        static let xl: CGFloat = 24
    }

    enum Radius {
        static let small: CGFloat = 14
        static let medium: CGFloat = 20
        static let large: CGFloat = 28
        static let full: CGFloat = 999
    }

    enum Shadow {
        static let subtle = ShadowToken(
            color: Color.black.opacity(0.06),
            radius: 2,
            y: 1
        )
        static let card = ShadowToken(
            color: Color.black.opacity(0.08),
            radius: 8,
            y: 4
        )
        static let floating = ShadowToken(
            color: Color.black.opacity(0.12),
            radius: 14,
            y: 8
        )
    }
}

private extension Color {
    init(hex: UInt, alpha: Double = 1.0) {
        let red = Double((hex >> 16) & 0xFF) / 255.0
        let green = Double((hex >> 8) & 0xFF) / 255.0
        let blue = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}
