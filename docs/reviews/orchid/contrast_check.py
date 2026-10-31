#!/usr/bin/env python3
"""
Repeatable contrast ratio check for the Orchid theme and dock elements.
Evaluates WCAG 2.1 relative luminance and contrast against:
- Text minimum: 4.5:1
- Non-text / icon / border minimum: 3.0:1
"""

def srgb_channel(c):
    c_norm = c / 255.0
    return c_norm / 12.92 if c_norm <= 0.03928 else ((c_norm + 0.055) / 1.055) ** 2.4

def relative_luminance(r, g, b):
    return 0.2126 * srgb_channel(r) + 0.7152 * srgb_channel(g) + 0.0722 * srgb_channel(b)

def contrast_ratio(rgb1, rgb2):
    l1 = relative_luminance(*rgb1)
    l2 = relative_luminance(*rgb2)
    lighter = max(l1, l2)
    darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)

def hex_to_rgb(hex_str):
    hex_str = hex_str.strip().lstrip('#')
    return (int(hex_str[0:2], 16), int(hex_str[2:4], 16), int(hex_str[4:6], 16))

def alpha_blend(fg_rgb, fg_alpha, bg_rgb):
    return tuple(int(round(fg_rgb[i] * fg_alpha + bg_rgb[i] * (1.0 - fg_alpha))) for i in range(3))

# Defined colors from design-system/.../Theme.kt
# Dark Palette
DARK_SURFACE = hex_to_rgb("110C16")
DARK_ON_SURFACE = hex_to_rgb("EDE6F2")
DARK_SURFACE_CONTAINER = hex_to_rgb("1B1820")
DARK_OUTLINE_VARIANT = hex_to_rgb("34313A")
DARK_PRIMARY = (234, 178, 255) # T80
DARK_ON_PRIMARY = (81, 0, 116)  # T20
DARK_TERTIARY_FILL = (255, 180, 167) # T80 Coral
DARK_TERTIARY_ON = (106, 0, 2)       # T20 Coral

# Light Palette
LIGHT_SURFACE = hex_to_rgb("FAF7F2")
LIGHT_ON_SURFACE = hex_to_rgb("1E1A22")
LIGHT_SURFACE_CONTAINER = hex_to_rgb("F2EEE9")
LIGHT_OUTLINE_VARIANT = hex_to_rgb("E3DDE3")
LIGHT_PRIMARY = (137, 57, 172) # T40
LIGHT_ON_PRIMARY = (255, 255, 255)
LIGHT_TERTIARY_FILL = (164, 59, 48) # T40 Coral
LIGHT_TERTIARY_ON = (255, 255, 255)

# AMOLED Palette
AMOLED_SURFACE = (0, 0, 0)
AMOLED_ON_SURFACE = hex_to_rgb("EDE6F2")
AMOLED_SURFACE_CONTAINER = hex_to_rgb("141019")
AMOLED_OUTLINE_VARIANT = hex_to_rgb("2C2734")

# Dock indicator composites
# Dark: container 0.92 alpha on surface, fill = primary at 0.06 alpha over dock container
dark_dock_bg = alpha_blend(DARK_SURFACE_CONTAINER, 0.92, DARK_SURFACE)
dark_lifted_fill = alpha_blend(DARK_PRIMARY, 0.06, dark_dock_bg)
dark_active_icon = DARK_PRIMARY

# Light: container 0.92 alpha on surface, fill = primary at 0.10 alpha over dock container
light_dock_bg = alpha_blend(LIGHT_SURFACE_CONTAINER, 0.92, LIGHT_SURFACE)
light_lifted_fill = alpha_blend(LIGHT_PRIMARY, 0.10, light_dock_bg)
light_active_icon = LIGHT_PRIMARY

pairs = [
    ("onSurface / surface-container (Dark)", DARK_ON_SURFACE, DARK_SURFACE_CONTAINER, 4.5, "text"),
    ("onSurface / surface-container (Light)", LIGHT_ON_SURFACE, LIGHT_SURFACE_CONTAINER, 4.5, "text"),
    ("onSurface / surface-container (AMOLED)", AMOLED_ON_SURFACE, AMOLED_SURFACE_CONTAINER, 4.5, "text"),
    ("onPrimary / primary (Dark)", DARK_ON_PRIMARY, DARK_PRIMARY, 4.5, "text"),
    ("onPrimary / primary (Light)", LIGHT_ON_PRIMARY, LIGHT_PRIMARY, 4.5, "text"),
    ("dock icon vs lifted glass fill (Dark)", dark_active_icon, dark_lifted_fill, 3.0, "icon"),
    ("dock icon vs lifted glass fill (Light)", light_active_icon, light_lifted_fill, 3.0, "icon"),
    ("tertiary coral badge text vs badge fill (Dark)", DARK_TERTIARY_ON, DARK_TERTIARY_FILL, 4.5, "text"),
    ("tertiary coral badge text vs badge fill (Light)", LIGHT_TERTIARY_ON, LIGHT_TERTIARY_FILL, 4.5, "text"),
    ("outlineVariant vs surface (Dark)", DARK_OUTLINE_VARIANT, DARK_SURFACE, 3.0, "border"),
    ("outlineVariant vs surface (Light)", LIGHT_OUTLINE_VARIANT, LIGHT_SURFACE, 3.0, "border"),
    ("outlineVariant vs surface (AMOLED)", AMOLED_OUTLINE_VARIANT, AMOLED_SURFACE, 3.0, "border"),
]

def main():
    print(f"{'Role / Pair':<48} | {'Ratio':<8} | {'Min Req':<8} | {'Status'}")
    print("-" * 75)
    for name, c1, c2, threshold, kind in pairs:
        ratio = contrast_ratio(c1, c2)
        status = "PASS" if ratio >= threshold else "FAIL"
        print(f"{name:<48} | {ratio:5.2f}:1  | {threshold:4.1f}:1  | {status}")

if __name__ == "__main__":
    main()
