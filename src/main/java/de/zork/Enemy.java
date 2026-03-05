package de.zork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Repräsentiert einen feindlichen Gegner an einem Spielort.
 *
 * <p>Gegner werden vom LLM generiert und können vom Spieler angegriffen werden.
 * Das Kampfsystem basiert auf D20-Würfeln: Der Spieler würfelt W20 + STR-Modifikator
 * gegen den Verteidigungs-DC des Gegners.</p>
 *
 * <p>Diese Klasse ist Jackson-serialisierbar für Spielstand-Speicherung und LLM-JSON-Parsung.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Enemy {

    /** Anzeigename der in Kampfmeldungen erscheint, z.B. "Goblin" oder "Steintroll". */
    @JsonProperty("name")
    private String name;

    /** Aktuelle Lebenspunkte. Der Kampf endet wenn dieser Wert 0 erreicht. */
    @JsonProperty("hp")
    private int hp;

    /** Maximale Lebenspunkte (für die Anzeige). */
    @JsonProperty("maxHp")
    private int maxHp;

    /** Angriffsbonus der zum W20-Wurf bei Gegenangriffen addiert wird. */
    @JsonProperty("attack")
    private int attack;

    /**
     * Schwierigkeitsgrad (DC) um diesen Gegner zu treffen.
     * Der Spieler muss W20 + STR-Mod >= Verteidigung würfeln um zu treffen.
     */
    @JsonProperty("defense")
    private int defense;

    /** Erfahrungspunkte die der Spieler beim Töten dieses Gegners erhält. */
    @JsonProperty("xpReward")
    private int xpReward;

    /** No-arg-Konstruktor für Jackson. */
    public Enemy() {}

    /**
     * Erstellt einen Gegner mit allen Kampfwerten.
     *
     * @param name      Anzeigename
     * @param hp        Start- (und Max-)Lebenspunkte
     * @param attack    Angriffsbonus für Gegenangriffe
     * @param defense   DC zum Treffen dieses Gegners
     * @param xpReward  EP bei Tod
     */
    public Enemy(String name, int hp, int attack, int defense, int xpReward) {
        this.name = name;
        this.hp = hp;
        this.maxHp = hp;
        this.attack = attack;
        this.defense = defense;
        this.xpReward = xpReward;
    }

    // --- Getter und Setter ---

    /** @return der Anzeigename des Gegners */
    public String getName() { return name; }

    /** @param name der Anzeigename des Gegners */
    public void setName(String name) { this.name = name; }

    /** @return aktuelle Lebenspunkte */
    public int getHp() { return hp; }

    /** @param hp aktuelle Lebenspunkte */
    public void setHp(int hp) { this.hp = hp; }

    /** @return maximale Lebenspunkte */
    public int getMaxHp() { return maxHp; }

    /** @param maxHp maximale Lebenspunkte */
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    /** @return Angriffsbonus für Gegenangriffe */
    public int getAttack() { return attack; }

    /** @param attack Angriffsbonus für Gegenangriffe */
    public void setAttack(int attack) { this.attack = attack; }

    /** @return DC der zum Treffen dieses Gegners erforderlich ist */
    public int getDefense() { return defense; }

    /** @param defense DC der zum Treffen dieses Gegners erforderlich ist */
    public void setDefense(int defense) { this.defense = defense; }

    /** @return EP die beim Tod gewährt werden */
    public int getXpReward() { return xpReward; }

    /** @param xpReward EP die beim Tod gewährt werden */
    public void setXpReward(int xpReward) { this.xpReward = xpReward; }

    /**
     * Prüft ob dieser Gegner besiegt wurde.
     *
     * @return true wenn HP &lt;= 0
     */
    public boolean isDead() { return hp <= 0; }

    /**
     * Verursacht Schaden bei diesem Gegner und begrenzt HP auf Minimum 0.
     *
     * @param menge die abzuziehenden Schadenspunkte (muss positiv sein)
     */
    public void takeDamage(int menge) {
        this.hp = Math.max(0, this.hp - menge);
    }

    @Override
    public String toString() {
        return String.format("%s (HP: %d/%d)", name, hp, maxHp);
    }
}
