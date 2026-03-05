package de.zork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repräsentiert den Spielercharakter mit allen Attributen, dem Inventar und der Stufenmechanik.
 *
 * <p><b>Attribute:</b>
 * <ul>
 *   <li>HP / MaxHP – Lebenspunkte</li>
 *   <li>Stufe / EP – Progression</li>
 *   <li>STR – Stärke (Nahkampf, Kraftproben)</li>
 *   <li>GES – Geschicklichkeit (Ausweichen, Schlösser)</li>
 *   <li>INT – Intelligenz (Überreden, Magie)</li>
 * </ul>
 *
 * <p>Jedes Attribut über 10 gewährt einen Modifikator nach D&amp;D-Formel: {@code (Wert - 10) / 2}.</p>
 *
 * <p>Diese Klasse ist Jackson-serialisierbar für den Spielstand-Speicher.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Player {

    /** Aktuelle Lebenspunkte. Bei 0 ist der Spieler besiegt. */
    @JsonProperty("hp")
    private int hp;

    /** Maximale Lebenspunkte; steigen beim Level-Up um 5. */
    @JsonProperty("maxHp")
    private int maxHp;

    /** Aktuelle Stufe des Spielers. Startet bei 1. */
    @JsonProperty("level")
    private int level;

    /** Gesammelte Erfahrungspunkte. Schwelle zum Aufstieg: {@code 100 * aktuelle Stufe}. */
    @JsonProperty("xp")
    private int xp;

    /** Stärke-Attribut. Beeinflusst Nahkampf-Angriffe und Kraftproben. */
    @JsonProperty("str")
    private int str;

    /** Geschicklichkeit-Attribut. Beeinflusst Ausweichen, Diebstahl, Schlösser knacken. */
    @JsonProperty("dex")
    private int dex;

    /** Intelligenz-Attribut. Beeinflusst Überreden, Rätsel und magische Aktionen. */
    @JsonProperty("intel")
    private int intel;

    /** Liste aller Gegenstände im Inventar des Spielers. */
    @JsonProperty("inventory")
    private List<Item> inventory;

    /** No-arg-Konstruktor für Jackson. */
    public Player() {
        this.inventory = new ArrayList<>();
    }

    /**
     * Erstellt einen neuen Spieler mit Startwerten für ein neues Spiel.
     * Startwerte: HP=20, Stufe=1, alle Attribute=10.
     *
     * @return ein frisch initialisierter Spieler
     */
    public static Player createNewPlayer() {
        Player p = new Player();
        p.hp = 20;
        p.maxHp = 20;
        p.level = 1;
        p.xp = 0;
        p.str = 10;
        p.dex = 10;
        p.intel = 10;
        p.inventory = new ArrayList<>();
        return p;
    }

    /**
     * Berechnet den D&amp;D-Attributsmodifikator für einen gegebenen Attributswert.
     * Formel: {@code (wert - 10) / 2} (ganzzahlige Division, z.B. 8 → -1, 10 → 0, 14 → +2).
     *
     * @param attributwert der rohe Attributswert (z.B. 14)
     * @return der Modifikator (kann negativ sein)
     */
    public int getModifier(int attributwert) {
        return (attributwert - 10) / 2;
    }

    /**
     * Gibt den Stärke-Modifikator zurück.
     *
     * @return {@code (str - 10) / 2}
     */
    public int getStrModifier() { return getModifier(str); }

    /**
     * Gibt den Geschicklichkeits-Modifikator zurück.
     *
     * @return {@code (dex - 10) / 2}
     */
    public int getDexModifier() { return getModifier(dex); }

    /**
     * Gibt den Intelligenz-Modifikator zurück.
     *
     * @return {@code (intel - 10) / 2}
     */
    public int getIntelModifier() { return getModifier(intel); }

    /**
     * Fügt Erfahrungspunkte hinzu und prüft ob ein Level-Up ausgelöst wird.
     * Schwellenwert: {@code 100 × aktuelle Stufe} EP.
     *
     * @param menge die zu addierenden EP (muss positiv sein)
     * @return true wenn ein Level-Up stattgefunden hat
     */
    public boolean addXp(int menge) {
        this.xp += menge;
        int schwelle = 100 * this.level;
        if (this.xp >= schwelle) {
            levelUp();
            return true;
        }
        return false;
    }

    /**
     * Führt einen Level-Up durch: erhöht Stufe, MaxHP (+5) und heilt den Spieler voll.
     * Die Zuweisung des freien Attributspunkts wird vom {@link GameEngine} verwaltet.
     */
    public void levelUp() {
        this.level++;
        this.maxHp += 5;
        this.hp = this.maxHp; // vollständige Heilung beim Level-Up
    }

    /**
     * Verursacht Schaden beim Spieler und begrenzt HP auf Minimum 0.
     *
     * @param schaden die abzuziehenden Schadenspunkte (muss positiv sein)
     */
    public void nimmSchaden(int schaden) {
        this.hp = Math.max(0, this.hp - schaden);
    }

    /**
     * Heilt den Spieler um die angegebene Menge, begrenzt auf MaxHP.
     *
     * @param menge die zu heilenden HP (muss positiv sein)
     */
    public void heile(int menge) {
        this.hp = Math.min(maxHp, this.hp + menge);
    }

    /**
     * Prüft ob der Spieler besiegt wurde (HP = 0).
     *
     * @return true wenn HP &lt;= 0
     */
    public boolean istBesiegt() {
        return this.hp <= 0;
    }

    /**
     * Fügt einen Gegenstand dem Inventar hinzu.
     *
     * @param gegenstand der hinzuzufügende Gegenstand
     */
    public void addItem(Item gegenstand) {
        this.inventory.add(gegenstand);
    }

    /**
     * Entfernt einen Gegenstand aus dem Inventar anhand seiner ID.
     *
     * @param itemId die ID des zu entfernenden Gegenstands
     * @return true wenn der Gegenstand gefunden und entfernt wurde
     */
    public boolean removeItem(String itemId) {
        return inventory.removeIf(i -> itemId.equals(i.getId()));
    }

    /**
     * Sucht einen Gegenstand im Inventar anhand einer partiellen Namensübereinstimmung.
     *
     * @param namensteil Teil des Gegenstandsnamens (Groß-/Kleinschreibung egal)
     * @return den ersten passenden Gegenstand oder {@code null}
     */
    public Item findeGegenstandNachName(String namensteil) {
        String klein = namensteil.toLowerCase();
        return inventory.stream()
                .filter(i -> i.getName() != null && i.getName().toLowerCase().contains(klein))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sucht einen Gegenstand im Inventar anhand seiner genauen ID.
     *
     * @param itemId die ID des gesuchten Gegenstands
     * @return den Gegenstand oder {@code null}
     */
    public Item findeGegenstandNachId(String itemId) {
        return inventory.stream()
                .filter(i -> itemId.equals(i.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Wendet die Stateffekte eines Gegenstands auf den Spieler an.
     * Unterstützte Schlüssel: "hp", "maxHp", "str", "dex", "intel".
     *
     * @param effekte Map von Attributsschlüssel zu Delta-Wert
     */
    public void wendeStateffekteAn(Map<String, Integer> effekte) {
        if (effekte == null) return;
        for (Map.Entry<String, Integer> eintrag : effekte.entrySet()) {
            switch (eintrag.getKey()) {
                case "hp"    -> heile(eintrag.getValue());
                case "maxHp" -> { this.maxHp += eintrag.getValue(); }
                case "str"   -> { this.str += eintrag.getValue(); }
                case "dex"   -> { this.dex += eintrag.getValue(); }
                case "intel" -> { this.intel += eintrag.getValue(); }
            }
        }
    }

    /**
     * Erhöht ein Attribut um 1 (für Level-Up Attributspunkt-Zuweisung).
     *
     * @param attribut der Attributsname: "str", "dex" oder "intel"
     * @return true wenn das Attribut bekannt war und erhöht wurde
     */
    public boolean erhoeheAttribut(String attribut) {
        return switch (attribut.toLowerCase()) {
            case "str"   -> { this.str++;   yield true; }
            case "dex"   -> { this.dex++;   yield true; }
            case "intel" -> { this.intel++; yield true; }
            default -> false;
        };
    }

    // --- Getter und Setter ---

    /** @return aktuelle Lebenspunkte */
    public int getHp() { return hp; }

    /** @param hp aktuelle Lebenspunkte */
    public void setHp(int hp) { this.hp = hp; }

    /** @return maximale Lebenspunkte */
    public int getMaxHp() { return maxHp; }

    /** @param maxHp maximale Lebenspunkte */
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    /** @return aktuelle Stufe */
    public int getLevel() { return level; }

    /** @param level aktuelle Stufe */
    public void setLevel(int level) { this.level = level; }

    /** @return gesammelte Erfahrungspunkte */
    public int getXp() { return xp; }

    /** @param xp gesammelte Erfahrungspunkte */
    public void setXp(int xp) { this.xp = xp; }

    /** @return Stärke-Attribut */
    public int getStr() { return str; }

    /** @param str Stärke-Attribut */
    public void setStr(int str) { this.str = str; }

    /** @return Geschicklichkeit-Attribut */
    public int getDex() { return dex; }

    /** @param dex Geschicklichkeit-Attribut */
    public void setDex(int dex) { this.dex = dex; }

    /** @return Intelligenz-Attribut */
    public int getIntel() { return intel; }

    /** @param intel Intelligenz-Attribut */
    public void setIntel(int intel) { this.intel = intel; }

    /** @return unveränderliche Sicht auf das Inventar */
    public List<Item> getInventory() { return inventory; }

    /** @param inventory das vollständige Inventar (für Deserialisierung) */
    public void setInventory(List<Item> inventory) {
        this.inventory = inventory != null ? inventory : new ArrayList<>();
    }

    /** @return EP-Schwelle für nächsten Level-Up */
    public int naechsteLevelSchwelle() {
        return 100 * this.level;
    }
}
