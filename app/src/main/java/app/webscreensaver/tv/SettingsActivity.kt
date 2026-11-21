package app.webscreensaver.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** Settings: URL list, delay, reload switch. Also opened from the system screensaver settings. */
class SettingsActivity : Activity() {

    private lateinit var store: SettingsStore
    private lateinit var urlsField: EditText
    private lateinit var delayField: EditText
    private lateinit var errorText: TextView
    private lateinit var statusText: TextView
    private lateinit var failureStore: FailureStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        store = SettingsStore(this)
        urlsField = findViewById(R.id.urls)
        delayField = findViewById(R.id.delay)
        errorText = findViewById(R.id.error)
        statusText = findViewById(R.id.status)
        failureStore = FailureStore(this)

        findViewById<TextView>(R.id.delay_label).text =
            getString(R.string.delay_label, Delay.MIN_MINUTES, Delay.MAX_MINUTES)

        if (savedInstanceState == null) {
            val settings = store.load()
            urlsField.setText(UrlList.serialize(settings.pages))
            delayField.setText(String.format(Locale.ROOT, "%d", settings.delayMinutes))
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

    override fun onResume() {
        super.onResume()
        showStatus()
    }

    /** Last load error per page; line numbers refer to the saved list. */
    private fun showStatus() {
        val rows = failureStore.load().statusFor(store.load().pages)
        statusText.text = if (rows.isEmpty()) {
            getString(R.string.status_none)
        } else {
            rows.joinToString("\n") { row ->
                val time = DateUtils.formatDateTime(
                    this,
                    row.failure.atMillis,
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                )
                getString(R.string.status_row, row.lineNumber, row.host, describe(row.failure), time)
            }
        }
    }

    private fun describe(failure: PageFailure): String = when (failure.kind) {
        FailureKind.CONNECTION -> getString(R.string.status_connection)
        FailureKind.HTTP -> getString(R.string.status_http, failure.httpStatus)
        FailureKind.CERTIFICATE -> getString(R.string.status_certificate)
        FailureKind.OTHER -> getString(R.string.status_other)
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
        store.save(SlideshowSettings(parsed.pages, delay!!))
        failureStore.retainOnly(parsed.pages)
        urlsField.setText(UrlList.serialize(parsed.pages))
        showStatus()
        return true
    }
}
