package app.webscreensaver.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Settings: URL list, delay, reload switch. Also opened from the system screensaver settings. */
class SettingsActivity : Activity() {

    private lateinit var store: SettingsStore
    private lateinit var urlsField: EditText
    private lateinit var delayField: EditText
    private lateinit var reloadSwitch: Switch
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = SettingsStore(this)
        urlsField = findViewById(R.id.urls)
        delayField = findViewById(R.id.delay)
        reloadSwitch = findViewById(R.id.reload)
        errorText = findViewById(R.id.error)

        findViewById<TextView>(R.id.delay_label).text =
            getString(R.string.delay_label, Delay.MIN_MINUTES, Delay.MAX_MINUTES)

        if (savedInstanceState == null) {
            val settings = store.load()
            urlsField.setText(UrlList.serialize(settings.urls))
            delayField.setText(String.format(Locale.ROOT, "%d", settings.delayMinutes))
            reloadSwitch.isChecked = settings.reloadEachTime
        }

        findViewById<Button>(R.id.save).setOnClickListener {
            if (save()) Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.preview).setOnClickListener {
            if (save()) {
                startActivity(
                    Intent(this, SlideshowActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
        }
    }

    /** Validates all fields; saves only if everything is valid. */
    private fun save(): Boolean {
        val parsed = UrlList.parse(urlsField.text.toString())
        val delay = Delay.parse(delayField.text.toString())
        val errors = mutableListOf<String>()
        if (!parsed.isValid) {
            errors += buildString {
                append(getString(R.string.error_invalid_urls))
                parsed.invalidLines.forEach {
                    append('\n').append(getString(R.string.error_invalid_line, it.lineNumber, it.text))
                }
            }
        }
        if (delay == null) {
            errors += getString(R.string.error_delay, Delay.MIN_MINUTES, Delay.MAX_MINUTES)
        }
        if (errors.isNotEmpty()) {
            errorText.text = errors.joinToString("\n\n")
            errorText.visibility = View.VISIBLE
            return false
        }
        errorText.visibility = View.GONE
        store.save(SlideshowSettings(parsed.urls, delay!!, reloadSwitch.isChecked))
        urlsField.setText(UrlList.serialize(parsed.urls))
        return true
    }
}
