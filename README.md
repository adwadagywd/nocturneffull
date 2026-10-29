# Nocturne - Fabric 1.21.4 utility client

Open the ClickGUI with RIGHT SHIFT (rebind in Controls -> Nocturne).
Tabs: Modules | Configs | Friends | Theme.

Modules tab:  LMB toggle, RMB expand settings / collapse panel, MMB bind a key (Backspace clears), drag headers, scroll.
Configs tab:  type a name + Save, Load / Delete saved configs (.minecraft/config/nocturne/configs).
Friends tab:  add nicknames - friends are green, never targeted by combat modules, protected by NoFriendsDamage.
Theme tab:    presets + custom colours, roundness, opacity, animation speed, blur, particles.

Build:  gradle build   ->  build/libs/nocturne-1.0.0.jar  (drop in mods/ together with Fabric API). Needs JDK 21.

Font: the UI uses the bundled "Instrument Sans" (SIL OFL licence, see FONT-LICENSE-InstrumentSans-OFL.txt).
Theme tab -> Font: Custom / Minecraft (use Minecraft if text ever shows up as boxes), Font Y offset fine-tunes the vertical position.
To use another font: replace assets/nocturne/font/instrument_sans(.ttf|_bold.ttf) and keep the same file names.

v9 additions: Visuals panel (AirParticles, Trails, JumpCircles, TargetRing, HitMarker, DamageText, Crosshair, Vignette),
ItemESP, Radar, PotionHUD, Notifications, Nuker, damage-taking FakePlayer, shorter enchant tags, cobweb-proof hitboxes.
Optional mixins (WorldFog, cobwebs, vanilla crosshair) live in nocturne.optional.mixins.json - if one cannot apply,
only that single feature stops working.

v10: TriggerBot Smart mode, medium-weight UI font (nocturne_ui.ttf, derived from Instrument Sans, OFL),
friend colour in Tracers/ESP/Nametags, NoFriendsDamage with a second protection layer and an ON/OFF switch in the Friends tab.
README: Nuker 'Selected' mode now learns block types by mining.

v12: FakePlayer reacts like a real player (hit sounds, crit/damage particles, i-frames, flinch, no knockback, no packet to the server);
Freecam rewritten (re-asserts the camera, smoothing, mouse-source switch, info pill).

v13: new Visuals - Aurora (northern lights + shooting stars at night), Orbitals (glowing orbs with comet tails + halo),
HitBurst (sparks + shockwave ring on every hit), SpeedLines (radial streaks at high speed). All colours follow the Theme tab.
