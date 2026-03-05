package de.zork;

import javax.swing.*;

/**
 * Einstiegspunkt der Zork-LLM-Anwendung.
 *
 * <p>Initialisiert alle Kernkomponenten in der korrekten Reihenfolge
 * und startet das Swing-UI auf dem Event Dispatch Thread (EDT).</p>
 *
 * <p><b>Startsequenz:</b>
 * <ol>
 *   <li>Look-and-Feel auf System-Native setzen</li>
 *   <li>{@link SettingsManager} laden (liest settings.json)</li>
 *   <li>{@link MainWindow} erstellen und anzeigen</li>
 *   <li>{@link GameEngine} mit Fenster und Einstellungen verbinden</li>
 *   <li>Begrüßungsbildschirm anzeigen</li>
 * </ol>
 */
public class Main {

    /**
     * Hauptmethode - Einstiegspunkt der Anwendung.
     *
     * @param args Kommandozeilenargumente (werden ignoriert)
     */
    public static void main(String[] args) {
        // Swing-UI muss auf dem Event Dispatch Thread initialisiert werden
        SwingUtilities.invokeLater(Main::starteAnwendung);
    }

    /**
     * Startet die Anwendung auf dem EDT.
     * Initialisiert alle Komponenten und zeigt das Hauptfenster.
     */
    private static void starteAnwendung() {
        // System-Look-and-Feel aktivieren für native Optik
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fallback auf Standard-Swing-Look-and-Feel - kein kritischer Fehler
            System.err.println("Warnung: System-Look-and-Feel nicht verfügbar: " + e.getMessage());
        }

        // Einstellungen laden (erstellt settings.json mit Standardwerten falls nicht vorhanden)
        SettingsManager einstellungen = new SettingsManager();

        // Hauptfenster erstellen
        MainWindow fenster = new MainWindow(einstellungen);

        // Spiellogik-Engine erstellen und mit Fenster verbinden
        GameEngine engine = new GameEngine(fenster, einstellungen);
        fenster.setSpieleEngine(engine);

        // Fenster anzeigen
        fenster.setVisible(true);

        // Begrüßungsbildschirm anzeigen
        fenster.ausgabeAnhaengen("""
                ╔══════════════════════════════════════════════════════════╗
                ║           ZORK LLM - FANTASY TEXT-ADVENTURE             ║
                ║                                                          ║
                ║  Ein LLM-generiertes Abenteuer im Stil von Zork.        ║
                ║                                                          ║
                ║  Vor dem Start:                                          ║
                ║  1. Einstellungen > API-Einstellungen öffnen            ║
                ║  2. API-URL, Schlüssel und Modell eingeben              ║
                ║  3. Spiel > Neues Spiel starten                         ║
                ║                                                          ║
                ║  Tippe 'hilfe' für eine Befehlsübersicht.              ║
                ╚══════════════════════════════════════════════════════════╝
                """);

        // API-Konfigurationshinweis anzeigen wenn noch kein Schlüssel gesetzt
        if (!einstellungen.istKonfiguriert()) {
            fenster.ausgabeAnhaengen(
                    "⚠ Kein API-Schlüssel konfiguriert. Bitte zuerst die Einstellungen öffnen.\n");
        }
    }
}
