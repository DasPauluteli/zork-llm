package de.zork;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP-Client für Anfragen an eine OpenAI-kompatible LLM-API.
 *
 * <p>Sendet Chat-Completion-Anfragen im OpenAI-Format und gibt den
 * Textinhalt der Antwort zurück. Verbindungsdaten (URL, Schlüssel, Modell)
 * werden aus dem {@link SettingsManager} gelesen.</p>
 *
 * <p>Alle Anfragen sind synchron und sollten daher außerhalb des
 * Swing Event Dispatch Thread (EDT) ausgeführt werden - z.B. in einem
 * {@link javax.swing.SwingWorker}.</p>
 */
public class LLMClient {

    /** HTTP-Medientyp für JSON-Anfragen. */
    private static final MediaType JSON_MEDIENTYP = MediaType.get("application/json; charset=utf-8");

    /** Timeout-Dauer für API-Anfragen in Sekunden. */
    private static final int TIMEOUT_SEKUNDEN = 60;

    /** OkHttp-Client-Instanz (threadsicher, kann wiederverwendet werden). */
    private final OkHttpClient httpClient;

    /** Jackson-Mapper für JSON-Erstellung und -Parsung. */
    private final ObjectMapper mapper;

    /** Quelle der API-Verbindungsdaten. */
    private final SettingsManager einstellungen;

    /**
     * Erstellt einen neuen LLM-Client.
     *
     * @param einstellungen der Einstellungsmanager mit API-URL, Schlüssel und Modell
     */
    public LLMClient(SettingsManager einstellungen) {
        this.einstellungen = einstellungen;
        this.mapper = new ObjectMapper();
        // HTTP-Client mit großzügigen Timeouts für LLM-Antwortzeiten konfigurieren
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Sendet eine Chat-Completion-Anfrage an die konfigurierte LLM-API.
     *
     * <p>Baut eine Anfrage im OpenAI-Chat-Completion-Format auf:
     * System-Prompt + User-Nachricht → Antworttext.</p>
     *
     * @param systemPrompt die Systemanweisung für das Modell (Rolle/Kontext)
     * @param benutzerNachricht die eigentliche Anfrage
     * @return der Textinhalt aus {@code choices[0].message.content}
     * @throws LLMException wenn die API-Anfrage fehlschlägt oder das Format unbekannt ist
     */
    public String chat(String systemPrompt, String benutzerNachricht) throws LLMException {
        // Anfrage-JSON im OpenAI-Chat-Completion-Format zusammenstellen
        ObjectNode anfrage = mapper.createObjectNode();
        anfrage.put("model", einstellungen.getModellName());

        // Nachrichten-Array mit System- und Benutzeranteil
        ArrayNode nachrichten = anfrage.putArray("messages");

        ObjectNode systemMsg = nachrichten.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);

        ObjectNode benutzerMsg = nachrichten.addObject();
        benutzerMsg.put("role", "user");
        benutzerMsg.put("content", benutzerNachricht);

        String anfrageJson;
        try {
            anfrageJson = mapper.writeValueAsString(anfrage);
        } catch (IOException e) {
            throw new LLMException("Fehler beim Erstellen der API-Anfrage: " + e.getMessage(), e);
        }

        // API-Endpunkt zusammenstellen (Basis-URL + /chat/completions)
        String endpunkt = einstellungen.getApiEndpunkt();
        if (!endpunkt.endsWith("/")) {
            endpunkt += "/";
        }
        String url = endpunkt + "chat/completions";

        // HTTP-POST-Anfrage erstellen
        Request httpAnfrage = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + einstellungen.getApiSchluessel())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(anfrageJson, JSON_MEDIENTYP))
                .build();

        // Anfrage senden und Antwort verarbeiten
        try (Response antwort = httpClient.newCall(httpAnfrage).execute()) {
            if (!antwort.isSuccessful()) {
                String fehlerBody = antwort.body() != null ? antwort.body().string() : "(kein Inhalt)";
                throw new LLMException("API-Fehler " + antwort.code() + ": " + fehlerBody);
            }

            String antwortJson = antwort.body() != null ? antwort.body().string() : "";
            return extrahiereAntwortText(antwortJson);

        } catch (IOException e) {
            throw new LLMException("Netzwerkfehler bei API-Anfrage: " + e.getMessage(), e);
        }
    }

    /**
     * Extrahiert den Antworttext aus dem OpenAI-Chat-Completion-JSON.
     *
     * @param antwortJson der vollständige JSON-Antwort-String
     * @return der Textinhalt aus {@code choices[0].message.content}
     * @throws LLMException wenn das JSON-Format nicht dem erwarteten Schema entspricht
     */
    private String extrahiereAntwortText(String antwortJson) throws LLMException {
        try {
            JsonNode wurzel = mapper.readTree(antwortJson);
            JsonNode choices = wurzel.path("choices");
            if (choices.isEmpty()) {
                throw new LLMException("Keine Antwort-Choices in der API-Antwort gefunden.");
            }
            // Ersten Choice-Eintrag auslesen
            String inhalt = choices.get(0).path("message").path("content").asText();
            if (inhalt.isBlank()) {
                throw new LLMException("Leerer Antwortinhalt von der API erhalten.");
            }
            return inhalt;
        } catch (IOException e) {
            throw new LLMException("Fehler beim Parsen der API-Antwort: " + e.getMessage(), e);
        }
    }

    /**
     * Ausnahme für Fehler bei der LLM-API-Kommunikation.
     */
    public static class LLMException extends Exception {

        /**
         * Erstellt eine LLMException mit einer Fehlermeldung.
         *
         * @param nachricht die Fehlerbeschreibung
         */
        public LLMException(String nachricht) {
            super(nachricht);
        }

        /**
         * Erstellt eine LLMException mit Meldung und Ursache.
         *
         * @param nachricht die Fehlerbeschreibung
         * @param ursache   die auslösende Ausnahme
         */
        public LLMException(String nachricht, Throwable ursache) {
            super(nachricht, ursache);
        }
    }
}
