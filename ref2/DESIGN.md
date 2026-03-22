# Design System Specification: High-End Mobility & Intelligence

## 1. Overview & Creative North Star
**Creative North Star: The Kinetic Observatory**
This design system rejects the static "dashboard" trope in favor of a fluid, cinematic experience. It is where the precision of high-performance automotive interfaces (Tesla) meets the editorial restraint of premium consumer hardware (Apple). 

To break the "template" look, we employ **Intentional Asymmetry**. Key data points are not just boxed; they are staged. We use extreme typographic scales—pairing massive, thin-weight numbers with tiny, high-contrast labels—to create a sense of authoritative data-storytelling. The interface should feel less like software and more like a projection onto a high-end cockpit windshield.

---

## 2. Colors & Tonal Depth
The palette is rooted in deep, "ink-pool" neutrals to ensure that the Cyan and Soft Green accents appear as light sources rather than just flat colors.

### The Foundation
*   **Surface-Dim / Background:** `#0c0e10` — The absolute base.
*   **Surface-Container-Low:** `#111416` — Subtle differentiation for secondary zones.
*   **Surface-Container-High:** `#1d2022` — For elevated, interactive elements.

### The "No-Line" Rule
Traditional 1px solid borders for sectioning are strictly prohibited. Boundaries must be defined solely through background color shifts. To separate a sidebar from a map, use a transition from `surface-dim` to `surface-container-low`. The eye should perceive depth through value changes, not "fences."

### Signature Textures (The Glass & Gradient Rule)
Main CTAs and hero data visualizations must use a subtle gradient transition from `primary` (#99f7ff) to `primary-container` (#00f1fe). This provides a "lit-from-within" glow that flat hex codes cannot replicate. Floating panels must use `backdrop-filter: blur(20px)` combined with an 8% opacity white fill to simulate heavy frosted glass.

---

## 3. Typography
We utilize a dual-font strategy to balance technical precision with modern elegance.

| Token | Font | Size | Weight | Intent |
| :--- | :--- | :--- | :--- | :--- |
| **Display-LG** | Inter | 3.5rem | 300 | Hero data (e.g., "Estimated Time") |
| **Headline-SM** | Inter | 1.5rem | 500 | Section headers |
| **Title-MD** | Inter | 1.125rem | 600 | Card titles |
| **Body-MD** | Inter | 0.875rem | 400 | General descriptions |
| **Label-MD** | Space Grotesk | 0.75rem | 700 | Technical metadata (Uppercase, +5% tracking) |

**The Typography Philosophy:** 
Focus on the numbers. Use `display-lg` for numeric values to make them the "hero" of the layout. Use `Space Grotesk` for labels to provide a "technical instrument" feel that contrasts against the humanist `Inter` body text.

---

## 4. Elevation & Depth
In this system, depth is a physical property. We move away from shadows toward **Tonal Layering**.

*   **The Layering Principle:** Stacking follows a logic of light. Place a `surface-container-highest` panel on a `surface-container-low` background to create a "lift" effect. 
*   **Ambient Shadows:** Use only for floating global elements (like a navigation fab). Shadows must be extra-diffused: `box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4)`.
*   **The "Ghost Border" Fallback:** If a border is required for accessibility, use `outline-variant` at 10% opacity. 100% opaque borders are considered "visual noise" and must be avoided.
*   **Glassmorphism:** All floating overlays must utilize the glass-blue tokens with a `backdrop-blur` of 20px. This allows the glowing map lines to bleed through the UI, integrating the data layer with the navigation layer.

---

## 5. Components

### Buttons & Interaction
*   **Primary:** Gradient fill (`primary` to `primary_dim`). `md` (1.5rem) corner radius. No border.
*   **Secondary:** Glass-fill (8% white). `outline-variant` Ghost Border at 20% opacity.
*   **States:** On hover, primary buttons should increase their "inner glow" via a subtle box-shadow (primary color at 20% opacity).

### Floating Navigation Cards
*   **Styling:** Use `surface-container` with 24px (`lg`) corner radius. 
*   **Constraint:** Forbid the use of divider lines within cards. Separate "Arrival Time" from "Route Options" using a `spacing-6` (2rem) vertical gap. Use a `surface-variant` background shift for the active route selection.

### Map Elements (The "Luminous Path")
*   **Route Lines:** Use `secondary` (#00ffab) with a 4px outer glow for the primary route. 
*   **Traffic:** Muted tones. Use `error_dim` (#d7383b) for heavy traffic, never high-saturation red, to maintain the premium dark-mode aesthetic.

### Input Fields
*   **Architecture:** Bottom-border only or Ghost Border. Focus state should trigger a `primary` color glow. Labels should use `label-sm` in `on-surface-variant` color.

---

## 6. Do’s and Don’ts

### Do:
*   **Do** use asymmetrical margins (e.g., `spacing-12` on the left, `spacing-6` on the right) to create a dynamic, editorial feel.
*   **Do** allow map elements to "peek" behind glass panels.
*   **Do** use `secondary` (#00ffab) for "Success" or "Optimal" states to reinforce the premium nature of the AI's logic.

### Don't:
*   **Don't** use pure white (#FFFFFF) for text. Use `on-surface` (#eeeef0) to reduce eye strain and maintain the "Tesla" mood.
*   **Don't** use standard "drop shadows" on cards. Rely on tonal shifts between `surface` tiers.
*   **Don't** use sharp corners. Everything follows the `DEFAULT` (1rem) or `lg` (2rem) scale to feel organic and machined.

---

## 7. Spacing Scale
Precision is non-negotiable. Use the following increments to maintain the rhythmic integrity of the layout:
*   **Tight (UI elements):** `spacing-2` (0.7rem) or `spacing-3` (1rem).
*   **Structural (Gutter):** `spacing-6` (2rem).
*   **Cinematic (Hero breathing room):** `spacing-16` (5.5rem) or `spacing-24` (8.5rem).