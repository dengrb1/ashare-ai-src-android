# Backdrop Android compatibility module

Sources from Kyant0/AndroidLiquidGlass tag 2.0.1 and Kyant0/Shapes 1.2.1,
distributed under Apache-2.0 (see LICENSE). The published 2.0.1 AAR requires
compileSdk 37 and Compose 1.12.0. This Android-only module compiles the same
renderer against this application's SDK 36 and Compose BOM.

Android expect/actual declarations are flattened into ordinary Kotlin declarations.
Platform implementations replace the corresponding common expect declarations.
No published AAR metadata checks are disabled. Keep compatibility edits documented
here when updating the upstream sources.

Compatibility edits: enable Kotlin context parameters in this module; retain the
common RuntimeShader interface beside its Android implementation; store (rather
than override) the two unused newer GraphicsLayerScope blendMode/colorFilter
properties in InverseLayerScope. The optical shader source is unchanged.
