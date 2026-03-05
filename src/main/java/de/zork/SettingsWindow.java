package de.zork;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Modaler Dialog zur Konfiguration der LLM-API-Verbindungsdaten.
 *
 * <p>Ermoeglicht die Eingabe von:
 * <ul>
 *   <li>API-Endpunkt-URL (OpenAI-kompatibel)</li>
 *   <li>API-Schluessel (optional)</li>
 *   <li>Modellname – waehle aus Dropdown oder trage manuell ein</li>
 * </ul>
 *
 * <p>Ueber "Modelle laden" werden verfuegbare Modelle vom Endpunkt abgefragt
 * (GET {endpunkt}/models) und als Dropdown-Liste angeboten.</p>
 *
 * <p>Die Einstellungen werden beim Klick auf "Speichern" ueber den
 * {@link SettingsManager} in {@code settings.json} persistiert.</p>
 */
public class SettingsWindow extends JDialog {

    /** Eingabefeld fuer die API-Endpunkt-URL. */
    private final JTextField endpunktFeld;

    /** Passwortfeld fuer den API-Schluessel (Zeichen werden maskiert). */
    private final JPasswordField schluesselFeld;

    /** Editierbare Dropdown-Liste fuer den Modellnamen. */
    private final JComboBox<String> modellCombo;

    /** Button zum Abrufen verfuegbarer Modelle vom Endpunkt. */
    private final JButton ladenButton;

    /** Referenz auf den Einstellungsmanager. */
    private final SettingsManager einstellungen;

    /**
     * Erstellt das Einstellungsfenster.
     *
     * @param elternFenster  das uebergeordnete Hauptfenster
     * @param einstellungen  der zu verwendende Einstellungsmanager
     */
    public SettingsWindow(JFrame elternFenster, SettingsManager einstellungen) {
        super(elternFenster, "API-Einstellungen", true);
        this.einstellungen = einstellungen;

        endpunktFeld   = new JTextField(einstellungen.getApiEndpunkt(), 40);
        schluesselFeld = new JPasswordField(einstellungen.getApiSchluessel(), 40);

        modellCombo = new JComboBox<>();
        modellCombo.setEditable(true);
        String aktuellesModell = einstellungen.getModellName();
        if (!aktuellesModell.isBlank()) {
            modellCombo.addItem(aktuellesModell);
            modellCombo.setSelectedItem(aktuellesModell);
        }

        ladenButton = new JButton("Modelle laden");
        ladenButton.addActionListener(e -> ladeModelle());

        initUI();
    }

    /**
     * Initialisiert das UI-Layout des Dialogs.
     */
    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel inhalt = new JPanel(new BorderLayout(10, 10));
        inhalt.setBorder(new EmptyBorder(15, 15, 10, 15));
        setContentPane(inhalt);

        JPanel formular = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Zeile 0: API-Endpunkt
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        formular.add(new JLabel("API-Endpunkt-URL:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        formular.add(endpunktFeld, gbc);

        // Zeile 1: Hinweis Endpunkt
        gbc.gridx = 1; gbc.gridy = 1; gbc.insets = new Insets(0, 5, 8, 5);
        JLabel hinweis1 = new JLabel("<html><small>z.B. http://127.0.0.1:1337/v1</small></html>");
        hinweis1.setForeground(Color.GRAY);
        formular.add(hinweis1, gbc);

        // Zeile 2: API-Schluessel
        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1;
        formular.add(new JLabel("API-Schluessel:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0; gbc.gridwidth = 2;
        formular.add(schluesselFeld, gbc);

        // Zeile 3: Modell-Dropdown + Laden-Button
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; gbc.gridwidth = 1;
        formular.add(new JLabel("Modell:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formular.add(modellCombo, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formular.add(ladenButton, gbc);

        // Zeile 4: Hinweis Modell
        gbc.gridx = 1; gbc.gridy = 4; gbc.insets = new Insets(0, 5, 8, 5); gbc.gridwidth = 2;
        JLabel hinweis2 = new JLabel(
                "<html><small>Endpunkt + Schluessel eingeben, dann \"Modelle laden\" klicken</small></html>");
        hinweis2.setForeground(Color.GRAY);
        formular.add(hinweis2, gbc);

        inhalt.add(formular, BorderLayout.CENTER);

        // Button-Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton abbrechenButton = new JButton("Abbrechen");
        abbrechenButton.addActionListener(e -> dispose());
        JButton speichernButton = new JButton("Speichern");
        speichernButton.setDefaultCapable(true);
        speichernButton.addActionListener(e -> speichern());
        buttonPanel.add(abbrechenButton);
        buttonPanel.add(speichernButton);
        inhalt.add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(speichernButton);
        pack();
        setLocationRelativeTo(getParent());
    }

    /**
     * Ruft verfuegbare Modelle vom konfigurierten API-Endpunkt ab (GET /models)
     * und befuellt die Dropdown-Liste. Laeuft asynchron via SwingWorker.
     */
    private void ladeModelle() {
        String endpunkt  = endpunktFeld.getText().trim();
        String schluessel = new String(schluesselFeld.getPassword()).trim();

        if (endpunkt.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Bitte zuerst die API-Endpunkt-URL eingeben.",
                    "Fehlende Eingabe", JOptionPane.WARNING_MESSAGE);
            endpunktFeld.requestFocus();
            return;
        }

        ladenButton.setEnabled(false);
        ladenButton.setText("Laedt...");

        String modelsUrl = endpunkt.endsWith("/") ? endpunkt + "models" : endpunkt + "/models";

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                OkHttpClient client = new OkHttpClient();
                Request.Builder requestBuilder = new Request.Builder().url(modelsUrl).get();
                if (!schluessel.isEmpty()) {
                    requestBuilder.header("Authorization", "Bearer " + schluessel);
                }
                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("HTTP-Fehler: " + response.code());
                    }
                    JsonNode root = new ObjectMapper().readTree(response.body().string());
                    JsonNode data = root.path("data");
                    List<String> modelle = new ArrayList<>();
                    if (data.isArray()) {
                        for (JsonNode model : data) {
                            String id = model.path("id").asText();
                            if (!id.isBlank()) modelle.add(id);
                        }
                    }
                    return modelle;
                }
            }

            @Override
            protected void done() {
                ladenButton.setEnabled(true);
                ladenButton.setText("Modelle laden");
                try {
                    List<String> modelle = get();
                    Object vorherige = modellCombo.getEditor().getItem();
                    modellCombo.removeAllItems();
                    modelle.forEach(modellCombo::addItem);
                    // Vorherige Auswahl beibehalten wenn vorhanden
                    if (vorherige != null && !vorherige.toString().isBlank()) {
                        modellCombo.setSelectedItem(vorherige.toString());
                    }
                    if (modelle.isEmpty()) {
                        JOptionPane.showMessageDialog(SettingsWindow.this,
                                "Keine Modelle gefunden.",
                                "Hinweis", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    Throwable ursache = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(SettingsWindow.this,
                            "Modelle konnten nicht geladen werden:\n" + ursache.getMessage(),
                            "Verbindungsfehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Liest die Formulareingaben aus, speichert sie im {@link SettingsManager}
     * und schliesst den Dialog.
     */
    private void speichern() {
        String endpunkt   = endpunktFeld.getText().trim();
        String schluessel = new String(schluesselFeld.getPassword()).trim();
        Object auswahl    = modellCombo.getEditor().getItem();
        String modell     = auswahl != null ? auswahl.toString().trim() : "";

        if (endpunkt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte gib eine API-Endpunkt-URL ein.",
                    "Fehlende Eingabe", JOptionPane.WARNING_MESSAGE);
            endpunktFeld.requestFocus();
            return;
        }
        if (modell.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte waehle oder gib einen Modellnamen ein.",
                    "Fehlende Eingabe", JOptionPane.WARNING_MESSAGE);
            modellCombo.requestFocus();
            return;
        }

        einstellungen.setApiEndpunkt(endpunkt);
        einstellungen.setApiSchluessel(schluessel);
        einstellungen.setModellName(modell);

        try {
            einstellungen.speichern();
            dispose();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Einstellungen konnten nicht gespeichert werden:\n" + e.getMessage(),
                    "Speicherfehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
