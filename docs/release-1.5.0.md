# TeutonLib 1.5.0

## Deutsch

TeutonLib 1.5.0 zieht die wiederverwendbare Computer-Infrastruktur aus CC-Aeroworks in
öffentliche, addon-neutrale APIs. Dazu gehören der Peripheral-Topologie- und
Attach/Detach-Lifecycle, clientseitige Workspace-Deskriptoren, persistente Kanalgruppen,
benannte Signalausgänge, der `HostedComputer`-Kernel sowie Sable-, Simulated- und
Netzwerkhilfen. CC-Aeroworks registriert nur noch seine Pult-, Kanal- und UI-Fachadapter.
Das TeutonLib-JAR enthält keine Klassen-, Mixin- oder Metadatenabhängigkeit auf
CC-Aeroworks mehr.

Auf bewegten Sable-Sublevels werden Peripheral-Link-, Funk- und Modem-Höhengeometrie in
sichtbaren Weltkoordinaten ausgewertet. GPS-Payloads und Trilateration bleiben unverändert
bei CC:Tweaked. Die GPS-Quelle von CC-Aeroworks erscheint weiterhin erst nach einem
erfolgreichen Fix und benötigt ein direkt erkennbares Funkmodem.

Der **Peripheral Link** liegt jetzt im CC:Tweaked-Kreativtab. Ein Rechtsklick öffnet
Weiterleitungs-, API- und Statusdetails sowie den gemeinsamen Spitznamen-Editor für Link
und Computer-Quellenansicht.

Persistierte Computer-IDs, Labels, Dateien, Terminalgrößen, Kanal-UUIDs, Gruppenbindungen,
Item-Komponenten und öffentliche Lua-Namen bleiben kompatibel. Addons sollen ausschließlich
Pakete unter `de.TeutonStudio.TeutonLib.api` verwenden.

## English

TeutonLib 1.5.0 moves reusable computer infrastructure out of CC-Aeroworks into public,
addon-neutral APIs: peripheral topology and attachment lifecycle, client workspace
descriptors, persistent channel groups, named signal outputs, the `HostedComputer` kernel,
and Sable, Simulated, and network helpers. CC-Aeroworks now supplies domain adapters only.
The TeutonLib artifact no longer has class, mixin, or metadata dependencies on CC-Aeroworks.

Peripheral Links, wireless distance, and modem altitude on moving Sable sublevels are
evaluated in visible world coordinates. GPS payloads and trilateration remain owned by
CC:Tweaked. CC-Aeroworks' GPS source still appears only after a successful fix and requires
a directly detectable wireless modem.

The **Peripheral Link** now appears in CC:Tweaked's creative tab. Right-clicking opens
forwarding, API, and status details plus the nickname editor shared with the computer
Sources view.

Saved computer IDs, labels, files, terminal sizes, channel UUIDs, group bindings, item
components, and public Lua names remain compatible. Addons should depend only on packages
below `de.TeutonStudio.TeutonLib.api`.

## Verifikation / Verification

- `./gradlew --offline clean test build compilePeripheralTestKotlin`: erfolgreich / passed.
- Dedicated Server ohne CC:Tweaked und Sable sowie CC:Tweaked/Create ohne Sable oder
  CC-Aeroworks: erfolgreich, einschließlich Neustart und Persistenz / passed, including restart
  and persistence.
- Vollständiges Console-Profil mit echten Sable-Sublevels, Funkreichweite, Welt-Y,
  Dimensionsisolation und CraftOS `gps.locate()` vor und nach Bewegung: erfolgreich / passed.
- TeutonLib-JAR ohne CC-Aeroworks-Klassen-, Mixin-, Metadaten- oder Bytecode-Referenzen;
  CC-Aeroworks verwendet ausschließlich öffentliche `api`-Pakete / architecture boundary passed.
- Automatisierte echte Clientläufe für normale/erweiterte Computer, Addon-Workspace,
  GUI-Skalierung 2/3, Resize, Fokus, Escape, Quellen-API, Config-Umschaltung und Peripheral Link:
  erfolgreich / automated real-client runs passed.
- Die manuelle Zwei-Spieler-Sichtprüfung wurde für diesen Build nicht erneut ausgeführt / the
  manual two-player visual check was not rerun for this build.
