# Vault Passcode Page: Figma Design vs. Native Implementation

## 1. Design Anchor Reference
- **Figma Frame Node ID**: `1:305` (`Locked space — Passcode (PIN) — Dark`)
- **Exported Figma Asset**: [`figma_passcode_1_305.png`](file:///d:/Istak/Messages/Messages%20App/01_active_codebase/messages/docs/reviews/round4/vault_compare/figma_passcode_1_305.png)
- **App Dark Screenshot**: [`vault_unlock_pin_dark.png`](file:///d:/Istak/Messages/Messages%20App/01_active_codebase/messages/docs/reviews/round4/after/vault_unlock_pin_dark.png)
- **App Light Screenshot**: [`vault_unlock_pin_light.png`](file:///d:/Istak/Messages/Messages%20App/01_active_codebase/messages/docs/reviews/round4/after/vault_unlock_pin_light.png)
- **Composite Comparison**: [`passcode_side_by_side.png`](file:///d:/Istak/Messages/Messages%20App/01_active_codebase/messages/docs/reviews/round4/vault_compare/passcode_side_by_side.png)

## 2. Measurement and Element Comparison

| Element | Figma Design (1:305) | Native Compose Implementation | Status |
| :--- | :--- | :--- | :--- |
| **Top Lock Badge** | 72dp circle, filled container, 34dp lock glyph | `VaultLockBadge`: 72dp circle `primaryContainer`, 34dp lock `onPrimaryContainer` | Match |
| **Title** | "Enter your secret code", 24sp Bold | `Text(R.string.secret_prompt_title)`, `MaterialTheme.typography.headlineSmall` | Match |
| **Subtitle** | "Enter your PIN", 14sp Regular | `Text(R.string.secret_prompt_subtitle_pin)`, `MaterialTheme.typography.bodyMedium` | Match |
| **PIN Dots** | 6 dots, 14dp diameter, 14dp spacing | `VaultPinDots`: 6 dots, 14dp circle, 14dp horizontal spacing (`Arrangement.spacedBy(14.dp)`) | Match |
| **Dot Active Fill** | Solid accent fill | `MaterialTheme.colorScheme.primary` | Match |
| **Dot Inactive Fill** | 2dp stroke outline | `BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant)` | Match |
| **Keypad Grid** | 4 rows x 3 columns | 4 rows x 3 columns (`Column` + `Row`) | Match |
| **Keypad Buttons** | Circular surface, ~76dp diameter | 76dp circular `Surface` (`CircleShape`, min touch target >= 48dp) | Match |
| **Keypad Text** | 28sp Medium, centered | `28.sp`, `FontWeight.Medium`, centered | Match |
| **Backspace Key** | Bottom-right, backspace vector | `Icons.AutoMirrored.Filled.Backspace`, 26dp tint `onSurface` | Match |
| **Empty Slot (Bottom-left)** | Empty space | `Spacer(Modifier.size(76.dp))` | Match |
| **Secondary Action** | "Use pattern instead" link | `TextButton`: `stringResource(R.string.secret_use_pattern_instead)` | Match |
| **Reset Action** | "Reset" button | `TextButton`: `stringResource(R.string.secret_reset)` | Match |

## 3. Notable Adaptations
- **Adaptive Insets**: Native Compose applies `safeDrawingPadding()` and WindowInsets to support Android 14/15 edge-to-edge navigation bars and display cutouts.
- **Dynamic Theming**: Figma design provides fixed dark palette tokens; Native implementation binds to `MaterialTheme.colorScheme` (`primary`, `primaryContainer`, `surfaceContainer`, `onSurface`), enabling seamless Light and Dark theme switching.
- **Accessibility & TalkBack**:
  - Dots row announces live updates via `contentDescription = stringResource(R.string.secret_pin_progress, length, 6)` with `liveRegion = LiveRegionMode.Polite`.
  - Keypad buttons have explicit `contentDescription` for each digit and `Role.Button`.
  - Keypad grid forces LTR layout direction (`LayoutDirection.Ltr`) to keep numeric keypad layout consistent in RTL locales.
- **Motion & Feedback**:
  - On incorrect code or weak entry, dots execute horizontal shake animation via `Animatable` with `Motion.spatialFast()`.
