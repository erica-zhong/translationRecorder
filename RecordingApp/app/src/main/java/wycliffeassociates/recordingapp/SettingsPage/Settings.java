package wycliffeassociates.recordingapp.SettingsPage;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import wycliffeassociates.recordingapp.FilesPage.ExportFiles;
import wycliffeassociates.recordingapp.MainMenu;
import wycliffeassociates.recordingapp.R;
import wycliffeassociates.recordingapp.SettingsPage.connectivity.LanguageNamesRequest;

/**
 *
 * The settings page -- for all persistent options/information.
 *
 */
public class Settings extends Activity {
    private Context c;
    private Button hardReset;
    private String sampleName;
    public static final String KEY_PREF_SOURCE = "pref_source";
    public static final String KEY_PREF_LANG = "pref_lang";
    public static final String KEY_PREF_BOOK = "pref_book";
    public static final String KEY_PREF_CHAPTER = "pref_chapter";
    public static final String KEY_PREF_CHUNK = "pref_chunk";
    public static final String KEY_PREF_FILENAME = "pref_filename";
    public static final String KEY_PREF_TAKE = "pref_take";
    public static final String KEY_PREF_CHUNK_VERSE = "pref_chunk_verse";
    public static final String KEY_PREF_VERSE = "pref_verse";

    MyAutoCompleteTextView setLangCode,setBookCode;

    /**
     * Request code for Android 5.0+
     */
    final int SET_SAVE_DIR = 21;

    /**
     * Request code for Android <5.0
     */
    final int SET_SAVE_DIR2 = 22;

    public static void updateFilename(Context c){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        String langCode = pref.getString(KEY_PREF_LANG, "en");
        String bookCode = pref.getString(KEY_PREF_BOOK, "mat");
        String chapter = formatDigit(pref.getString(KEY_PREF_CHAPTER, "1"));
        String chunk = formatDigit(pref.getString(KEY_PREF_CHUNK, "1"));
        String take = formatDigit(pref.getString(KEY_PREF_TAKE, "1"));
        String source = pref.getString(KEY_PREF_SOURCE, "udb");
        String verse = formatDigit(pref.getString(KEY_PREF_VERSE, "1"));
        String chunkOrVerse = pref.getString(KEY_PREF_CHUNK_VERSE, "chunk");
        String filename;
        if(chunkOrVerse.compareTo("chunk") == 0) {
            filename = langCode + "_" + source + "_" + bookCode + "_" + chapter + "-" + chunk + "_" + take;
        } else {
            filename = langCode + "_" + source + "_" + bookCode + "_" + chapter + "-" + verse + "_" + take;
        }
        pref.edit().putString(KEY_PREF_FILENAME, filename).commit();
    }

    public static void updateFilename(Context c, String lang, String src, String book,
                                      int chap, int chunk){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        pref.edit().putString(KEY_PREF_LANG, lang).commit();
        pref.edit().putString(KEY_PREF_SOURCE, src).commit();
        pref.edit().putString(KEY_PREF_BOOK, book).commit();
        pref.edit().putString(KEY_PREF_CHAPTER, String.valueOf(chap)).commit();
        String chunkOrVerse = pref.getString(KEY_PREF_CHUNK_VERSE, "chunk");
        if(chunkOrVerse.compareTo("chunk") == 0) {
            pref.edit().putString(KEY_PREF_CHUNK, String.valueOf(chunk)).commit();
        } else {
            pref.edit().putString(KEY_PREF_VERSE, String.valueOf(chunk)).commit();
        }
        updateFilename(c);
    }

    public static String formatDigit(String number){
        int value = Integer.parseInt(number);
        return String.format("%02d", value);
    }

    public static void incrementTake(Context c, int setTo){
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(c);
        pref.edit().putString(KEY_PREF_TAKE, String.valueOf(setTo)).commit();
        updateFilename(c);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        PreferenceManager.setDefaultValues(this, R.xml.preference, false);
    }

    public void resetPrefs() {

    }

    public void onBackPressed(View v) {
        Intent intent = new Intent(Settings.this, MainMenu.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }




}