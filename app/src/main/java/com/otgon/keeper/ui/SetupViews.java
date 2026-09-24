package com.otgon.keeper.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.otgon.keeper.R;

import java.util.Locale;

/**
 * Builds the setup screen's views in code. The screen is fully state-driven and rebuilt on
 * every refresh, so this is simpler than inflating and binding XML layouts.
 */
final class SetupViews {

    private static final int INDENT_DP = 42;

    private final Context context;
    private final LinearLayout root;

    SetupViews(Context context, LinearLayout root) {
        this.context = context;
        this.root = root;
    }

    void header(int logoRes, CharSequence title) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(16), 0, dp(16));

        ImageView logo = new ImageView(context);
        logo.setImageResource(logoRes);
        row.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView name = text(title, 26, R.color.text, true);
        name.setPadding(dp(12), 0, 0, 0);
        row.addView(name);

        root.addView(row);
    }

    void statusCard(boolean ok, CharSequence heading, CharSequence body) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(ok ? R.color.ok_bg : R.color.warn_bg, 20));

        TextView icon = text(ok ? "✓" : "!", 22, R.color.card, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(oval(ok ? R.color.ok : R.color.warn));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout column = column();
        column.setPadding(dp(14), 0, 0, 0);
        column.addView(text(heading, 18, R.color.text, true));
        TextView bodyView = text(body, 14, R.color.text2, false);
        bodyView.setPadding(0, dp(2), 0, 0);
        column.addView(bodyView);
        card.addView(column, weighted());

        root.addView(card);
    }

    void section(CharSequence name, CharSequence trailing) {
        LinearLayout row = new LinearLayout(context);
        row.setPadding(dp(4), dp(24), dp(4), dp(8));
        String label = name.toString().toUpperCase(Locale.getDefault());
        row.addView(text(label, 12, R.color.text2, true), weighted());
        row.addView(text(trailing, 12, R.color.text2, false));
        root.addView(row);
    }

    /** Adds a step card and returns it so callers can append a guide or buttons. */
    Step step(int number, boolean done, CharSequence title, CharSequence subtitle) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(rounded(R.color.card, 16));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(done ? "✓" : String.valueOf(number), 14,
                done ? R.color.card : R.color.warn, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(oval(done ? R.color.ok : R.color.warn_bg));
        header.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout column = column();
        column.setPadding(dp(12), 0, dp(8), 0);
        column.addView(text(title, 16, R.color.text, true));
        TextView sub = text(subtitle, 13, R.color.text2, false);
        sub.setPadding(0, dp(2), 0, 0);
        column.addView(sub);
        header.addView(column, weighted());

        card.addView(header);
        root.addView(card, matchWidth(dp(10)));
        return new Step(card, header);
    }

    void footer(CharSequence note) {
        TextView view = text(note, 12, R.color.text2, false);
        view.setGravity(Gravity.CENTER);
        root.addView(view, matchWidth(dp(20)));
    }

    final class Step {
        private final LinearLayout card;
        private final LinearLayout header;
        private LinearLayout buttonRow;

        private Step(LinearLayout card, LinearLayout header) {
            this.card = card;
            this.header = header;
        }

        /** Places a control (e.g. a switch) at the end of the header row. */
        Step trailing(View view) {
            header.addView(view);
            return this;
        }

        Step guide(CharSequence... steps) {
            LinearLayout list = column();
            list.setPadding(dp(INDENT_DP), 0, 0, 0);
            for (int i = 0; i < steps.length; i++) {
                LinearLayout row = new LinearLayout(context);
                row.setPadding(0, dp(6), 0, 0);
                row.addView(text((i + 1) + ".", 14, R.color.accent, true),
                        new LinearLayout.LayoutParams(dp(22), -2));
                row.addView(text(steps[i], 14, R.color.text, false), weighted());
                list.addView(row);
            }
            card.addView(list, matchWidth(dp(8)));
            return this;
        }

        Step code(CharSequence command) {
            TextView view = text(command, 12, R.color.text, false);
            view.setTypeface(Typeface.MONOSPACE);
            view.setTextIsSelectable(true);
            view.setBackground(rounded(R.color.bg, 8));
            view.setPadding(dp(12), dp(10), dp(12), dp(10));
            card.addView(view, matchWidth(dp(12)));
            return this;
        }

        Step button(CharSequence label, boolean primary, View.OnClickListener onClick) {
            if (buttonRow == null) {
                buttonRow = new LinearLayout(context);
                LinearLayout.LayoutParams lp = matchWidth(dp(12));
                lp.leftMargin = dp(INDENT_DP);
                card.addView(buttonRow, lp);
            }
            TextView button = text(label, 14, primary ? R.color.card : R.color.accent, true);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(18), dp(10), dp(18), dp(10));
            GradientDrawable bg = rounded(primary ? R.color.accent : R.color.card, 22);
            if (!primary) bg.setStroke(dp(1), context.getColor(R.color.divider));
            button.setBackground(bg);
            button.setOnClickListener(onClick);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (buttonRow.getChildCount() > 0) lp.leftMargin = dp(8);
            buttonRow.addView(button, lp);
            return this;
        }
    }

    // ---- primitives ----

    private TextView text(CharSequence value, int sp, int colorRes, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(context.getColor(colorRes));
        if (medium) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable rounded(int colorRes, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(context.getColor(colorRes));
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable oval(int colorRes) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(context.getColor(colorRes));
        return d;
    }

    private static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private static LinearLayout.LayoutParams matchWidth(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
