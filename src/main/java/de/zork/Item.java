package de.zork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Repräsentiert einen Gegenstand in der Spielwelt.
 *
 * <p>Gegenstände können aufgenommen, untersucht und optional auf Ziele angewendet werden.
 * Sie können außerdem Attributseffekte tragen (Buffs/Debuffs), die auf den Spieler wirken.</p>
 *
 * <p>Diese Klasse ist Jackson-serialisierbar für Spielstand-Speicherung und LLM-JSON-Parsung.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {

    /** Einzigartiger maschinenlesbarer Bezeichner, z.B. "rostiger_schluessel" oder "heiltraenk". */
    @JsonProperty("id")
    private String id;

    /** Menschenlesbarer Anzeigename für den Spieler. */
    @JsonProperty("name")
    private String name;

    /** Beschreibungstext der bei "untersuche" angezeigt wird. */
    @JsonProperty("description")
    private String description;

    /**
     * Gibt an ob dieser Gegenstand aktiv benutzt werden kann (z.B. Schlüssel, Tränke).
     * Nicht benutzbare Gegenstände sind passive Sammelgegenstände.
     */
    @JsonProperty("usable")
    private boolean usable;

    /**
     * Beschreibt worauf dieser Gegenstand angewendet werden kann, z.B. "locked_door".
     * Wird von der GameEngine zur Validierung von "benutze [gegenstand]"-Befehlen verwendet.
     * Leerer String bedeutet generische Nutzung.
     */
    @JsonProperty("useTarget")
    private String useTarget;

    /**
     * Zuordnung von Attributsschlüsseln zu Delta-Werten, die bei Benutzung angewendet werden.
     * Schlüssel: "hp", "maxHp", "str", "dex", "intel"
     * Beispiel: {"hp": 10} stellt 10 HP wieder her; {"str": 2} erhöht STR um 2.
     */
    @JsonProperty("statEffects")
    private Map<String, Integer> statEffects;

    /**
     * Wenn true, sind die Attributseffekte dauerhaft (bleiben nach Raumwechsel erhalten).
     * Wenn false, wirken die Effekte nur für den aktuellen Aufenthaltsort.
     */
    @JsonProperty("permanent")
    private boolean permanent;

    /** No-arg-Konstruktor für Jackson. */
    public Item() {
        this.statEffects = new HashMap<>();
        this.permanent = true;
    }

    /**
     * Erstellt einen Gegenstand mit allen Feldern.
     *
     * @param id          eindeutiger Bezeichner
     * @param name        Anzeigename
     * @param description Beschreibungstext
     * @param usable      ob der Gegenstand aktiv benutzt werden kann
     * @param useTarget   Zieltyp-String (kann leer sein)
     * @param statEffects Zuordnung von Attributsschlüssel zu Delta-Wert
     * @param permanent   ob Attributseffekte dauerhaft sind
     */
    public Item(String id, String name, String description,
                boolean usable, String useTarget,
                Map<String, Integer> statEffects, boolean permanent) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.usable = usable;
        this.useTarget = useTarget;
        this.statEffects = statEffects != null ? statEffects : new HashMap<>();
        this.permanent = permanent;
    }

    // --- Getter und Setter ---

    /** @return der eindeutige Gegenstandsbezeichner */
    public String getId() { return id; }

    /** @param id der eindeutige Gegenstandsbezeichner */
    public void setId(String id) { this.id = id; }

    /** @return der Anzeigename */
    public String getName() { return name; }

    /** @param name der Anzeigename */
    public void setName(String name) { this.name = name; }

    /** @return der Beschreibungstext */
    public String getDescription() { return description; }

    /** @param description der Beschreibungstext */
    public void setDescription(String description) { this.description = description; }

    /** @return true wenn der Gegenstand aktiv benutzt werden kann */
    public boolean isUsable() { return usable; }

    /** @param usable true wenn der Gegenstand aktiv benutzt werden kann */
    public void setUsable(boolean usable) { this.usable = usable; }

    /** @return der Zieltyp-String für die Nutzungsvalidierung */
    public String getUseTarget() { return useTarget; }

    /** @param useTarget der Zieltyp-String für die Nutzungsvalidierung */
    public void setUseTarget(String useTarget) { this.useTarget = useTarget; }

    /** @return Zuordnung von Attributs-Deltas die bei Nutzung angewendet werden */
    public Map<String, Integer> getStatEffects() { return statEffects; }

    /** @param statEffects Zuordnung von Attributs-Deltas */
    public void setStatEffects(Map<String, Integer> statEffects) { this.statEffects = statEffects; }

    /** @return true wenn Attributseffekte dauerhaft sind */
    public boolean isPermanent() { return permanent; }

    /** @param permanent true wenn Effekte nach Raumwechsel bestehen bleiben sollen */
    public void setPermanent(boolean permanent) { this.permanent = permanent; }

    @Override
    public String toString() {
        return name + (description != null && !description.isEmpty() ? " – " + description : "");
    }
}
