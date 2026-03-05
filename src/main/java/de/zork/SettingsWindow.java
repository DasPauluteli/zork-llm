package de.zork;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;

/**
 * Modaler Dialog zur Konfiguration der LLM-API-Verbindungsdaten.
 *
 * <p>Ermöglicht die Eingabe von:
 * <ul>
 *   <li>API-Endpunkt-URL (OpenAI-kompatibel)</li>
 *   <li>API-Schlüssel</li>
 *   <li>Modellname</li>
 * </ul>
 *
 * <p>Die Einstellungen werden beim Klick auf "Speichern" über den
 * {@link SettingsManager} in {@code settings.json} persistiert.</p>
 */
public class SettingsWindow extends JDialog {

    /** Eingabefeld für die API-Endpunkt-URL. */
    private final JTextField endpunktFeld;

    /** Passwortfeld für den API-Schlüssel (Zeichen werden maskiert). */
    private final JPasswordField schluesselFeld;

    /** Eingabefeld für den Modellnamen. */
    private final JTextField modellFeld;

    /** Referenz auf den Einstellungsmanager. */
    private final SettingsManager einstellungen;

    /**
     * Erstellt das Einstellungsfenster.
     *
     * @param elternFenster  das übergeordnete Hauptfenster
     * @param einstellungen  der zu verwendende Einstellungsmanager
     */
    public SettingsWindow(JFrame elternFenster, SettingsManager einstellungen) {
        super(elternFenster, "API-Einstellungen", true); // modaler Dialog
        this.einstellungen = einstellungen;

        // Aktuelle Werte in die Felder laden
        endpunktFeld = new JTextField(einstellungen.getApiEndpunkt(), 40);
        schluesselFeld = new JPasswordField(einstellungen.getApiSchluessel(), 40);
        modellFeld = new JTextField(einstellungen.getModellName(), 40);

        initUI();
    }

    /**
     * Initialisiert das UI-Layout des Dialogs.
     * Verwendet ein GridBagLayout für eine übersichtliche Formular-Darstellung.
     */
    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Hauptinhalt mit Abstand
        JPanel inhalt = new JPanel(new BorderLayout(10, 10));
        inhalt.setBorder(new EmptyBorder(15, 15, 10, 15));
        setContentPane(inhalt);

        // Formular-Panel mit GridBagLayout
        JPanel formular = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Zeile 1: API-Endpunkt
        gbc.gridx = 0; gbc.gridy = 0;
        formular.add(new JLabel("API-Endpunkt-URL:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formular.add(endpunktFeld, gbc);

        // Erklärungstext
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(0, 5, 8, 5);
        JLabel hinweis1 = new JLabel("<html><small>z.B. https://api.openai.com/v1</small></html>");
        hinweis1.setForeground(Color.GRAY);
        formular.add(hinweis1, gbc);

        // Zeile 2: API-Schlüssel
        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(5, 5, 5, 5); gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formular.add(new JLabel("API-Schlüssel:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formular.add(schluesselFeld, gbc);

        // Zeile 3: Modellname
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formular.add(new JLabel("Modellname:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formular.add(modellFeld, gbc);

        // Erklärungstext
        gbc.gridx = 1; gbc.gridy = 4; gbc.insets = new Insets(0, 5, 8, 5);
        JLabel hinweis2 = new JLabel("<html><small>z.B. gpt-4o-mini, gpt-4o, claude-3-5-haiku-20241022</small></html>");
        hinweis2.setForeground(Color.GRAY);
        formular.add(hinweis2, gbc);

        inhalt.add(formular, BorderLayout.CENTER);

        // Button-Panel unten
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton abbrechenButton = new JButton("Abbrechen");
        abbrechenButton.addActionListener(e -> dispose());

        JButton speichernButton = new JButton("Speichern");
        speichernButton.setDefaultCapable(true);
        speichernButton.addActionListener(e -> speichern());

        buttonPanel.add(abbrechenButton);
        buttonPanel.add(speichernButton);
        inhalt.add(buttonPanel, BorderLayout.SOUTH);

        // Standard-Button-Verknüpfung: Enter drücken = Speichern
        getRootPane().setDefaultButton(speichernButton);

        pack();
        setLocationRelativeTo(getParent());
    }

    /**
     * Liest die Formulareingaben aus, speichert sie im {@link SettingsManager}
     * und schließt den Dialog.
     * Bei Schreibfehler wird eine Fehlermeldung angezeigt.
     */
    private void speichern() {
        String endpunkt = endpunktFeld.getText().trim();
        String schluessel = new String(schluesselFeld.getPassword()).trim();
        String modell = modellFeld.getText().trim();

        // Pflichtfelder prüfen
        if (endpunkt.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bitte gib eine API-Endpunkt-URL ein.",
                    "Fehlende Eingabe", JOptionPane.WARNING_MESSAGE);
            endpunktFeld.requestFocus();
            return;
        }
        if (modell.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bitte gib einen Modellnamen ein.",
                    "Fehlende Eingabe", JOptionPane.WARNING_MESSAGE);
            modellFeld.requestFocus();
            return;
        }

        // Werte in den Einstellungsmanager übertragen
        einstellungen.setApiEndpunkt(endpunkt);
        einstellungen.setApiSchluessel(schluessel);
        einstellungen.setModellName(modell);

        try {
            einstellungen.speichern();
            dispose(); // Dialog schließen
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Einstellungen konnten nicht gespeichert werden:\n" + e.getMessage(),
                    "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
