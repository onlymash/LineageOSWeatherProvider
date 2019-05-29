package onlymash.lineageos.weather

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

const val API_KEY = "api_key"
const val API_KEY_VERIFIED_STATE = "api_key_verified_state"
const val API_KEY_PENDING_VERIFICATION = 1


class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

        private var apiKeyPreference: EditTextPreference? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            val apiKeyVerificationState = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(API_KEY_VERIFIED_STATE, -1)
            val state = try {
                val stateEntries = resources.getStringArray(R.array.api_key_states_entries)
                stateEntries[apiKeyVerificationState]
            } catch (_: IndexOutOfBoundsException) {
                getString(R.string.prefscreen_api_key_summary, getString(R.string.app_name))
            }
            apiKeyPreference = findPreference(API_KEY)
            apiKeyPreference?.summary = state
        }

        override fun onResume() {
            super.onResume()
            context?.apply {
                val apiKey = PreferenceManager.getDefaultSharedPreferences(this).getString(API_KEY, null)
                if (apiKey.isNullOrEmpty()) {
                    Toast.makeText(context, getString(R.string.api_key_not_set_message, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
                }
            }
            apiKeyPreference?.onPreferenceChangeListener = this
        }

        override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
            return if (preference?.key == API_KEY) {
                context?.let {
                    PreferenceManager.getDefaultSharedPreferences(it).edit().putInt(API_KEY_VERIFIED_STATE, API_KEY_PENDING_VERIFICATION).apply()
                    apiKeyPreference?.summary = resources.getStringArray(R.array.api_key_states_entries)[API_KEY_PENDING_VERIFICATION]
                    Toast.makeText(it, R.string.api_key_changed_verification_warning, Toast.LENGTH_LONG).show()
                }
                true
            } else {
                false
            }
        }
    }
}