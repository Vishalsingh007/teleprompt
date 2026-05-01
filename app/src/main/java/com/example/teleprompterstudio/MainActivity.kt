package com.example.teleprompterstudio

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.vosk.Model
import org.vosk.Recognizer

// =================================================================================================
// 1. ELITE DESIGN SYSTEM & DATA
// =================================================================================================

val DeepForestGreen = Color(0xFF0F2B1F)
val CardForestGreen = Color(0xFF163A2A)
val GoldenrodYellow = Color(0xFFD6AD4B)
val SaturatedCrimson = Color(0xFF9E2A2F)
val CharredBrown = Color(0xFF331A05)

val StageNearBlackGreen = Color(0xFF040B08)
val SoftWarmWhite = Color(0xFFF5E8C8)

val UltraThinBorder = 0.5.dp

object TpTheme {
    val fonts = FontFamily.Serif

    val studioColorScheme = darkColorScheme(
        primary = GoldenrodYellow,
        background = DeepForestGreen,
        surface = CardForestGreen,
        error = SaturatedCrimson,
        onBackground = SoftWarmWhite,
        onSurface = CharredBrown
    )

    val stageColorScheme = darkColorScheme(
        background = StageNearBlackGreen,
        onBackground = SoftWarmWhite,
        primary = SaturatedCrimson
    )
}

data class VocabWord(
    val word: String,
    val pronunciation: String,
    val partOfSpeech: String,
    val definition: String,
    val exampleSentence: String,
    val wrongDefinitions: List<String>,
    val category: String
)

val VOCAB_WORDS = listOf(
    VocabWord("Perspicacious", "per·spi·CA·cious", "adjective",
        "Having a ready insight; shrewd and discerning beyond ordinary perception",
        "A perspicacious speaker always understands their audience before uttering a word.",
        listOf("Speaking with unnecessary complexity", "Moving with graceful elegance", "Relating to royal ancestry"),
        "Intellectual"),
    VocabWord("Mellifluous", "mel·LIF·lu·ous", "adjective",
        "Sweet or musical in tone; smooth and pleasant to hear, like honey flowing",
        "Her mellifluous voice commanded the room without ever needing to rise above a whisper.",
        listOf("Filled with nervous anxiety", "Excessively dramatic in speech", "Relating to mathematical precision"),
        "Eloquent"),
    VocabWord("Magnanimous", "mag·NAN·i·mous", "adjective",
        "Generous and forgiving, especially toward a rival or less powerful person; noble of mind",
        "The magnanimous king pardoned his enemies after victory, earning greater loyalty than conquest ever could.",
        listOf("Extremely cautious and calculating", "Obsessed with personal appearance", "Stubbornly resistant to change"),
        "Royal"),
    VocabWord("Indefatigable", "in·de·FAT·i·ga·ble", "adjective",
        "Persisting tirelessly; incapable of being fatigued regardless of difficulty or duration",
        "Only an indefatigable orator could speak for three hours and leave the audience wanting more.",
        listOf("Easily distracted by minor details", "Dependent on external validation", "Prone to emotional outbursts"),
        "Powerful"),
    VocabWord("Eloquent", "EL·o·quent", "adjective",
        "Fluent and persuasive in speech; able to express ideas with clarity, power and beauty",
        "An eloquent argument does not shout — it guides the listener to your conclusion as though it were their own.",
        listOf("Technically precise but cold", "Overly dramatic and theatrical", "Speaking only in metaphors"),
        "Eloquent"),
    VocabWord("Sagacious", "sa·GA·cious", "adjective",
        "Having or showing keen mental discernment and good judgement; profoundly wise",
        "A sagacious communicator knows when silence speaks louder than any carefully chosen word.",
        listOf("Overly confident without evidence", "Highly emotional in reasoning", "Focused purely on aesthetics"),
        "Intellectual"),
    VocabWord("Loquacious", "lo·QUA·cious", "adjective",
        "Tending to talk a great deal; using more words than necessary, yet doing so with flair",
        "The loquacious host filled every silence with anecdote, never allowing discomfort to settle.",
        listOf("Refusing to speak publicly", "Speaking only in whispers", "Communicating through gesture alone"),
        "Eloquent"),
    VocabWord("Ebullient", "e·BUL·lient", "adjective",
        "Cheerful and full of energy; overflowing with enthusiasm and high spirits",
        "Her ebullient delivery transformed an ordinary script into a performance audiences remembered for years.",
        listOf("Calm and deeply reserved", "Methodically slow in thinking", "Pessimistic about outcomes"),
        "Powerful"),
    VocabWord("Sanguine", "SAN·guine", "adjective",
        "Optimistic, especially in a difficult situation; confidently positive about uncertain outcomes",
        "Remain sanguine in front of the camera — the audience mirrors whatever energy the speaker projects.",
        listOf("Deeply suspicious of others", "Overly cautious about risks", "Prone to self-doubt"),
        "Royal"),
    VocabWord("Erudite", "ER·u·dite", "adjective",
        "Having or showing great knowledge or learning acquired through extensive study and curiosity",
        "An erudite speaker earns authority not through volume but through the weight of what they know.",
        listOf("Knowledgeable only in one narrow field", "Skilled primarily with hands", "Experienced through action alone"),
        "Intellectual"),
    VocabWord("Fastidious", "fas·TID·i·ous", "adjective",
        "Very attentive to accuracy, detail and quality; difficult to please; meticulous to a fault",
        "Be fastidious about your script — every unnecessary word is dead weight your audience must carry.",
        listOf("Careless about personal appearance", "Easily satisfied with approximations", "Unwilling to review work"),
        "Royal"),
    VocabWord("Tenacious", "te·NA·cious", "adjective",
        "Tending to keep a firm hold; not easily discouraged; persistent with extraordinary grip",
        "A tenacious creator does not abandon their voice simply because the first ten videos underperformed.",
        listOf("Easily swayed by popular opinion", "Quick to abandon difficult tasks", "Flexible to the point of inconsistency"),
        "Powerful"),
    VocabWord("Vivacious", "vi·VA·cious", "adjective",
        "Attractively lively and animated; radiating vitality and a contagious enthusiasm for life",
        "A vivacious on-screen presence is not born — it is crafted through deliberate study of delivery.",
        listOf("Intensely quiet and contemplative", "Serious to the point of severity", "Focused exclusively on accuracy"),
        "Eloquent"),
    VocabWord("Lucid", "LU·cid", "adjective",
        "Expressed clearly and easily understood; mentally sharp and brilliantly articulate",
        "The most powerful speeches are not the most complex — they are the most lucid.",
        listOf("Highly complex and layered", "Deliberately ambiguous", "Open to multiple interpretations"),
        "Intellectual"),
    VocabWord("Imperious", "im·PE·ri·ous", "adjective",
        "Assuming power or authority without justification; having the bearing and confidence of royalty",
        "He entered the room with an imperious calm that made everyone present feel they were in an audience.",
        listOf("Timid and self-effacing", "Endlessly seeking approval", "Reluctant to take positions"),
        "Royal"),
    VocabWord("Articulate", "ar·TIC·u·late", "adjective",
        "Having or showing the ability to speak fluently and coherently; expressing ideas with precision",
        "To be articulate is to respect your audience enough to say exactly what you mean, no more, no less.",
        listOf("Speaking rapidly without pause", "Using technical language exclusively", "Relying on visual aids"),
        "Eloquent"),
    VocabWord("Resplendent", "re·SPLEN·dent", "adjective",
        "Attractive and impressive through being richly colourful or sumptuous; dazzling in appearance",
        "A resplendent vocabulary does not merely describe the world — it elevates it.",
        listOf("Quietly understated and minimal", "Deliberately plain and simple", "Blending into the background"),
        "Royal"),
    VocabWord("Venerable", "VEN·er·a·ble", "adjective",
        "Accorded a great deal of respect, especially because of age, wisdom, or character",
        "Speak like someone whose words will one day be considered venerable — because they just might be.",
        listOf("Young and untested", "Controversial and divisive", "Easily dismissed"),
        "Royal"),
    VocabWord("Formidable", "FOR·mi·da·ble", "adjective",
        "Inspiring fear or respect through being impressively large, powerful, intense or capable",
        "A formidable communicator does not raise their voice — they lower it, and the room goes silent.",
        listOf("Easily approachable and gentle", "Lacking in presence", "Soft-spoken to a fault"),
        "Powerful"),
    VocabWord("Gravitas", "GRAV·i·tas", "noun",
        "Dignity, seriousness, and solemnity of manner; the quality that makes people listen",
        "Gravitas cannot be performed — it accumulates through years of meaning every word you say.",
        listOf("Lightness and comedic ease", "Physical imposing stature only", "Speed of verbal delivery"),
        "Powerful"),
    VocabWord("Ineffable", "in·EF·fa·ble", "adjective",
        "Too great or extreme to be expressed or described in words",
        "The speaker left the audience in a state of ineffable awe.",
        listOf("Lacking any real substance", "Easily offended by minor things", "Quick to change opinions"),
        "Eloquent"),
    VocabWord("Grandiloquent", "gran·DIL·o·quent", "adjective",
        "Pompous or extravagant in language, style, or manner",
        "His grandiloquent speech sounded impressive but contained very few actual facts.",
        listOf("Speaking in a quiet, timid voice", "Refusing to use long words", "Focused entirely on logic"),
        "Eloquent"),
    VocabWord("Sovereign", "SOV·er·eign", "adjective",
        "Possessing supreme or ultimate power",
        "She walked onto the stage with a sovereign confidence that demanded absolute silence.",
        listOf("Yielding easily to pressure", "Concerned with trivial matters", "Incapable of making decisions"),
        "Royal"),
    VocabWord("Omnipotent", "om·NIP·o·tent", "adjective",
        "Having unlimited power; able to do anything",
        "To a beginner, a master orator can appear almost omnipotent in their control of the room.",
        listOf("Lacking the ability to speak", "Easily confused by questions", "Dependent on written notes"),
        "Powerful"),
    VocabWord("Didactic", "di·DAC·tic", "adjective",
        "Intended to teach, particularly in having moral instruction",
        "The best educational videos are engaging without being overly didactic.",
        listOf("Intended to confuse the listener", "Lacking any educational value", "Focused purely on entertainment"),
        "Intellectual"),
    VocabWord("Esoteric", "es·o·TER·ic", "adjective",
        "Intended for or likely to be understood by only a small number of people",
        "He translated esoteric academic research into stories that anyone could understand.",
        listOf("Understood by everyone instantly", "Highly emotional and reactive", "Physically imposing and loud"),
        "Intellectual"),
    VocabWord("August", "au·GUST", "adjective",
        "Respected and impressive",
        "The august panel of judges listened to the presentation with unbroken focus.",
        listOf("Youthful and highly inexperienced", "Quick to anger and shout", "Easily distracted and bored"),
        "Royal"),
    VocabWord("Inexorable", "in·EX·o·ra·ble", "adjective",
        "Impossible to stop or prevent",
        "The speaker built their argument with an inexorable logic that left no room for doubt.",
        listOf("Easily stopped or discouraged", "Moving slowly and hesitantly", "Frequently changing direction"),
        "Powerful"),
    VocabWord("Sonorous", "SON·o·rous", "adjective",
        "Imposingly deep and full in sound",
        "His sonorous voice carried to the back of the auditorium without a microphone.",
        listOf("High-pitched and squeaky", "Quiet and easily ignored", "Harsh and grating to the ear"),
        "Eloquent"),
    VocabWord("Pellucid", "pel·LU·cid", "adjective",
        "Translucently clear; easily understood",
        "She broke down the complex topic into pellucid explanations that delighted the viewers.",
        listOf("Murky, confusing, and dark", "Overly long and repetitive", "Filled with technical jargon"),
        "Intellectual"),
    VocabWord("Fervent", "FER·vent", "adjective",
        "Having or displaying a passionate intensity",
        "A fervent delivery will often cover up minor mistakes in the script itself.",
        listOf("Showing no emotion at all", "Coldly calculating and precise", "Easily distracted from the goal"),
        "Powerful"),
    VocabWord("Epiphany", "e·PIPH·a·ny", "noun",
        "An experience of a sudden and striking realization",
        "A great explainer video guides the viewer toward an epiphany rather than just listing facts.",
        listOf("A long, boring explanation", "A complete misunderstanding", "A physical feeling of fatigue"),
        "Intellectual"),
    VocabWord("Intrepid", "in·TREP·id", "adjective",
        "Resolute fearlessness, endurance, and fortitude",
        "The intrepid journalist asked the questions everyone else was too afraid to voice.",
        listOf("Deeply afraid of public speaking", "Easily intimidated by authority", "Cautious to a fault"),
        "Powerful"),
    VocabWord("Regal", "RE·gal", "adjective",
        "Of, resembling, or fit for a monarch, especially in being magnificent or dignified",
        "She maintained a regal posture even when faced with aggressive questioning.",
        listOf("Slouching and looking defeated", "Acting like a commoner", "Speaking with heavy slang"),
        "Royal"),
    VocabWord("Profound", "pro·FOUND", "adjective",
        "Very great or intense; having or showing great knowledge or insight",
        "Sometimes a ten-second pause can be more profound than ten minutes of talking.",
        listOf("Shallow and lacking depth", "Easily forgotten", "Physically lightweight"),
        "Intellectual")
)

@Composable
fun SymmetricTopBar(title: String, onBackClicked: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClicked != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GoldenrodYellow,
                modifier = Modifier
                    .clickable { onBackClicked() }
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title.uppercase(),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = GoldenrodYellow,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
        }

        if (onBackClicked == null) {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}

// =================================================================================================
// 2. MVI APPLICATION STATE & VOSK REPLICA ENGINE
// =================================================================================================

data class SpeakingProfile(
    val id: String,
    val name: String,
    val wpm: Int,
    val icon: ImageVector,
    val desc: String
)

sealed class TeleprompterMode {
    object Setup : TeleprompterMode()
    object Vocab : TeleprompterMode()
    object Reading : TeleprompterMode()
    object Replica : TeleprompterMode()
}

data class SyncedLine(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

enum class ReplicaPhase {
    IDLE, DOWNLOADING_MODEL, EXTRACTING_AUDIO, TRANSCRIBING, READY, ERROR
}

data class TpState(
    val mode: TeleprompterMode = TeleprompterMode.Setup,
    val scriptText: String = "",
    val availableProfiles: List<SpeakingProfile> = emptyList(),
    val selectedProfileId: String = "natural",
    val wpm: Int = 140,

    val scriptLines: List<String> = emptyList(),

    val replicaVideoUri: Uri? = null,
    val replicaPhase: ReplicaPhase = ReplicaPhase.IDLE,
    val engineProgress: Float = 0f,
    val statusMessage: String = "",
    val engineDetails: String = "",
    val syncedScript: List<SyncedLine> = emptyList(),
    val currentVideoTimeMs: Long = 0L,

    val isPlaying: Boolean = false,
    val isFocusMode: Boolean = false,
    val replayTrigger: Int = 0,

    val isCameraPermitted: Boolean = false,
    val showMonitor: Boolean = true,

    val currentVocabIndex: Int = 0,
    val vocabAnswered: Boolean = false,
    val vocabSelectedAnswer: String = "",
    val vocabShowingResult: Boolean = false,
    val transcriptionReadyDismissed: Boolean = false
)

sealed class TpIntent {
    data class UpdateText(val text: String) : TpIntent()
    data class SelectProfile(val profileId: String) : TpIntent()
    data class AdjustWpm(val delta: Int) : TpIntent()
    object ToggleCameraPermission : TpIntent()

    object StartReading : TpIntent()
    object StopReading : TpIntent()
    object TogglePlayPause : TpIntent()
    object ForcePause : TpIntent()

    object ToggleFocusMode : TpIntent()
    object ReplayVideo : TpIntent()

    data class ProcessReplicaVideo(val uri: Uri, val appFilesDir: File) : TpIntent()
    object StartReplicaMode : TpIntent()
    data class UpdateVideoTime(val timeMs: Long) : TpIntent()
    object ResetReplicaEngine : TpIntent()

    object NextVocabWord : TpIntent()
    data class AnswerVocabMCQ(val selectedAnswer: String) : TpIntent()
    object DismissTranscriptionReady : TpIntent()
}

class TpViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TpState(
        availableProfiles = listOf(
            SpeakingProfile("calm", "Turtle", 100, Icons.Default.Add, "Tutorials & Explainers"),
            SpeakingProfile("natural", "YouTube", 140, Icons.Default.PlayArrow, "Default pace"),
            SpeakingProfile("turbo", "Energetic", 180, Icons.Default.Star, "Rapid-fire delivery")
        )
    ))
    val state: StateFlow<TpState> = _state.asStateFlow()

    private val wordsPerLine = 5

    private val VOSK_MODEL_ZIP  = "vosk-model.zip"
    private val VOSK_MODEL_DIR  = "vosk-model-small-en-us-0.15"

    fun dispatch(intent: TpIntent) {
        when (intent) {
            is TpIntent.UpdateText -> _state.update { it.copy(scriptText = intent.text) }
            is TpIntent.SelectProfile -> handleProfileSelection(intent.profileId)
            is TpIntent.AdjustWpm -> {
                val newWpm = (_state.value.wpm + intent.delta).coerceIn(60, 300)
                _state.update { it.copy(wpm = newWpm) }
            }
            TpIntent.ToggleCameraPermission -> _state.update { it.copy(isCameraPermitted = !it.isCameraPermitted) }

            TpIntent.StartReading -> handleStartReading()
            TpIntent.StopReading -> handleStopReading()
            TpIntent.TogglePlayPause -> _state.update { it.copy(isPlaying = !it.isPlaying) }
            TpIntent.ForcePause -> _state.update { it.copy(isPlaying = false) }

            TpIntent.ToggleFocusMode -> _state.update { it.copy(isFocusMode = !it.isFocusMode) }
            TpIntent.ReplayVideo -> _state.update { it.copy(
                isPlaying = false,
                currentVideoTimeMs = 0L,
                replayTrigger = _state.value.replayTrigger + 1
            )}

            is TpIntent.ProcessReplicaVideo -> {
                _state.update { it.copy(mode = TeleprompterMode.Vocab) }
                runOfflineAI(intent.uri, intent.appFilesDir)
            }
            TpIntent.StartReplicaMode -> _state.update { it.copy(mode = TeleprompterMode.Replica, isPlaying = false) }
            is TpIntent.UpdateVideoTime -> _state.update { it.copy(currentVideoTimeMs = intent.timeMs) }
            TpIntent.ResetReplicaEngine -> _state.update {
                it.copy(
                    replicaPhase = ReplicaPhase.IDLE,
                    engineDetails = "",
                    mode = TeleprompterMode.Setup
                )
            }

            TpIntent.NextVocabWord -> _state.update {
                var nextIdx = VOCAB_WORDS.indices.random()
                while (nextIdx == it.currentVocabIndex && VOCAB_WORDS.size > 1) {
                    nextIdx = VOCAB_WORDS.indices.random()
                }
                it.copy(
                    currentVocabIndex = nextIdx,
                    vocabAnswered = false,
                    vocabSelectedAnswer = "",
                    vocabShowingResult = false
                )
            }
            is TpIntent.AnswerVocabMCQ -> _state.update {
                it.copy(
                    vocabSelectedAnswer = intent.selectedAnswer,
                    vocabAnswered = true,
                    vocabShowingResult = true
                )
            }
            TpIntent.DismissTranscriptionReady -> _state.update {
                it.copy(transcriptionReadyDismissed = true)
            }
        }
    }

    private fun handleProfileSelection(id: String) {
        val profile = _state.value.availableProfiles.find { it.id == id } ?: return
        _state.update { it.copy(selectedProfileId = id, wpm = profile.wpm) }
    }

    private fun handleStartReading() {
        val words = _state.value.scriptText.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val lines = words.chunked(wordsPerLine).map { it.joinToString(" ") }

        _state.update { it.copy(
            mode = TeleprompterMode.Reading,
            isPlaying = false,
            scriptLines = lines
        ) }
    }

    private fun handleStopReading() {
        _state.update { it.copy(mode = TeleprompterMode.Setup, isPlaying = false) }
    }

    // =================================================================================================
    // ELITE ON-DEVICE VOSK ENGINE (BUNDLED ASSETS VERSION)
    // =================================================================================================

    private fun runOfflineAI(videoUri: Uri, filesDir: File) {
        _state.update { it.copy(
            replicaVideoUri = videoUri,
            engineDetails = "",
            currentVocabIndex = VOCAB_WORDS.indices.random(),
            vocabAnswered = false,
            vocabSelectedAnswer = "",
            vocabShowingResult = false,
            transcriptionReadyDismissed = false
        )}

        viewModelScope.launch(Dispatchers.IO) {

            // ── GUARD: script must exist for energy-align fallback ──
            val script = _state.value.scriptText.trim()

            // ── STAGE 1: Unpack Vosk model from assets if not present ──────────────
            val modelDir = File(filesDir, VOSK_MODEL_DIR)
            if (!modelDir.exists() || modelDir.listFiles().isNullOrEmpty()) {
                _state.update {
                    it.copy(
                        replicaPhase = ReplicaPhase.DOWNLOADING_MODEL,
                        engineProgress = 0f,
                        statusMessage = "Unpacking Speech Model…",
                        engineDetails = "First time only · ~40 MB"
                    )
                }

                try {
                    _state.update { it.copy(engineDetails = "Unpacking bundled model…", engineProgress = 0.5f) }
                    ZipInputStream(app.assets.open(VOSK_MODEL_ZIP)).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val outFile = File(filesDir, entry.name)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }

                } catch (e: Exception) {
                    if (script.isBlank()) {
                        _state.update {
                            it.copy(
                                replicaPhase = ReplicaPhase.ERROR,
                                statusMessage = "Missing Model Asset and no script found.",
                                engineDetails = "Make sure vosk-model.zip is bundled in the assets folder, or paste a script for energy sync."
                            )
                        }
                        return@launch
                    }
                }
            }

            // ── STAGE 2: Extract audio ────────────────────────────────────
            _state.update {
                it.copy(
                    replicaPhase = ReplicaPhase.EXTRACTING_AUDIO,
                    engineProgress = 0f,
                    statusMessage = "Extracting Audio…",
                    engineDetails = "Reading media format"
                )
            }

            val audioResult = extractAudioPcm(videoUri)
            if (audioResult == null) {
                _state.update {
                    it.copy(
                        replicaPhase = ReplicaPhase.ERROR,
                        statusMessage = "Could not read audio.",
                        engineDetails = "Try an MP4 file with AAC audio."
                    )
                }
                return@launch
            }
            val (pcm16k, durationSec) = audioResult

            // ── STAGE 3: Transcribe ───────────────────────────────────────
            _state.update {
                it.copy(
                    replicaPhase = ReplicaPhase.TRANSCRIBING,
                    engineProgress = 0f,
                    statusMessage = "Transcribing Speech…",
                    engineDetails = "Running on-device AI"
                )
            }

            val modelDir2 = File(filesDir, VOSK_MODEL_DIR)
            val syncedLines: List<SyncedLine>

            if (modelDir2.exists() && !modelDir2.listFiles().isNullOrEmpty()) {
                // PATH A: Real Vosk word timestamps
                syncedLines = runVoskTranscription(pcm16k, durationSec, modelDir2.absolutePath)
            } else {
                // PATH B: Energy alignment fallback
                if (script.isBlank()) {
                    _state.update {
                        it.copy(
                            replicaPhase = ReplicaPhase.ERROR,
                            statusMessage = "No model and no script.",
                            engineDetails = "Bundle the speech model, or paste your script to use audio energy sync."
                        )
                    }
                    return@launch
                }
                val scriptWords = script.split("\\s+".toRegex()).filter { it.isNotBlank() }
                syncedLines = groupWordsIntoChunks(
                    energyAlign(
                        FloatArray(pcm16k.size) { pcm16k[it] / 32768f },
                        16000, durationSec, scriptWords
                    ),
                    chunkSize = 5
                )
            }

            // ── DONE ──────────────────────────────────────────────────────
            withContext(Dispatchers.Main) {
                val newScript = if (_state.value.scriptText.isBlank() && syncedLines.isNotEmpty()) {
                    syncedLines.joinToString(" ") { it.text }
                } else {
                    _state.value.scriptText
                }

                _state.update {
                    it.copy(
                        replicaPhase = ReplicaPhase.READY,
                        engineProgress = 1f,
                        syncedScript = syncedLines,
                        scriptText = newScript,
                        statusMessage = "Sync Ready",
                        engineDetails = "${syncedLines.size} words aligned across ${durationSec.toInt()}s",
                        transcriptionReadyDismissed = false
                    )
                }
            }
        }
    }

    private fun extractAudioPcm(videoUri: Uri): Pair<ShortArray, Float>? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(app, videoUri, null)

            var audioIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIndex = i; audioFormat = fmt; break
                }
            }
            if (audioIndex < 0 || audioFormat == null) return null

            extractor.selectTrack(audioIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION))
                audioFormat.getLong(MediaFormat.KEY_DURATION) else 0L
            val durationSec = durationUs / 1_000_000f
            val origRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            _state.update { it.copy(engineDetails = "Decoding ${durationSec.toInt()}s of audio") }

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val shorts = mutableListOf<Short>()
            var inputDone = false; var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000L)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(idx, 0, sz, extractor.sampleTime, 0)
                            if (durationUs > 0) {
                                val pct = extractor.sampleTime.toFloat() / durationUs
                                _state.update {
                                    it.copy(
                                        engineProgress = pct * 0.5f,
                                        engineDetails = "Decoded ${(extractor.sampleTime / 1_000_000f).toInt()}s / ${durationSec.toInt()}s"
                                    )
                                }
                            }
                            extractor.advance()
                        }
                    }
                }
                val idx = codec.dequeueOutputBuffer(info, 10_000L)
                if (idx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    val buf = codec.getOutputBuffer(idx)!!.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val chunk = ShortArray(buf.remaining() / 2)
                    buf.asShortBuffer().get(chunk)
                    chunk.forEach { shorts.add(it) }
                    codec.releaseOutputBuffer(idx, false)
                }
            }
            codec.stop(); codec.release()

            var raw = shorts.toShortArray()
            if (channels == 2) {
                raw = ShortArray(raw.size / 2) { i ->
                    ((raw[i * 2].toInt() + raw[i * 2 + 1].toInt()) / 2).toShort()
                }
            }

            val target = 16_000
            val resampled = if (origRate != target) {
                val ratio = origRate.toDouble() / target
                ShortArray((raw.size / ratio).toInt()) { i ->
                    val pos = i * ratio
                    val lo = pos.toInt().coerceIn(0, raw.size - 1)
                    val hi = (lo + 1).coerceIn(0, raw.size - 1)
                    val frac = (pos - lo).toFloat()
                    (raw[lo] + (raw[hi] - raw[lo]) * frac).toInt().toShort()
                }
            } else raw

            _state.update { it.copy(engineProgress = 0.5f, engineDetails = "Audio ready — starting transcription") }
            Pair(resampled, durationSec)

        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun runVoskTranscription(
        pcm: ShortArray,
        durationSec: Float,
        modelPath: String
    ): List<SyncedLine> {
        return try {
            val model = Model(modelPath)
            val recognizer = Recognizer(model, 16000.0f)
            recognizer.setWords(true)
            val chunkSize = 4096
            var processed = 0
            while (processed < pcm.size) {
                val end = min(processed + chunkSize, pcm.size)
                val chunk = pcm.copyOfRange(processed, end)
                recognizer.acceptWaveForm(chunk, chunk.size)
                processed = end
                val pct = processed.toFloat() / pcm.size
                _state.update { it.copy(
                    engineProgress = 0.5f + pct * 0.5f,
                    engineDetails = "Transcribing… ${(pct * 100).toInt()}%"
                )}
            }
            val finalJson = recognizer.finalResult
            recognizer.close()
            model.close()
            parseVoskResult(finalJson)
        } catch (e: Exception) {
            val script = _state.value.scriptText.trim()
            val scriptWords = script.split("\\s+".toRegex()).filter { it.isNotBlank() }
            groupWordsIntoChunks(
                energyAlign(
                    FloatArray(pcm.size) { pcm[it] / 32768f },
                    16000, durationSec, scriptWords
                ),
                chunkSize = 5
            )
        }
    }

    private fun parseVoskResult(json: String): List<SyncedLine> {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("result") ?: return emptyList()
            val wordList = (0 until arr.length()).map { i ->
                val word = arr.getJSONObject(i)
                SyncedLine(
                    text        = word.getString("word").trim(),
                    startTimeMs = (word.getDouble("start") * 1000).toLong(),
                    endTimeMs   = (word.getDouble("end")   * 1000).toLong()
                )
            }
            groupWordsIntoChunks(wordList, chunkSize = 5)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun groupWordsIntoChunks(words: List<SyncedLine>, chunkSize: Int = 5): List<SyncedLine> {
        if (words.isEmpty()) return emptyList()
        val chunks = words.chunked(chunkSize).map { chunk ->
            SyncedLine(
                text       = chunk.joinToString(" ") { it.text },
                startTimeMs = chunk.first().startTimeMs,
                endTimeMs   = chunk.last().endTimeMs
            )
        }
        return chunks.mapIndexed { i, chunk ->
            val nextStart = chunks.getOrNull(i + 1)?.startTimeMs
            chunk.copy(endTimeMs = nextStart ?: (chunk.endTimeMs + 2000L))
        }
    }

    private fun energyAlign(audioData: FloatArray, sampleRate: Int, totalDur: Float, scriptWords: List<String>): List<SyncedLine> {
        val FRAME = 0.025f
        val fSamp = (sampleRate * FRAME).toInt()
        if (fSamp <= 0) return emptyList()
        val nFrames = audioData.size / fSamp
        val rms = FloatArray(nFrames)
        for (i in 0 until nFrames) {
            var s = 0f
            val off = i * fSamp
            for (j in 0 until fSamp) {
                val sample = audioData[off + j]
                s += sample * sample
            }
            rms[i] = sqrt(s / fSamp)
        }
        val W = kotlin.math.ceil(0.06f / FRAME).toInt()
        val smooth = FloatArray(nFrames)
        for (i in 0 until nFrames) {
            var s = 0f
            var c = 0
            val startK = max(0, i - W)
            val endK = min(nFrames - 1, i + W)
            for (k in startK..endK) {
                s += rms[k]
                c++
            }
            smooth[i] = s / c
        }
        var maxR = 0f
        for (mi in smooth.indices) {
            if (smooth[mi] > maxR) maxR = smooth[mi]
        }
        val thresh = maxR * 0.10f
        val GAP = kotlin.math.ceil(0.15f / FRAME).toInt()
        val voicedTimes = mutableListOf<Float>()
        var lastVoiced = -999
        for (i in 0 until nFrames) {
            if (smooth[i] > thresh) {
                if (i - lastVoiced > GAP && lastVoiced > 0) {
                    for (g in (lastVoiced + 1) until i) {
                        voicedTimes.add(g * FRAME)
                    }
                }
                voicedTimes.add(i * FRAME)
                lastVoiced = i
            }
        }
        val nWords = if (scriptWords.isNotEmpty()) scriptWords.size else 1
        if (voicedTimes.size < nWords) {
            return scriptWords.mapIndexed { i, w ->
                val start = (i.toFloat() / nWords) * totalDur
                val end = ((i + 1).toFloat() / nWords) * totalDur
                SyncedLine(w, (start * 1000).toLong(), (end * 1000).toLong())
            }
        }
        return scriptWords.mapIndexed { i, w ->
            val idx = (i * voicedTimes.size) / nWords
            val t = voicedTimes[min(idx, voicedTimes.size - 1)]
            val endIdx = min(idx + (voicedTimes.size / nWords), voicedTimes.size - 1)
            val t2 = voicedTimes.getOrNull(endIdx) ?: min(t + 0.4f, totalDur)
            SyncedLine(w, (t * 1000).toLong(), (t2 * 1000).toLong())
        }
    }
}

// =================================================================================================
// 3. UI ARCHITECTURE
// =================================================================================================

@Composable
fun PrimaryCrimsonButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SaturatedCrimson,
            disabledContainerColor = SaturatedCrimson.copy(alpha = 0.3f),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontFamily = TpTheme.fonts,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.05.sp
        )
    }
}

@Composable
fun SetupScreen(state: TpState, viewModel: TpViewModel) {
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.dispatch(TpIntent.ProcessReplicaVideo(it, context.filesDir)) }
    }

    MaterialTheme(colorScheme = TpTheme.studioColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepForestGreen)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
            ) {
                item {
                    SymmetricTopBar(title = "Teleprompter Studio")
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(6.dp)).border(BorderStroke(UltraThinBorder, SaturatedCrimson.copy(alpha = 0.5f))).background(CardForestGreen).padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "OFFLINE VIDEO REPLICA", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SaturatedCrimson)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = SaturatedCrimson, modifier = Modifier.size(16.dp))
                        }

                        Text(text = "Upload a video of you reading your script. The app unpacks a bundled 40MB speech model once, then transcribes your video on-device with exact word timestamps — no API key, no cost, works offline.", fontSize = 12.sp, color = SoftWarmWhite.copy(alpha = 0.8f), fontFamily = TpTheme.fonts, modifier = Modifier.padding(vertical = 12.dp))

                        OutlinedButton(
                            onClick = { videoPickerLauncher.launch("video/*") },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            border = BorderStroke(UltraThinBorder, SaturatedCrimson),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SaturatedCrimson),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Select Video to Sync", fontSize = 13.sp, fontFamily = TpTheme.fonts, fontWeight = FontWeight.SemiBold)
                        }

                        if (state.replicaPhase == ReplicaPhase.ERROR) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF3D1E1E))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Error", tint = SaturatedCrimson, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Engine Error", color = SaturatedCrimson, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text(text = state.statusMessage, color = SoftWarmWhite, fontFamily = TpTheme.fonts, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))

                                if (state.engineDetails.isNotBlank()) {
                                    Text(
                                        text = state.engineDetails,
                                        color = SoftWarmWhite.copy(alpha = 0.6f),
                                        fontFamily = TpTheme.fonts,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(text = "MANUAL SCRIPT".uppercase(), fontSize = 11.sp, color = GoldenrodYellow.copy(alpha = 0.7f), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.scriptText,
                        onValueChange = { viewModel.dispatch(TpIntent.UpdateText(it)) },
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 20.dp),
                        placeholder = { Text("Or paste your speech here...", color = SoftWarmWhite.copy(alpha = 0.4f), fontFamily = TpTheme.fonts) },
                        textStyle = LocalTextStyle.current.copy(color = SoftWarmWhite, fontFamily = TpTheme.fonts, fontSize = 15.sp, lineHeight = 22.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = GoldenrodYellow.copy(alpha = 0.3f),
                            focusedBorderColor = GoldenrodYellow,
                            cursorColor = GoldenrodYellow
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(6.dp)).border(BorderStroke(UltraThinBorder, GoldenrodYellow.copy(alpha = 0.2f))).background(CardForestGreen).clickable { viewModel.dispatch(TpIntent.ToggleCameraPermission) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Picture-in-Picture Monitor", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GoldenrodYellow)
                            Text(text = "Record yourself while reading to check your framing and performance.", fontSize = 11.sp, color = SoftWarmWhite.copy(alpha = 0.6f), fontFamily = TpTheme.fonts, modifier = Modifier.padding(top = 4.dp))
                        }
                        Switch(
                            checked = state.isCameraPermitted,
                            onCheckedChange = { viewModel.dispatch(TpIntent.ToggleCameraPermission) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepForestGreen,
                                checkedTrackColor = GoldenrodYellow,
                                uncheckedThumbColor = SoftWarmWhite,
                                uncheckedTrackColor = CardForestGreen
                            )
                        )
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                        PrimaryCrimsonButton(text = "Start Manual Teleprompter →", onClick = { viewModel.dispatch(TpIntent.StartReading) }, enabled = state.scriptText.isNotBlank())
                    }
                }
            }
        }
    }
}

@Composable
fun VocabLoadingScreen(state: TpState, viewModel: TpViewModel) {
    val vocabWord = VOCAB_WORDS[state.currentVocabIndex % VOCAB_WORDS.size]

    val mcqOptions = remember(state.currentVocabIndex) {
        (listOf(vocabWord.definition) + vocabWord.wrongDefinitions).shuffled()
    }

    val categoryColor = when (vocabWord.category) {
        "Royal"        -> Color(0xFFD6AD4B)
        "Intellectual" -> Color(0xFF6B9EC7)
        "Eloquent"     -> Color(0xFF7BC47F)
        "Powerful"     -> Color(0xFF9E2A2F)
        else           -> SoftWarmWhite
    }

    // Beautiful step-down sizing that preserves the massive 48sp font for most words
    // but gracefully shrinks just enough for the long ones without wrapping.
    val wordLength = vocabWord.word.length
    val vocabFontSize = when {
        wordLength >= 13 -> 28.sp
        wordLength >= 10 -> 32.sp
        wordLength >= 8  -> 40.sp
        else             -> 48.sp
    }
    val pronunFontSize = 14.sp

    MaterialTheme(colorScheme = TpTheme.studioColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepForestGreen)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "✕ Cancel",
                        color = SoftWarmWhite.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { viewModel.dispatch(TpIntent.ResetReplicaEngine) }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }

                if (state.replicaPhase != ReplicaPhase.READY && state.replicaPhase != ReplicaPhase.ERROR) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardForestGreen)
                            .border(BorderStroke(UltraThinBorder, GoldenrodYellow.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GoldenrodYellow.copy(alpha = alpha)))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Transcribing your video… solve a word while you wait",
                                    color = GoldenrodYellow,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = state.statusMessage,
                                color = SoftWarmWhite.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontFamily = TpTheme.fonts
                            )
                            Text(
                                text = "${(state.engineProgress * 100).toInt()}%",
                                color = SoftWarmWhite.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.engineProgress.coerceIn(0f, 1f) },
                            color = SaturatedCrimson,
                            trackColor = DeepForestGreen,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    BorderStroke(UltraThinBorder, categoryColor.copy(alpha = 0.4f)),
                                    RoundedCornerShape(12.dp)
                                )
                                .background(CardForestGreen)
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(categoryColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = vocabWord.category.uppercase(),
                                        color = categoryColor,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = vocabWord.partOfSpeech,
                                    color = SoftWarmWhite.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    fontFamily = TpTheme.fonts,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = vocabWord.word,
                                color = SoftWarmWhite,
                                fontFamily = TpTheme.fonts,
                                fontWeight = FontWeight.Bold,
                                fontSize = vocabFontSize,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1
                            )

                            Text(
                                text = vocabWord.pronunciation,
                                color = categoryColor.copy(alpha = 0.8f),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = pronunFontSize,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    BorderStroke(UltraThinBorder, SoftWarmWhite.copy(alpha = 0.08f)),
                                    RoundedCornerShape(12.dp)
                                )
                                .background(CardForestGreen)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "What does this word mean?",
                                color = SoftWarmWhite.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )

                            mcqOptions.forEachIndexed { idx, option ->
                                val isSelected = state.vocabSelectedAnswer == option
                                val isCorrect  = option == vocabWord.definition
                                val bgColor = when {
                                    !state.vocabAnswered                  -> CardForestGreen
                                    isSelected && isCorrect               -> Color(0xFF1E3D2A)
                                    isSelected && !isCorrect              -> Color(0xFF3D1E1E)
                                    !isSelected && isCorrect && state.vocabAnswered -> Color(0xFF1E3D2A)
                                    else                                  -> CardForestGreen
                                }
                                val borderColor = when {
                                    !state.vocabAnswered -> SoftWarmWhite.copy(alpha = 0.12f)
                                    isSelected && isCorrect  -> Color(0xFF7BC47F)
                                    isSelected && !isCorrect -> SaturatedCrimson
                                    !isSelected && isCorrect && state.vocabAnswered -> Color(0xFF7BC47F).copy(alpha = 0.5f)
                                    else -> SoftWarmWhite.copy(alpha = 0.06f)
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (idx < 3) 8.dp else 0.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            BorderStroke(UltraThinBorder, borderColor),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .background(bgColor)
                                        .clickable(enabled = !state.vocabAnswered) {
                                            viewModel.dispatch(TpIntent.AnswerVocabMCQ(option))
                                        }
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = option,
                                        color = when {
                                            !state.vocabAnswered -> SoftWarmWhite.copy(alpha = 0.85f)
                                            isSelected && isCorrect  -> Color(0xFF7BC47F)
                                            isSelected && !isCorrect -> SaturatedCrimson
                                            !isSelected && isCorrect && state.vocabAnswered -> Color(0xFF7BC47F).copy(alpha = 0.7f)
                                            else -> SoftWarmWhite.copy(alpha = 0.3f)
                                        },
                                        fontSize = 13.sp,
                                        fontFamily = TpTheme.fonts,
                                        lineHeight = 19.sp
                                    )
                                }
                            }

                            if (state.vocabAnswered) {
                                val isCorrect = state.vocabSelectedAnswer == vocabWord.definition
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isCorrect) "✓ Correct! Well done." else "✗ Not quite — the correct answer is highlighted above.",
                                    color = if (isCorrect) Color(0xFF7BC47F) else SaturatedCrimson.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    BorderStroke(UltraThinBorder, categoryColor.copy(alpha = 0.2f)),
                                    RoundedCornerShape(12.dp)
                                )
                                .background(CardForestGreen)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "USED IN SPEECH",
                                color = categoryColor.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "\"${vocabWord.exampleSentence}\"",
                                color = SoftWarmWhite.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                fontFamily = TpTheme.fonts,
                                lineHeight = 22.sp,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.dispatch(TpIntent.NextVocabWord) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CardForestGreen,
                                contentColor = GoldenrodYellow
                            ),
                            border = BorderStroke(UltraThinBorder, GoldenrodYellow.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Next Word", fontFamily = TpTheme.fonts, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }

            if (state.replicaPhase == ReplicaPhase.READY) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, DeepForestGreen, DeepForestGreen)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                ) {
                    PrimaryCrimsonButton(
                        text = "Launch Teleprompter →",
                        onClick = { viewModel.dispatch(TpIntent.StartReplicaMode) }
                    )
                }
            }

            if (state.replicaPhase == ReplicaPhase.READY && !state.transcriptionReadyDismissed) {
                Dialog(
                    onDismissRequest = { viewModel.dispatch(TpIntent.DismissTranscriptionReady) },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardForestGreen)
                            .border(BorderStroke(UltraThinBorder, Color(0xFF7BC47F).copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GoldenrodYellow,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Video Transcribed!",
                                color = GoldenrodYellow,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.engineDetails,
                                color = SoftWarmWhite,
                                fontFamily = TpTheme.fonts,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            PrimaryCrimsonButton(
                                text = "OK",
                                onClick = { viewModel.dispatch(TpIntent.DismissTranscriptionReady) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// 4. THE STAGE (READING & REPLICA SPLIT SCREEN)
// =================================================================================================

@Composable
fun StageScreen(state: TpState, viewModel: TpViewModel) {
    MaterialTheme(colorScheme = TpTheme.stageColorScheme) {
        Box(modifier = Modifier.fillMaxSize().background(StageNearBlackGreen)) {

            if (state.isFocusMode) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)))
            }

            Column(modifier = Modifier.fillMaxSize()) {

                if (state.mode == TeleprompterMode.Replica && state.replicaVideoUri != null) {
                    Box(modifier = Modifier.fillMaxWidth().weight(0.4f).background(Color.Black)) {
                        VideoPlayerWidget(
                            uri = state.replicaVideoUri,
                            isPlaying = state.isPlaying,
                            replayTrigger = state.replayTrigger,
                            onTimeUpdate = { ms -> viewModel.dispatch(TpIntent.UpdateVideoTime(ms)) }
                        )
                    }
                    HorizontalDivider(color = SaturatedCrimson, thickness = 2.dp)
                }

                if (state.mode == TeleprompterMode.Reading) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp).graphicsLayer { alpha = if (state.isFocusMode) 0.05f else 0.4f }, horizontalArrangement = Arrangement.Center) {
                        Text(text = "${state.wpm} WPM · MANUAL", color = SoftWarmWhite, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().weight(0.6f)) {
                    if (state.mode == TeleprompterMode.Reading) {
                        ManualReadingContent(state, viewModel)
                    } else {
                        ReplicaSyncContent(state, viewModel)
                    }
                }

                TpControlsFooter(state, viewModel)
            }

            if (state.mode == TeleprompterMode.Reading) {
                InAppPiPMonitor(state)
            }
        }
    }
}

@Composable
fun VideoPlayerWidget(uri: Uri, isPlaying: Boolean, replayTrigger: Int, onTimeUpdate: (Long) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) exoPlayer.play() else exoPlayer.pause()
    }

    LaunchedEffect(replayTrigger) {
        if (replayTrigger > 0) {
            exoPlayer.seekTo(0)
            exoPlayer.pause()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            onTimeUpdate(exoPlayer.currentPosition)
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ReplicaSyncContent(state: TpState, viewModel: TpViewModel) {
    if (state.syncedScript.isEmpty()) return

    val listState = rememberLazyListState()

    LaunchedEffect(state.currentVideoTimeMs) {
        val activeIndex = state.syncedScript.indexOfLast {
            it.startTimeMs <= state.currentVideoTimeMs
        }.coerceAtLeast(0)

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 120

        val centreOffset = -(viewportHeight / 2 - itemHeight / 2)

        listState.animateScrollToItem(
            index = activeIndex,
            scrollOffset = centreOffset
        )
    }

    val activeIndex = state.syncedScript.indexOfLast {
        it.startTimeMs <= state.currentVideoTimeMs
    }.coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }
            .pointerInput(Unit) { detectTapGestures { viewModel.dispatch(TpIntent.TogglePlayPause) } }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = false
        ) {
            itemsIndexed(state.syncedScript) { index, line ->
                val isActive = index == activeIndex
                val isPast   = index < activeIndex
                Text(
                    text = line.text,
                    color = when {
                        isActive -> Color.White
                        isPast   -> SoftWarmWhite.copy(alpha = 0.18f)
                        else     -> SoftWarmWhite.copy(alpha = 0.35f)
                    },
                    fontFamily = TpTheme.fonts,
                    fontSize = if (isActive) 30.sp else 26.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun ManualReadingContent(state: TpState, viewModel: TpViewModel) {
    if (state.scriptLines.isEmpty()) return

    val listState = rememberLazyListState()
    var exactLineHeightPx by remember { mutableFloatStateOf(0f) }

    val isDragging = listState.isScrollInProgress
    LaunchedEffect(isDragging) {
        if (isDragging && state.isPlaying) viewModel.dispatch(TpIntent.ForcePause)
    }

    LaunchedEffect(state.isPlaying, state.wpm, exactLineHeightPx) {
        if (state.isPlaying && exactLineHeightPx > 0f) {
            while (isActive) {
                val linesPerMin = state.wpm / 5f
                val pixelsPerMin = linesPerMin * exactLineHeightPx
                val pixelsPerSec = pixelsPerMin / 60f
                val pixelsPerFrame = pixelsPerSec * (16f / 1000f)

                listState.scroll { scrollBy(pixelsPerFrame) }
                delay(16)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }
            .pointerInput(Unit) { detectTapGestures { viewModel.dispatch(TpIntent.TogglePlayPause) } }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 300.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(state.scriptLines) { line ->
                Text(
                    text = line,
                    color = SoftWarmWhite,
                    fontFamily = TpTheme.fonts,
                    fontSize = 34.sp,
                    lineHeight = 44.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .onGloballyPositioned { coordinates ->
                            if (exactLineHeightPx == 0f) exactLineHeightPx = coordinates.size.height.toFloat()
                        }
                )
            }
        }
    }
}

@Composable
fun TpControlsFooter(state: TpState, viewModel: TpViewModel) {
    val targetAlpha = if (state.isFocusMode && state.isPlaying) 0.0f else 1.0f
    val currentAlpha by animateFloatAsState(targetAlpha, label = "Controls alpha")

    if (currentAlpha <= 0.01f) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
            .graphicsLayer { alpha = currentAlpha; translationY = (1f - currentAlpha) * 100f }
            .background(StageNearBlackGreen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = SaturatedCrimson.copy(alpha = 0.1f), thickness = UltraThinBorder, modifier = Modifier.padding(bottom = 12.dp))

        if (state.mode == TeleprompterMode.Reading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.dispatch(TpIntent.AdjustWpm(-10)) }) { Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Slower", tint = SoftWarmWhite) }
                Text(text = "${state.wpm} WPM", color = GoldenrodYellow, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { viewModel.dispatch(TpIntent.AdjustWpm(10)) }) { Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Faster", tint = SoftWarmWhite) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ControlButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                desc = "Back",
                onClick = { viewModel.dispatch(TpIntent.StopReading) }
            )

            FloatingActionButton(
                onClick = { viewModel.dispatch(TpIntent.TogglePlayPause) },
                shape = CircleShape,
                containerColor = SaturatedCrimson,
                contentColor = Color.White,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }

            ControlButton(
                icon = Icons.Default.Refresh,
                desc = "Replay",
                onClick = { viewModel.dispatch(TpIntent.ReplayVideo) }
            )
            ControlButton(icon = Icons.AutoMirrored.Filled.Send, desc = "Focus", onClick = { viewModel.dispatch(TpIntent.ToggleFocusMode) }, isActive = state.isFocusMode)
        }
    }
}

@Composable
fun ControlButton(icon: ImageVector, desc: String, onClick: () -> Unit, isActive: Boolean = false) {
    val activeTint = if (isActive) SaturatedCrimson else SoftWarmWhite.copy(alpha = 0.4f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Icon(imageVector = icon, contentDescription = desc, tint = activeTint, modifier = Modifier.size(24.dp))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InAppPiPMonitor(state: TpState) {
    if (!state.isCameraPermitted) return

    val targetAlpha = if (state.isFocusMode && state.isPlaying) 0.0f else 0.8f
    val curAlpha by animateFloatAsState(targetAlpha, label = "PiP Alpha")

    var offsetX by remember { mutableStateOf(20f) }
    var offsetY by remember { mutableStateOf(100f) }

    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val curContext = LocalContext.current

    if (cameraPermissionState.status.isGranted && state.showMonitor && curAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 160.dp)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer { alpha = curAlpha }
                .clip(RoundedCornerShape(8.dp))
                .border(BorderStroke(UltraThinBorder, SaturatedCrimson))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            CameraPreview(modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(DeepForestGreen.copy(alpha = 0.15f)))
        }
    } else if (!cameraPermissionState.status.isGranted && state.mode == TeleprompterMode.Reading) {
        SideEffect {
            cameraPermissionState.launchPermissionRequest()
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier) {
    val curContext = LocalContext.current
    val curLifecycle = LocalLifecycleOwner.current
    val curPreview = remember { PreviewView(curContext) }

    AndroidView(factory = { curPreview }, modifier = modifier) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(curContext)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val previewUseCase = Preview.Builder().build().also { it.setSurfaceProvider(curPreview.surfaceProvider) }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(curLifecycle, cameraSelector, previewUseCase)
            } catch (exc: Exception) {
            }
        }, ContextCompat.getMainExecutor(curContext))
    }
}

// =================================================================================================
// 4. MAIN APPLICATION COMPOSABLE
// =================================================================================================

@Composable
fun TeleprompterApp() {
    val context = LocalContext.current
    val viewModel: TpViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TpViewModel(context.applicationContext as Application) as T
        }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state.mode,
        label = "Flow Transition",
        transitionSpec = {
            slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
        }
    ) { mode ->
        when (mode) {
            TeleprompterMode.Setup -> SetupScreen(state, viewModel)
            TeleprompterMode.Vocab -> VocabLoadingScreen(state, viewModel)
            TeleprompterMode.Reading, TeleprompterMode.Replica -> StageScreen(state, viewModel)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeleprompterApp()
        }
    }
}