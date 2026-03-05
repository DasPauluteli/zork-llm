package de.zork;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

/**
 * Verwaltet die Anwendungseinstellungen (API-Verbindungsdaten).
 *
 * <p>Einstellungen werden in der Datei {@code settings.json} im Arbeitsverzeichnis
 * persistent gespeichert. Fehlt die Datei, werden Standardwerte verwendet.</p>
 *
 * <p><b>Gespeicherte Einstellungen:</b>
 * <ul>
 *   <li>API-Endpunkt-URL (OpenAI-kompatibel)</li>
 *   <li>API-Schlüssel</li>
 *   <li>Modellname</li>
 * </ul>
 */
public class SettingsManager {

    /** Dateipfad der Einstellungsdatei im Arbeitsverzeichnis. */
    private static final String EINSTELLUNGS_DATEI = "settings.json";

    /** Standard-Endpunkt für OpenAI-kompatible APIs. */
    private static final String STANDARD_ENDPUNKT = "https://api.openai.com/v1";

    /** Standard-Modellname. */
    private static final String STANDARD_MODELL = "gpt-4o-mini";

    /** Jackson-Mapper für JSON-Verarbeitung. */
    private final ObjectMapper mapper = new ObjectMapper();

    /** Die aktuell geladenen Einstellungsdaten. */
    private EinstellungsDaten daten;

    /**
     * Erstellt einen SettingsManager und lädt sofort die Einstellungen.
     * Falls keine Einstellungsdatei vorhanden ist, werden Standardwerte verwendet.
     */
    public SettingsManager() {
        laden();
    }

    /**
     * Lädt die Einstellungen aus {@code settings.json}.
     * Existiert die Datei nicht, werden Standardwerte initialisiert.
     */
    public void laden() {
        File datei = new File(EINSTELLUNGS_DATEI);
        if (datei.exists()) {
            try {
                daten = mapper.readValue(datei, EinstellungsDaten.class);
            } catch (IOException e) {
                // Defekte Einstellungsdatei: mit Standardwerten fortfahren
                System.err.println("Warnung: Einstellungsdatei konnte nicht gelesen werden: " + e.getMessage());
                daten = new EinstellungsDaten();
            }
        } else {
            // Keine Datei vorhanden: Standardwerte setzen
            daten = new EinstellungsDaten();
        }
    }

    /**
     * Speichert die aktuellen Einstellungen in {@code settings.json}.
     *
     * @throws IOException wenn die Datei nicht geschrieben werden kann
     */
    public void speichern() throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(EINSTELLUNGS_DATEI), daten);
    }

    /**
     * Gibt die konfigurierte API-Endpunkt-URL zurück.
     *
     * @return die API-URL (z.B. {@code https://api.openai.com/v1})
     */
    public String getApiEndpunkt() {
        return daten.apiEndpunkt;
    }

    /**
     * Setzt die API-Endpunkt-URL.
     *
     * @param endpunkt die neue API-URL
     */
    public void setApiEndpunkt(String endpunkt) {
        daten.apiEndpunkt = endpunkt;
    }

    /**
     * Gibt den API-Schlüssel zurück.
     *
     * @return der API-Schlüssel (leer wenn nicht konfiguriert)
     */
    public String getApiSchluessel() {
        return daten.apiSchluessel;
    }

    /**
     * Setzt den API-Schlüssel.
     *
     * @param schluessel der neue API-Schlüssel
     */
    public void setApiSchluessel(String schluessel) {
        daten.apiSchluessel = schluessel;
    }

    /**
     * Gibt den konfigurierten Modellnamen zurück.
     *
     * @return der Modellname (z.B. "gpt-4o-mini")
     */
    public String getModellName() {
        return daten.modellName;
    }

    /**
     * Setzt den Modellnamen.
     *
     * @param modell der neue Modellname
     */
    public void setModellName(String modell) {
        daten.modellName = modell;
    }

    /**
     * Prüft ob ein API-Schlüssel konfiguriert wurde.
     *
     * @return true wenn der Schlüssel nicht leer ist
     */
    public boolean istKonfiguriert() {
        return daten.apiSchluessel != null && !daten.apiSchluessel.isBlank();
    }

    /**
     * Interne Datenklasse für die JSON-Serialisierung der Einstellungen.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class EinstellungsDaten {

        @JsonProperty("apiEndpunkt")
        String apiEndpunkt = STANDARD_ENDPUNKT;

        @JsonProperty("apiSchluessel")
        String apiSchluessel = "";

        @JsonProperty("modellName")
        String modellName = STANDARD_MODELL;
    }
}
