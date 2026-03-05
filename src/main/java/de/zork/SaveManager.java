package de.zork;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;

/**
 * Verwaltet das Speichern und Laden des Spielstands als JSON-Datei.
 *
 * <p>Der Spielstand wird als {@link GameState}-Objekt serialisiert, das alle
 * relevanten Spieldaten enthält: Spieler, besuchte Orte, aktuelle Position
 * und Ortsverlauf.</p>
 *
 * <p>Standard-Speicherdatei: {@code savegame.json} im Arbeitsverzeichnis.</p>
 */
public class SaveManager {

    /** Standard-Dateipfad für die Speicherdatei. */
    public static final String STANDARD_SPEICHERDATEI = "savegame.json";

    /** Jackson-Mapper mit aktivierter Pretty-Print-Ausgabe für lesbare JSON-Dateien. */
    private final ObjectMapper mapper;

    /**
     * Erstellt einen neuen SaveManager mit konfiguriertem Jackson-Mapper.
     */
    public SaveManager() {
        this.mapper = new ObjectMapper();
        // Eingerückte JSON-Ausgabe für bessere Lesbarkeit der Speicherdatei
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Speichert den aktuellen Spielzustand als JSON-Datei.
     *
     * @param zustand  der zu speichernde Spielzustand
     * @param dateipfad der Pfad zur Zieldatei (z.B. "savegame.json")
     * @throws IOException wenn die Datei nicht geschrieben werden kann
     */
    public void speichern(GameState zustand, String dateipfad) throws IOException {
        mapper.writeValue(new File(dateipfad), zustand);
    }

    /**
     * Speichert den Spielzustand in der Standarddatei {@code savegame.json}.
     *
     * @param zustand der zu speichernde Spielzustand
     * @throws IOException wenn die Datei nicht geschrieben werden kann
     */
    public void speichern(GameState zustand) throws IOException {
        speichern(zustand, STANDARD_SPEICHERDATEI);
    }

    /**
     * Lädt einen Spielzustand aus einer JSON-Datei.
     *
     * @param dateipfad der Pfad zur Quelldatei
     * @return der geladene Spielzustand
     * @throws IOException wenn die Datei nicht gelesen oder geparst werden kann
     */
    public GameState laden(String dateipfad) throws IOException {
        File datei = new File(dateipfad);
        if (!datei.exists()) {
            throw new IOException("Spielstand-Datei nicht gefunden: " + dateipfad);
        }
        return mapper.readValue(datei, GameState.class);
    }

    /**
     * Lädt den Spielzustand aus der Standarddatei {@code savegame.json}.
     *
     * @return der geladene Spielzustand
     * @throws IOException wenn die Datei nicht gefunden oder lesbar ist
     */
    public GameState laden() throws IOException {
        return laden(STANDARD_SPEICHERDATEI);
    }

    /**
     * Prüft ob eine Speicherdatei vorhanden ist.
     *
     * @return true wenn {@code savegame.json} im Arbeitsverzeichnis existiert
     */
    public boolean speicherstandVorhanden() {
        return new File(STANDARD_SPEICHERDATEI).exists();
    }
}
