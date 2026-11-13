# Sable-Geometrie-API

**Deutsch** · [English](Sable-Geometrie-API-en) · [Übersicht](Home)

Für Addon-Entwickler bietet TeutonLib 1.6.0 öffentliche Fassaden unter `de.TeutonStudio.TeutonLib.api.sable`. Ihre Signaturen verwenden Minecraft-Typen; die Sable-Implementierung wird erst nach der Prüfung auf die geladene Mod verwendet.

## Weltposition und Entfernung

`SubLevelGeometry.projectToWorld(level, position)` projiziert eine mögliche Plotposition in die sichtbare Welt. Ohne Sable wird die Eingabeposition zurückgegeben.

`distanceSquared(level, first, second)` und `distance(level, first, second)` vergleichen Positionen in derselben Welt einschließlich ihrer Sable-Projektion. Sie ersetzen keine Prüfung auf gleiche Dimension oder geladene Chunks.

```kotlin
val worldPosition = SubLevelGeometry.projectToWorld(level, blockPos.center)
val distance = SubLevelGeometry.distance(level, player.position(), blockPos.center)
```

## Interaktionen

`SubLevelInteractionGeometry.withinReach(player, level, position, padding)` verwendet die Blockinteraktionsreichweite des Spielers plus `padding` (Standard 1 Block). `mayInteract(player, level, position)` fragt `level.mayInteract` an der projizierten Weltposition ab.

Ein Addon muss zusätzlich Menüidentität, Dimension und eigene Zugriffsrechte prüfen. Die Fassade lädt keine Chunks und ersetzt keinen vollständigen serverseitigen Berechtigungsvertrag.

## Darstellung

`SubLevelRenderPose.apply(poseStack, blockEntity, x, y, z, camera, partialTicks)` wendet die clientseitige Projektion auf den PoseStack an und liefert `Result(worldPosition, usedSubLevel)`. Ohne Sable wird zur kamerarelativ berechneten Position verschoben. Diese Renderklasse ausschließlich im Clientpfad verwenden.

[Funk und GPS](Funk-und-GPS) · [Peripheral Link](Peripheral-Link) · [Alle Funktionen](Home)

Quellen: [Geometrie](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelGeometry.kt), [Interaktion](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelInteractionGeometry.kt), [Renderpose](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelRenderPose.kt).

## Abbildung


Die sichtbare Weltposition wird in einem realen Sable-GPS-Nutzer ausgegeben; nicht jeder Interaktions- und Renderhelfer wird damit einzeln geprüft.

![Sable-Geometrie-API: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-sable-gps.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
