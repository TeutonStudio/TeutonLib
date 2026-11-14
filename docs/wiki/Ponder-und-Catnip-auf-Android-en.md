# Ponder and Catnip on Android

[Deutsch](Ponder-und-Catnip-auf-Android) · **English** · [Overview](English)

> **Migration in TeutonLib 1.6.2:** These adapters now belong to TerrArchitecture’s [`mobilecompat` module](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md). TeutonLib no longer ships them. The description and desktop evidence below refer to earlier TeutonLib releases. Existing `teutonlib.mobile.*` options remain aliases in TerrArchitecture.

## Historical TeutonLib behavior through 1.6.1

For detected Android and exactly **Ponder 1.0.82+mc1.21.1**, TeutonLib selects a framebuffer strategy that verifies real stencil behavior, not just nominal completeness. Open ordinary Ponder/Create interfaces normally.

Candidate order: native NeoForge/Ponder path, D24S8 texture with separate depth/stencil attachments, D24S8 texture with combined attachment, then D24S8 renderbuffer. Each must pass framebuffer completeness **and a stencil/pixel test**. Failed candidates are released; resizing revalidates the strategy. Logs identify selection/failure.

If all stencil choices fail, the optional launcher flag `-Dteutonlib.mobile.render.colorMask=true` permits an experimental two-texture color-mask compositor. It defaults off and is a last fallback. Mobile mode options remain separate. Other Ponder versions do not automatically receive these mixins.

The integration is client-only; dedicated servers load no OpenGL compatibility classes. The illustration shows the actual verified dependency entry point/desktop check, **not an Android GPU capture**. Device-specific stencil behavior needs testing on that device.

## Illustration

Executed desktop check of the exact supported target methods. Not a functional validation on an Android device.

![Ponder and Catnip on Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)

## Sources

- [MobileStencilRenderTarget.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/compat/MobileStencilRenderTarget.kt)
- [ColorMaskFallback.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/compat/ColorMaskFallback.kt)
- [PonderRenderTargetMixin.java](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/PonderRenderTargetMixin.java)

[Media provenance and verification scope](Bildnachweise-en)
