package de.zork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Repräsentiert einen einzelnen Ort (Raum) in der Spielwelt.
 *
 * <p>Orte werden bei Bedarf vom LLM generiert und in der Weltkarte gecacht.
 * Jeder Ort hat einen Namen, eine Beschreibung, verfügbare Ausgänge,
 * Gegenstände auf dem Boden und ggf. Gegner.</p>
 *
 * <p>Verschlossene Türen bieten eine Rätselmechanik: Ein Ausgang kann blockiert sein
 * bis der Spieler den passenden Schlüsselgegenstand benutzt.</p>
 *
 * <p>Diese Klasse ist Jackson-serialisierbar für Spielstand-Speicherung und LLM-JSON-Parsung.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {

    /** Kurzer Anzeigename des Orts, z.B. "Dunkle Höhle" oder "Verfallener Turm". */
    @JsonProperty("name")
    private String name;

    /** Vollständige atmosphärische Beschreibung, die beim Betreten oder bei "schau" angezeigt wird. */
    @JsonProperty("description")
    private String description;

    /**
     * Verfügbare Richtungen von diesem Ort aus.
     * Gültige Werte: "north", "south", "east", "west".
     * Navigationsbuttons werden basierend auf dieser Liste aktiviert/deaktiviert.
     */
    @JsonProperty("exits")
    private List<String> exits;

    /** Gegenstände auf dem Boden die der Spieler aufnehmen kann. */
    @JsonProperty("items")
    private List<Item> items;

    /** Gegner an diesem Ort (kann leer sein für friedliche Räume). */
    @JsonProperty("enemies")
    private List<Enemy> enemies;

    /**
     * Wenn true, ist einer der Ausgänge durch eine verschlossene Tür blockiert.
     * Der Spieler muss den richtigen Schlüsselgegenstand benutzen um sie zu öffnen.
     */
    @JsonProperty("hasLockedDoor")
    private boolean hasLockedDoor;

    /**
     * Die Richtung des verschlossenen Ausgangs, z.B. "north".
     * Nur relevant wenn {@code hasLockedDoor} true ist.
     */
    @JsonProperty("lockedDoorDirection")
    private String lockedDoorDirection;

    /**
     * Die Gegenstand-ID die zum Öffnen der Tür benötigt wird.
     * Muss mit der {@code id} eines Gegenstands übereinstimmen.
     * Nur relevant wenn {@code hasLockedDoor} true ist.
     */
    @JsonProperty("requiredKeyId")
    private String requiredKeyId;

    /** No-arg-Konstruktor für Jackson. */
    public Location() {
        this.exits = new ArrayList<>();
        this.items = new ArrayList<>();
        this.enemies = new ArrayList<>();
    }

    /**
     * Erstellt einen einfachen Ort ohne verschlossene Türen.
     *
     * @param name        Anzeigename
     * @param description atmosphärische Beschreibung
     * @param exits       verfügbare Richtungsstrings
     * @param items       Gegenstände auf dem Boden
     * @param enemies     Gegner an diesem Ort
     */
    public Location(String name, String description,
                    List<String> exits, List<Item> items, List<Enemy> enemies) {
        this.name = name;
        this.description = description;
        this.exits = exits != null ? exits : new ArrayList<>();
        this.items = items != null ? items : new ArrayList<>();
        this.enemies = enemies != null ? enemies : new ArrayList<>();
        this.hasLockedDoor = false;
        this.lockedDoorDirection = "";
        this.requiredKeyId = "";
    }

    // --- Getter und Setter ---

    /** @return der Anzeigename des Orts */
    public String getName() { return name; }

    /** @param name der Anzeigename des Orts */
    public void setName(String name) { this.name = name; }

    /** @return die atmosphärische Beschreibung */
    public String getDescription() { return description; }

    /** @param description die atmosphärische Beschreibung */
    public void setDescription(String description) { this.description = description; }

    /** @return Liste der verfügbaren Ausgangsrichtungen */
    public List<String> getExits() { return exits; }

    /** @param exits Liste der verfügbaren Ausgangsrichtungen */
    public void setExits(List<String> exits) { this.exits = exits; }

    /** @return Liste der Gegenstände auf dem Boden */
    public List<Item> getItems() { return items; }

    /** @param items Liste der Gegenstände auf dem Boden */
    public void setItems(List<Item> items) { this.items = items; }

    /** @return Liste der Gegner an diesem Ort */
    public List<Enemy> getEnemies() { return enemies; }

    /** @param enemies Liste der Gegner an diesem Ort */
    public void setEnemies(List<Enemy> enemies) { this.enemies = enemies; }

    /** @return true wenn ein Ausgang durch eine verschlossene Tür blockiert ist */
    public boolean isHasLockedDoor() { return hasLockedDoor; }

    /** @param hasLockedDoor true wenn ein Ausgang blockiert ist */
    public void setHasLockedDoor(boolean hasLockedDoor) { this.hasLockedDoor = hasLockedDoor; }

    /** @return die Richtung der verschlossenen Tür */
    public String getLockedDoorDirection() { return lockedDoorDirection; }

    /** @param lockedDoorDirection die Richtung der verschlossenen Tür */
    public void setLockedDoorDirection(String lockedDoorDirection) {
        this.lockedDoorDirection = lockedDoorDirection;
    }

    /** @return die Gegenstand-ID die zum Öffnen der Tür benötigt wird */
    public String getRequiredKeyId() { return requiredKeyId; }

    /** @param requiredKeyId die Gegenstand-ID die zum Öffnen benötigt wird */
    public void setRequiredKeyId(String requiredKeyId) { this.requiredKeyId = requiredKeyId; }

    /**
     * Prüft ob die angegebene Richtung aktuell zugänglich ist.
     * Eine Richtung ist blockiert wenn sie in den Ausgängen steht
     * aber die verschlossene Tür in diese Richtung führt.
     *
     * @param richtung die zu prüfende Richtung (z.B. "north")
     * @return true wenn die Richtung in den Ausgängen steht und nicht verschlossen ist
     */
    public boolean isExitOpen(String richtung) {
        if (!exits.contains(richtung)) return false;
        // Verschlossene Tür blockiert die angegebene Richtung
        if (hasLockedDoor && richtung.equals(lockedDoorDirection)) return false;
        return true;
    }

    /**
     * Gibt die Liste aller aktuell zugänglichen Ausgänge zurück (nicht verschlossen).
     *
     * @return Liste offener Richtungsstrings
     */
    @JsonIgnore
    public List<String> getOpenExits() {
        return exits.stream()
                .filter(this::isExitOpen)
                .toList();
    }

    /**
     * Entfernt einen Gegenstand vom Boden anhand seiner ID.
     *
     * @param itemId die ID des zu entfernenden Gegenstands
     * @return true wenn der Gegenstand gefunden und entfernt wurde
     */
    public boolean removeItem(String itemId) {
        return items.removeIf(item -> itemId.equals(item.getId()));
    }

    /**
     * Sucht einen Gegenstand am Boden anhand eines Namensbruchstücks (Groß-/Kleinschreibung egal).
     *
     * @param namensteil Namensbruchstück zur Suche
     * @return den ersten passenden Gegenstand oder {@code null}
     */
    public Item findItemByName(String namensteil) {
        String klein = namensteil.toLowerCase();
        return items.stream()
                .filter(i -> i.getName() != null && i.getName().toLowerCase().contains(klein))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sucht einen lebenden Gegner anhand eines Namensbruchstücks (Groß-/Kleinschreibung egal).
     *
     * @param namensteil Namensbruchstück zur Suche
     * @return den ersten lebenden passenden Gegner oder {@code null}
     */
    public Enemy findEnemyByName(String namensteil) {
        String klein = namensteil.toLowerCase();
        return enemies.stream()
                .filter(e -> !e.isDead() && e.getName() != null
                        && e.getName().toLowerCase().contains(klein))
                .findFirst()
                .orElse(null);
    }

    /**
     * Entsperrt die Tür an diesem Ort und macht den zuvor gesperrten Ausgang passierbar.
     */
    public void unlockDoor() {
        this.hasLockedDoor = false;
    }

    @Override
    public String toString() {
        return name;
    }
}
