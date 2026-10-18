# Engine-pack touch overlay

This is the plain-View overlay injected into the prebuilt **LÖVE 11.5** and
**LÖVE 0.10.2** APKs. It is separate from the launcher's Compose/Miuix UI on
purpose: those engine packs do not contain Compose, and keeping this overlay
View-only means the injection is only a ~40 KB `classes2.dex`.

## How it is injected

1. Build `build/classes2.dex` with `./build.sh`.
2. Copy it into the engine APK as `classes2.dex` (without touching the
   existing `classes.dex`).
3. Add this provider to the APK's manifest:

   ```xml
   <provider
       android:name="org.love2d.android.engineoverlay.OverlayProvider"
       android:authorities="<engine-package>.overlay"
       android:exported="false" />
   ```

4. Re-sign the APK.

The provider registers `ActivityLifecycleCallbacks`; when
`org.love2d.android.GameActivity` is resumed it adds `EngineOverlay` on top of
the SDL surface. There is no need to modify the game or the engine's Java.

Key events are sent by reflection to the pack's own
`org.libsdl.app.SDLActivity.onNativeKeyDown/onNativeKeyUp`, so the game receives
real keyboard input.

## Settings

The overlay persists its layout in the engine pack's own `engine_touch`
SharedPreferences. Each engine pack therefore keeps an independent layout.

## Files

- `EngineOverlay.java` — floating ball, menu, add/delete and property dialog.
- `TouchView.java` — joystick and buttons, 8-direction arrows, key injection.
- `ToggleView.java` — large custom on/off switch.
- `Config.java` — JSON layout persistence and defaults.
- `OverlayProvider.java` — lifecycle hook.
