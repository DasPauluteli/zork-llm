package de.zork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enthält den gesamten serialisierbaren Spielzustand für Speichern und Laden.
 *
 * <p>Dieses Objekt wird von {@link SaveManager} als JSON-Datei ({@code savegame.json})
 * geschrieben und gelesen. Es enthält alle Informationen, die benötigt werden,
 * um das Spiel an genau derselben Stelle fortzusetzen.</p>
 *
 * <p><b>Enthaltene Daten:</b>
 * <ul>
 *   <li>Spielerdaten (Attribute, Inventar, Stufe, EP)</li>
 *   <li>Alle bisher besuchten Orte als Koordinaten-Map</li>
 *   <li>Aktuelle Position im dreidimensionalen Koordinatensystem</li>
 *   <li>Verlauf der zuletzt besuchten Orte (für LLM-Kontext)</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameState {

    /** Spielerdaten inkl. Attribute, Inventar und Progression. */
    @JsonProperty("spieler")
    private Player spieler;

    /**
     * Map aller bisher generierten und besuchten Orte.
     * Schlüssel: Koordinatenstring im Format {@code "x,y,z"}, z.B. {@code "0,1,0"}.
     */
    @JsonProperty("besuchteOrte")
    private Map<String, Location> besuchteOrte;

    /**
     * Aktuelle Position des Spielers als dreistelliger Koordinatenvektor [x, y, z].
     * Startposition: [0, 0, 0].
     */
    @JsonProperty("aktuellePosition")
    private int[] aktuellePosition;

    /**
     * Liste der zuletzt besuchten Ort-Schlüssel (max. 3 Einträge).
     * Wird als Kontext an den LLM-Prompt übergeben, damit neue Räume
     * thematisch kohärent zur Spielgeschichte passen.
     */
    @JsonProperty("ortsVerlauf")
    private List<String> ortsVerlauf;

    /** No-arg-Konstruktor für Jackson. */
    public GameState() {
        this.besuchteOrte = new HashMap<>();
        this.aktuellePosition = new int[]{0, 0, 0};
        this.ortsVerlauf = new ArrayList<>();
    }

    /**
     * Erstellt einen vollständig initialisierten Spielzustand.
     *
     * @param spieler          der Spielercharakter
     * @param besuchteOrte     Map aller gecachten Orte
     * @param aktuellePosition aktuelle Koordinaten [x, y, z]
     * @param ortsVerlauf      zuletzt besuchte Ort-Schlüssel
     */
    public GameState(Player spieler, Map<String, Location> besuchteOrte,
                     int[] aktuellePosition, List<String> ortsVerlauf) {
        this.spieler = spieler;
        this.besuchteOrte = besuchteOrte != null ? besuchteOrte : new HashMap<>();
        this.aktuellePosition = aktuellePosition != null ? aktuellePosition : new int[]{0, 0, 0};
        this.ortsVerlauf = ortsVerlauf != null ? ortsVerlauf : new ArrayList<>();
    }

    // --- Getter und Setter ---

    /** @return der Spielercharakter */
    public Player getSpieler() { return spieler; }

    /** @param spieler der Spielercharakter */
    public void setSpieler(Player spieler) { this.spieler = spieler; }

    /** @return Map aller besuchten Orte mit Koordinaten-Schlüsseln */
    public Map<String, Location> getBesuchteOrte() { return besuchteOrte; }

    /** @param besuchteOrte Map aller besuchten Orte */
    public void setBesuchteOrte(Map<String, Location> besuchteOrte) {
        this.besuchteOrte = besuchteOrte;
    }

    /** @return aktuelle Position als [x, y, z] */
    public int[] getAktuellePosition() { return aktuellePosition; }

    /** @param aktuellePosition aktuelle Position als [x, y, z] */
    public void setAktuellePosition(int[] aktuellePosition) {
        this.aktuellePosition = aktuellePosition;
    }

    /** @return Liste der zuletzt besuchten Ort-Schlüssel */
    public List<String> getOrtsVerlauf() { return ortsVerlauf; }

    /** @param ortsVerlauf Liste der zuletzt besuchten Ort-Schlüssel */
    public void setOrtsVerlauf(List<String> ortsVerlauf) { this.ortsVerlauf = ortsVerlauf; }
}
