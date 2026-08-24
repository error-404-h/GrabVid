package com.grabvid.downloader;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class PHPWebServer extends Thread {
    private static final String TAG = "PHPWebServer";
    private Process process;
    private String documentRoot;
    private String phpPath;
    private boolean isRunning = false;

    public PHPWebServer(String documentRoot, String phpPath) {
        this.documentRoot = documentRoot;
        this.phpPath = phpPath;
    }

    @Override
    public void run() {
        try {
            File phpFile = new File(phpPath);
            if (!phpFile.exists()) {
                Log.e(TAG, "PHP not found at: " + phpPath);
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(
                phpPath,
                "-S",
                "127.0.0.1:8080",
                "-t",
                documentRoot + "/www",
                "-c",
                documentRoot + "/php.ini"
            );

            pb.redirectErrorStream(true);
            pb.environment().put("PATH", 
                documentRoot + "/bin:" + System.getenv("PATH")
            );
            pb.environment().put("LD_LIBRARY_PATH", 
                documentRoot + "/bin:" + System.getenv("LD_LIBRARY_PATH")
            );

            process = pb.start();
            isRunning = true;

            // قراءة المخرجات
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line;
            while ((line = reader.readLine()) != null && isRunning) {
                Log.d(TAG, "PHP: " + line);
            }

            int exitCode = process.waitFor();
            Log.d(TAG, "PHP server exited with code: " + exitCode);
            isRunning = false;

        } catch (Exception e) {
            Log.e(TAG, "Error starting PHP server", e);
            isRunning = false;
        }
    }

    public void stopServer() {
        isRunning = false;
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}