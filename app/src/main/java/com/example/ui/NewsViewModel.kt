package com.example.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import com.example.data.NewsDraft
import com.example.data.NewsDraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.regex.Pattern

data class LiveNews(
    val title: String,
    val summary: String,
    val date: String,
    val category: String,
    val url: String = "https://upm-millenium.com"
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "NewsViewModel"
    private val repository: NewsDraftRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NewsDraftRepository(database.newsDraftDao())
    }

    // Tab Navigation State (0: Dashboard, 1: Tulis Berita, 2: Portal Administrator)
    var currentTab by mutableIntStateOf(0)

    // Room Drafts observation
    val allDrafts: StateFlow<List<NewsDraft>> = repository.allDrafts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current Edit/Draft State
    var editorId by mutableStateOf<Int?>(null)
    var editorTitle by mutableStateOf("")
    var editorSubTitle by mutableStateOf("")
    var editorAuthor by mutableStateOf("")
    var editorContent by mutableStateOf("")
    var editorCategory by mutableStateOf("Berita Kampus")

    // UI Feedback State
    var toastMessage by mutableStateOf<String?>(null)

    // Gemini AI helper states
    var aiLoading by mutableStateOf(false)
    var aiResult by mutableStateOf<String?>(null)

    // UPM Millenium Live Portal feed states
    var liveNewsList by mutableStateOf<List<LiveNews>>(emptyList())
    var liveNewsLoading by mutableStateOf(false)
    var liveNewsError by mutableStateOf<String?>(null)

    // Core fallback content matching parsed content
    private val fallbackLiveNews = listOf(
        LiveNews(
            title = "Diduga Tangkap Aktivis Tanpa Prosedur, LBH Surabaya Ajukan Penangguhan",
            summary = "Upaya hukum dan pendampingan untuk aktivis petani Pakel terus diadvokasi.",
            date = "Terbaru",
            category = "Advokasi"
        ),
        LiveNews(
            title = "Lima Tahun Pendudukan: Momentum Refleksi Satu Abad Perjuangan Petani Pakel",
            summary = "Aksi solidaritas mengenang perjuangan agraria petani Banyuwangi.",
            date = "Terbaru",
            category = "Liputan Khusus"
        ),
        LiveNews(
            title = "Mahasiswa Gelar Aksi Demo Desak Pimpinan Kampus Realisasikan Tuntutan",
            summary = "Mahasiswa UIN KHAS Jember tumpah ruah di rektorat menuntut transparansi UKT dan sarana UKM.",
            date = "Terbaru",
            category = "Berita Kampus"
        ),
        LiveNews(
            title = "Gudang Elektronik Fakultas Syariah UIN KHAS Jember Terbakar",
            summary = "Peristiwa mengejutkan terjadi di gedung Syariah, diduga korsleting listrik merusak gudang.",
            date = "Terbaru",
            category = "Berita Kampus"
        ),
        LiveNews(
            title = "Ketika Eksistensi Lebih Penting dari Substansi",
            summary = "Sebuah tulisan opini tajam mengenai gaya hidup akademisi magang di media sosial.",
            date = "Terbaru",
            category = "Opini"
        ),
        LiveNews(
            title = "Ospek Kampus: Ajang Pengenalan Atau Ladang Pendoktrinan?",
            summary = "Analisis kritis pelaksanaan PBAK terhadap mahasiswa baru tahun ini.",
            date = "Terbaru",
            category = "Kritika"
        )
    )

    init {
        // Fetch news feed automatically on init
        fetchLiveNews()
    }

    /**
     * Attempts to fetch and parse headlines directly from UPM Millenium homepage,
     * fallback to high quality offline matches if network is disconnected.
     */
    fun fetchLiveNews() {
        viewModelScope.launch {
            liveNewsLoading = true
            liveNewsError = null
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://upm-millenium.com/")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) Mobile")
                    .build()

                val responseString = withContext(Dispatchers.IO) {
                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) response.body?.string() else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (responseString != null) {
                    // Quick and reliable regex extraction of news articles from HTML title markers
                    // Joomla typically uses <a href="..." itemprop="url"> Title </a> or standard headers
                    // We can match titles directly from typical joomla classes or fallback
                    val parsedList = mutableListOf<LiveNews>()
                    
                    // Simple pattern parsing for links containing readable Indonesian text
                    val regex = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>")
                    val matcher = regex.matcher(responseString)
                    
                    var count = 0
                    while (matcher.find() && count < 8) {
                        val path = matcher.group(1) ?: ""
                        val title = matcher.group(2)?.trim() ?: ""
                        
                        // Filter for typical interesting news headlines matching length and words
                        if (title.length > 25 && !title.contains("{" ) && !title.contains("<") && 
                            (title.contains("UIN") || title.contains("Mahasiswa") || title.contains("Aktivis") || 
                             title.contains("Petani") || title.contains("Kampus") || title.contains("Terbakar") ||
                             title.contains("Ospek") || title.contains("Eksistensi") || title.contains("Raih") ||
                             title.contains("Perunggu") || title.contains("Prosedur") || title.contains("LBH"))) {
                            
                            val cleanTitle = title.replace("&amp;", "&").replace("&#039;", "'")
                            parsedList.add(
                                LiveNews(
                                    title = cleanTitle,
                                    summary = "Klik untuk mengakses artikel lengkap di Portal UPM Millenium.",
                                    date = "Live Feed",
                                    category = "Berita Terbaru",
                                    url = if (path.startsWith("http")) path else "https://upm-millenium.com$path"
                                )
                            )
                            count++
                        }
                    }

                    if (parsedList.isNotEmpty()) {
                        liveNewsList = parsedList
                    } else {
                        // fallback to pre-parsed actual content
                        liveNewsList = fallbackLiveNews
                    }
                } else {
                    liveNewsList = fallbackLiveNews
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed live fetching", e)
                liveNewsList = fallbackLiveNews
            } finally {
                liveNewsLoading = false
            }
        }
    }

    /**
     * Saves the current text editor fields to local Room database.
     */
    fun saveDraft() {
        if (editorTitle.isBlank()) {
            showToast("Judul draf tidak boleh kosong!")
            return
        }

        viewModelScope.launch {
            val draft = NewsDraft(
                id = editorId ?: 0,
                title = editorTitle.trim(),
                subTitle = editorSubTitle.trim(),
                author = editorAuthor.trim(),
                content = editorContent,
                category = editorCategory,
                lastUpdated = System.currentTimeMillis()
            )
            val newId = repository.insert(draft)
            if (editorId == null) {
                editorId = newId.toInt()
                showToast("Draf baru berhasil ditambahkan!")
            } else {
                showToast("Draf berhasil diperbarui!")
            }
        }
    }

    /**
     * Clears the editor to write a fresh new draft.
     */
    fun createNewDraft() {
        editorId = null
        editorTitle = ""
        editorSubTitle = ""
        editorAuthor = ""
        editorContent = ""
        editorCategory = "Berita Kampus"
        aiResult = null
        showToast("Editor siap untuk draf baru!")
    }

    /**
     * Loads a draft from list into the workspace editor.
     */
    fun loadDraftToEditor(draft: NewsDraft) {
        editorId = draft.id
        editorTitle = draft.title
        editorSubTitle = draft.subTitle
        editorAuthor = draft.author
        editorContent = draft.content
        editorCategory = draft.category
        aiResult = null
        currentTab = 1 // Switch to write workspace
        showToast("Draf loaded: \"${draft.title}\"")
    }

    /**
     * Deletes a draft.
     */
    fun deleteDraft(draft: NewsDraft) {
        viewModelScope.launch {
            repository.delete(draft)
            if (editorId == draft.id) {
                createNewDraft()
            }
            showToast("Draf \"${draft.title}\" berhasil dihapus")
        }
    }

    /**
     * Invokes Gemini AI to perform professional journalistic edits/polishing on the editor content.
     * @param mode 1 = Format 5W+1H structure, 2 = Proofread KBBI/PUEBI, 3 = Title alternatives.
     */
    fun askGeminiAssistant(mode: Int) {
        val originalText = editorContent
        val originalTitle = editorTitle

        if (originalText.isBlank() && mode != 3) {
            showToast("Isi konten draf kosong. Tulis draf atau catatan kasar terlebih dahulu!")
            return
        }

        if (mode == 3 && originalTitle.isBlank() && originalText.isBlank()) {
            showToast("Judul atau konten kosong. Tulis gagasan berita untuk dirancang judulnya!")
            return
        }

        viewModelScope.launch {
            aiLoading = true
            aiResult = null

            val prompt = when (mode) {
                1 -> """
                    Anda adalah Editor Redaksi senior yang profesional di Pers Mahasiswa UPM Millenium. 
                    Tugas Anda adalah merapikan dan memformat catatan liputan kasar reporter di bawah ini menjadi berita yang memenuhi kaidah struktur jurnalistik yang baik:
                    - Gunakan struktur piramida terbalik (Lead/Teras yang kuat, data penting di atas).
                    - Pastikan memenuhi format 5W + 1H (What, Who, When, Where, Why, How).
                    - Gunakan bahasa berita yang objektif, mendalam, berbobot pers kampus, dan lugas.
                    
                    Catatan reporter:
                    "$originalText"
                    
                    Keluarkan HASIL FORMAT BERITA saja yang lengkap siap saji, jangan beri percakapan basa-basi.
                """.trimIndent()

                2 -> """
                    Anda adalah Editor Korektor Bahasa senior yang sangat teliti di Pers Mahasiswa UPM Millenium.
                    Tugas Anda adalah mengoreksi ejaan, tata bahasa, kesalahan ketik (typo), serta kesesuaian dengan KBBI (Kamus Besar Bahasa Indonesia) dan PUEBI (Pedoman Umum Ejaan Bahasa Indonesia) untuk draf berita di bawah ini:
                    - Buat kalimat lebih mengalir (readable), padat, dan efektif.
                    - Ubah kata-kata tidak baku atau slang kasual menjadi formal jurnalistik kecuali di dalam kuotasi narasumber.
                    - Perbaiki tanda baca dan huruf kapital yang salah.
                    
                    Konten draf berita:
                    "$originalText"
                    
                    Keluarkan HASIL KOREKSI BERITA saja tanpa basa-basi penerjemahan atau penjelasan perbaikan. Langsung berikan teks berita final hasil polesan Anda yang siap dipublikasikan.
                """.trimIndent()

                3 -> """
                    Anda adalah Ahli Penulisan Judul (Headline Strategist) di Pers Mahasiswa UPM Millenium.
                    Gaya pers kami adalah kritis, berbobot, berbasis fakta kampus, independen, dan menarik bagi mahasiswa dan akademisi.
                    Tugas Anda adalah menghasilkan 3 rekomendasi pilihan judul berita yang paling memikat, tajam, klik, dan sesuai dengan etika jurnalistik dari informasi draf di bawah ini.
                    
                    Judul saat ini: "$originalTitle"
                    Konten draf berita: "$originalText"
                    
                    Keluarkan hasilnya dalam bentuk daftar bernomor 1, 2, dan 3 yang berisi judul-judul tersebut, ditambahkan ulasan singkat 1 kalimat di bawah masing-masing judul tentang mengapa judul tersebut menarik. Tanpa basa-basi percakapan pembuka.
                """.trimIndent()

                else -> ""
            }

            val result = GeminiClient.generateContent(prompt)
            aiResult = result
            aiLoading = false

            if (result.startsWith("[ERROR]")) {
                showToast("AI Gagal: ${result.substringAfter("[ERROR] ")}")
            } else {
                showToast("Rekomendasi AI Redaksi berhasil dimuat!")
            }
        }
    }

    /**
     * Copies the Gemini response or formatted output into the main editor.
     */
    fun applyAiResultToEditor() {
        val result = aiResult
        if (result.isNullOrEmpty() || result.startsWith("[ERROR]")) {
            showToast("Tidak ada hasil AI yang valid untuk diterapkan.")
            return
        }
        editorContent = result
        aiResult = null
        showToast("Hasil edit AI berhasil dipindahkan ke Editor utama!")
    }

    private fun showToast(msg: String) {
        toastMessage = msg
    }
}
