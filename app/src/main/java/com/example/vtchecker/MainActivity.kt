package com.example.vtchecker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var linearLayout: LinearLayout
    private lateinit var hashTextView: TextView
    private lateinit var copyHashButton: Button
    private lateinit var detectionTextView: TextView
    private lateinit var fullReportButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var selectFileButton: Button
    private lateinit var fileNameTextView: TextView
    
    private var currentFileHash: String = ""
    private var currentFileName: String = ""
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleFileUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUI()
        
        // Handle file share intent
        if (intent?.action == Intent.ACTION_SEND && intent.type != null) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                handleFileUri(uri)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            if (it.action == Intent.ACTION_SEND && it.type != null) {
                it.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                    handleFileUri(uri)
                }
            }
        }
    }

    private fun createUI() {
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
        }

        linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView.addView(linearLayout)
        setContentView(scrollView)

        // Title
        val titleText = TextView(this).apply {
            text = "VT File Checker"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setPadding(0, 0, 0, 32)
        }
        linearLayout.addView(titleText)

        // Select File Button
        selectFileButton = Button(this).apply {
            text = "Выбрать файл"
            setPadding(0, 0, 0, 32)
            setOnClickListener {
                filePickerLauncher.launch("*/*")
            }
        }
        linearLayout.addView(selectFileButton)

        // Progress Bar
        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 32)
        }
        linearLayout.addView(progressBar)

        // File name display
        fileNameTextView = TextView(this).apply {
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            setPadding(0, 0, 0, 16)
            visibility = View.GONE
        }
        linearLayout.addView(fileNameTextView)

        // Hash label
        val hashLabel = TextView(this).apply {
            text = "Хэш файла:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setPadding(0, 0, 0, 8)
        }
        linearLayout.addView(hashLabel)

        // Hash TextView with copy button container
        val hashContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        hashTextView = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            setPadding(8, 8, 8, 8)
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            isSingleLine = false
            maxLines = 1
        }

        copyHashButton = Button(this).apply {
            text = "Копировать"
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                copyHashToClipboard()
            }
        }

        hashContainer.addView(hashTextView)
        hashContainer.addView(copyHashButton)
        linearLayout.addView(hashContainer)

        // Detection section label
        val detectionLabel = TextView(this).apply {
            text = "Результаты проверки (Detection):"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setPadding(0, 32, 0, 16)
        }
        linearLayout.addView(detectionLabel)

        // Detection results TextView
        detectionTextView = TextView(this).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            setPadding(8, 8, 8, 8)
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            minLines = 10
        }
        linearLayout.addView(detectionTextView)

        // Full Report Button at the bottom
        fullReportButton = Button(this).apply {
            text = "Полный отчёт в браузере"
            setPadding(0, 32, 0, 32)
            setOnClickListener {
                openFullReport()
            }
            visibility = View.GONE
        }
        linearLayout.addView(fullReportButton)
    }

    private fun handleFileUri(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        selectFileButton.isEnabled = false
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Calculate file hash
                val fileHash = calculateFileHash(uri)
                currentFileHash = fileHash
                
                // Get file name
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            currentFileName = it.getString(nameIndex)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    hashTextView.text = fileHash
                    fileNameTextView.apply {
                        text = "Файл: $currentFileName"
                        visibility = View.VISIBLE
                    }
                }
                
                // Query VirusTotal API
                queryVirusTotal(fileHash)
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    progressBar.visibility = View.GONE
                    selectFileButton.isEnabled = true
                }
            }
        }
    }

    private fun calculateFileHash(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("Не удалось открыть файл")
        
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var bytesRead: Int
        
        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        } finally {
            inputStream.close()
        }
        
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun queryVirusTotal(hash: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get API key from BuildConfig
                val apiKey = BuildConfig.VIRUSTOTAL_API_KEY
                
                if (apiKey.isEmpty() || apiKey == "YOUR_VIRUSTOTAL_API_KEY") {
                    withContext(Dispatchers.Main) {
                        detectionTextView.text = "Ошибка: API ключ не настроен.\nПожалуйста, добавьте VIRUSTOTAL_API_KEY в настройки сборки."
                        progressBar.visibility = View.GONE
                        selectFileButton.isEnabled = true
                    }
                    return@launch
                }
                
                val client = OkHttpClient()
                
                val request = Request.Builder()
                    .url("https://www.virustotal.com/api/v3/files/$hash")
                    .addHeader("x-apikey", apiKey)
                    .build()
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                
                if (response.isSuccessful && responseBody != null) {
                    parseAndDisplayResults(responseBody)
                } else {
                    withContext(Dispatchers.Main) {
                        detectionTextView.text = "Файл не найден в базе VirusTotal или ошибка API.\nСтатус: ${response.code}"
                        fullReportButton.visibility = View.VISIBLE
                        progressBar.visibility = View.GONE
                        selectFileButton.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    detectionTextView.text = "Ошибка при запросе к VirusTotal: ${e.message}"
                    progressBar.visibility = View.GONE
                    selectFileButton.isEnabled = true
                }
            }
        }
    }

    private fun parseAndDisplayResults(jsonResponse: String) {
        try {
            val json = JSONObject(jsonResponse)
            val data = json.getJSONObject("data")
            val attributes = data.getJSONObject("attributes")
            val lastAnalysisStats = attributes.getJSONObject("last_analysis_stats")
            val lastAnalysisResults = attributes.getJSONObject("last_analysis_results")
            
            val malicious = lastAnalysisStats.optInt("malicious", 0)
            val suspicious = lastAnalysisStats.optInt("suspicious", 0)
            val undetected = lastAnalysisStats.optInt("undetected", 0)
            val total = malicious + suspicious + undetected
            
            val resultBuilder = StringBuilder()
            resultBuilder.append("Всего проверок: $total\n")
            resultBuilder.append("Обнаружено угроз: $malicious\n")
            resultBuilder.append("Подозрительных: $suspicious\n\n")
            resultBuilder.append("Детали (первые 20):\n")
            resultBuilder.append("-".repeat(40)).append("\n")
            
            var count = 0
            val keys = lastAnalysisResults.keys()
            while (keys.hasNext() && count < 20) {
                val engineName = keys.next()
                val engineResult = lastAnalysisResults.getJSONObject(engineName)
                val category = engineResult.optString("category", "unknown")
                val result = engineResult.optString("result", "N/A")
                
                if (category == "malicious" || category == "suspicious") {
                    resultBuilder.append("$engineName: $result\n")
                    count++
                }
            }
            
            if (count == 0) {
                resultBuilder.append("Угроз не обнаружено!")
            }
            
            withContext(Dispatchers.Main) {
                detectionTextView.text = resultBuilder.toString()
                fullReportButton.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                selectFileButton.isEnabled = true
            }
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                detectionTextView.text = "Ошибка парсинга ответа: ${e.message}"
                progressBar.visibility = View.GONE
                selectFileButton.isEnabled = true
            }
        }
    }

    private fun copyHashToClipboard() {
        if (currentFileHash.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("File Hash", currentFileHash)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Хэш скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFullReport() {
        if (currentFileHash.isNotEmpty()) {
            val url = "https://www.virustotal.com/gui/file/$currentFileHash/detection"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }
}
