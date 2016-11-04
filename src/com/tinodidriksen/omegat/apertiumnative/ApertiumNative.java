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

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import org.omegat.core.Core;
import org.omegat.core.machinetranslators.BaseTranslate;
import org.omegat.util.Language;

public class ApertiumNative extends BaseTranslate {
    public static final Preferences settings = Preferences.userNodeForPackage(ApertiumNative.class);
    public static final String BTYPE = "nightly";
    private final Map<String,String> modes = new HashMap<>();

    public ApertiumNative() {
        JMenuItem item = new JMenuItem("Apertium Native (" + BTYPE + ")...");
        item.addActionListener((ActionEvent) -> {
            openInstaller();
            refreshModes();
        });
        Core.getMainWindow().getMainMenu().getOptionsMenu().add(item);
        init();
    }

    @Override
    protected String getPreferenceName() {
        return "apertium-native-" + BTYPE;
    }

    @Override
    public String getName() {
        return "Apertium Native (" + BTYPE + ")";
    }

    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        String pair = sLang.getLocale().getISO3Language() + " → " + tLang.getLocale().getISO3Language();
        if (!modes.containsKey(pair)) {
            return "No such mode: " + pair;
        }

        File root = new File(settings.get("root", ""), BTYPE + "/apertium-all-dev/bin/");

        ProcessBuilder pb;
        if (settings.get("os", "win32").equals("win32")) {
            pb = new ProcessBuilder("cmd", "/D", "/Q", "/S", "/C", modes.get(pair));
        }
        else {
            pb = new ProcessBuilder("/bin/sh", "-c", modes.get(pair));
        }
        Map<String,String> env = pb.environment();
        env.put("PATH", root.getCanonicalPath() + File.pathSeparator + System.getenv("PATH"));
        env.put("LC_ALL", "en_US.UTF-8");

        Process tx = pb.start();
        OutputStream stdin = tx.getOutputStream();
        stdin.write(text.getBytes("UTF-8"));
        stdin.flush();
        stdin.close();
        InputStream stdout = tx.getInputStream();
        InputStream stderr = tx.getErrorStream();
        tx.waitFor();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        while ((b = stdout.read()) != -1) {
            bos.write(b);
        }
        while ((b = stderr.read()) != -1) {
            bos.write(b);
        }
        return new String(bos.toByteArray(), "UTF-8");
    }

    private void openInstaller() {
        new Installer((JFrame) Core.getMainWindow(), true).setVisible(true);
    }

    private void init() {
        settings.remove("root");
        settings.remove("pkg");
        settings.remove("os");

        String os = "";
        if (System.getProperty("os.name").startsWith("Mac")) {
            os = "osx";
        }

        String xdg = System.getenv("XDG_DATA_HOME");
        if (System.getProperty("os.name").startsWith("Windows")) {
            os = "win32";
            xdg = System.getenv("APPDATA");
        }
        if (xdg == null) {
            xdg = System.getenv("XDG_CONFIG_HOME");
        }
        if (xdg == null) {
            xdg = System.getProperty("user.home") + "/.local/share/";
        }
        File root = new File(xdg, "apertium-omegat-native");
        if (!root.exists() && !root.mkdirs()) {
            Helpers.showError("Missing Data Folder", xdg + " did not exist and could not be created");
            return;
        }
        if (!root.canWrite()) {
            Helpers.showError("Invalid Data Folder", xdg + " is not writable");
            return;
        }

        xdg = root.getPath();
        Helpers.log(Level.CONFIG, "Set root path to: {0}", xdg);
        settings.put("root", xdg);

        // On Windows and macOS, this isn't relevant
        String pkg = "n/a";
        if (System.getProperty("os.name").startsWith("Linux")) {
            // Assume we're on a yum based distro, until we can prove otherwise
            // This covers RHEL, CentOS, and derivatives
            pkg = "yum";
            os = "rpm";

            String var = "";
            File lsb = new File("/etc/os-release");
            if (lsb.exists() && lsb.canRead()) {
                try {
                    var = new String(Files.readAllBytes(lsb.toPath()));
                }
                catch (IOException ex) {
                    Helpers.log(Level.SEVERE, null, ex);
                }
            }

            if (var.contains("Debian") || var.contains("Ubuntu") || var.contains("debian") || var.contains("ubuntu")) {
                pkg = "apt-get";
                os = "apt";
            }
            else if (var.contains("Fedora")) {
                pkg = "dnf";
            }
            else if (var.contains("OpenSUSE")) {
                pkg = "zypper";
            }
        }

        Helpers.log(Level.CONFIG, "Set package manager to: {0}", pkg);
        settings.put("pkg", pkg);

        Helpers.log(Level.CONFIG, "Set OS to: {0}", os);
        settings.put("os", os);

        refreshModes();
    }

    private void refreshModes() {
        File root = new File(settings.get("root", ""), BTYPE);
        File mdir = new File(root, "usr/share/apertium/modes");
        if (!mdir.isDirectory()) {
            Helpers.log(Level.CONFIG, "Modes folder did not exist: {0}", mdir.toString());
            return;
        }

        modes.clear();

        String[] list = mdir.list((File dir, String mode) -> {
            if (!mode.endsWith(".mode")) {
                return false;
            }
            mode = mode.substring(0, mode.length() - ".mode".length());
            String[] ps = mode.split("-");
            // ToDo: Support locale and script variants
            return (ps.length == 2) && (ps[0].length() == 2 || ps[0].length() == 3) && (ps[1].length() == 2 || ps[1].length() == 3);
        });

        Helpers.log(Level.CONFIG, "Found possible modes: {0}", String.join("\t", list));
        for (String entry : list) {
            String mode;
            try {
                mode = new String(Files.readAllBytes(new File(mdir, entry).toPath()));
            }
            catch (IOException ex) {
                Helpers.log(Level.SEVERE, null, ex);
                continue;
            }
            if (!mode.contains("apertium-transfer")) {
                continue;
            }

            mode = mode.replace("$1", "-n").replace("$2", "").trim();
            if (!mode.contains("'/usr/share")) {
                mode = mode.replaceAll("(\\s*)(/usr/share/\\S+)(\\s*)", "\\1\"\\2\"\\3");
            }
            mode = mode.replace("/usr/share", root.toString() + "/usr/share");
            mode = mode.replace("/", File.separator).replace("'", "\"");
            mode = "apertium-deshtml | " + mode + " | apertium-rehtml-noent";

            entry = entry.substring(0, entry.length() - ".mode".length());
            String[] tf = entry.split("-");
            Locale a = Helpers.isoNormalize(tf[0]);
            Locale b = Helpers.isoNormalize(tf[1]);

            entry = a.getISO3Language() + " → " + b.getISO3Language();

            Helpers.log(Level.CONFIG, "Mode " + entry + ": {0}", mode);
            modes.put(entry, mode);
        }
    }
}
