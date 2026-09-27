package com.shuhaibnc.mozhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline English–Malayalam dictionary. Data + logic adapted from
 *  https://github.com/ShuhaibNC/mozhi (enml.json -> prebuilt SQLite). */
public class MainActivity extends Activity {

    private EditText searchInput;
    private Button clearBtn;
    private LinearLayout stage;
    private LinearLayout searchBox;
    private FrameLayout content;
    private ListView suggestionsView;
    private ScrollView detailView;
    private TextView wordTitle;
    private LinearLayout meaningsBox;
    private TextView emptyView;
    private View loadingView;
    private TextView statusView;

    private SuggestAdapter adapter;
    private String currentQuery = "";
    private boolean showingDetail = false;
    private boolean dbReady = false;
    private boolean searchAtTop = false;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(ThemeHelper.wrapContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ThemeHelper.styleChrome(this);
        Fonts.applyAll(findViewById(R.id.root));

        searchInput = findViewById(R.id.search_input);
        clearBtn = findViewById(R.id.clear_btn);
        stage = findViewById(R.id.stage);
        searchBox = findViewById(R.id.search_box);
        content = findViewById(R.id.content);
        suggestionsView = findViewById(R.id.suggestions);
        detailView = findViewById(R.id.detail_view);
        wordTitle = findViewById(R.id.word_title);
        meaningsBox = findViewById(R.id.meanings_box);
        emptyView = findViewById(R.id.empty_view);
        loadingView = findViewById(R.id.loading_view);
        statusView = findViewById(R.id.status);

        adapter = new SuggestAdapter();
        suggestionsView.setAdapter(adapter);
        suggestionsView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view,
                                            int position, long id) {
                        selectWord(adapter.getItem(position));
                    }
                });

        searchInput.setEnabled(false);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                onQueryChanged(s.toString());
            }
        });
        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    if (adapter.getCount() > 0) {
                        selectWord(adapter.getItem(0));
                        hideKeyboard();
                    }
                    return true;
                }
                return false;
            }
        });

        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                searchInput.setText("");
            }
        });

        findViewById(R.id.theme_btn).setOnClickListener(
                new View.OnClickListener() {
                    @Override public void onClick(View v) { showThemeDialog(); }
                });
        findViewById(R.id.about_btn).setOnClickListener(
                new View.OnClickListener() {
                    @Override public void onClick(View v) { showAboutDialog(); }
                });

        // Keep the search bar centered if the stage height changes while idle
        // (e.g. rotation).
        stage.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b,
                    int ol, int ot, int or, int ob) {
                if (!searchAtTop && (b - t) != (ob - ot)) {
                    searchBox.animate().cancel();
                    searchBox.setTranslationY(idleOffset());
                }
            }
        });

        // Copy the prebuilt DB off the UI thread, then enable search.
        // During loading the search bar sits at the top with the spinner below.
        setSearchTop(true, false);
        loadingView.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DictDb.get(MainActivity.this).ensureReady();
                    final int count = DictDb.get(MainActivity.this).wordCount();
                    main.post(new Runnable() {
                        @Override public void run() {
                            dbReady = true;
                            loadingView.setVisibility(View.GONE);
                            searchInput.setEnabled(true);
                            statusView.setText(NumberFormat.getInstance(Locale.US)
                                    .format(count) + " words · offline");
                            // Settle back to the centered idle state.
                            if (searchInput.getText().toString().isEmpty()) {
                                setSearchTop(false, true);
                            }
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            emptyView.setVisibility(View.VISIBLE);
                            emptyView.setText("Could not load dictionary:\n" +
                                    e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /** Vertical offset that centers the search box inside the stage. */
    private float idleOffset() {
        if (stage.getHeight() == 0) return searchBox.getTranslationY();
        return Math.max(0f, (stage.getHeight() - searchBox.getHeight()) / 2f
                - searchBox.getTop());
    }

    /**
     * Slide the search bar between the centered idle position and the top.
     * Pure translation — the input itself never changes size.
     */
    private void setSearchTop(boolean top, boolean animate) {
        if (searchAtTop == top) return;
        searchAtTop = top;
        float target = top ? 0f : idleOffset();
        searchBox.animate().cancel();
        if (animate) {
            searchBox.animate().translationY(target).setDuration(280).start();
        } else {
            searchBox.setTranslationY(target);
        }
        content.animate().cancel();
        if (top) {
            content.setVisibility(View.VISIBLE);
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(200).start();
        } else {
            content.setVisibility(View.GONE);
            content.setAlpha(1f);
        }
    }

    private void onQueryChanged(String q) {
        currentQuery = q;
        clearBtn.setVisibility(q.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        showingDetail = false;
        detailView.setVisibility(View.GONE);
        setSearchTop(!q.trim().isEmpty(), true);
        if (!dbReady) return;
        if (q.trim().isEmpty()) {
            adapter.setWords(new ArrayList<String>());
            suggestionsView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            return;
        }
        List<String> words = DictDb.get(this).suggestions(q);
        adapter.setWords(words);
        if (words.isEmpty()) {
            suggestionsView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(R.string.no_matches);
        } else {
            emptyView.setVisibility(View.GONE);
            suggestionsView.setVisibility(View.VISIBLE);
        }
    }

    private void selectWord(String word) {
        List<String> meanings = DictDb.get(this).meanings(word);
        wordTitle.setText(word);
        meaningsBox.removeAllViews();
        int accent = ThemeHelper.accentColor(this);
        for (int i = 0; i < meanings.size(); i++) {
            meaningsBox.addView(meaningRow(i + 1, meanings.get(i), accent));
        }
        showingDetail = true;
        suggestionsView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        detailView.setVisibility(View.VISIBLE);
        detailView.scrollTo(0, 0);
    }

    private View meaningRow(int n, String meaning, int accent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        row.setLayoutParams(lp);

        TextView num = new TextView(this);
        num.setText(n + ".");
        num.setTextSize(16);
        num.setTypeface(Fonts.get(this, true));
        num.setTextColor(accent);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.rightMargin = dp(10);
        num.setLayoutParams(nlp);

        TextView body = new TextView(this);
        body.setText(meaning);
        body.setTextSize(17);
        body.setLineSpacing(0, 1.25f);
        body.setTextColor(getResources().getColor(R.color.on_bg));
        body.setTextIsSelectable(true);
        Fonts.apply(body);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(num);
        row.addView(body);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    /** Dialog theme matching the current app theme. */
    private int dialogTheme() {
        return ThemeHelper.isDark(this)
                ? R.style.Dialog_Mozhi_Dark : R.style.Dialog_Mozhi;
    }

    /** Apply Manjari to everything inside a shown dialog. */
    private void styleDialog(AlertDialog d) {
        if (d.getWindow() != null) {
            Fonts.applyAll(d.getWindow().getDecorView());
        }
    }

    private void showThemeDialog() {
        final String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)};
        int checked = ThemeHelper.getThemeMode(this);
        AlertDialog d = new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.theme)
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ThemeHelper.setThemeMode(MainActivity.this, which);
                                d.dismiss();
                                recreate();
                            }
                        })
                .show();
        styleDialog(d);
    }

    private void showAboutDialog() {
        AlertDialog d = new AlertDialog.Builder(this, dialogTheme())
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(R.string.close, null)
                .show();
        styleDialog(d);
    }

    @Override
    public void onBackPressed() {
        if (showingDetail) {
            // Back out of the word detail to the suggestion list.
            onQueryChanged(currentQuery);
            return;
        }
        super.onBackPressed();
    }

    /** Suggestion rows with the matched prefix highlighted, like the web UI. */
    private class SuggestAdapter extends BaseAdapter {
        private List<String> words = new ArrayList<>();

        void setWords(List<String> w) {
            words = w;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return words.size(); }
        @Override public String getItem(int p) { return words.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = new TextView(MainActivity.this);
                tv.setTextSize(17);
                int p = dp(14);
                tv.setPadding(dp(20), p, dp(20), p);
                Fonts.apply(tv);
            }
            String word = words.get(position);
            String q = currentQuery.trim().toLowerCase(Locale.US);
            SpannableString ss = new SpannableString(word);
            if (!q.isEmpty() && word.toLowerCase(Locale.US).startsWith(q)
                    && q.length() <= word.length()) {
                ss.setSpan(new StyleSpan(Typeface.BOLD), 0, q.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new ForegroundColorSpan(ThemeHelper.accentColor(
                                MainActivity.this)), 0, q.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tv.setText(ss);
            tv.setTextColor(getResources().getColor(R.color.on_bg));
            return tv;
        }
    }
}
