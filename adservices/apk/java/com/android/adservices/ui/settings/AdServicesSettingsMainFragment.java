package com.android.adservices.ui.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import com.android.adservices.api.R;

/**
 * Fragment for the main view of the AdServices Settings App.
 */
public class AdServicesSettingsMainFragment extends PreferenceFragmentCompat {
  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.main_preferences, rootKey);
  }
}
