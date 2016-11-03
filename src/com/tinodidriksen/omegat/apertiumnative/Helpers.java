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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.io.*;
import java.util.Enumeration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.*;
import org.omegat.core.Core;

public class Helpers {
    private static final String BTYPE = ApertiumNative.BTYPE;

    public static void log(Level level, String title, Object data) {
        // Need org.omegat for it to show up in OmegaT's log
        Logger.getLogger("org.omegat.apertiumnative." + BTYPE).log(level, title, data);
    }

    public static void showError(String title, String body) {
        log(Level.SEVERE, title + ": {0}", body);
        JOptionPane.showMessageDialog((JFrame) Core.getMainWindow(), body, title, JOptionPane.ERROR_MESSAGE);
    }

    public static Locale isoNormalize(String in) {
        if (in.equals("deu")) {
            in = "de";
        }
        else if (in.equals("fra")) {
            in = "fr";
        }
        else if (in.equals("hbs")) {
            in = "hr";
        }
        else if (in.equals("isl")) {
            in = "is";
        }
        else if (in.equals("mkd")) {
            in = "mk";
        }

        return new Locale(in);
    }

    public static void extractZip(File from, File to) throws IOException {
        // http://stackoverflow.com/a/1529707/145919
        ZipFile zip = new ZipFile(from);
        Enumeration entries = zip.entries();

        final JDialog popup = new JDialog((Frame)null, "Extracting " + from.getName(), true);
        JProgressBar bar = new JProgressBar(0, zip.size());
        bar.setStringPainted(true);
        popup.add(BorderLayout.CENTER, bar);
        popup.add(BorderLayout.NORTH, new JLabel(from.getName() + " → " + to));
        popup.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        popup.setSize(300, 75);

        new Thread() {
            @Override
            public void run() {
                try {
                    while (entries.hasMoreElements()) {
                        ZipEntry file = (ZipEntry) entries.nextElement();
                        File f = new File(to, file.getName());
                        f.getParentFile().mkdirs();

                        InputStream is = zip.getInputStream(file);
                        FileOutputStream fos = new FileOutputStream(f);
                        while (is.available() > 0) {
                            fos.write(is.read());
                        }

                        fos.close();
                        is.close();
                        // For this program, we happen to always want +x on all files coming from zips
                        f.setExecutable(true);

                        bar.setValue(bar.getValue() + 1);
                    }
                    zip.close();
                }
                catch (IOException ex) {
                    showError("Zip Extraction Failed", ex.getLocalizedMessage());
                }
                finally {
                    popup.dispose();
                }
            }
        }.start();

        popup.setVisible(true);
    }
}
