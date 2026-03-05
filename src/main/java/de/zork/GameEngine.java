package de.zork;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * Zentrale Spiellogik-Klasse: koordiniert alle Spielkomponenten.
 *
 * <p>Die GameEngine verarbeitet Spielerbefehle, verwaltet den Weltzustand,
 * führt das Würfelsystem aus und kommuniziert Ergebnisse an das Hauptfenster.</p>
 *
 * <p><b>Hauptaufgaben:</b>
 * <ul>
 *   <li>Befehle parsen und ausführen (Angriff, Bewegung, Inventar etc.)</li>
 *   <li>Ortsgenerierung via LLM asynchron in SwingWorker-Threads</li>
 *   <li>Würfelsystem (D20) für Aktionsprüfungen</li>
 *   <li>Level-Up-Mechanik mit Attributspunkt-Dialog</li>
 *   <li>Spielstand speichern und laden</li>
 * </ul>
 *
 * <p>Alle GUI-Aktualisierungen erfolgen über {@code SwingUtilities.invokeLater()}
 * um die Thread-Sicherheit des Swing-EDT zu gewährleisten.</p>
 */
public class GameEngine {

    // --- Richtungsmapping: Name → Koordinatendelta [x, y] ---
    private static final Map<String, int[]> RICHTUNGS_DELTAS = Map.of(
            "north", new int[]{0, 1, 0},
            "south", new int[]{0, -1, 0},
            "east",  new int[]{1, 0, 0},
            "west",  new int[]{-1, 0, 0}
    );

    // --- Richtungsübersetzung Deutsch → Englisch ---
    private static final Map<String, String> DEUTSCHE_RICHTUNGEN = Map.of(
            "norden", "north", "nord", "north",
            "süden",  "south", "süd",  "south",
            "osten",  "east",  "ost",  "east",
            "westen", "west",  "west", "west"
    );

    /** Referenz auf das Hauptfenster für UI-Aktualisierungen. */
    private final MainWindow fenster;

    /** Einstellungsverwaltung für API-Konfiguration. */
    private final SettingsManager einstellungen;

    /** LLM-HTTP-Client für Weltgenerierung und Aktionsnarration. */
    private final LLMClient llmClient;

    /** Generiert neue Orte via LLM. */
    private final WorldGenerator weltGenerator;

    /** Verwaltet Spielstand-Dateien. */
    private final SaveManager saveManager;

    /** D20-Würfelsystem. */
    private final DiceRoller wuerfel;

    /** Der aktive Spielercharakter. */
    private Player spieler;

    /** Cache aller bisher generierten Orte (Schlüssel: "x,y,z"). */
    private Map<String, Location> weltCache;

    /** Aktuelle Spielerposition als [x, y, z]. */
    private int[] position;

    /**
     * Verlauf der letzten besuchten Ort-Schlüssel (max. 3).
     * Wird als Kontext an den LLM-Prompt übergeben.
     */
    private List<String> ortsVerlauf;

    /** Gibt an ob aktuell eine LLM-Generierung läuft. */
    private boolean generierungLaeuft = false;

    /**
     * Erstellt eine neue GameEngine.
     *
     * @param fenster      das Hauptfenster für UI-Callbacks
     * @param einstellungen der Einstellungsmanager
     */
    public GameEngine(MainWindow fenster, SettingsManager einstellungen) {
        this.fenster = fenster;
        this.einstellungen = einstellungen;
        this.llmClient = new LLMClient(einstellungen);
        this.weltGenerator = new WorldGenerator(llmClient);
        this.saveManager = new SaveManager();
        this.wuerfel = new DiceRoller();
    }

    /**
     * Startet ein neues Spiel: setzt alle Zustandsdaten zurück und
     * generiert den Startort (Position 0,0,0).
     */
    public void neuesSpiel() {
        spieler = Player.createNewPlayer();
        weltCache = new HashMap<>();
        position = new int[]{0, 0, 0};
        ortsVerlauf = new ArrayList<>();

        fenster.ausgabeLeeren();
        fenster.ausgabeAnhaengen("""
                ╔══════════════════════════════════════════════════╗
                ║         ZORK-LLM: DAS FANTASY-ABENTEUER          ║
                ╚══════════════════════════════════════════════════╝

                Willkommen, tapferer Abenteurer!
                Du erwachst in einer unbekannten Welt voller Geheimnisse.
                Tippe 'hilfe' für eine Liste der verfügbaren Befehle.
                """);

        // Startort generieren (asynchron via SwingWorker)
        ladeOderGeneriereOrt(position, "none", true);
    }

    /**
     * Verarbeitet einen Freitext-Befehl des Spielers.
     * Parst den Befehl und leitet ihn an die entsprechende Handler-Methode weiter.
     *
     * @param eingabe der rohe Eingabetext des Spielers
     */
    public void verarbeiteBefehl(String eingabe) {
        if (eingabe == null || eingabe.isBlank()) return;
        if (generierungLaeuft) {
            fenster.ausgabeAnhaengen("Bitte warte, während der Ort generiert wird...\n");
            return;
        }

        String bereinigt = eingabe.trim().toLowerCase();
        String[] teile = bereinigt.split("\\s+", 2);
        String befehl = teile[0];
        String argument = teile.length > 1 ? teile[1] : "";

        fenster.ausgabeAnhaengen("\n> " + eingabe + "\n");

        // Befehlsauflösung – Deutsch und Englisch werden akzeptiert
        switch (befehl) {
            case "hilfe", "help", "?" -> zeigeHilfe();
            case "schau", "look", "l", "umschauen" -> beschreibeAktuellenOrt();
            case "inventar", "inv", "i", "inventory" -> zeigeInventar();
            case "nimm", "nehme", "take", "pickup" -> nimmGegenstand(argument);
            case "lasse", "drop" -> lasseGegenstand(argument);
            case "untersuche", "examine", "x", "betrachte" -> untersuche(argument);
            case "benutze", "use", "verwende" -> benutzeGegenstand(argument);
            case "angriff", "greife", "attack", "atk", "kampf" -> greifAn(argument);
            case "gehe", "go", "bewege", "laufe" -> {
                // "gehe nord" → bewegeInRichtung("north")
                String richtung = DEUTSCHE_RICHTUNGEN.getOrDefault(argument, argument);
                bewegeInRichtung(richtung);
            }
            case "north", "south", "east", "west",
                 "n", "s", "e", "o", "w",
                 "norden", "süden", "osten", "westen",
                 "nord", "süd", "ost" -> {
                // Direkteingabe einer Richtung (Deutsch oder Englisch)
                String richtung = DEUTSCHE_RICHTUNGEN.getOrDefault(befehl,
                        kuerzelZuRichtung(befehl));
                bewegeInRichtung(richtung);
            }
            case "rede", "talk", "spreche", "sprich" -> rede(argument);
            case "status", "stats", "charakter" -> zeigeStatus();
            default -> {
                // Unbekannter Befehl: als Freitext-Aktion an LLM senden
                verarbeiteFreitextAktion(eingabe);
            }
        }
    }

    /**
     * Bewegt den Spieler in die angegebene Richtung.
     * Prüft ob die Richtung verfügbar ist, dann lädt/generiert den Zielort.
     *
     * @param richtung die Zielrichtung ("north", "south", "east", "west")
     */
    public void bewegeInRichtung(String richtung) {
        Location aktuellerOrt = aktuellerOrt();
        if (aktuellerOrt == null) {
            fenster.ausgabeAnhaengen("Kein Ort geladen.\n");
            return;
        }

        // Prüfen ob die Richtung verfügbar ist
        if (!aktuellerOrt.isExitOpen(richtung)) {
            if (aktuellerOrt.getExits().contains(richtung) &&
                aktuellerOrt.isHasLockedDoor() &&
                richtung.equals(aktuellerOrt.getLockedDoorDirection())) {
                fenster.ausgabeAnhaengen("Die Tür in diese Richtung ist verschlossen. " +
                        "Du brauchst den richtigen Schlüssel.\n");
            } else {
                fenster.ausgabeAnhaengen("Du kannst nicht in diese Richtung gehen.\n");
            }
            return;
        }

        // Koordinaten der neuen Position berechnen
        int[] delta = RICHTUNGS_DELTAS.get(richtung);
        if (delta == null) {
            fenster.ausgabeAnhaengen("Unbekannte Richtung: " + richtung + "\n");
            return;
        }
        int[] neuePos = {position[0] + delta[0], position[1] + delta[1], position[2] + delta[2]};

        // Aktuellen Ort in den Verlauf aufnehmen
        String alterSchluessel = koordinatenSchluessel(position);
        ortsVerlaufAktualisieren(alterSchluessel);

        position = neuePos;
        ladeOderGeneriereOrt(position, richtung, false);
    }

    /**
     * Lädt einen bekannten oder generiert einen neuen Ort asynchron via SwingWorker.
     *
     * @param pos        die Zielposition [x, y, z]
     * @param vonRichtung die Ankunftsrichtung für den LLM-Kontext
     * @param istStart   true wenn es sich um den Startort handelt
     */
    private void ladeOderGeneriereOrt(int[] pos, String vonRichtung, boolean istStart) {
        String schluessel = koordinatenSchluessel(pos);

        // Gecachter Ort vorhanden?
        if (weltCache.containsKey(schluessel)) {
            zeigeOrt(weltCache.get(schluessel));
            return;
        }

        // Neuer Ort: asynchron via SwingWorker generieren
        generierungLaeuft = true;
        fenster.eingabeAktivieren(false);
        fenster.ausgabeAnhaengen("\n[Dungeon Master denkt nach...]\n");

        // Kontext der letzten 3 Orte zusammenstellen
        List<Location> letzteOrte = ortsVerlauf.stream()
                .map(weltCache::get)
                .filter(Objects::nonNull)
                .toList();

        // Vorheriger Raumname für den LLM-Prompt
        String vorherName = letzteOrte.isEmpty() ? "Unbekannt" :
                letzteOrte.get(letzteOrte.size() - 1).getName();

        SwingWorker<Location, Void> worker = new SwingWorker<>() {
            @Override
            protected Location doInBackground() {
                // LLM-Aufruf im Hintergrundthread (nicht EDT)
                return weltGenerator.generiere(vonRichtung, vorherName, letzteOrte);
            }

            @Override
            protected void done() {
                try {
                    Location neuerOrt = get();
                    weltCache.put(schluessel, neuerOrt);
                    zeigeOrt(neuerOrt);
                } catch (Exception e) {
                    fenster.ausgabeAnhaengen("Fehler beim Generieren des Orts: " + e.getMessage() + "\n");
                } finally {
                    generierungLaeuft = false;
                    fenster.eingabeAktivieren(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * Zeigt den aktuellen Ort an und aktualisiert alle UI-Elemente.
     *
     * @param ort der anzuzeigende Ort
     */
    private void zeigeOrt(Location ort) {
        if (ort == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("\n═══════════════════════════════════════\n");
        sb.append("  ").append(ort.getName()).append("\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append(ort.getDescription()).append("\n\n");

        // Ausgänge anzeigen
        List<String> offeneAusgaenge = ort.getOpenExits();
        if (!offeneAusgaenge.isEmpty()) {
            sb.append("Ausgänge: ").append(String.join(", ", offeneAusgaenge.stream()
                    .map(this::richtungAufDeutsch).toList())).append("\n");
        }
        if (ort.isHasLockedDoor()) {
            sb.append("Hinweis: Eine Tür in Richtung ")
              .append(richtungAufDeutsch(ort.getLockedDoorDirection()))
              .append(" ist verschlossen.\n");
        }

        // Gegenstände anzeigen
        if (!ort.getItems().isEmpty()) {
            sb.append("Gegenstände: ");
            sb.append(String.join(", ", ort.getItems().stream()
                    .map(Item::getName).toList())).append("\n");
        }

        // Gegner anzeigen
        List<Enemy> lebendeGegner = ort.getEnemies().stream()
                .filter(e -> !e.isDead()).toList();
        if (!lebendeGegner.isEmpty()) {
            sb.append("Gegner: ");
            sb.append(String.join(", ", lebendeGegner.stream()
                    .map(Enemy::getName).toList())).append("\n");
        }

        fenster.ausgabeAnhaengen(sb.toString());

        // UI aktualisieren: Navbuttons, Statanzeige
        aktualisiereUI(ort);
    }

    /**
     * Aktualisiert Navigationsbuttons und Statusanzeige auf dem EDT.
     *
     * @param ort der aktuelle Ort (für Navbutton-Status)
     */
    private void aktualisiereUI(Location ort) {
        SwingUtilities.invokeLater(() -> {
            // Navbuttons aktivieren/deaktivieren basierend auf verfügbaren Ausgängen
            fenster.navButtonAktivieren("north", ort.isExitOpen("north"));
            fenster.navButtonAktivieren("south", ort.isExitOpen("south"));
            fenster.navButtonAktivieren("east",  ort.isExitOpen("east"));
            fenster.navButtonAktivieren("west",  ort.isExitOpen("west"));

            // Spielerstatus in der Tabelle aktualisieren
            fenster.statusAktualisieren(spieler, ort.getName());
        });
    }

    /**
     * Führt einen Angriff auf einen Gegner im aktuellen Ort durch.
     * Nutzt das D20-System: STR-Modifikator vs. Gegner-Defense-DC.
     *
     * @param ziel der (Teil-)Name des Gegners
     */
    private void greifAn(String ziel) {
        Location ort = aktuellerOrt();
        if (ort == null) return;

        if (ziel.isBlank()) {
            // Wenn kein Ziel: ersten verfügbaren Gegner angreifen
            List<Enemy> lebende = ort.getEnemies().stream().filter(e -> !e.isDead()).toList();
            if (lebende.isEmpty()) {
                fenster.ausgabeAnhaengen("Hier gibt es niemanden zum Angreifen.\n");
                return;
            }
            ziel = lebende.get(0).getName();
        }

        Enemy gegner = ort.findEnemyByName(ziel);
        if (gegner == null) {
            fenster.ausgabeAnhaengen("Kein Gegner namens '" + ziel + "' gefunden.\n");
            return;
        }

        StringBuilder kampfLog = new StringBuilder();

        // Spieler greift an: W20 + STR-Mod vs Gegner-Defense
        DiceRoller.Aktionsergebnis angriff = wuerfel.probe(spieler.getStrModifier(), gegner.getDefense());
        kampfLog.append(String.format("Du greifst %s an! (W20: %d + %d Mod = %d vs DC %d)\n",
                gegner.getName(), angriff.wurf(), spieler.getStrModifier(),
                angriff.wurf() + spieler.getStrModifier(), gegner.getDefense()));

        if (angriff.kritischerTreffer()) {
            // Kritischer Treffer: doppelter W6-Schaden
            int schaden = wuerfel.kritischerSchadenW6(spieler.getStrModifier());
            gegner.takeDamage(schaden);
            kampfLog.append(String.format("KRITISCHER TREFFER! Du verursachst %d Schaden!\n", schaden));

        } else if (angriff.kritischerFehlschlag()) {
            // Kritischer Fehlschlag: Spieler verletzt sich selbst
            int selbstSchaden = wuerfel.wurf(4);
            spieler.nimmSchaden(selbstSchaden);
            kampfLog.append(String.format("KRITISCHER FEHLSCHLAG! Du stolperst und verletzt dich für %d Schaden!\n",
                    selbstSchaden));

        } else if (angriff.erfolg()) {
            // Normaler Treffer
            int schaden = wuerfel.schadenW6(spieler.getStrModifier());
            gegner.takeDamage(schaden);
            kampfLog.append(String.format("Treffer! Du verursachst %d Schaden.\n", schaden));

        } else {
            // Verfehlt
            kampfLog.append("Verfehlt! Dein Angriff trifft ins Leere.\n");
        }

        // Gegner tot?
        if (gegner.isDead()) {
            kampfLog.append(String.format("%s wurde besiegt! Du erhältst %d EP.\n",
                    gegner.getName(), gegner.getXpReward()));
            boolean levelUp = spieler.addXp(gegner.getXpReward());
            if (levelUp) {
                kampfLog.append("\n*** STUFE AUFGESTIEGEN! ***\n");
                kampfLog.append(String.format("Du erreichst Stufe %d! +5 MaxHP.\n", spieler.getLevel()));
                fenster.ausgabeAnhaengen(kampfLog.toString());
                zeigeAttributspunktDialog();
                return;
            }
        } else {
            // Gegner schlägt zurück (wenn noch am Leben)
            DiceRoller.Aktionsergebnis gegenAngriff = wuerfel.probe(gegner.getAttack(), 12);
            kampfLog.append(String.format("%s greift zurück! (W20: %d)\n",
                    gegner.getName(), gegenAngriff.wurf()));

            if (gegenAngriff.kritischerTreffer()) {
                int schaden = wuerfel.kritischerSchadenW6(0);
                spieler.nimmSchaden(schaden);
                kampfLog.append(String.format("KRITISCHER GEGENTREFFER! Du nimmst %d Schaden!\n", schaden));
            } else if (!gegenAngriff.kritischerFehlschlag() && gegenAngriff.erfolg()) {
                int schaden = wuerfel.schadenW6(0);
                spieler.nimmSchaden(schaden);
                kampfLog.append(String.format("%s trifft dich für %d Schaden.\n",
                        gegner.getName(), schaden));
            } else {
                kampfLog.append(String.format("%s verfehlt dich!\n", gegner.getName()));
            }
        }

        // HP-Anzeige nach dem Kampf
        kampfLog.append(String.format("Deine HP: %d/%d\n", spieler.getHp(), spieler.getMaxHp()));
        fenster.ausgabeAnhaengen(kampfLog.toString());

        // Tod des Spielers prüfen
        if (spieler.istBesiegt()) {
            spielerBesiegt();
        } else {
            // UI nach Kampf aktualisieren
            aktualisiereUI(aktuellerOrt());
        }
    }

    /**
     * Nimmt einen Gegenstand vom Boden des aktuellen Ortes auf.
     *
     * @param name der (Teil-)Name des Gegenstands
     */
    private void nimmGegenstand(String name) {
        Location ort = aktuellerOrt();
        if (ort == null) return;
        if (name.isBlank()) {
            fenster.ausgabeAnhaengen("Was möchtest du aufnehmen?\n");
            return;
        }

        Item gegenstand = ort.findItemByName(name);
        if (gegenstand == null) {
            fenster.ausgabeAnhaengen("Kein Gegenstand namens '" + name + "' hier gefunden.\n");
            return;
        }

        spieler.addItem(gegenstand);
        ort.removeItem(gegenstand.getId());
        fenster.ausgabeAnhaengen(String.format("Du nimmst '%s' auf.\n", gegenstand.getName()));
        aktualisiereUI(ort);
    }

    /**
     * Lässt einen Gegenstand aus dem Inventar fallen.
     *
     * @param name der (Teil-)Name des Gegenstands
     */
    private void lasseGegenstand(String name) {
        if (name.isBlank()) {
            fenster.ausgabeAnhaengen("Was möchtest du fallen lassen?\n");
            return;
        }

        Item gegenstand = spieler.findeGegenstandNachName(name);
        if (gegenstand == null) {
            fenster.ausgabeAnhaengen("Du hast keinen Gegenstand namens '" + name + "'.\n");
            return;
        }

        Location ort = aktuellerOrt();
        spieler.removeItem(gegenstand.getId());
        if (ort != null) {
            ort.getItems().add(gegenstand);
        }
        fenster.ausgabeAnhaengen(String.format("Du lässt '%s' fallen.\n", gegenstand.getName()));
    }

    /**
     * Benutzt einen Gegenstand aus dem Inventar.
     * Verarbeitet Schlüssel (öffnen verschlossener Türen) und Verbrauchsgegenstände.
     *
     * @param name der (Teil-)Name des Gegenstands
     */
    private void benutzeGegenstand(String name) {
        if (name.isBlank()) {
            fenster.ausgabeAnhaengen("Was möchtest du benutzen?\n");
            return;
        }

        Item gegenstand = spieler.findeGegenstandNachName(name);
        if (gegenstand == null) {
            fenster.ausgabeAnhaengen("Du hast keinen Gegenstand namens '" + name + "'.\n");
            return;
        }

        if (!gegenstand.isUsable()) {
            fenster.ausgabeAnhaengen(String.format("'%s' kann nicht aktiv benutzt werden.\n",
                    gegenstand.getName()));
            return;
        }

        Location ort = aktuellerOrt();

        // Schlüssel: prüfen ob eine passende verschlossene Tür im aktuellen Ort vorhanden ist
        if ("locked_door".equals(gegenstand.getUseTarget())) {
            if (ort != null && ort.isHasLockedDoor() &&
                gegenstand.getId().equals(ort.getRequiredKeyId())) {
                ort.unlockDoor();
                spieler.removeItem(gegenstand.getId());
                fenster.ausgabeAnhaengen(String.format(
                        "Du benutzt '%s' und öffnest die verschlossene Tür. Der Weg ist frei!\n",
                        gegenstand.getName()));
                aktualisiereUI(ort);
                return;
            } else {
                fenster.ausgabeAnhaengen("Hier gibt es keine passende verschlossene Tür.\n");
                return;
            }
        }

        // Stateffekte anwenden (z.B. Heiltrank)
        if (!gegenstand.getStatEffects().isEmpty()) {
            spieler.wendeStateffekteAn(gegenstand.getStatEffects());
            // Verbrauchbar: aus Inventar entfernen wenn nicht permanent
            if (!gegenstand.isPermanent()) {
                spieler.removeItem(gegenstand.getId());
            }
            fenster.ausgabeAnhaengen(String.format("Du benutzt '%s'.\n", gegenstand.getName()));
            fenster.ausgabeAnhaengen(String.format("Deine HP: %d/%d\n", spieler.getHp(), spieler.getMaxHp()));
            if (ort != null) aktualisiereUI(ort);
        } else {
            fenster.ausgabeAnhaengen(String.format("Du benutzt '%s', aber nichts passiert.\n",
                    gegenstand.getName()));
        }
    }

    /**
     * Untersucht einen Gegenstand im Inventar oder am aktuellen Ort.
     *
     * @param ziel der (Teil-)Name des zu untersuchenden Gegenstands/Ortes
     */
    private void untersuche(String ziel) {
        if (ziel.isBlank()) {
            beschreibeAktuellenOrt();
            return;
        }

        // Zuerst im Inventar suchen
        Item imInventar = spieler.findeGegenstandNachName(ziel);
        if (imInventar != null) {
            fenster.ausgabeAnhaengen(String.format("'%s': %s\n",
                    imInventar.getName(), imInventar.getDescription()));
            return;
        }

        // Dann am Boden suchen
        Location ort = aktuellerOrt();
        if (ort != null) {
            Item amBoden = ort.findItemByName(ziel);
            if (amBoden != null) {
                fenster.ausgabeAnhaengen(String.format("'%s': %s\n",
                        amBoden.getName(), amBoden.getDescription()));
                return;
            }

            // Gegner untersuchen
            Enemy gegner = ort.findEnemyByName(ziel);
            if (gegner != null) {
                fenster.ausgabeAnhaengen(String.format("'%s': HP %d/%d, Angriff +%d, Verteidigung DC %d\n",
                        gegner.getName(), gegner.getHp(), gegner.getMaxHp(),
                        gegner.getAttack(), gegner.getDefense()));
                return;
            }
        }

        fenster.ausgabeAnhaengen("Du siehst hier nichts Nennenswertes mit dem Namen '" + ziel + "'.\n");
    }

    /**
     * Versucht eine Unterhaltung (INT-Probe gegen DC 12).
     * Bei Erfolg gibt der LLM eine narrative Antwort.
     *
     * @param ziel optionaler Gesprächspartner
     */
    private void rede(String ziel) {
        Location ort = aktuellerOrt();

        // Prüfen ob Gesprächspartner vorhanden
        boolean gegnerVorhanden = ort != null && !ort.getEnemies().stream()
                .filter(e -> !e.isDead())
                .filter(e -> ziel.isBlank() || e.getName().toLowerCase().contains(ziel.toLowerCase()))
                .toList().isEmpty();

        if (!gegnerVorhanden && !ziel.isBlank()) {
            fenster.ausgabeAnhaengen("Mit wem möchtest du reden?\n");
            return;
        }

        // INT-Probe
        DiceRoller.Aktionsergebnis probe = wuerfel.probe(spieler.getIntelModifier(), 12);
        fenster.ausgabeAnhaengen(String.format("Redewurf: W20=%d + INT-Mod=%d = %d vs DC 12\n",
                probe.wurf(), spieler.getIntelModifier(),
                probe.wurf() + spieler.getIntelModifier()));

        if (probe.kritischerTreffer()) {
            fenster.ausgabeAnhaengen("Kritischer Erfolg! Deine Worte treffen ins Schwarze.\n");
        } else if (probe.kritischerFehlschlag()) {
            fenster.ausgabeAnhaengen("Kritischer Fehlschlag! Deine Worte lösen Feindseligkeit aus.\n");
            if (gegnerVorhanden && ort != null) {
                // Gegner wird wütend und greift an
                Enemy wuetenderGegner = ort.getEnemies().stream()
                        .filter(e -> !e.isDead()).findFirst().orElse(null);
                if (wuetenderGegner != null) {
                    greifAn(wuetenderGegner.getName());
                }
            }
        } else if (probe.erfolg()) {
            fenster.ausgabeAnhaengen("Erfolg! Das Gespräch verläuft zu deinen Gunsten.\n");
        } else {
            fenster.ausgabeAnhaengen("Misserfolg. Das Gespräch führt zu nichts.\n");
        }
    }

    /**
     * Sendet eine Freitext-Aktion als Narrations-Anfrage an den LLM.
     * Der LLM beschreibt das Ergebnis der Aktion im Spielkontext.
     *
     * @param aktion die freie Aktionsbeschreibung des Spielers
     */
    private void verarbeiteFreitextAktion(String aktion) {
        Location ort = aktuellerOrt();
        if (ort == null) return;

        generierungLaeuft = true;
        fenster.eingabeAktivieren(false);

        String kontext = String.format(
                "Du bist Dungeon-Master. Der Spieler befindet sich in '%s'. " +
                "Beschreibung: %s. " +
                "Aktion des Spielers: '%s'. " +
                "Beschreibe das Ergebnis in 2-3 Sätzen auf Deutsch. Keine JSON-Ausgabe.",
                ort.getName(), ort.getDescription(), aktion
        );

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return llmClient.chat(
                        "Du bist ein Dungeon-Master für ein Fantasy-Textadventure. " +
                        "Antworte auf Deutsch in 2-3 atmosphärischen Sätzen.",
                        kontext);
            }

            @Override
            protected void done() {
                try {
                    String narration = get();
                    fenster.ausgabeAnhaengen(narration + "\n");
                } catch (Exception e) {
                    fenster.ausgabeAnhaengen("Die Umgebung reagiert nicht auf deine Aktion.\n");
                } finally {
                    generierungLaeuft = false;
                    fenster.eingabeAktivieren(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * Zeigt die Hilfe-Übersicht aller verfügbaren Befehle an.
     */
    private void zeigeHilfe() {
        fenster.ausgabeAnhaengen("""
                ┌─────────────────────────────────────────────────┐
                │                  BEFEHLSLISTE                   │
                ├─────────────────────────────────────────────────┤
                │ BEWEGUNG:                                        │
                │  nord/süd/ost/west  – In Richtung gehen        │
                │  gehe [richtung]    – Alternative Bewegung      │
                │                                                  │
                │ ERKUNDUNG:                                       │
                │  schau              – Ort neu beschreiben       │
                │  untersuche [name]  – Gegenstand untersuchen    │
                │                                                  │
                │ INVENTAR:                                        │
                │  inventar           – Inventar anzeigen         │
                │  nimm [name]        – Gegenstand aufnehmen      │
                │  lasse [name]       – Gegenstand fallen lassen  │
                │  benutze [name]     – Gegenstand benutzen       │
                │                                                  │
                │ KAMPF:                                           │
                │  angriff [name]     – Gegner angreifen          │
                │                                                  │
                │ SOZIAL:                                          │
                │  rede [name]        – Mit jemandem reden         │
                │                                                  │
                │ SONSTIGES:                                       │
                │  status             – Charakter-Stats anzeigen  │
                │  hilfe              – Diese Hilfe anzeigen      │
                │                                                  │
                │ Freie Eingabe wird als Aktion interpretiert!    │
                └─────────────────────────────────────────────────┘
                """);
    }

    /**
     * Zeigt das Inventar des Spielers an.
     */
    private void zeigeInventar() {
        List<Item> inventar = spieler.getInventory();
        if (inventar.isEmpty()) {
            fenster.ausgabeAnhaengen("Dein Inventar ist leer.\n");
            return;
        }
        StringBuilder sb = new StringBuilder("Inventar:\n");
        for (Item item : inventar) {
            sb.append("  - ").append(item.getName());
            if (item.isUsable()) sb.append(" [verwendbar]");
            sb.append("\n");
        }
        fenster.ausgabeAnhaengen(sb.toString());
    }

    /**
     * Zeigt den detaillierten Charakterstatus an.
     */
    private void zeigeStatus() {
        fenster.ausgabeAnhaengen(String.format("""
                Charakterstatus:
                  HP:   %d/%d
                  Stufe: %d (EP: %d/%d)
                  STR:  %d (Mod: %+d)
                  GES:  %d (Mod: %+d)
                  INT:  %d (Mod: %+d)
                """,
                spieler.getHp(), spieler.getMaxHp(),
                spieler.getLevel(), spieler.getXp(), spieler.naechsteLevelSchwelle(),
                spieler.getStr(), spieler.getStrModifier(),
                spieler.getDex(), spieler.getDexModifier(),
                spieler.getIntel(), spieler.getIntelModifier()));
    }

    /**
     * Beschreibt den aktuellen Ort erneut (wie "schau").
     */
    private void beschreibeAktuellenOrt() {
        Location ort = aktuellerOrt();
        if (ort != null) {
            zeigeOrt(ort);
        } else {
            fenster.ausgabeAnhaengen("Du befindest dich nirgendwo.\n");
        }
    }

    /**
     * Behandelt die Niederlage des Spielers.
     */
    private void spielerBesiegt() {
        fenster.ausgabeAnhaengen("""

                ╔══════════════════════════════════════════════════╗
                ║               DU BIST GESTORBEN!                 ║
                ║                                                   ║
                ║  Die Dunkelheit hüllt dich ein...                ║
                ║  Starte ein neues Spiel über das Menü.           ║
                ╚══════════════════════════════════════════════════╝
                """);
        fenster.eingabeAktivieren(false);
        fenster.navButtonsAktivieren(false);
    }

    /**
     * Zeigt einen Dialog zur Auswahl des Level-Up-Attributspunkts.
     * Erhöht das gewählte Attribut um 1.
     */
    private void zeigeAttributspunktDialog() {
        String[] optionen = {"Stärke (STR)", "Geschicklichkeit (GES)", "Intelligenz (INT)"};
        int wahl = JOptionPane.showOptionDialog(
                fenster,
                String.format("Du bist auf Stufe %d aufgestiegen!\n" +
                              "Wähle ein Attribut zum Erhöhen (+1):", spieler.getLevel()),
                "Level-Up!",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                optionen,
                optionen[0]
        );

        String attribut = switch (wahl) {
            case 0 -> "str";
            case 1 -> "dex";
            case 2 -> "intel";
            default -> "str";
        };

        spieler.erhoeheAttribut(attribut);
        fenster.ausgabeAnhaengen(String.format("Attribut %s erhöht!\n", optionen[Math.max(0, wahl)]));

        // UI nach Level-Up aktualisieren
        Location ort = aktuellerOrt();
        if (ort != null) aktualisiereUI(ort);
    }

    /**
     * Gibt den aktuellen Ort aus dem Weltcache zurück.
     *
     * @return der aktuelle Ort oder {@code null} wenn noch keiner generiert wurde
     */
    public Location aktuellerOrt() {
        return weltCache.get(koordinatenSchluessel(position));
    }

    /**
     * Aktualisiert den Ortsverlauf (max. 3 Einträge für LLM-Kontext).
     *
     * @param ortSchluessel der Koordinaten-Schlüssel des besuchten Ortes
     */
    private void ortsVerlaufAktualisieren(String ortSchluessel) {
        ortsVerlauf.add(ortSchluessel);
        // Verlauf auf max. 3 Einträge begrenzen
        if (ortsVerlauf.size() > 3) {
            ortsVerlauf.remove(0);
        }
    }

    /**
     * Erzeugt einen eindeutigen Schlüsselstring für eine 3D-Koordinate.
     *
     * @param pos die Position [x, y, z]
     * @return Schlüssel im Format "x,y,z"
     */
    private String koordinatenSchluessel(int[] pos) {
        return String.format("%d,%d,%d", pos[0], pos[1], pos[2]);
    }

    /**
     * Übersetzt eine interne Richtungskennung ins Deutsche.
     *
     * @param richtung englische Richtung ("north", "south", "east", "west")
     * @return deutsche Bezeichnung ("Norden", "Süden", "Osten", "Westen")
     */
    private String richtungAufDeutsch(String richtung) {
        return switch (richtung) {
            case "north" -> "Norden";
            case "south" -> "Süden";
            case "east"  -> "Osten";
            case "west"  -> "Westen";
            default -> richtung;
        };
    }

    /**
     * Wandelt ein Richtungskürzel in die vollständige englische Richtung um.
     *
     * @param kuerzel z.B. "n", "s", "e", "w"
     * @return vollständige Richtung oder das Original wenn unbekannt
     */
    private String kuerzelZuRichtung(String kuerzel) {
        return switch (kuerzel) {
            case "n" -> "north";
            case "s" -> "south";
            case "e" -> "east";
            case "o" -> "east";
            case "w" -> "west";
            default -> kuerzel;
        };
    }

    /**
     * Speichert den aktuellen Spielzustand.
     *
     * @throws IOException bei Schreibfehler
     */
    public void spielSpeichern() throws IOException {
        GameState zustand = new GameState(spieler, weltCache, position.clone(), new ArrayList<>(ortsVerlauf));
        saveManager.speichern(zustand);
    }

    /**
     * Lädt einen Spielzustand aus der Standardspeicherdatei.
     *
     * @throws IOException bei Lesefehler
     */
    public void spielLaden() throws IOException {
        GameState zustand = saveManager.laden();
        this.spieler = zustand.getSpieler();
        this.weltCache = zustand.getBesuchteOrte();
        this.position = zustand.getAktuellePosition();
        this.ortsVerlauf = zustand.getOrtsVerlauf();

        fenster.ausgabeLeeren();
        fenster.ausgabeAnhaengen("Spielstand geladen.\n\n");

        Location ort = aktuellerOrt();
        if (ort != null) {
            zeigeOrt(ort);
        }
    }

    /**
     * Prüft ob ein gespeicherter Spielstand vorhanden ist.
     *
     * @return true wenn savegame.json existiert
     */
    public boolean speicherstandVorhanden() {
        return saveManager.speicherstandVorhanden();
    }
}
