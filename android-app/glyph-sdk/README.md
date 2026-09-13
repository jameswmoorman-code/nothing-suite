# Glyph Developer Kit

Nothing's Glyph SDK is distributed as an `.aar` under its own licence and is
not redistributed here. Download the latest release from
https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit and place
it in this folder as `glyph-sdk.aar`. `settings.gradle.kts` already adds this
folder as a flatDir repository and `:app` depends on `name = "glyph-sdk"`.

For the Phone (3) 25×25 LED matrix, use the separate Glyph Matrix SDK and
swap `GlyphController` for a `GlyphMatrixController` implementation.

During development the manifest carries `NothingKey = "test"`; before a
public release apply for a production key through the developer programme.
