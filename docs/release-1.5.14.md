# TeutonLib 1.5.14

Züge verwenden bei installiertem Create 6.0.10, Sable 2.0.6 und CTF 0.1.47
standardmäßig `teutonlib:hybrid_carriage`. Im Fahrmodus ist der Aufbau gesperrt,
während Blockzustände, Maschinen und Unter-Contraptions weiterlaufen. Die Befehle
`/teutonlib train edit` und `/teutonlib train run` wechseln einen angehaltenen,
vollständig geladenen Zug zwischen bearbeitbaren Sable-Blöcken und Fahrbetrieb.
Inventare und Maschinen werden dabei nicht kopiert. Der Umbaumodus verhindert
Gleisbewegung und wird einschließlich Serverneustart gespeichert.

Die bestehende IP-Aeronautics-2.6.4-Portalbrücke bleibt zuständig für Wagentransfer
und Rückabwicklung. Laufende Lager werden nach der Zielinitialisierung erneut
auf ihren Betriebszustand geprüft und bei Bedarf gestartet. Die Wiederanlaufabsicht
liegt im übertragenen Aufbau und übersteht auch Rückabwicklung und Neustart.

Mechanische Lager, Swivel-Lager (Simulated 1.3.2) samt Nutzlast und Inventar sowie
normale und feste Kanonenlager (Create Big Cannons 5.11.7, RPL 2.1.2) werden beim
Portaltransfer kontrolliert zerlegt und nach der Zielinitialisierung wieder
zusammengesetzt. Geladene Munition und Steuerzustände reisen mit den echten
Blöcken. Die Schlauchrolle behält ihren physischen Schlauchkopf auch beim
Dimensionswechsel; der native Fluidhandler bleibt verwendbar.

Create: Radars 0.4.9.2-1.21.1 erhält einen eigenen gespeicherten Contraption-Typ
mit Schüsselzahl, Empfänger, Ausrichtung und Creative-Status. Unter-Contraptions
dürfen ihre eigenen Blöcke beim Zusammenbau entnehmen und beim Zerlegen absetzen.
Der feste Wagen wird während der Fahrt nicht durch Sables Zusammenhangsprüfung
in Einzelteile aufgespalten.

Der Fahrbetrieb verwendet weiterhin einen echten Sable-Simulationsplot samt
Darstellung, Transformation und Physikanbindung. Diese Version stellt keinen
vollständig Sable-freien Laufzeitersatz bereit und verspricht keine entsprechende
Physik- oder Tickkostenersparnis.

[Bedienung und technische Grenzen](hybrid-trains.md). Die echten Serverprüfungen
stehen in `tools/verify_hybrid_trains.py` und `tools/verify_train_portals.py`;
`--packaged` prüft jeweils die Release-JAR.
