# Afaq VPN Android Branding Pack

This package contains the generated Afaq VPN launcher icon and splash artwork.

## Direct repository overlay
Copy the included `android` directory into the repository root and allow matching files to be replaced. The paths are already arranged under `android/app/src/main/res`.

## Capacitor source assets
The `assets` directory also includes:
- `icon.png` and `icon-only.png`
- `icon-foreground.png`
- `icon-background.png`
- `splash.png` (2732 x 2732)
- `splash-portrait.png` (1440 x 2560)

If the repository uses `@capacitor/assets`, Jules may copy `assets` to the repository root and run:

```bash
npx @capacitor/assets generate --android
npx cap sync android
```

## Important integration note
Android 12 and newer use the system splash-screen API. Jules should inspect the existing `styles.xml`, `themes.xml`, and `AndroidManifest.xml` and keep the current working startup theme. The included legacy/full-screen file is `android/app/src/main/res/drawable-nodpi/splash.png`. No VPN keys, endpoints, or secrets are included.

## Validation
After applying the assets, run the existing TypeScript check, production build, Capacitor sync, and Android Debug APK build.
