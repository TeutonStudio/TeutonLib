# Installation

[Deutsch](Installation) · **English** · [Overview](English)

This wiki covers **TeutonLib 1.6.1** for **Minecraft 1.21.1**, **NeoForge 21.1.234 or newer for 1.21.1**, **Java 21** and **KotlinForForge 5.11.0–<6**.

1. Download the regular mod JAR from [GitHub Releases](https://github.com/TeutonStudio/TeutonLib/releases).
2. Put TeutonLib and KotlinForForge in the `mods` directory on **the server and every client**.
3. Install the optional mods for the features you need, including their own dependencies.
4. Remove **Create Sable Optimization** (`cso`) and **SableSchlauch / Hose Pulley Sable Bridge** (`hosepulley_sable_bridge`). Their features are integrated into TeutonLib; the separate JARs conflict.

The sources JAR is for development. The computer UI template ZIP contains examples and documentation; `SHA256SUMS` covers all three artifacts. Optional mods are not bundled.

## Version and development scope

The 1.6.1 baseline includes [Peripheral Link antennas and locating endpoints](Peripheral-Link-en#antenna-and-locating-endpoints). Release 1.6.2 adds cable routing, station repairs, Flux connection models and tank surfaces. Mobile adapters have moved to TerrArchitecture; see [migration](Android-und-Zalith-Kompatibilitaet-en).

In the November 14 local source state, Android/Zalith adapters moved to TerrArchitecture. Update TeutonLib and TerrArchitecture together for that state. See [Android and Zalith](Android-und-Zalith-Kompatibilitaet-en) for current JVM option names and historical TeutonLib behavior. Resource repairs remain in TeutonLib.

| Feature | Verified optional dependencies |
| --- | --- |
| Cogwheel bridges and analog docks | Create 6.0.10, Sable 2.0.6, Simulated 1.3.2 from Aeronautics 1.3.2 |
| Hybrid trains and station construction | Create 6.0.10, Sable 2.0.6, Train Functionality 0.1.47 |
| Train portals | Above train stack plus IP Aeronautics 2.6.4 |
| Physical hose and drain coupling | Create 6.0.10, Sable 2.0.6 |
| Computers | CC:Tweaked 1.120.2 tested; declared range 1.119.0–<1.121.0 |
| DbW channels | Drive by Wire 0.3.0 tested; declared range 0.3.0–<0.4.0 |
| Motor extension FE input | Create New Age 1.1.7c; Flux Networks 8.0.0 tested as an FE source |

An installed Sable must be at least 2.0.6. Individual integrations may require **exactly** 2.0.6 and verified class hashes. Newer versions are not automatically supported. See [Configuration](Konfiguration-und-Befehle-en).

## Illustration

Built-in NeoForge configuration editor for TeutonLib; no extra config mod is required.

![Installation: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-config.png)

## Sources

- [neoforge.mods.toml](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/templates/META-INF/neoforge.mods.toml)
- [gradle.properties](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/gradle.properties)

[Media provenance and verification scope](Bildnachweise-en)
