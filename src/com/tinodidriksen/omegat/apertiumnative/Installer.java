/*
 * Copyright (C) 2016 Tino Didriksen <mail@tinodidriksen.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.tinodidriksen.omegat.apertiumnative;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import javax.net.ssl.*;
import javax.swing.*;

public class Installer extends javax.swing.JDialog implements ActionListener {
    private static final int FsCadence = 0;
    private static final int FsName = 1;
    private static final int FsVersion = 2;
    private static final int FsRevision = 3;
    private static final int FsMTime = 4;
    private static final int FsSize = 5;
    private static final int FsURL = 6;
    private static final int FsFrom = 7;
    private static final int FsTo = 8;

    private static final int CHUNKZ = 128*1024;
    private static final String ROOT_URL = "https://apertium.projectjj.com";
    private static final String BTYPE = ApertiumNative.BTYPE;

    private final Preferences settings = ApertiumNative.settings;
    private TreeMap<String,String> pkgs = new TreeMap<>();

    public Installer(JFrame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        this.setLocationRelativeTo(parent);

        refreshPkgs();
    }

    private void refreshPkgs() {
        // Yeah, this is a horrible thing to do, but Java prior to version 8u101 doesn't know LetsEncrypt
        // ToDo: Implement http://stackoverflow.com/a/36707080/145919 instead
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        }
        catch (Exception ex) {
            Helpers.showError("Failed Cheating HTTPS", ex.getLocalizedMessage());
            this.dispose();
            return;
        }

        GridBagLayout grid = new GridBagLayout();
        GridBagConstraints con = new GridBagConstraints();
        body.setLayout(grid);
        con.gridy = 0;

        con.gridx = 0;
        con.anchor = GridBagConstraints.BASELINE_LEADING;
        JLabel p = new JLabel("Package");
        grid.setConstraints(p, con);
        body.add(p);

        ++con.gridx;
        con.anchor = GridBagConstraints.BASELINE;
        p = new JLabel("Install");
        grid.setConstraints(p, con);
        body.add(p);

        ++con.gridx;
        con.anchor = GridBagConstraints.BASELINE_TRAILING;
        p = new JLabel("Bytes");
        grid.setConstraints(p, con);
        body.add(p);

        String pkglist = null;
        try {
            pkglist = downloadString(ROOT_URL + "/pkgs.php");
        }
        catch (Exception ex) {
            Helpers.showError("Failed Listing Packages", ex.getClass() + "\n" + ex.getLocalizedMessage());
            this.dispose();
            return;
        }

        final Pattern rx_pair = Pattern.compile("^apertium-[a-z]{2,3}-[a-z]{2,3}$");
        final String os = settings.get("os", "win32");

        String[] lines = pkglist.split("\n");
        // Skip header
        for (int i=1 ; i<lines.length ; ++i) {
            String[] pkg = lines[i].split("\t");
            // Only show packages of our desired cadence
            if (!pkg[FsCadence].equals(BTYPE)) {
                continue;
            }
            // Only show core tools for this platform
            if (pkg[FsName].startsWith("core-")) {
                if (!pkg[FsName].equals("core-" + os)) {
                    continue;
                }
            }
            // Skip non-pair packages
            else if (!rx_pair.matcher(pkg[FsName]).matches()) {
                continue;
            }

            if (pkg[FsName].startsWith("apertium-")) {
                pkg[FsName] = pkg[FsName].substring("apertium-".length());
                String[] tf = pkg[FsName].split("-");
                Locale a = Helpers.isoNormalize(tf[0]);
                Locale b = Helpers.isoNormalize(tf[1]);
                pkg[FsName] = (a.getDisplayLanguage()) + " ↔ " + (b.getDisplayLanguage());
                lines[i] = lines[i] + "\t" + a.getLanguage() + "\t" + b.getLanguage();
            }
            else {
                pkg[FsName] = "(required core tools)";
                lines[i] = lines[i] + "\tcore\ttools";
            }

            pkgs.put(pkg[FsName], lines[i]);
        }

        con.gridwidth = 1;
        for (Map.Entry<String,String> e : pkgs.entrySet()) {
            ++con.gridy;

            con.gridx = 0;
            con.anchor = GridBagConstraints.BASELINE_LEADING;
            p = new JLabel(e.getKey());
            grid.setConstraints(p, con);
            body.add(p);

            String s = e.getValue();
            String[] ps = s.split("\t");

            String txt = "Install";
            String node = ps[FsCadence] + "/" + ps[FsFrom] + " " + ps[FsTo];
            int mtime = settings.getInt(node, 0);
            if (mtime != 0) {
                txt = "Reinstall";
                if (mtime < Integer.parseInt(ps[FsMTime])) {
                    txt = "Update";
                }
            }
            ++con.gridx;
            con.anchor = GridBagConstraints.BASELINE;
            JButton btn = new JButton(txt);
            btn.setActionCommand(s);
            btn.addActionListener(this);
            grid.setConstraints(btn, con);
            body.add(btn);

            ++con.gridx;
            con.anchor = GridBagConstraints.BASELINE_TRAILING;
            p = new JLabel(ps[FsSize]);
            grid.setConstraints(p, con);
            body.add(p);
        }

        this.repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String s = e.getActionCommand();
        String[] ps = s.split("\t");

        try {
            installPkg(ps);
        }
        catch (Exception ex) {
            Helpers.showError("Package Install Failed", s + "\n" + ex.getClass().getName() + "\n" + ex.getLocalizedMessage());
        }
    }

    private void installPkg(String[] ps) throws Exception {
        // Check we have 7-Zip, download if not
        File z7 = new File(settings.get("root", ""), "7z");
        if (!z7.exists()) {
            z7.mkdirs();
            File zip = downloadFile(ROOT_URL + "/" + settings.get("os", "win32") + "/7z.zip");
            Helpers.extractZip(zip, z7);
        }
        if (!(new File(z7, "7z").canExecute() || new File(z7, "7z.exe").canExecute())) {
            Helpers.showError("7-Zip Missing", "7-Zip either failed to install or files in your data folder are not executable.");
            return;
        }

        // Install or update core tools if needed
        if (!ps[FsName].startsWith("core-")) {
            try {
                String s = pkgs.get("(required core tools)");
                String[] cps = s.split("\t");
                String node = cps[FsCadence] + "/" + cps[FsFrom] + " " + cps[FsTo];
                int mtime = settings.getInt(node, 0);
                if (mtime < Integer.parseInt(cps[FsMTime])) {
                    installPkg(cps);
                }
            }
            catch (Exception ex) {
                Helpers.showError("Core Tools Install Failed", ex.getClass().getName() + "\n" + ex.getLocalizedMessage());
                return;
            }
        }

        File arc = downloadFile(ROOT_URL + ps[FsURL]);
        File root = new File(settings.get("root", ""), BTYPE);
        root.mkdirs();

        final JDialog popup = new JDialog(this, "Unpacking " + ps[FsName], true);
        popup.add(BorderLayout.CENTER, new JLabel("Unpacking " + ps[FsName]));
        popup.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        popup.setSize(300, 75);
        popup.setLocationRelativeTo(this);

        new Thread() {
            @Override
            public void run() {
                try {
                    ProcessBuilder pb = new ProcessBuilder(new File(z7, "7z").toString(), "x", "-y", arc.toString());
                    pb.directory(root);
                    pb.start().waitFor();

                    File tar = new File(root, "data.tar");
                    if (tar.canRead()) {
                        ProcessBuilder untar = new ProcessBuilder(new File(z7, "7z").toString(), "x", "-y", "data.tar");
                        untar.directory(root);
                        untar.start().waitFor();
                        tar.delete();
                    }

                    String node = ps[FsCadence] + "/" + ps[FsFrom] + " " + ps[FsTo];
                    settings.putInt(node, Integer.parseInt(ps[FsMTime]));
                }
                catch (Exception ex) {
                    Helpers.showError("Unpacking Failed", ex.getClass().getName() + "\n" + ex.getLocalizedMessage());
                }
                finally {
                    arc.delete();
                    popup.dispose();
                }
            }
        }.start();

        popup.setVisible(true);
    }

    private File downloadFile(String where) throws Exception {
        URL url = new URL(where);
        URLConnection conn = url.openConnection();
        int length = conn.getContentLength();
        InputStream is = conn.getInputStream();

        File out = File.createTempFile("ap-native-", ".tmp");
        FileOutputStream os = new FileOutputStream(out);

        final JDialog popup = new JDialog(this, "Downloading " + where, true);
        JProgressBar bar = new JProgressBar(0, Math.max(CHUNKZ, length));
        bar.setStringPainted(true);
        popup.add(BorderLayout.CENTER, bar);
        popup.add(BorderLayout.NORTH, new JLabel(where));
        popup.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        popup.setSize(300, 75);
        popup.setLocationRelativeTo(this);

        new Thread() {
            @Override
            public void run() {
                byte[] bytes = new byte[CHUNKZ];
                try {
                    int read = 0;
                    while ((read = is.read(bytes)) > 0) {
                        os.write(bytes, 0, read);
                        bar.setValue(bar.getValue() + read);
                        bar.setMaximum(Math.max(bar.getMaximum(), bar.getValue()+1));
                    }
                    is.close();
                    os.close();
                }
                catch (IOException ex) {
                    Helpers.showError("Download Failed", ex.getLocalizedMessage());
                }
                finally {
                    popup.dispose();
                }
            }
        }.start();

        popup.setVisible(true);

        return out;
    }

    private String downloadString(String where) throws Exception {
        File in = downloadFile(where);
        String rv = new String(Files.readAllBytes(in.toPath()));
        in.delete();
        return rv;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnClose = new javax.swing.JButton();
        scroller = new javax.swing.JScrollPane();
        body = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Install Language Pairs");

        btnClose.setText("Close");
        btnClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCloseActionPerformed(evt);
            }
        });

        scroller.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setToolTipText("");

        body.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        scroller.setViewportView(body);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(scroller, javax.swing.GroupLayout.DEFAULT_SIZE, 330, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnClose)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(scroller, javax.swing.GroupLayout.PREFERRED_SIZE, 444, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnClose)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCloseActionPerformed
        this.dispose();
    }//GEN-LAST:event_btnCloseActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel body;
    private javax.swing.JButton btnClose;
    private javax.swing.JScrollPane scroller;
    // End of variables declaration//GEN-END:variables
}
