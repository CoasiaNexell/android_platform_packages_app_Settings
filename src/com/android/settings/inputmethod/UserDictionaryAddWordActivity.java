/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.inputmethod;

import com.android.settings.R;
import com.android.settings.UserDictionarySettings;
import com.android.settings.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.UserDictionary;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

public class UserDictionaryAddWordActivity extends Activity
        implements AdapterView.OnItemSelectedListener {
    private static final int FREQUENCY_FOR_USER_DICTIONARY_ADDS = 250;

    private static final String STATE_KEY_IS_OPEN = "isOpen";

    public static final String MODE_EDIT_ACTION = "com.android.settings.USER_DICTIONARY_EDIT";
    public static final String MODE_INSERT_ACTION = "com.android.settings.USER_DICTIONARY_INSERT";

    private static final int[] IDS_SHOWN_ONLY_IN_MORE_OPTIONS_MODE = {
        R.id.user_dictionary_add_word_label,
        R.id.user_dictionary_add_shortcut_label,
        R.id.user_dictionary_add_locale_label,
        R.id.user_dictionary_settings_add_dialog_shortcut,
        R.id.user_dictionary_settings_add_dialog_locale,
    };

    private UserDictionaryAddWordContents mContents;
    private String mOldWord;

    private boolean mIsShowingMoreOptions = false;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_dictionary_add_word);
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final int mode;
        if (MODE_EDIT_ACTION.equals(action)) {
            mode = UserDictionaryAddWordContents.MODE_EDIT;
        } else if (MODE_INSERT_ACTION.equals(action)) {
            mode = UserDictionaryAddWordContents.MODE_INSERT;
        } else {
            // Can never come here because we only support these two actions in the manifest
            throw new RuntimeException("Unsupported action: " + action);
        }

        // The following will get the EXTRA_WORD and EXTRA_LOCALE fields that are in the intent.
        // We do need to add the action by hand, because UserDictionaryAddWordContents expects
        // it to be in the bundle, in the EXTRA_MODE key.
        final Bundle args = intent.getExtras();
        args.putInt(UserDictionaryAddWordContents.EXTRA_MODE, mode);

        if (null != savedInstanceState) {
            mIsShowingMoreOptions =
                    savedInstanceState.getBoolean(STATE_KEY_IS_OPEN, mIsShowingMoreOptions);
            // Override options if we have a saved state.
            args.putAll(savedInstanceState);
        }

        mOldWord = intent.getStringExtra(UserDictionaryAddWordContents.EXTRA_WORD);

        mContents = new UserDictionaryAddWordContents(getWindow().getDecorView(), args);

        if (mIsShowingMoreOptions) {
            onClickMoreOptions(findViewById(R.id.user_dictionary_settings_add_dialog_more_options));
        }

        // TODO: The following code enables layout transition for eye-candy, but there is still
        // a jankiness issue with the window moving on one frame, resizing suddenly on the next,
        // and animation only starting afterwards on children.
        final ViewGroup v = (ViewGroup)findViewById(R.id.user_dictionary_add_word_grid);
        final LayoutTransition transition = new LayoutTransition();
        transition.setStartDelay(LayoutTransition.APPEARING, 0);
        v.setLayoutTransition(transition);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putBoolean(STATE_KEY_IS_OPEN, mIsShowingMoreOptions);
        outState.putString(
                UserDictionaryAddWordContents.EXTRA_WORD, mContents.mEditText.getText().toString());
        outState.putString(UserDictionaryAddWordContents.EXTRA_LOCALE, mContents.mLocale);
    }

    public void onClickCancel(final View v) {
        finish();
    }

    public void onClickConfirm(final View v) {
        if (UserDictionaryAddWordContents.MODE_EDIT == mContents.mMode
                && !TextUtils.isEmpty(mOldWord)) {
            UserDictionarySettings.deleteWord(mOldWord, this.getContentResolver());
        }
        final String newWord = mContents.mEditText.getText().toString();
        if (TextUtils.isEmpty(newWord)) {
            // If the word is somehow empty, don't insert it.
            // TODO: grey out the Ok button when the text is empty?
            finish();
            return;
        }
        // Disallow duplicates.
        // TODO: Redefine the logic when we support shortcuts.
        UserDictionarySettings.deleteWord(newWord, this.getContentResolver());

        if (TextUtils.isEmpty(mContents.mLocale)) {
            // Empty string means insert for all languages.
            UserDictionary.Words.addWord(this, newWord.toString(),
                    FREQUENCY_FOR_USER_DICTIONARY_ADDS, UserDictionary.Words.LOCALE_TYPE_ALL);
        } else {
            // TODO: fix the framework so that it can accept a locale when we add a word
            // to the user dictionary instead of querying the system locale.
            final Locale prevLocale = Locale.getDefault();
            Locale.setDefault(Utils.createLocaleFromString(mContents.mLocale));
            UserDictionary.Words.addWord(this, newWord.toString(),
                    FREQUENCY_FOR_USER_DICTIONARY_ADDS, UserDictionary.Words.LOCALE_TYPE_CURRENT);
            Locale.setDefault(prevLocale);
        }
        finish();
    }

    private static class LocaleRenderer {
        private final String mLocaleString;
        private final String mDescription;
        // LocaleString may NOT be null.
        public LocaleRenderer(final Context context, final String localeString) {
            mLocaleString = localeString;
            if (null == localeString) {
                mDescription = context.getString(R.string.user_dict_settings_more_languages);
            } else if ("".equals(localeString)) {
                mDescription = context.getString(R.string.user_dict_settings_all_languages);
            } else {
                mDescription = Utils.createLocaleFromString(localeString).getDisplayName();
            }
        }
        @Override
        public String toString() {
            return mDescription;
        }
        public String getLocaleString() {
            return mLocaleString;
        }
    }

    private static void addLocaleDisplayNameToList(final Context context,
            final List<LocaleRenderer> list, final String locale) {
        if (null != locale) {
            list.add(new LocaleRenderer(context, locale));
        }
    }

    public void onClickMoreOptions(final View v) {
        for (final int idToShow : IDS_SHOWN_ONLY_IN_MORE_OPTIONS_MODE) {
            final View viewToShow = findViewById(idToShow);
            viewToShow.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.user_dictionary_settings_add_dialog_more_options)
                .setVisibility(View.GONE);
        findViewById(R.id.user_dictionary_settings_add_dialog_less_options)
                .setVisibility(View.VISIBLE);

        final Set<String> locales = UserDictionaryList.getUserDictionaryLocalesList(this);
        // Remove our locale if it's in, because we're always gonna put it at the top
        locales.remove(mContents.mLocale); // mLocale may not be null
        final String systemLocale = Locale.getDefault().toString();
        // The system locale should be inside. We want it at the 2nd spot.
        locales.remove(systemLocale); // system locale may not be null
        locales.remove(""); // Remove the empty string if it's there
        final ArrayList<LocaleRenderer> localesList = new ArrayList<LocaleRenderer>();
        // Add the passed locale, then the system locale at the top of the list. Add an
        // "all languages" entry at the bottom of the list.
        addLocaleDisplayNameToList(this, localesList, mContents.mLocale);
        if (!systemLocale.equals(mContents.mLocale)) {
            addLocaleDisplayNameToList(this, localesList, systemLocale);
        }
        for (final String l : locales) {
            // TODO: sort in unicode order
            addLocaleDisplayNameToList(this, localesList, l);
        }
        localesList.add(new LocaleRenderer(this, "")); // meaning: all languages
        localesList.add(new LocaleRenderer(this, null)); // meaning: select another locale
        final Spinner localeSpinner =
                (Spinner)findViewById(R.id.user_dictionary_settings_add_dialog_locale);
        final ArrayAdapter<LocaleRenderer> adapter = new ArrayAdapter<LocaleRenderer>(this,
                android.R.layout.simple_spinner_item, localesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        localeSpinner.setAdapter(adapter);
        localeSpinner.setOnItemSelectedListener(this);
        mIsShowingMoreOptions = true;
    }

    public void onClickLessOptions(final View v) {
        for (final int idToHide : IDS_SHOWN_ONLY_IN_MORE_OPTIONS_MODE) {
            final View viewToHide = findViewById(idToHide);
            viewToHide.setVisibility(View.GONE);
        }
        findViewById(R.id.user_dictionary_settings_add_dialog_more_options)
                .setVisibility(View.VISIBLE);
        findViewById(R.id.user_dictionary_settings_add_dialog_less_options)
                .setVisibility(View.GONE);
        mIsShowingMoreOptions = false;
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int pos,
            final long id) {
        final LocaleRenderer locale = (LocaleRenderer)parent.getItemAtPosition(pos);
        mContents.updateLocale(locale.getLocaleString());
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // I'm not sure we can come here, but if we do, that's the right thing to do.
        final Intent intent = getIntent();
        mContents.updateLocale(intent.getStringExtra(UserDictionaryAddWordContents.EXTRA_LOCALE));
    }
}