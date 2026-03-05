package de.zork;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generiert neue Spielorte über den LLM und wandelt die JSON-Antworten
 * in {@link Location}-Objekte um.
 *
 * <p><b>Generierungsablauf:</b>
 * <ol>
 *   <li>LLM-Prompt mit Kontext der letzten 3 Orte zusammenstellen</li>
 *   <li>{@link LLMClient#chat(String, String)} aufrufen</li>
 *   <li>Markdown-Code-Fences ggf. entfernen</li>
 *   <li>JSON mit Jackson in ein {@link Location}-Objekt parsen</li>
 *   <li>Bei Parse-Fehler: einen vordefinierten Fallback-Ort zurückgeben</li>
 * </ol>
 */
public class WorldGenerator {

    /** Systemanweisung für den LLM - definiert die Rolle als Dungeon-Master. */
    private static final String SYSTEM_PROMPT =
            "Du bist ein Dungeon-Master für ein Fantasy-Textadventure. " +
            "Antworte NUR mit einem JSON-Objekt ohne Markdown-Formatierung oder Codeblöcke. " +
            "Erstelle atmosphärische, vielfältige Räume mit interessanten Details. " +
            "Beachte den Kontext der zuletzt besuchten Orte für thematische Kohärenz. " +
            "WICHTIG: Ausgänge (exits) NUR als englische Kleinbuchstaben: \"north\", \"south\", \"east\", \"west\". " +
            "Niemals Deutsch, niemals Großbuchstaben, niemals andere Richtungen.";

    /** JSON-Schema als Vorlage für den LLM-Prompt. */
    private static final String JSON_SCHEMA =
            "{ \"name\": \"string\", \"description\": \"string\", " +
            "\"exits\": [\"north\",\"east\",\"south\",\"west\"], " +
            "\"items\": [{ \"id\": \"string\", \"name\": \"string\", \"description\": \"string\", " +
            "\"usable\": false, \"useTarget\": \"string\", \"statEffects\": {}, \"permanent\": true }], " +
            "\"enemies\": [{ \"name\": \"string\", \"hp\": 10, \"maxHp\": 10, \"attack\": 2, " +
            "\"defense\": 12, \"xpReward\": 20 }], " +
            "\"hasLockedDoor\": false, \"lockedDoorDirection\": \"\", \"requiredKeyId\": \"\" }";

    private final LLMClient llmClient;
    private final ObjectMapper mapper;

    /**
     * Erstellt einen WorldGenerator.
     *
     * @param llmClient der konfigurierte LLM-HTTP-Client
     */
    public WorldGenerator(LLMClient llmClient) {
        this.llmClient = llmClient;
        this.mapper = new ObjectMapper();
    }

    /**
     * Generiert einen neuen Spielort mittels LLM.
     *
     * <p>Der Prompt enthält die Richtung, aus der der Spieler kommt,
     * den Namen des vorherigen Raums sowie die letzten 3 besuchten Orte
     * als Kontext für thematische Kohärenz.</p>
     *
     * @param vonRichtung     die Richtung, aus der der Spieler kommt (z.B. "north")
     * @param vorherrRaum     Name des zuletzt besuchten Ortes
     * @param letzteOrte      Liste der letzten bis zu 3 Orte für den Kontext
     * @return den generierten Ort (oder einen Fallback bei Parse-Fehler)
     */
    public Location generiere(String vonRichtung, String vorherrRaum, List<Location> letzteOrte) {
        // Kontext-String aus den letzten besuchten Orten zusammenstellen
        StringBuilder kontext = new StringBuilder();
        if (letzteOrte != null && !letzteOrte.isEmpty()) {
            kontext.append("Zuletzt besuchte Orte: ");
            for (int i = 0; i < letzteOrte.size(); i++) {
                if (i > 0) kontext.append(", ");
                Location ort = letzteOrte.get(i);
                kontext.append("'").append(ort.getName()).append("'");
            }
            kontext.append(". ");
        }

        // Benutzer-Prompt mit allen Kontextinformationen
        String benutzerPrompt = String.format(
                "Generiere einen neuen Raum für ein Fantasy-Textadventure als reines JSON-Objekt.\n" +
                "Kontext: Der Spieler kommt aus Richtung '%s' aus '%s'. %s\n" +
                "Schema (fülle alle Felder aus): %s\n" +
                "Hinweise:\n" +
                "- 'exits' soll 2-4 Richtungen enthalten (mindestens die Gegenrichtung von '%s')\n" +
                "- Items und Gegner sind optional (können leere Arrays sein)\n" +
                "- Beschreibung auf Deutsch, atmosphärisch und detailreich\n" +
                "- Falls hasLockedDoor=true: requiredKeyId muss eine konkrete ID sein (z.B. 'alter_schluessel')",
                vonRichtung, vorherrRaum, kontext, JSON_SCHEMA, vonRichtung
        );

        try {
            String antwort = llmClient.chat(SYSTEM_PROMPT, benutzerPrompt);
            String bereinigt = entferneMarkdownFences(antwort);
            Location ort = mapper.readValue(bereinigt, Location.class);
            normalisiereAusgaenge(ort);
            return ort;

        } catch (LLMClient.LLMException e) {
            System.err.println("LLM-Fehler bei Ortsgenerierung: " + e.getMessage());
            return erstelleFallbackOrt(vonRichtung);

        } catch (Exception e) {
            // JSON-Parse-Fehler oder andere unerwartete Probleme
            System.err.println("Fehler beim Parsen der LLM-Antwort: " + e.getMessage());
            return erstelleFallbackOrt(vonRichtung);
        }
    }

    /**
     * Normalisiert die Ausgangsnamen eines Ortes auf englische Kleinbuchstaben.
     * Übersetzt deutsche Richtungsnamen und filtert ungültige Werte heraus.
     *
     * @param ort der zu normalisierende Ort
     */
    private void normalisiereAusgaenge(Location ort) {
        if (ort.getExits() == null) return;
        Map<String, String> sprachMapping = Map.of(
                "norden", "north", "nord", "north",
                "süden",  "south", "süd",  "south",
                "osten",  "east",  "ost",  "east",
                "westen", "west"
        );
        Set<String> gueltig = Set.of("north", "south", "east", "west");
        List<String> normalisiert = ort.getExits().stream()
                .map(String::toLowerCase)
                .map(e -> sprachMapping.getOrDefault(e, e))
                .filter(gueltig::contains)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        ort.setExits(normalisiert);
    }

    /**
     * Entfernt Markdown-Code-Fences aus der LLM-Antwort.
     * Einige Modelle umhüllen JSON-Antworten trotz Anweisung mit ```json ... ```.
     *
     * @param text der rohe LLM-Antworttext
     * @return bereinigter JSON-Text ohne Code-Fences
     */
    private String entferneMarkdownFences(String text) {
        String bereinigt = text.trim();
        // Markdown-Code-Block entfernen: ```json ... ``` oder ``` ... ```
        if (bereinigt.startsWith("```")) {
            int zeilenende = bereinigt.indexOf('\n');
            if (zeilenende != -1) {
                bereinigt = bereinigt.substring(zeilenende + 1);
            }
            if (bereinigt.endsWith("```")) {
                bereinigt = bereinigt.substring(0, bereinigt.lastIndexOf("```")).trim();
            }
        }
        return bereinigt;
    }

    /**
     * Erstellt einen vordefinierten Fallback-Ort für den Fall, dass der LLM
     * keinen gültigen JSON zurückgibt. Verhindert Spielabbrüche.
     *
     * @param vonRichtung die Richtung, aus der der Spieler gekommen ist
     *                    (wird als Ausgang in den Fallback-Ort integriert)
     * @return ein grundlegender, spielbarer Fallback-Ort
     */
    private Location erstelleFallbackOrt(String vonRichtung) {
        // Die Gegenrichtung bestimmen, damit der Spieler zurückgehen kann
        String gegenrichtung = gegenrichtung(vonRichtung);

        List<String> ausgaenge = new ArrayList<>();
        ausgaenge.add(gegenrichtung); // Rückweg immer verfügbar

        // Zufällig einen weiteren Ausgang hinzufügen für Erkundbarkeit
        List<String> moeglicheRichtungen = new ArrayList<>(List.of("north", "south", "east", "west"));
        moeglicheRichtungen.remove(vonRichtung);
        moeglicheRichtungen.remove(gegenrichtung);
        if (!moeglicheRichtungen.isEmpty()) {
            ausgaenge.add(moeglicheRichtungen.get(0));
        }

        return new Location(
                "Verlassener Gang",
                "Ein staubiger, verlassener Gang erstreckt sich vor dir. " +
                "Die Steine der Wände sind feucht und von Moos bewachsen. " +
                "Das schwache Licht einer vergessenen Fackel flackert in der Zugluft.",
                ausgaenge,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    /**
     * Bestimmt die entgegengesetzte Himmelsrichtung.
     *
     * @param richtung die Ausgangsrichtung ("north", "south", "east", "west")
     * @return die Gegenrichtung; "south" bei unbekannter Eingabe
     */
    private String gegenrichtung(String richtung) {
        return switch (richtung) {
            case "north" -> "south";
            case "south" -> "north";
            case "east"  -> "west";
            case "west"  -> "east";
            default      -> "south";
        };
    }
}
