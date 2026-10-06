# Changelog

## 0.4.10

- Hide the sidebar count label when the stored amount is exactly one, matching vanilla inventory behavior.
- Preserve count labels for larger amounts and K/M/B abbreviations. Keep durability bars and other vanilla item decorations.
- Format abbreviated counts consistently across system locales.
- Include the fixes for item counts being obscured by item models and for overflowing search hints (truncated with `...`).
- Support Minecraft 1.20.1 / Forge with Refined Storage 1.12.x, and Minecraft 1.21.1 / NeoForge with Refined Storage 2.x.

## Earlier fixes

- Support bound wireless grids and creative wireless grids carried in the inventory or optional Curios slots.
- Restore Refined Storage network discovery and server-authorized sidebar interaction.
- Exclude Refined Storage's own screens from the sidebar.
- Use vanilla-style panels and recessed inventory slots.
- Skip portable terminals during bulk deposits.
