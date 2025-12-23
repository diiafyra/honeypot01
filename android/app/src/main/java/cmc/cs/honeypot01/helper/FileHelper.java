package cmc.cs.honeypot01.helper;


import java.io.File;
import java.util.Arrays;

public class FileHelper {

    private static final String audioDir = "/storage/emulated/0/MIUI/sound_recorder/call_rec";


    public static File getLatestMp3() {
        File dir = new File(audioDir);
        if (!dir.exists() || !dir.isDirectory()) return null;

        File[] files = dir.listFiles((d, name) ->
                name.toLowerCase().endsWith(".mp3"));

        if (files == null || files.length == 0) return null;

        Arrays.sort(files, (a, b) ->
                Long.compare(b.lastModified(), a.lastModified()));

        return files[0];
    }
}
