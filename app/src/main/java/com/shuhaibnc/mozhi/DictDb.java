package com.shuhaibnc.mozhi;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline dictionary backed by a prebuilt SQLite database shipped in assets.
 * Same data and prefix-search behavior as https://github.com/ShuhaibNC/mozhi.
 */
public class DictDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "mozhi.db";
    private static final int DB_VERSION = 1;
    /** Same suggestion cap as the web version. */
    private static final int MAX_SUGGESTIONS = 60;

    private final Context ctx;
    private static DictDb instance;

    public static synchronized DictDb get(Context c) {
        if (instance == null) {
            instance = new DictDb(c.getApplicationContext());
        }
        return instance;
    }

    private DictDb(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
        this.ctx = c;
    }

    /** Copy the prebuilt DB out of assets on first run. */
    public void ensureReady() throws IOException {
        File dbFile = ctx.getDatabasePath(DB_NAME);
        if (dbFile.exists() && dbFile.length() > 0) return;
        File parent = dbFile.getParentFile();
        if (parent != null) parent.mkdirs();
        InputStream in = ctx.getAssets().open(DB_NAME);
        OutputStream out = new FileOutputStream(dbFile);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
        out.close();
        in.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Database is prebuilt and copied from assets; nothing to create.
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No upgrades yet.
    }

    /** Escape LIKE wildcards in a user-typed prefix. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * Case-insensitive prefix search over the lowercased word index,
     * mirroring the web version's startsWith behavior.
     */
    public List<String> suggestions(String query) {
        List<String> out = new ArrayList<>();
        String q = query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return out;
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT word FROM entries WHERE lword LIKE ? ESCAPE '\\' " +
                "ORDER BY lword LIMIT " + MAX_SUGGESTIONS,
                new String[]{escapeLike(q) + "%"});
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    /** Malayalam meanings for an exact word, or an empty list. */
    public List<String> meanings(String word) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT meanings FROM entries WHERE word = ? LIMIT 1",
                new String[]{word});
        if (c.moveToFirst()) {
            try {
                JSONArray arr = new JSONArray(c.getString(0));
                for (int i = 0; i < arr.length(); i++) {
                    out.add(arr.optString(i));
                }
            } catch (Exception ignored) {}
        }
        c.close();
        return out;
    }

    /** Total word count for the status line. */
    public int wordCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM entries", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }
}
