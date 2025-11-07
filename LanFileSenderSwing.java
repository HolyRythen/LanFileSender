import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;

public class LanFileSenderSwing extends JFrame {

    // UI
    private final DefaultListModel<File> listModel = new DefaultListModel<>();
    private final JList<File> fileList = new JList<>(listModel);
    private final JButton addBtn = new JButton("Dateien hinzufügen…");
    private final JButton removeBtn = new JButton("Entfernen");
    private final JButton clearBtn = new JButton("Leeren");
    private final JButton upBtn = new JButton("↑");
    private final JButton downBtn = new JButton("↓");

    private final JComboBox<String> ipBox = new JComboBox<>();
    private final JSpinner portSpin = new JSpinner(new SpinnerNumberModel(8080, 1024, 65535, 1));
    private final JButton startBtn = new JButton("Server starten");
    private final JButton stopBtn = new JButton("Stop");
    private final JButton openBtn = new JButton("Im Browser öffnen");
    private final JButton copyBtn = new JButton("URL kopieren");
    private final JLabel urlLabel = new JLabel("URL: —");
    private final JLabel status = new JLabel("Bereit.");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel progressDetail = new JLabel("");

    private final JLabel qrLabel = new JLabel("", SwingConstants.CENTER);

    // Server
    private HttpServer server;
    private final Map<String, File> tokenToFile = new HashMap<>();
    private volatile long currentSent = 0;
    private volatile long currentTotal = 0;

    public LanFileSenderSwing() {
        super("LAN File Sender (Swing)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 640));
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        // Liste
        fileList.setVisibleRowCount(10);
        fileList.setCellRenderer(new FileCellRenderer());
        fileList.setDragEnabled(true);
        fileList.setDropMode(DropMode.ON_OR_INSERT);

        // Drag & Drop auf Liste
        new DropTarget(fileList, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    if (dtde.getTransferable().isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) dtde.getTransferable()
                                .getTransferData(DataFlavor.javaFileListFlavor);
                        addFiles(files);
                    }
                } catch (Exception ignored) {}
            }
        });

        // Buttons links
        JPanel leftBtns = new JPanel(new GridLayout(5,1,6,6));
        leftBtns.add(addBtn);
        leftBtns.add(removeBtn);
        leftBtns.add(clearBtn);
        leftBtns.add(upBtn);
        leftBtns.add(downBtn);

        // Mitte: Liste
        JPanel centerList = new JPanel(new BorderLayout(8,8));
        centerList.add(new JLabel("Dateien zum Freigeben (Reihenfolge egal)"), BorderLayout.NORTH);
        centerList.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // Rechts: QR + Info
        JPanel right = new JPanel(new BorderLayout(8,8));
        qrLabel.setBorder(BorderFactory.createTitledBorder("QR-Code (Index-URL)"));
        right.add(qrLabel, BorderLayout.CENTER);

        JPanel info = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4,4,4,4);
        g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        int r=0;
        g.gridx=0; g.gridy=r; g.weightx=0; info.add(new JLabel("LAN-IP"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; info.add(ipBox, g);
        g.gridx=0; g.gridy=r; g.weightx=0; info.add(new JLabel("Port"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; info.add(portSpin, g);

        g.gridx=0; g.gridy=r; g.gridwidth=2;
        JPanel srvBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        srvBtns.add(startBtn); srvBtns.add(stopBtn); srvBtns.add(openBtn); srvBtns.add(copyBtn);
        info.add(srvBtns, g);
        g.gridwidth=1; r++;

        g.gridx=0; g.gridy=r; g.weightx=0; info.add(new JLabel("Link"), g);
        g.gridx=1; g.gridy=r++; g.weightx=1; info.add(urlLabel, g);

        right.add(info, BorderLayout.SOUTH);

        add(centerList, BorderLayout.CENTER);
        add(leftBtns, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(8,8));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        left.add(status);
        south.add(left, BorderLayout.WEST);
        JPanel prog = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        progress.setPreferredSize(new Dimension(260, 20));
        progress.setStringPainted(true);
        prog.add(progress);
        prog.add(progressDetail);
        south.add(prog, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        // Actions
        addBtn.addActionListener(e -> onAdd());
        removeBtn.addActionListener(e -> onRemove());
        clearBtn.addActionListener(e -> { listModel.clear(); setStatus("Liste geleert."); });
        upBtn.addActionListener(e -> move(-1));
        downBtn.addActionListener(e -> move(+1));

        startBtn.addActionListener(this::onStart);
        stopBtn.addActionListener(e -> stopServer());
        openBtn.addActionListener(e -> openInBrowser(getIndexUrlOrNull()));
        copyBtn.addActionListener(e -> copyUrl(getIndexUrlOrNull()));

        // IPs
        refreshIPs();

        pack();
        setLocationRelativeTo(null);
    }

    // ===== Server =====
    private void onStart(ActionEvent e) {
        if (server != null) { msg("Server läuft bereits."); return; }
        if (listModel.isEmpty()) { msg("Bitte mindestens eine Datei hinzufügen."); return; }
        String host = Objects.toString(ipBox.getSelectedItem(), "").trim();
        int port = (Integer) portSpin.getValue();
        if (host.isEmpty()) { msg("Keine LAN-IP gefunden. Bist du verbunden?"); return; }

        // Build token map
        tokenToFile.clear();
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < listModel.getSize(); i++) {
            File f = listModel.get(i);
            String token;
            do { token = Long.toHexString(rnd.nextLong()); } while (tokenToFile.containsKey(token));
            tokenToFile.put(token.substring(0,8), f);
        }

        try {
            InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName(host), port);
            server = HttpServer.create(addr, 0);
            server.createContext("/", new IndexHandler());
            server.createContext("/d", new DownloadHandler()); // /d/<token>/<name>
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();

            String url = getIndexUrlOrNull();
            setStatus("Server läuft: " + url);
            urlLabel.setText(url);
            drawQr(url);
        } catch (BindException be) {
            msg("Port belegt. Wähle einen anderen Port.");
        } catch (Exception ex) {
            msg("Server-Fehler: " + ex.getMessage());
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
            setStatus("Server gestoppt.");
            urlLabel.setText("URL: —");
            qrLabel.setIcon(null);
            progress.setValue(0); progress.setString(null);
            progressDetail.setText("");
        }
    }

    private String getIndexUrlOrNull() {
        if (server == null) return null;
        String host = Objects.toString(ipBox.getSelectedItem(), "").trim();
        int port = (Integer) portSpin.getValue();
        return "http://" + host + ":" + port + "/";
    }

    // ===== Handlers =====
    private class IndexHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendText(ex, 405, "Method Not Allowed"); return; }
            StringBuilder html = new StringBuilder();
            html.append("<!doctype html><meta charset='utf-8'><title>LAN File Sender</title>");
            html.append("<style>body{font-family:Segoe UI,Arial,sans-serif;padding:1rem}table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:.5rem}a{color:#0b69c7;text-decoration:none}</style>");
            html.append("<h1>Freigegebene Dateien</h1><table><tr><th>Datei</th><th>Größe</th><th>Download</th></tr>");
            DecimalFormat df = new DecimalFormat("#,##0.##");
            for (Map.Entry<String, File> e : tokenToFile.entrySet()) {
                String token = e.getKey();
                File f = e.getValue();
                String name = f.getName();
                String escName = URLEncoder.encode(name, StandardCharsets.UTF_8);
                html.append("<tr><td>").append(escapeHtml(name)).append("</td>")
                        .append("<td>").append(humanSize(f.length(), df)).append("</td>")
                        .append("<td><a href='/d/").append(token).append("/").append(escName).append("'>Download</a></td></tr>");
            }
            html.append("</table><p>Bereitgestellt von <b>LAN File Sender</b></p>");
            byte[] data = html.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        }
    }

    private class DownloadHandler implements HttpHandler {
        @Override public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { sendText(ex, 405, "Method Not Allowed"); return; }
            String[] split = ex.getRequestURI().getPath().split("/");
            // Expect: ["", "d", "<token>", "<filename>"]
            if (split.length < 4) { sendText(ex, 400, "Bad Request"); return; }
            String token = split[2];
            File f = tokenToFile.get(token);
            if (f == null || !f.isFile()) { sendText(ex, 404, "Not Found"); return; }

            String mime = Files.probeContentType(f.toPath());
            if (mime == null) mime = "application/octet-stream";

            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", mime);
            h.set("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(f.getName(), StandardCharsets.UTF_8));

            long len = f.length();
            ex.sendResponseHeaders(200, len);

            currentTotal = len;
            currentSent = 0;
            SwingUtilities.invokeLater(() -> {
                progress.setIndeterminate(false);
                progress.setMinimum(0);
                progress.setMaximum(100);
                progress.setValue(0);
                progress.setString("0%");
                progressDetail.setText("Sende: " + f.getName());
            });

            try (InputStream in = new BufferedInputStream(new FileInputStream(f));
                 OutputStream raw = ex.getResponseBody()) {

                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    raw.write(buf, 0, n);
                    currentSent += n;
                    int pct = (int)Math.round(100.0 * currentSent / Math.max(1, currentTotal));
                    SwingUtilities.invokeLater(() -> {
                        progress.setValue(pct);
                        progress.setString(pct + "%");
                    });
                }
                raw.flush();
            } catch (IOException io) {
                // Client abgebrochen – ignorieren
            } finally {
                SwingUtilities.invokeLater(() -> {
                    progressDetail.setText("Fertig.");
                    setStatus("Übertragung abgeschlossen.");
                });
            }
        }
    }

    // ===== Helpers =====
    private void onAdd() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Dateien auswählen");
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter("Alle Dateien (*.*)", "*"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addFiles(Arrays.asList(fc.getSelectedFiles()));
        }
    }
    private void onRemove() {
        List<File> sel = fileList.getSelectedValuesList();
        sel.forEach(listModel::removeElement);
        setStatus(sel.isEmpty() ? "Nichts ausgewählt." : (sel.size() + " entfernt."));
    }
    private void move(int dir) {
        int idx = fileList.getSelectedIndex();
        if (idx < 0) return;
        int to = idx + dir;
        if (to < 0 || to >= listModel.size()) return;
        File f = listModel.get(idx);
        listModel.remove(idx);
        listModel.add(to, f);
        fileList.setSelectedIndex(to);
        fileList.ensureIndexIsVisible(to);
    }
    private void addFiles(List<File> files) {
        int added = 0;
        for (File f : files) {
            if (f != null && f.isFile()) { listModel.addElement(f.getAbsoluteFile()); added++; }
        }
        setStatus(added + " Datei(en) hinzugefügt.");
    }

    private void refreshIPs() {
        ipBox.removeAllItems();
        for (InetAddress addr : getLocalIPv4()) {
            ipBox.addItem(addr.getHostAddress());
        }
        if (ipBox.getItemCount() == 0) ipBox.addItem("127.0.0.1");
    }

    private static List<InetAddress> getLocalIPv4() {
        List<InetAddress> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                var addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) out.add(a);
                }
            }
        } catch (SocketException ignored) {}
        out.sort(Comparator.comparing(InetAddress::getHostAddress));
        return out;
    }

    private void drawQr(String url) {
        if (url == null) { qrLabel.setIcon(null); return; }
        try {
            BufferedImage img = makeQr(url, 320);
            qrLabel.setIcon(new ImageIcon(img));
        } catch (Exception ex) {
            qrLabel.setText("QR konnte nicht erzeugt werden.");
        }
    }

    private static BufferedImage makeQr(String text, int size) throws WriterException {
        QRCodeWriter w = new QRCodeWriter();
        Map<EncodeHintType,Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix m = w.encode(text, BarcodeFormat.QR_CODE, size, size, hints);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<size; y++) {
            for (int x=0; x<size; x++) {
                img.setRGB(x, y, m.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }

    private void openInBrowser(String url) {
        if (url == null) return;
        try { Desktop.getDesktop().browse(URI.create(url)); }
        catch (Exception ex) { msg("Konnte Browser nicht öffnen:\n" + ex.getMessage()); }
    }
    private void copyUrl(String url) {
        if (url == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        setStatus("URL kopiert: " + url);
    }

    private static void sendText(HttpExchange ex, int code, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static String humanSize(long bytes, DecimalFormat df) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return df.format(kb) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024) return df.format(mb) + " MB";
        double gb = mb / 1024.0;
        return df.format(gb) + " GB";
    }
    private void setStatus(String s) { status.setText(s); }

    private static String escapeHtml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    private static void msg(String s) {
        JOptionPane.showMessageDialog(null, s);
    }

    // Renderer: Name + Größe
    private static class FileCellRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof File f) {
                setText(f.getName() + "  (" + f.length() + " bytes)");
            }
            return c;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LanFileSenderSwing().setVisible(true));
    }
}
