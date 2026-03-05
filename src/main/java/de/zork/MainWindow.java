package de.zork;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Map;
import java.net.URI;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Hauptfenster der Zork-LLM-Anwendung.
 *
 * <p><b>Aufbau (BorderLayout):</b>
 * <ul>
 *   <li><b>Norden:</b> Statustabelle mit HP, Stufe, EP, STR, GES, INT, Ort</li>
 *   <li><b>Mitte:</b> Scrollbarer Textbereich (nicht editierbar) für Spielausgaben</li>
 *   <li><b>Osten:</b> Navigationsbereich mit 4 Richtungsbuttons (N/O/S/W)</li>
 *   <li><b>Süden:</b> Eingabebereich mit Textfeld und "Senden"-Button</li>
 * </ul>
 *
 * <p><b>Menüleiste:</b>
 * <ul>
 *   <li>"Spiel": Neues Spiel, Speichern, Laden, Beenden</li>
 *   <li>"Einstellungen": API-Einstellungen...</li>
 * </ul>
 *
 * <p>Alle GUI-Aktualisierungen durch andere Threads erfolgen über
 * {@code SwingUtilities.invokeLater()} für Thread-Sicherheit.</p>
 */
public class MainWindow extends JFrame {

    /** Breite des Hauptfensters beim Start. */
    private static final int FENSTER_BREITE = 900;

    /** Höhe des Hauptfensters beim Start. */
    private static final int FENSTER_HOEHE = 650;

    /** Nicht editierbarer Textbereich für alle Spielausgaben. */
    private final JTextArea ausgabeBereich;

    /** Eingabetextfeld für Spielerbefehle. */
    private final JTextField eingabeBereich;

    /** Button zum Absenden des Befehls. */
    private final JButton sendenButton;

    /** Navigationsbuttons (Schlüssel: interne Richtung "north" etc.). */
    private final Map<String, JButton> navButtons;

    /** Tabellenmodell für die Statusanzeige. */
    private final DefaultTableModel statusModell;

    /** Referenz auf die Spiellogik. */
    private GameEngine spielEngine;

    /** Einstellungsmanager für den Einstellungsdialog. */
    private final SettingsManager einstellungen;

    /**
     * Erstellt und initialisiert das Hauptfenster.
     *
     * @param einstellungen der Einstellungsmanager für API-Konfiguration
     */
    public MainWindow(SettingsManager einstellungen) {
        super("Zork LLM - Fantasy Text-Adventure");
        this.einstellungen = einstellungen;

        // GUI-Komponenten initialisieren
        ausgabeBereich = erstelleAusgabeBereich();
        eingabeBereich = new JTextField();
        sendenButton = new JButton("Senden");

        // Navigationsbuttons erstellen (alle 6 Richtungen inkl. hoch/runter)
        navButtons = Map.ofEntries(
                Map.entry("north", new JButton("Nord  ↑")),
                Map.entry("south", new JButton("↓  Süd")),
                Map.entry("east",  new JButton("Ost  →")),
                Map.entry("west",  new JButton("←  West")),
                Map.entry("up",    new JButton("▲ Hoch")),
                Map.entry("down",  new JButton("▼ Runter"))
        );

        // Statustabelle initialisieren
        String[] spalten = {"HP", "MaxHP", "Stufe", "EP", "STR", "GES", "INT", "Ort"};
        statusModell = new DefaultTableModel(spalten, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Tabelle nicht editierbar
            }
        };
        statusModell.addRow(new Object[]{"20", "20", "1", "0/100", "10", "10", "10", "—"});

        // Layout zusammenbauen
        initLayout();
        initMenuLeiste();
        initEventHandler();

        // Fenster konfigurieren
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(FENSTER_BREITE, FENSTER_HOEHE);
        setMinimumSize(new Dimension(700, 500));
        setLocationRelativeTo(null); // Fenstermitte des Bildschirms

        // Navbuttons initial deaktivieren (kein Ort geladen)
        navButtonsAktivieren(false);
        eingabeAktivieren(false);
    }

    /**
     * Setzt die Spiellogik-Referenz. Muss vor dem ersten Spielstart aufgerufen werden.
     *
     * @param engine die initialisierte GameEngine
     */
    public void setSpieleEngine(GameEngine engine) {
        this.spielEngine = engine;
    }

    /**
     * Initialisiert das Haupt-Layout des Fensters.
     * Verwendet BorderLayout mit den vier Hauptbereichen.
     */
    private void initLayout() {
        setLayout(new BorderLayout(5, 5));
        JPanel hauptPanel = new JPanel(new BorderLayout(5, 5));
        hauptPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(hauptPanel);

        // NORDEN: Statustabelle
        hauptPanel.add(erstelleStatusPanel(), BorderLayout.NORTH);

        // MITTE: Ausgabebereich
        JScrollPane scrollPane = new JScrollPane(ausgabeBereich);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Spielgeschehen",
                TitledBorder.LEFT, TitledBorder.TOP));
        hauptPanel.add(scrollPane, BorderLayout.CENTER);

        // OSTEN: Navigationsbereich
        hauptPanel.add(erstelleNavPanel(), BorderLayout.EAST);

        // SÜDEN: Eingabebereich
        hauptPanel.add(erstelleEingabePanel(), BorderLayout.SOUTH);
    }

    /**
     * Erstellt den nicht editierbaren Ausgabe-Textbereich.
     *
     * @return konfigurierter JTextArea
     */
    private JTextArea erstelleAusgabeBereich() {
        JTextArea bereich = new JTextArea();
        bereich.setEditable(false);
        bereich.setLineWrap(true);
        bereich.setWrapStyleWord(true);
        bereich.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        bereich.setBackground(new Color(20, 20, 30));    // dunkles Fantasy-Theme
        bereich.setForeground(new Color(200, 200, 180)); // warmes Beige
        bereich.setCaretColor(Color.WHITE);
        bereich.setMargin(new Insets(8, 8, 8, 8));
        return bereich;
    }

    /**
     * Erstellt das Statustabellen-Panel im oberen Bereich.
     *
     * @return Panel mit der JTable für Spielerstats
     */
    private JPanel erstelleStatusPanel() {
        JTable tabelle = new JTable(statusModell);
        tabelle.setFillsViewportHeight(true);
        tabelle.setRowHeight(22);
        tabelle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        tabelle.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        tabelle.setBackground(new Color(45, 45, 65));
        tabelle.setForeground(new Color(220, 220, 200));
        tabelle.getTableHeader().setBackground(new Color(60, 60, 90));
        tabelle.getTableHeader().setForeground(new Color(220, 220, 200));
        tabelle.setSelectionBackground(new Color(80, 80, 120));
        tabelle.setGridColor(new Color(80, 80, 100));

        // Spaltenbreiten optimieren
        tabelle.getColumnModel().getColumn(7).setPreferredWidth(150); // Ort breiter

        JScrollPane scrollPane = new JScrollPane(tabelle);
        // 82px: 20px TitledBorder + ~23px Header + ~23px Datenzeile + Puffer
        scrollPane.setPreferredSize(new Dimension(0, 82));
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Charakterstatus",
                TitledBorder.LEFT, TitledBorder.TOP));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Erstellt das Navigations-Panel mit 6 Richtungsbuttons (N/S/O/W + Hoch/Runter).
     *
     * <p>Layout (von oben nach unten):
     * <pre>
     *   [▲ Hoch]
     *   [Nord ↑]
     *   [← West] [Ost →]
     *   [↓ Süd]
     *   [▼ Runter]
     * </pre>
     *
     * @return Panel mit den Navigationsbuttons
     */
    private JPanel erstelleNavPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Navigation",
                TitledBorder.LEFT, TitledBorder.TOP));
        panel.setPreferredSize(new Dimension(135, 0));

        // Haupt-Richtungsbuttons (Nord/Süd/Ost/West) stylen
        Dimension hauptGroesse = new Dimension(58, 32);
        Font hauptFont = new Font(Font.SANS_SERIF, Font.BOLD, 11);
        for (String dir : new String[]{"north", "south", "east", "west"}) {
            navButtons.get(dir).setPreferredSize(hauptGroesse);
            navButtons.get(dir).setFont(hauptFont);
        }
        // Vertikal-Buttons (Hoch/Runter) etwas kleiner
        Dimension vertGroesse = new Dimension(116, 26);
        Font vertFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        for (String dir : new String[]{"up", "down"}) {
            navButtons.get(dir).setPreferredSize(vertGroesse);
            navButtons.get(dir).setFont(vertFont);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);

        // Zeile 0: Hoch-Button (zentriert, volle Breite)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(navButtons.get("up"), gbc);

        // Zeile 1: Nord (zentriert, volle Breite)
        gbc.gridy = 1;
        panel.add(navButtons.get("north"), gbc);

        // Zeile 2: West links, Ost rechts - GridWidth auf 1 zurücksetzen
        gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST;
        panel.add(navButtons.get("west"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(navButtons.get("east"), gbc);

        // Zeile 3: Süd (zentriert, volle Breite)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(navButtons.get("south"), gbc);

        // Zeile 4: Runter-Button (zentriert, volle Breite)
        gbc.gridy = 4;
        panel.add(navButtons.get("down"), gbc);

        return panel;
    }

    /**
     * Erstellt den Eingabebereich mit Textfeld und Senden-Button.
     *
     * @return Panel mit Eingabeelementen
     */
    private JPanel erstelleEingabePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Befehlseingabe",
                TitledBorder.LEFT, TitledBorder.TOP));

        eingabeBereich.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        eingabeBereich.setToolTipText("Befehl eingeben und Enter drücken (oder 'hilfe' für Hilfe)");

        sendenButton.setPreferredSize(new Dimension(90, 0));

        panel.add(eingabeBereich, BorderLayout.CENTER);
        panel.add(sendenButton, BorderLayout.EAST);
        return panel;
    }

    /**
     * Initialisiert die Menüleiste mit allen Menüpunkten.
     */
    private void initMenuLeiste() {
        JMenuBar menuLeiste = new JMenuBar();

        // Menü "Spiel"
        JMenu spielMenu = new JMenu("Spiel");
        spielMenu.setMnemonic(KeyEvent.VK_S);

        JMenuItem neuesSpielItem = new JMenuItem("Neues Spiel");
        neuesSpielItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        neuesSpielItem.addActionListener(e -> neuesSpielStarten());

        JMenuItem speichernItem = new JMenuItem("Spiel speichern");
        speichernItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK));
        speichernItem.addActionListener(e -> spielSpeichern());

        JMenuItem ladenItem = new JMenuItem("Spiel laden");
        ladenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, ActionEvent.CTRL_MASK));
        ladenItem.addActionListener(e -> spielLaden());

        JMenuItem beendenItem = new JMenuItem("Beenden");
        beendenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
        beendenItem.addActionListener(e -> System.exit(0));

        spielMenu.add(neuesSpielItem);
        spielMenu.add(speichernItem);
        spielMenu.add(ladenItem);
        spielMenu.addSeparator();
        spielMenu.add(beendenItem);

        // Menü "Einstellungen"
        JMenu einstellungsMenu = new JMenu("Einstellungen");
        einstellungsMenu.setMnemonic(KeyEvent.VK_E);

        JMenuItem apiItem = new JMenuItem("API-Einstellungen...");
        apiItem.addActionListener(e -> zeigeEinstellungen());

        einstellungsMenu.add(apiItem);

        JMenu hilfeMenu = new JMenu("Hilfe");
        JMenuItem ueberItem = new JMenuItem("Über Zork LLM");
        ueberItem.addActionListener(e -> {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            String infoText = "<html><div style='text-align: center;'>" +
                    "<b>Zork LLM - Fantasy Text-Adventure</b><br>" +
                    "Ein LLM-gestütztes Textabenteuer im Stil von Zork.<br><br>" +
                    "Technologie: Java 25 (ekelhaft), Swing, Jackson, OkHttp<br>" +
                    "API: OpenAI-kompatibel<br><br></div></html>";
            JLabel textLabel = new JLabel(infoText);
            textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(textLabel);

            JLabel githubLabel = new JLabel(new ImageIcon(new ImageIcon(MainWindow.class.getResource("/GitHub_Invertocat_Black.png")).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH))); // Alles hier nur für ein Icon 🖕 Java, leck eier
            githubLabel.setForeground(Color.BLUE.darker());
            githubLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            githubLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            githubLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String url = "https://github.com/DasPauluteli/zork-llm";
                try {
                    // Erst versuchen wir den offiziellen Java-Weg
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(new URI(url));
                    } else {
                        // Manuelle Fallbacks für verschiedene Betriebssysteme
                        String os = System.getProperty("os.name").toLowerCase();
                        Runtime runtime = Runtime.getRuntime();

                        if (os.contains("win")) {
                            // Windows Fallback
                            runtime.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                        } else if (os.contains("mac")) {
                            // macOS Fallback
                            runtime.exec(new String[]{"open", url});
                        } else {
                            // Linux / Unix Fallback
                            runtime.exec(new String[]{"xdg-open", url});
                        }
                    }
                } catch (Exception ex) {
                    // Wenn gar nichts geht, zeigen wir dem User zumindest die URL
                    JOptionPane.showMessageDialog(null, 
                        "Browser konnte nicht automatisch geöffnet werden.\nURL: " + url, 
                        "Link öffnen", JOptionPane.WARNING_MESSAGE);
                    ex.printStackTrace();
                }
            }
        });
            panel.add(githubLabel);
            JOptionPane.showMessageDialog(this, panel, "Über Zork LLM", JOptionPane.PLAIN_MESSAGE);
        });
        hilfeMenu.add(ueberItem);

        menuLeiste.add(spielMenu);
        menuLeiste.add(einstellungsMenu);
        menuLeiste.add(hilfeMenu);
        setJMenuBar(menuLeiste);
    }

    /**
     * Registriert alle Event-Handler für Benutzereingaben.
     */
    private void initEventHandler() {
        // Senden-Button-Klick
        sendenButton.addActionListener(e -> eingabeVerarbeiten());

        // Enter-Taste im Eingabefeld
        eingabeBereich.addActionListener(e -> eingabeVerarbeiten());

        // Navigationsbuttons - alle 6 Richtungen
        for (String richtung : new String[]{"north", "south", "east", "west", "up", "down"}) {
            final String r = richtung;
            navButtons.get(r).addActionListener(e -> {
                if (spielEngine != null) spielEngine.bewegeInRichtung(r);
            });
        }
    }

    /**
     * Liest den Eingabetext aus, sendet ihn an die GameEngine und leert das Feld.
     */
    private void eingabeVerarbeiten() {
        String text = eingabeBereich.getText().trim();
        if (text.isEmpty() || spielEngine == null) return;
        eingabeBereich.setText("");
        spielEngine.verarbeiteBefehl(text);
    }

    /**
     * Fragt nach Bestätigung und startet dann ein neues Spiel.
     * Prüft zuerst ob die API-Einstellungen konfiguriert sind.
     */
    private void neuesSpielStarten() {
        if (spielEngine == null) return;

        // API-Konfiguration prüfen
        if (!einstellungen.istKonfiguriert()) {
            int antwort = JOptionPane.showConfirmDialog(this,
                    "Es wurde noch kein API-Schlüssel konfiguriert.\n" +
                    "Möchtest du die Einstellungen jetzt öffnen?",
                    "API nicht konfiguriert",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (antwort == JOptionPane.YES_OPTION) {
                zeigeEinstellungen();
                return;
            }
        }

        int bestaetigung = JOptionPane.showConfirmDialog(this,
                "Ein neues Spiel starten? Nicht gespeicherter Fortschritt geht verloren.",
                "Neues Spiel", JOptionPane.YES_NO_OPTION);
        if (bestaetigung == JOptionPane.YES_OPTION) {
            eingabeAktivieren(true);
            spielEngine.neuesSpiel();
        }
    }

    /**
     * Speichert den aktuellen Spielstand.
     */
    private void spielSpeichern() {
        if (spielEngine == null) return;
        try {
            spielEngine.spielSpeichern();
            ausgabeAnhaengen("\n[Spielstand gespeichert.]\n");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Spielstand konnte nicht gespeichert werden:\n" + e.getMessage(),
                    "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Lädt einen vorhandenen Spielstand.
     */
    private void spielLaden() {
        if (spielEngine == null) return;
        if (!spielEngine.speicherstandVorhanden()) {
            JOptionPane.showMessageDialog(this,
                    "Kein Spielstand gefunden (savegame.json).",
                    "Laden nicht möglich", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            spielEngine.spielLaden();
            eingabeAktivieren(true);
        } catch (Exception e) {
            // Alle Exception-Typen abfangen (auch RuntimeException bei Deserialisierungsfehlern)
            ausgabeAnhaengen("\n[Fehler beim Laden: " + e.getMessage() + "]\n");
            JOptionPane.showMessageDialog(this,
                    "Spielstand konnte nicht geladen werden:\n" + e.getMessage(),
                    "Ladefehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Öffnet den modalen Einstellungsdialog.
     */
    private void zeigeEinstellungen() {
        SettingsWindow dialog = new SettingsWindow(this, einstellungen);
        dialog.setVisible(true);
    }

    // =========================================================================
    // Öffentliche UI-Aktualisierungsmethoden (werden von GameEngine aufgerufen)
    // =========================================================================

    /**
     * Hängt Text an den Ausgabebereich an und scrollt automatisch nach unten.
     * Thread-sicher: verwendet {@code SwingUtilities.invokeLater()}.
     *
     * @param text der anzuzeigende Text
     */
    public void ausgabeAnhaengen(String text) {
        SwingUtilities.invokeLater(() -> {
            ausgabeBereich.append(text);
            // Automatisch nach unten scrollen
            ausgabeBereich.setCaretPosition(ausgabeBereich.getDocument().getLength());
        });
    }

    /**
     * Leert den gesamten Ausgabebereich.
     * Thread-sicher via {@code SwingUtilities.invokeLater()}.
     */
    public void ausgabeLeeren() {
        SwingUtilities.invokeLater(() -> ausgabeBereich.setText(""));
    }

    /**
     * Aktualisiert die Statusanzeige mit den aktuellen Spielerwerten.
     * Thread-sicher via {@code SwingUtilities.invokeLater()}.
     *
     * @param spieler     der aktuelle Spielercharakter
     * @param ortsName    der Name des aktuellen Ortes
     */
    public void statusAktualisieren(Player spieler, String ortsName) {
        SwingUtilities.invokeLater(() -> {
            if (statusModell.getRowCount() == 0) {
                statusModell.addRow(new Object[8]);
            }
            statusModell.setValueAt(spieler.getHp() + "/" + spieler.getMaxHp(), 0, 0);
            statusModell.setValueAt(spieler.getMaxHp(), 0, 1);
            statusModell.setValueAt(spieler.getLevel(), 0, 2);
            statusModell.setValueAt(spieler.getXp() + "/" + spieler.naechsteLevelSchwelle(), 0, 3);
            statusModell.setValueAt(spieler.getStr(), 0, 4);
            statusModell.setValueAt(spieler.getDex(), 0, 5);
            statusModell.setValueAt(spieler.getIntel(), 0, 6);
            statusModell.setValueAt(ortsName, 0, 7);
        });
    }

    /**
     * Aktiviert oder deaktiviert einen einzelnen Navigationsbutton.
     * Thread-sicher via {@code SwingUtilities.invokeLater()}.
     *
     * @param richtung die interne Richtungskennung ("north", "south", "east", "west")
     * @param aktiv    true zum Aktivieren, false zum Deaktivieren
     */
    public void navButtonAktivieren(String richtung, boolean aktiv) {
        SwingUtilities.invokeLater(() -> {
            JButton button = navButtons.get(richtung);
            if (button != null) button.setEnabled(aktiv);
        });
    }

    /**
     * Aktiviert oder deaktiviert alle Navigationsbuttons auf einmal.
     *
     * @param aktiv true zum Aktivieren, false zum Deaktivieren
     */
    public void navButtonsAktivieren(boolean aktiv) {
        SwingUtilities.invokeLater(() ->
                navButtons.values().forEach(b -> b.setEnabled(aktiv)));
    }

    /**
     * Aktiviert oder deaktiviert den Eingabebereich (Textfeld + Senden-Button).
     * Wird verwendet während LLM-Generierungen, um Doppeleingaben zu vermeiden.
     * Thread-sicher via {@code SwingUtilities.invokeLater()}.
     *
     * @param aktiv true zum Aktivieren, false zum Deaktivieren
     */
    public void eingabeAktivieren(boolean aktiv) {
        SwingUtilities.invokeLater(() -> {
            eingabeBereich.setEnabled(aktiv);
            sendenButton.setEnabled(aktiv);
            if (aktiv) eingabeBereich.requestFocusInWindow();
        });
    }
}
