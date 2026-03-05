package de.zork;

import java.util.Random;

/**
 * Implementiert das D20-Würfelsystem im D&amp;D-Stil.
 *
 * <p>Alle wichtigen Aktionen (Angriff, Überreden, Schlösser knacken usw.)
 * werden durch einen W20-Wurf zuzüglich Attributsmodifikator gegen einen
 * Schwierigkeitsgrad (DC) aufgelöst.</p>
 *
 * <p><b>Kritische Ergebnisse:</b>
 * <ul>
 *   <li>Natürliche 20 → Kritischer Treffer (unabhängig vom DC immer Erfolg)</li>
 *   <li>Natürliche 1  → Kritischer Fehlschlag (unabhängig vom Modifikator immer Misserfolg)</li>
 * </ul>
 */
public class DiceRoller {

    /** Zufallsgenerator für alle Würfelwürfe. */
    private static final Random ZUFALL = new Random();

    /**
     * Kapselt das Ergebnis einer Aktionsprüfung (W20-Wurf gegen DC).
     */
    public record Aktionsergebnis(
            /** {@code true} wenn die Probe bestanden wurde */
            boolean erfolg,
            /** Das rohe W20-Ergebnis (1–20) */
            int wurf,
            /** {@code true} bei natürlicher 20 (kritischer Treffer) */
            boolean kritischerTreffer,
            /** {@code true} bei natürlicher 1 (kritischer Fehlschlag) */
            boolean kritischerFehlschlag
    ) {}

    /**
     * Wirft einen Würfel mit der angegebenen Seitzahl.
     * Gibt einen Wert von 1 bis {@code seiten} zurück.
     *
     * @param seiten Anzahl der Würfelseiten (z.B. 6 für W6, 20 für W20)
     * @return zufälliges Ergebnis zwischen 1 und {@code seiten} (inklusiv)
     * @throws IllegalArgumentException wenn seiten &lt; 1
     */
    public int wurf(int seiten) {
        if (seiten < 1) {
            throw new IllegalArgumentException("Ein Würfel muss mindestens 1 Seite haben.");
        }
        return ZUFALL.nextInt(seiten) + 1;
    }

    /**
     * Führt eine W20-Probe gegen einen Schwierigkeitsgrad (DC) durch.
     *
     * <p>Ablauf:
     * <ol>
     *   <li>W20 werfen → roher Wert 1–20</li>
     *   <li>Natürliche 20: sofortiger kritischer Treffer (Erfolg)</li>
     *   <li>Natürliche 1: sofortiger kritischer Fehlschlag (Misserfolg)</li>
     *   <li>Sonst: {@code wurf + modifikator >= dc} → Erfolg</li>
     * </ol>
     *
     * @param modifikator der Attributsmodifikator (kann negativ sein)
     * @param dc          der Schwierigkeitsgrad (Difficulty Class)
     * @return ein {@link Aktionsergebnis} mit allen Details des Wurfs
     */
    public Aktionsergebnis probe(int modifikator, int dc) {
        int rohwurf = wurf(20);

        // Kritischer Treffer: natürliche 20, immer Erfolg
        if (rohwurf == 20) {
            return new Aktionsergebnis(true, rohwurf, true, false);
        }

        // Kritischer Fehlschlag: natürliche 1, immer Misserfolg
        if (rohwurf == 1) {
            return new Aktionsergebnis(false, rohwurf, false, true);
        }

        // Normaler Wurf: Summe gegen DC prüfen
        boolean erfolg = (rohwurf + modifikator) >= dc;
        return new Aktionsergebnis(erfolg, rohwurf, false, false);
    }

    /**
     * Berechnet den Schaden eines Treffers als W6 + Stärkemodifikator.
     * Mindestwert ist 1 (auch wenn Modifikator negativ).
     *
     * @param staerkeModifikator der STR-Modifikator des Angreifers
     * @return Schadenspunkte (mindestens 1)
     */
    public int schadenW6(int staerkeModifikator) {
        int basis = wurf(6);
        return Math.max(1, basis + staerkeModifikator);
    }

    /**
     * Berechnet kritischen Schaden als 2×W6 + Stärkemodifikator.
     * Mindestwert ist 2.
     *
     * @param staerkeModifikator der STR-Modifikator des Angreifers
     * @return kritische Schadenspunkte (mindestens 2)
     */
    public int kritischerSchadenW6(int staerkeModifikator) {
        int basis = wurf(6) + wurf(6); // doppelter Würfel bei krit
        return Math.max(2, basis + staerkeModifikator);
    }
}
