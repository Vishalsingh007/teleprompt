package com.example.teleprompterstudio

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    // ── ORIGINAL 20 WORDS ──────────────────────────────────────────────────
    VocabWord("Perspicacious", "per·spi·CA·cious", "adjective",
        "Having a ready insight; shrewd and discerning beyond ordinary perception",
        "A perspicacious speaker always understands their audience before uttering a word.",
        listOf(
            "Possessing an unusually calm and measured manner when under significant pressure",
            "Demonstrating exceptional physical coordination and awareness of one's immediate surroundings",
            "Having a natural gift for vivid and expressive use of spoken language"
        ),
        "Intellectual"),
    VocabWord("Mellifluous", "mel·LIF·lu·ous", "adjective",
        "Sweet or musical in tone; smooth and pleasant to hear, like honey flowing",
        "Her mellifluous voice commanded the room without ever needing to rise above a whisper.",
        listOf(
            "Harsh or grating in tone; difficult and highly unpleasant to endure over time",
            "Quick or erratic in rhythm; completely lacking in consistent and predictable pacing",
            "Deep or resonant in pitch; rumbling and intensely powerful to experience firsthand"
        ),
        "Eloquent"),
    VocabWord("Magnanimous", "mag·NAN·i·mous", "adjective",
        "Generous and forgiving, especially toward a rival or less powerful person; noble of mind",
        "The magnanimous king pardoned his enemies after victory, earning greater loyalty than conquest ever could.",
        listOf(
            "Strict and unyielding, particularly toward a subordinate or less experienced team member",
            "Calculated and deeply cautious, primarily when dealing with unexpected financial or social situations",
            "Obsessed with superficial appearances; completely driven by public perception rather than moral duty"
        ),
        "Royal"),
    VocabWord("Indefatigable", "in·de·FAT·i·ga·ble", "adjective",
        "Persisting tirelessly; incapable of being fatigued regardless of difficulty or duration",
        "Only an indefatigable orator could speak for three hours and leave the audience wanting more.",
        listOf(
            "Yielding quickly under pressure; unable to maintain focus during complex or tedious tasks",
            "Easily distracted by external stimuli; prone to shifting attention away from the primary objective",
            "Dependent entirely on external validation; lacking any internal source of personal motivation"
        ),
        "Powerful"),
    VocabWord("Eloquent", "EL·o·quent", "adjective",
        "Fluent and persuasive in speech; able to express ideas with clarity, power and beauty",
        "An eloquent argument does not shout — it guides the listener to your conclusion as though it were their own.",
        listOf(
            "Hesitant and confused in expression; struggling to convey complex or highly nuanced concepts",
            "Technically precise but emotionally void; conveying information strictly through literal and dry terminology",
            "Aggressive and dominating in tone; seeking to overpower the listener rather than convince them"
        ),
        "Eloquent"),
    VocabWord("Sagacious", "sa·GA·cious", "adjective",
        "Having or showing keen mental discernment and good judgement; profoundly wise",
        "A sagacious communicator knows when silence speaks louder than any carefully chosen word.",
        listOf(
            "Acting impulsively without foresight; prone to making rushed and poorly considered decisions",
            "Relying entirely on intense emotional responses rather than logical or measured analysis",
            "Possessing deep knowledge in one extremely narrow and specific academic discipline exclusively"
        ),
        "Intellectual"),
    VocabWord("Loquacious", "lo·QUA·cious", "adjective",
        "Tending to talk a great deal; using more words than necessary, yet doing so with flair",
        "The loquacious host filled every silence with anecdote, never allowing discomfort to settle.",
        listOf(
            "Preferring to remain completely silent; communicating exclusively through written or nonverbal methods",
            "Speaking in short, abrupt sentences; refusing to elaborate on any given topic",
            "Possessing an unusually quiet and timid voice that is incredibly difficult to hear clearly"
        ),
        "Eloquent"),
    VocabWord("Ebullient", "e·BUL·lient", "adjective",
        "Cheerful and full of energy; overflowing with enthusiasm and high spirits",
        "Her ebullient delivery transformed an ordinary script into a performance audiences remembered for years.",
        listOf(
            "Somber and deeply reflective; exhibiting a serious and entirely unapproachable public demeanor",
            "Methodical and painstakingly slow; moving through tasks with deliberate and exhausting caution",
            "Chronically pessimistic about future outcomes; expecting the absolute worst in every possible scenario"
        ),
        "Powerful"),
    VocabWord("Sanguine", "SAN·guine", "adjective",
        "Optimistic, especially in a difficult situation; confidently positive about uncertain outcomes",
        "Remain sanguine in front of the camera — the audience mirrors whatever energy the speaker projects.",
        listOf(
            "Pessimistic, particularly during challenging events; highly fearful of potentially negative future results",
            "Deeply suspicious of the motives of others; assuming betrayal at every possible opportunity",
            "Easily overwhelmed by sudden stress; completely unable to function during unexpected public crises"
        ),
        "Royal"),
    VocabWord("Erudite", "ER·u·dite", "adjective",
        "Having or showing great knowledge or learning acquired through extensive study and curiosity",
        "An erudite speaker earns authority not through volume but through the weight of what they know.",
        listOf(
            "Skilled exclusively in physical labor; possessing virtually no formal education or theoretical background",
            "Speaking confidently despite lacking any factual basis; relying entirely on rumor and unproven anecdotes",
            "Naturally gifted in social situations but struggling significantly with complex intellectual concepts"
        ),
        "Intellectual"),
    VocabWord("Fastidious", "fas·TID·i·ous", "adjective",
        "Very attentive to accuracy, detail and quality; difficult to please; meticulous to a fault",
        "Be fastidious about your script — every unnecessary word is dead weight your audience must carry.",
        listOf(
            "Careless and entirely sloppy; consistently willing to accept mediocre or heavily flawed results",
            "Highly flexible and easygoing; completely unconcerned with rules, regulations, or strict quality standards",
            "Working quickly and recklessly; prioritizing sheer volume of output over any measure of accuracy"
        ),
        "Royal"),
    VocabWord("Tenacious", "te·NA·cious", "adjective",
        "Tending to keep a firm hold; not easily discouraged; persistent with extraordinary grip",
        "A tenacious creator does not abandon their voice simply because the first ten videos underperformed.",
        listOf(
            "Quick to surrender at the first obstacle; easily swayed to abandon long-term personal goals",
            "Highly flexible and accommodating; willing to change core beliefs to please the current audience",
            "Lacking any strong personal convictions; generally indifferent to whether projects succeed or ultimately fail"
        ),
        "Powerful"),
    VocabWord("Vivacious", "vi·VA·cious", "adjective",
        "Attractively lively and animated; radiating vitality and a contagious enthusiasm for life",
        "A vivacious on-screen presence is not born — it is crafted through deliberate study of delivery.",
        listOf(
            "Intensely quiet and highly contemplative; avoiding any unnecessary physical movement or vocal expression",
            "Stern, serious, and completely unsmiling; maintaining a rigid and highly formal posture at all times",
            "Moving slowly and sluggishly; projecting an overwhelming sense of deep exhaustion and chronic boredom"
        ),
        "Eloquent"),
    VocabWord("Lucid", "LU·cid", "adjective",
        "Expressed clearly and easily understood; mentally sharp and brilliantly articulate",
        "The most powerful speeches are not the most complex — they are the most lucid.",
        listOf(
            "Highly convoluted and layered with jargon; intentionally designed to confuse the average listener entirely",
            "Deliberately ambiguous and vague; leaving the core message open to dozens of conflicting interpretations",
            "Spoken with a heavy, thick accent that makes accurate verbal comprehension nearly impossible"
        ),
        "Intellectual"),
    VocabWord("Imperious", "im·PE·ri·ous", "adjective",
        "Assuming power or authority without justification; having the bearing and confidence of royalty",
        "He entered the room with an imperious calm that made everyone present feel they were in an audience.",
        listOf(
            "Timid, shy, and entirely self-effacing; constantly seeking permission before speaking or taking action",
            "Highly democratic and exceptionally collaborative; refusing to make decisions without absolute team consensus",
            "Clumsy, socially awkward, and deeply uncomfortable when placed in any position of visible leadership"
        ),
        "Royal"),
    VocabWord("Articulate", "ar·TIC·u·late", "adjective",
        "Having or showing the ability to speak fluently and coherently; expressing ideas with precision",
        "To be articulate is to respect your audience enough to say exactly what you mean, no more, no less.",
        listOf(
            "Stuttering heavily and constantly searching for words; completely unable to finish a single cohesive thought",
            "Speaking incredibly rapidly without pausing for breath; overwhelming the listener with an endless wall of sound",
            "Communicating exclusively through obscure visual metaphors; refusing to offer literal or straightforward verbal explanations"
        ),
        "Eloquent"),
    VocabWord("Resplendent", "re·SPLEN·dent", "adjective",
        "Attractive and impressive through being richly colourful or sumptuous; dazzling in appearance",
        "A resplendent vocabulary does not merely describe the world — it elevates it.",
        listOf(
            "Quietly understated and extremely minimal; deliberately blending seamlessly into the surrounding background environment",
            "Dull, faded, and entirely uninteresting; completely lacking any visual flair or distinguishing characteristics",
            "Cheap, poorly constructed, and obviously damaged; visibly showing severe signs of long-term neglect"
        ),
        "Royal"),
    VocabWord("Venerable", "VEN·er·a·ble", "adjective",
        "Accorded a great deal of respect, especially because of age, wisdom, or character",
        "Speak like someone whose words will one day be considered venerable — because they just might be.",
        listOf(
            "Young, naive, and entirely untested; lacking the life experience required to offer meaningful guidance",
            "Widely despised and openly mocked; famous only for committing severe historical blunders and mistakes",
            "Easily dismissed and frequently forgotten; possessing absolutely no lasting influence on the surrounding culture"
        ),
        "Royal"),
    VocabWord("Formidable", "FOR·mi·da·ble", "adjective",
        "Inspiring fear or respect through being impressively large, powerful, intense or capable",
        "A formidable communicator does not raise their voice — they lower it, and the room goes silent.",
        listOf(
            "Easily approachable, gentle, and utterly harmless; posing absolutely no threat or challenge to anyone",
            "Small, frail, and entirely insignificant; easily overpowered by even the most basic of counterarguments",
            "Soft-spoken, highly agreeable, and completely passive; lacking any sort of commanding stage presence"
        ),
        "Powerful"),
    VocabWord("Gravitas", "GRAV·i·tas", "noun",
        "Dignity, seriousness, and solemnity of manner; the quality that makes people listen",
        "Gravitas cannot be performed — it accumulates through years of meaning every word you say.",
        listOf(
            "A persistent sense of lightness and comedic ease; relying strictly on humor to maintain audience engagement",
            "A physical imposing stature without any intellectual weight; commanding attention strictly through sheer size",
            "An extremely rapid speed of verbal delivery; using an overwhelming pace to prevent any questions or interruptions"
        ),
        "Powerful"),

    // ── NEW EXPANSION PACK ─────────────────────────────────────────────────────────────
    VocabWord("Magisterial", "ma·jis·TEER·e·al", "adjective",
        "Having or showing great authority; dictatorial",
        "He delivered the opening hook with a magisterial tone that demanded instant respect.",
        listOf(
            "Acting with an overly submissive demeanor in the presence of more powerful figures",
            "Exhibiting a complete lack of clear direction when faced with difficult or stressful circumstances",
            "Demonstrating a strong preference for highly collaborative and entirely democratic decision-making processes"
        ),
        "Royal"),
    VocabWord("Suzerain", "SOO·ze·rain", "noun",
        "A sovereign or a state having some control over another state",
        "In the niche of tech reviews, she acted as the undisputed suzerain of the platform.",
        listOf(
            "A subordinate individual who assists a primary ruler in executing daily administrative decisions",
            "An ancient mechanical device used historically to project a speaker's voice across long distances",
            "A completely isolated territory that refuses to engage in any form of international commerce"
        ),
        "Royal"),
    VocabWord("Palatine", "PAL·a·tine", "adjective",
        "Having royal authority or privileges",
        "The studio was decorated with a palatine elegance that elevated the entire production.",
        listOf(
            "Demonstrating a cheap, rushed, and unpolished aesthetic typical of amateur broadcast attempts",
            "Located entirely outdoors without any formal shelter or dedicated environmental protections",
            "Possessing absolutely no inherent power, influence, or measurable authority within the existing organization"
        ),
        "Royal"),
    VocabWord("Patrician", "pa·TRICH·an", "adjective",
        "Belonging to or characteristic of the aristocracy",
        "Her patrician vocabulary made every sentence sound like a highly polished essay.",
        listOf(
            "Highly unrefined and common; utilizing slang and colloquialisms to appeal to a broad audience",
            "Clumsy and frequently prone to making embarrassing social or professional public mistakes",
            "Lacking any formal education or foundational understanding of the subject matter being discussed"
        ),
        "Royal"),
    VocabWord("August", "aw·GUST", "adjective",
        "Respected and impressive; majestic",
        "The august panel of creators listened to his presentation with absolute silence.",
        listOf(
            "Young, foolish, and incredibly naive; completely lacking the necessary experience to command an audience",
            "Quick to intense anger and prone to shouting unpredictably during public debates or discussions",
            "Easily distracted by minor background noises and completely unable to maintain a focused demeanor"
        ),
        "Royal"),
    VocabWord("Baronial", "ba·RO·ni·al", "adjective",
        "Belonging or relating to a baron; grand and impressive",
        "His baronial studio set gave the video a premium, network-television aesthetic.",
        listOf(
            "Cramped, visually cheap, and noticeably dark; giving off the distinct impression of a tight budget",
            "Highly disorganized and aggressively messy; featuring visible clutter scattered across the background",
            "Completely lacking any proper lighting setup or professional audio treatment whatsoever"
        ),
        "Royal"),
    VocabWord("Regnant", "REG·nant", "adjective",
        "Reigning; ruling; currently having the greatest influence",
        "As the regnant expert in the field, his tutorials were treated as absolute law.",
        listOf(
            "Overtaken by modern trends and entirely forgotten by the current generation of active consumers",
            "Serving humbly as a lowly assistant to the primary recognized leaders within the industry",
            "Permanently banished from the community following a highly publicized and controversial dispute"
        ),
        "Royal"),
    VocabWord("Preeminent", "pre·EM·i·nent", "adjective",
        "Surpassing all others; very distinguished in some way",
        "She is the preeminent voice in documentary storytelling on the platform.",
        listOf(
            "Considered to be the absolute worst performer within the current competitive group of peers",
            "Perfectly average and entirely forgettable; blending seamlessly into the vast crowd of similar creators",
            "Operating strictly as a complete beginner with absolutely zero prior industry or technical experience"
        ),
        "Royal"),
    VocabWord("Peerless", "PEER·less", "adjective",
        "Unequaled; unrivaled",
        "His ability to seamlessly transition between topics is entirely peerless.",
        listOf(
            "Exactly average and incredibly common; offering nothing distinct from the established industry standard",
            "Demonstrably worse than almost everyone else currently attempting to perform the exact same task",
            "Easily and perfectly replicated by modern artificial intelligence software without any noticeable human input"
        ),
        "Royal"),
    VocabWord("Illustrious", "il·LUS·tri·ous", "adjective",
        "Well known, respected, and admired for past achievements",
        "The illustrious guest brought instant credibility to the podcast.",
        listOf(
            "Completely unknown and entirely unproven; lacking any track record of past public success",
            "Deeply infamous for highly toxic behavior and universally shunned by reputable industry professionals",
            "Lacking any real technical skill or verifiable talent despite attempting to present as an authority"
        ),
        "Royal"),
    VocabWord("Exalted", "eg·ZAWL·ted", "adjective",
        "Placed at a high or powerful level; held in high regard",
        "She spoke from an exalted position of authority, earned through years of mastery.",
        listOf(
            "Cast down and utterly defeated; stripped of all previous ranks, titles, and public honors",
            "Deliberately hidden from public view; operating entirely in secret without any external recognition",
            "Filled with deep and persistent sorrow; unable to project any sense of positive forward momentum"
        ),
        "Royal"),
    VocabWord("Imperial", "im·PEER·i·al", "adjective",
        "Relating to an empire or an emperor; majestic",
        "The orchestral soundtrack gave the video essay an imperial, cinematic weight.",
        listOf(
            "Peasant-like and highly common; featuring a rough and completely unpolished aesthetic tone",
            "Cheap, tinny, and incredibly low-quality; displaying absolutely no attention to production value",
            "Completely silent and intensely dull; lacking any background audio or dynamic visual elements"
        ),
        "Royal"),
    VocabWord("Majestic", "ma·JES·tic", "adjective",
        "Having or showing impressive beauty or dignity",
        "The pacing of her speech was majestic, commanding the stage without ever rushing.",
        listOf(
            "Small, weak, and highly insignificant; failing to make any lasting impression on the audience",
            "Intentionally comical and deeply lighthearted; seeking strictly to entertain rather than to inform",
            "Disorganized, entirely chaotic, and rushed; clearly demonstrating a complete lack of proper preparation"
        ),
        "Royal"),
    VocabWord("Statuesque", "stat·u·ESK", "adjective",
        "Attractively tall and dignified",
        "He maintained a statuesque posture throughout the entire hour-long presentation.",
        listOf(
            "Slouching constantly and looking deeply defeated; failing to maintain proper stage or camera presence",
            "Constantly pacing back and forth and fidgeting nervously with various objects during the presentation",
            "Actively hiding behind the podium or out of the camera frame to avoid direct visual contact"
        ),
        "Royal"),
    VocabWord("Chivalric", "shi·VAL·ric", "adjective",
        "Relating to the qualities idealized by knighthood, such as courage and honor",
        "He offered a chivalric defense of his peers when the controversy broke out.",
        listOf(
            "Deeply cowardly and actively avoiding any form of direct conflict or necessary confrontation",
            "Intensely selfish and noticeably rude; prioritizing personal gain above any shared moral code",
            "Speaking entirely in modern internet slang rather than utilizing formal or structured language"
        ),
        "Royal"),
    VocabWord("Courtly", "COURT·ly", "adjective",
        "Polite, refined, and elegant",
        "His courtly demeanor made the interview subject feel instantly at ease on camera.",
        listOf(
            "Incredibly rude, brash, and excessively loud; alienating guests through sheer overwhelming aggression",
            "Physically clumsy and deeply socially awkward; unable to read the basic tone of the room",
            "Highly aggressive and overtly hostile; treating every conversation as a battle to be won"
        ),
        "Royal"),
    VocabWord("Lordly", "LORD·ly", "adjective",
        "Of, characteristic of, or suitable for a lord; grand and magnificent",
        "He surveyed the massive auditorium with a lordly gaze before stepping up to the mic.",
        listOf(
            "Timid, extremely shy, and constantly looking down; refusing to establish eye contact with the audience",
            "Acting entirely like a theatrical jester; utilizing cheap physical comedy to distract from the core message",
            "Slouching heavily behind the podium and mumbling into the microphone with a profound lack of energy"
        ),
        "Royal"),
    VocabWord("Crowned", "CROWND", "adjective",
        "Invested with regal power; supremely successful or rewarded",
        "The video was crowned as the definitive guide to the subject by the community.",
        listOf(
            "Forcefully stripped of all previous titles and completely ostracized from the professional community",
            "Entirely ignored by the platform algorithm; receiving absolutely no traction or meaningful external validation",
            "Deleted instantly for violating platform guidelines regarding acceptable visual or auditory content standards"
        ),
        "Royal"),
    VocabWord("Triumphant", "try·UMPH·ant", "adjective",
        "Having won a battle or contest; victorious",
        "He ended the stream with a triumphant smile, knowing they had crushed the goal.",
        listOf(
            "Visibly defeated and looking downward; apologizing profusely for a severely failed or broken project",
            "Crying in absolute despair after realizing the significant financial investment had been completely lost",
            "Apathetic and incredibly bored; demonstrating zero emotional investment in the final outcome of the work"
        ),
        "Royal"),
    VocabWord("Aristocratic", "a·ris·to·CRAT·ic", "adjective",
        "Distinguished in manners or bearing",
        "Her aristocratic posture conveyed absolute authority before she even checked the mic.",
        listOf(
            "Slouching constantly and behaving in a deeply unprofessional manner during the live broadcast segment",
            "Visibly nervous and highly fidgety; constantly adjusting clothing and checking the time anxiously",
            "Exceptionally loud, obnoxious, and intensely rude; speaking over others without any form of hesitation"
        ),
        "Royal"),
    VocabWord("Princely", "PRINCE·ly", "adjective",
        "Sumptuous and splendid; worthy of a prince",
        "The sponsor paid a princely sum for the sixty-second integration in the video.",
        listOf(
            "Extremely cheap, painfully stingy, and noticeably poor; reflecting a highly restricted operating budget",
            "Actively begging for spare change or relying entirely on small community donations to survive",
            "Lacking any real monetary value or intrinsic worth despite a highly polished exterior appearance"
        ),
        "Royal"),
    VocabWord("Monarchical", "mo·NARCH·i·cal", "adjective",
        "Relating to a monarch or the style of a king or queen",
        "He ruled his comment section with a monarchical hand, banning trolls instantly.",
        listOf(
            "Highly democratic and completely open; allowing the community to vote on every single creative decision",
            "Entirely chaotic with absolutely no moderation; allowing toxic behavior to spread completely unchecked",
            "Yielding completely to mob rule and constantly shifting opinions to align with the loudest voices"
        ),
        "Royal"),
    VocabWord("Lionhearted", "LY·on·har·ted", "adjective",
        "Brave and determined",
        "It takes a lionhearted creator to pivot their channel's niche after millions of subs.",
        listOf(
            "Deeply afraid of public speaking and entirely unwilling to appear on camera under any circumstances",
            "Running immediately from any form of conflict and refusing to stand by previously stated opinions",
            "Easily intimidated by negative comments and highly likely to delete videos following minor criticism"
        ),
        "Royal"),
    VocabWord("Magniloquent", "mag·NIL·o·quent", "adjective",
        "Using high-flown or bombastic language",
        "His magniloquent delivery turned a simple channel update into an epic saga.",
        listOf(
            "Speaking constantly in a quiet, barely audible whisper that frustrates the audience entirely",
            "Using strictly one-syllable words in a deliberate attempt to sound as uneducated as possible",
            "Refusing to speak entirely and relying exclusively on poorly animated visual text graphics"
        ),
        "Eloquent"),
    VocabWord("Voluble", "VOL·yu·ble", "adjective",
        "Speaking or spoken incessantly and fluently",
        "The voluble host kept the energy high, never letting the podcast fall into a dead silence.",
        listOf(
            "Struggling severely to find the right words; frequently pausing and uttering loud filler noises",
            "Speaking in a harsh, deeply grating tone that causes the audience physical auditory discomfort",
            "Preferring written communication exclusively and actively refusing any requests for live verbal interviews"
        ),
        "Eloquent"),
    VocabWord("Sonorous", "SON·o·rous", "adjective",
        "Imposingly deep and full in sound",
        "His sonorous voice carried to the back of the auditorium without the need for a microphone.",
        listOf(
            "Incredibly high-pitched and unpleasantly squeaky; lacking any form of bass or vocal warmth",
            "Exceptionally quiet and easily ignored; fading entirely into the ambient background noise",
            "Harsh, metallic, and deeply grating to the ear; sounding like a broken digital speaker"
        ),
        "Eloquent"),
    VocabWord("Lyrical", "LYR·i·cal", "adjective",
        "Expressing the writer's emotions in an imaginative and beautiful way",
        "The script was so lyrical it felt less like a video and more like spoken poetry.",
        listOf(
            "Intensely harsh and overly technical; focusing strictly on dry statistics and raw data points",
            "Stuttering frequently and completely broken; lacking any form of rhythmic or musical flow",
            "Written entirely without any emotion or creative flair; reading like a legal terms of service document"
        ),
        "Eloquent"),
    VocabWord("Euphonious", "yu·PHO·ni·ous", "adjective",
        "Pleasing to the ear",
        "The euphonious cadence of her sentences made the complex topic easy to listen to.",
        listOf(
            "Overwhelmingly loud, sharply clashing, and aggressively painful to endure for long periods of time",
            "Completely silent for long, deeply uncomfortable stretches that confuse the viewing audience entirely",
            "Struggling constantly with basic pronunciation and stumbling over words of three or more syllables"
        ),
        "Eloquent"),
    VocabWord("Dulcet", "DUL·cet", "adjective",
        "Sweet and soothing (often used ironically)",
        "He delivered the devastating critique in the most dulcet, calm tones imaginable.",
        listOf(
            "Screaming loudly in absolute, uncontrolled rage directly into the primary recording microphone",
            "Speaking entirely in dense technical jargon that alienates the vast majority of the audience",
            "Lacking any vocal control whatsoever; fluctuating wildly between whispers and deafening shouts"
        ),
        "Eloquent"),
    VocabWord("Facund", "FAK·und", "adjective",
        "Eloquent and articulate; capable of fluent speech",
        "A facund speaker can improvise a ten-minute monologue without a single filler word.",
        listOf(
            "Completely unable to form a grammatically correct sentence without referencing a physical script",
            "Speaking strictly from a rigid teleprompter and freezing completely if the text stops scrolling",
            "Highly anxious on stage; exhibiting visible physical tremors and an inability to maintain eye contact"
        ),
        "Eloquent"),
    VocabWord("Oratorical", "or·a·TOR·i·cal", "adjective",
        "Relating to the art or practice of public speaking",
        "His oratorical skills transformed a dry, boring script into a cinematic experience.",
        listOf(
            "Written down meticulously but never actually spoken aloud to a live or recorded audience",
            "Relating strictly to the visual design elements and graphical overlays of a video production",
            "Focused purely on the backend audio editing and equalization processes rather than the performance itself"
        ),
        "Eloquent"),
    VocabWord("Grandiloquent", "gran·DIL·o·quent", "adjective",
        "Pompous or extravagant in language, style, or manner",
        "His grandiloquent speech sounded impressive but actually contained very few facts.",
        listOf(
            "Speaking constantly in a quiet, timid voice that fails to project past the first row",
            "Refusing adamantly to use any long or complex words, resulting in a juvenile presentation",
            "Focused entirely on raw logic and empirical data while abandoning any attempt at stylistic flair"
        ),
        "Eloquent"),
    VocabWord("Silver-tongued", "SIL·ver-tongued", "adjective",
        "Persuasive and eloquent in speech",
        "The silver-tongued salesman convinced the audience to buy the course before the video ended.",
        listOf(
            "Stuttering constantly and highly nervous; completely failing to build trust with the target audience",
            "Speaking with a harsh, grating voice that actively repels listeners from engaging with the content",
            "Unable to form a complete or cohesive argument without relying on extensive written bullet points"
        ),
        "Eloquent"),
    VocabWord("Facile", "FAS·ile", "adjective",
        "Appearing neat and comprehensive only by ignoring the true complexities of an issue",
        "A facile argument might win a quick view, but it won't build a loyal subscriber base.",
        listOf(
            "Deeply complex and highly nuanced; requiring significant mental effort and concentration to fully understand",
            "Struggling constantly to articulate a clear point; wandering through unrelated topics without resolution",
            "Based entirely on heavy, dense academic study and peer-reviewed scientific literature without exception"
        ),
        "Eloquent"),
    VocabWord("Resonance", "REZ·o·nance", "noun",
        "The quality in a sound of being deep, full, and reverberating",
        "Speaking from the diaphragm gives your voice a natural resonance that microphones love.",
        listOf(
            "A high, aggressively squeaky pitch that causes immediate auditory fatigue in the listening audience",
            "A sudden, unexpected loss of all audio recording capabilities during a critical live broadcast",
            "Speaking far too quickly for the human ear or auto-captioning software to accurately process"
        ),
        "Eloquent"),
    VocabWord("Diction", "DIC·tion", "noun",
        "The choice and use of words and phrases in speech or writing",
        "Precise diction ensures that your global audience understands every word despite the accent.",
        listOf(
            "Mumbling incoherently and heavily slurring words together to the point of complete incomprehensibility",
            "The physical posture and staging of a speaker during a highly formal public presentation",
            "The specific lighting setup and camera angle utilized in a professional broadcasting studio environment"
        ),
        "Eloquent"),
    VocabWord("Enunciation", "e·nun·ci·A·tion", "noun",
        "The act of pronouncing words clearly",
        "Crisp enunciation allows the auto-captioning AI to perfectly transcribe your video without errors.",
        listOf(
            "Mumbling continuously with a tightly closed mouth, resulting in deeply muffled and unclear audio",
            "Shouting loudly and entirely incoherently; relying strictly on sheer volume rather than clarity",
            "Speaking at a pace far too fast for any listener to properly hear the individual syllables"
        ),
        "Eloquent"),
    VocabWord("Modulation", "mod·u·LA·tion", "noun",
        "Variation in the strength, tone, or pitch of one's voice",
        "Voice modulation is the ultimate key to holding an audience's attention during a long video.",
        listOf(
            "Speaking consistently at one exact, unchanging volume and pitch for the duration of the presentation",
            "Writing a highly complex script incredibly fast without reviewing it for pacing or grammatical errors",
            "Editing out absolutely all dead air and breathing noises, creating a highly unnatural audio flow"
        ),
        "Eloquent"),
    VocabWord("Timbre", "TAM·ber", "noun",
        "The character or quality of a musical sound or voice",
        "The rich, warm timbre of his voice makes his audiobooks incredibly soothing to listen to.",
        listOf(
            "The absolute volume level at which a speaker chooses to project their voice during a speech",
            "The literal speed or words-per-minute rate at which a presenter reads through their provided script",
            "The physical brand and model of microphone utilized to capture the audio during the recording session"
        ),
        "Eloquent"),
    VocabWord("Rhetoric", "RHET·o·ric", "noun",
        "The art of effective or persuasive speaking or writing",
        "Mastering rhetoric allows you to inspire action rather than just delivering dry information.",
        listOf(
            "The highly specific technical setup of a camera, including aperture, shutter speed, and ISO settings",
            "The literal software process of rendering and uploading a finalized video file to the internet",
            "A physical microphone stand or similar piece of hardware used to stabilize audio recording equipment"
        ),
        "Eloquent"),
    VocabWord("Cadence", "CA·dence", "noun",
        "A modulation or inflection of the voice",
        "Mastering your vocal cadence prevents you from sounding like you are just reading a teleprompter.",
        listOf(
            "A single, entirely flat monotone pitch maintained relentlessly throughout the duration of the entire broadcast",
            "The visual layout, font choice, and structural formatting of a script presented on a screen",
            "The specific three-point lighting setup utilized in a high-end professional indoor broadcasting studio"
        ),
        "Eloquent"),
    VocabWord("Phrasing", "PHRAS·ing", "noun",
        "The way in which something is expressed in words",
        "Adjusting the phrasing of your title can be the difference between ten views and ten million.",
        listOf(
            "The post-production color grading and visual effects applied to the final cut of the video",
            "The physical stage design, background props, and aesthetic set dressing of the recording environment",
            "The absolute decibel volume captured by the microphone during the loudest peak of the performance"
        ),
        "Eloquent"),
    VocabWord("Stirring", "STIR·ring", "adjective",
        "Causing strong emotion; rousing",
        "The stirring background music swelled perfectly as he delivered the final, powerful line.",
        listOf(
            "Putting the audience to sleep through the use of an intensely boring and entirely flat delivery",
            "Completely silent and utterly dull; lacking any element designed to capture or retain human attention",
            "Highly annoying and deeply distracting; utilizing sound effects that actively frustrate the listening audience"
        ),
        "Eloquent"),
    VocabWord("Ineffable", "in·EF·fa·ble", "adjective",
        "Too great or extreme to be expressed or described in words",
        "The speaker left the audience in a state of ineffable awe after the final revelation.",
        listOf(
            "Lacking any real substance or meaningful content; offering absolutely no value to the watching audience",
            "Easily offended by minor things; highly reactive and prone to creating unnecessary public drama",
            "Quick to change opinions frequently based purely on whatever trend is currently popular online"
        ),
        "Eloquent"),
    VocabWord("Poignant", "POIN·yant", "adjective",
        "Evoking a keen sense of sadness or regret; deeply affecting",
        "The poignant silence at the end of the story moved the entire audience to tears.",
        listOf(
            "Extremely loud, highly funny, and purely comedic; aimed strictly at generating superficial internet laughs",
            "Completely lacking any emotional impact; reading more like an instruction manual than a compelling story",
            "Highly technical, deeply dry, and entirely devoid of any relatable human experience or personal narrative"
        ),
        "Eloquent"),
    VocabWord("Glib", "GLIB", "adjective",
        "Fluent and voluble but insincere and shallow",
        "Avoid sounding glib; the camera easily detects when a speaker doesn't believe their own words.",
        listOf(
            "Deeply thoughtful and incredibly slow; carefully weighing the absolute truth of every single spoken syllable",
            "Stuttering continuously with severe nervousness; visibly terrified of being judged by the watching audience",
            "Speaking entirely with heavy technical jargon in an attempt to sound far more educated than reality"
        ),
        "Eloquent"),
    VocabWord("Epistemological", "e·pis·te·mo·LOJ·i·cal", "adjective",
        "Relating to the theory of knowledge, especially with regard to its methods and validation",
        "The video took an epistemological dive into how we actually know what we know.",
        listOf(
            "Focused exclusively on highly intense physical workouts and advanced athletic training regimens without academic foundation",
            "Relating strictly to simple, entry-level mathematics and basic arithmetic principles suitable for young children",
            "Based purely and entirely on rapid emotional reactions rather than any form of structured intellectual analysis"
        ),
        "Intellectual"),
    VocabWord("Dialectical", "dy·a·LEC·ti·cal", "adjective",
        "Relating to the logical discussion of ideas and opinions",
        "Using a dialectical approach, he presented both sides of the argument before delivering his thesis.",
        listOf(
            "Refusing aggressively to hear any opposing opinions and actively silencing those who attempt to disagree",
            "Shouting down all opponents entirely; relying strictly on sheer volume rather than any reasoned debate",
            "Speaking at length without utilizing any form of underlying logic, structure, or coherent thematic narrative"
        ),
        "Intellectual"),
    VocabWord("Heuristic", "hyu·RIS·tic", "adjective",
        "Enabling a person to discover or learn something for themselves",
        "A heuristic teaching style keeps viewers engaged because they feel like they are solving the puzzle.",
        listOf(
            "Spoon-feeding every single answer slowly and painfully, treating the audience as if they possess zero intelligence",
            "Refusing to explain anything at all; leaving the audience entirely confused and lacking basic context",
            "Making relatively simple concepts overly complicated in a deliberate attempt to sound artificially highly intelligent"
        ),
        "Intellectual"),
    VocabWord("Axiomatic", "ax·i·o·MAT·ic", "adjective",
        "Self-evident or unquestionable",
        "In content creation, it is axiomatic that audio quality matters more than video quality.",
        listOf(
            "Highly doubtful and fiercely debated by experts across multiple distinct and respected professional academic disciplines",
            "Completely unproven by any form of modern science and relying strictly on ancient, outdated superstitions",
            "Incredibly difficult to understand without dedicating years of intense study to the highly specific subject"
        ),
        "Intellectual"),
    VocabWord("Cogent", "CO·gent", "adjective",
        "Clear, logical, and convincing",
        "A cogent argument leaves the audience with absolutely no choice but to agree with your premise.",
        listOf(
            "Speaking with excessive, uncontrolled emotion that completely undermines the actual factual points being presented",
            "Lacking any clear direction or structure; wandering aimlessly between completely unrelated and highly confusing topics",
            "Repeating the exact same shallow point blindly without offering any new supporting evidence or nuanced details"
        ),
        "Intellectual"),
    VocabWord("Incisive", "in·SY·siv", "adjective",
        "Intelligently analytical and clear-thinking",
        "Her incisive commentary cut straight through the noise and delivered pure value.",
        listOf(
            "Wandering far off the main topic frequently and filling time with completely irrelevant personal stories",
            "Speaking with a heavy stutter that makes it incredibly difficult for the listener to remain fully engaged",
            "Lacking any real substance whatsoever; utilizing buzzwords exclusively to mask a complete absence of knowledge"
        ),
        "Intellectual"),
    VocabWord("Pedantic", "pe·DAN·tic", "adjective",
        "Excessively concerned with minor details or rules; overscrupulous",
        "Don't be so pedantic about minor script deviations that you lose the natural flow of delivery.",
        listOf(
            "Careless and highly sloppy; demonstrating zero concern for accuracy, grammar, or fundamental factual correctness",
            "Ignoring all established rules entirely and actively encouraging others to adopt a similarly reckless approach",
            "Focused strictly and exclusively on the big picture while entirely ignoring the necessary foundational details"
        ),
        "Intellectual"),
    VocabWord("Omniscient", "om·NISH·ent", "adjective",
        "Knowing everything",
        "You don't need to be omniscient to make a great video; you just need to share what you know honestly.",
        listOf(
            "Knowing absolutely nothing about the subject matter yet projecting an air of supreme and unearned confidence",
            "Refusing adamantly to learn new things or adapt to clearly changing circumstances in the market",
            "Forgetting established lines constantly and relying heavily on producers to feed the script off-camera"
        ),
        "Intellectual"),
    VocabWord("Socratic", "so·CRAT·ic", "adjective",
        "Relating to the method of asking questions to draw out answers and encourage insight",
        "The Socratic method of opening a video with a profound question guarantees high retention.",
        listOf(
            "Lecturing aggressively without ever allowing any input, questions, or feedback from the participating audience members",
            "Speaking entirely and exclusively in rigid statements that leave absolutely no room for interpretation or debate",
            "Refusing to ask anything at all; actively punishing anyone who dares to interrupt the primary monologue"
        ),
        "Intellectual"),
    VocabWord("Pragmatic", "prag·MAT·ic", "adjective",
        "Dealing with things sensibly and realistically",
        "The most pragmatic speakers know that clear audio is far more important than 4K video.",
        listOf(
            "Lost constantly in entirely unrealistic fantasies that have absolutely zero application in the real working world",
            "Focused strictly and exclusively on high-end visual aesthetics while ignoring the actual substance of the content",
            "Highly emotional, intensely illogical, and prone to making sweeping decisions based strictly on temporary feelings"
        ),
        "Intellectual"),
    VocabWord("Analytical", "an·a·LYT·i·cal", "adjective",
        "Relating to or using analysis or logical reasoning",
        "Her analytical review of the dashboard revealed exactly why the last video failed.",
        listOf(
            "Ignoring all available data completely and choosing to operate strictly on unverified gut instinct and hunches",
            "Guessing entirely based on passing feelings alone without consulting any established historical or statistical precedents",
            "Acting entirely on sudden impulse without ever pausing to consider the long-term strategic consequences involved"
        ),
        "Intellectual"),
    VocabWord("Cerebral", "ce·RE·bral", "adjective",
        "Intellectual rather than emotional or physical",
        "The video essay took a cerebral approach, breaking down the complex theories perfectly.",
        listOf(
            "Purely emotional and highly reactive; designing content specifically to trigger immediate outrage and internet drama",
            "Focused entirely on broad physical comedy, utilizing slapstick humor rather than any form of elevated wit",
            "Lacking deep thought completely; appealing strictly to the lowest possible common denominator of the viewing audience"
        ),
        "Intellectual"),
    VocabWord("Trenchant", "TREN·chant", "adjective",
        "Vigorous or incisive in expression or style",
        "His trenchant critique of the industry went viral almost immediately.",
        listOf(
            "Dull, incredibly boring, and entirely uninteresting; guaranteed to put the majority of viewers completely to sleep",
            "Overly polite, highly cautious, and actively avoiding saying anything that might potentially offend a single person",
            "Speaking endlessly without making any clear point; rambling for hours without arriving at a cohesive conclusion"
        ),
        "Intellectual"),
    VocabWord("Astute", "as·TUTE", "adjective",
        "Having an ability to accurately assess situations and turn them into an advantage",
        "An astute creator knows exactly when to pause for maximum dramatic effect.",
        listOf(
            "Completely unaware of surrounding circumstances; frequently making highly inappropriate comments due to lack of situational reading",
            "Acting entirely on sudden impulse without dedicating any time whatsoever to assessing the current environmental factors",
            "Refusing adamantly to plan ahead for obvious contingencies, resulting in entirely avoidable and highly destructive public failures"
        ),
        "Intellectual"),
    VocabWord("Empirical", "em·PIR·i·cal", "adjective",
        "Based on, concerned with, or verifiable by observation or experience",
        "Back up your storytelling with empirical evidence to completely cement your authority.",
        listOf(
            "Based purely on intense imagination and absolute fiction; possessing absolutely no grounding in objective, verifiable reality",
            "Completely unproven by any accepted modern scientific methods; relying heavily on widely debunked historical myths",
            "Lacking any practical real-world application; existing solely as a theoretical thought experiment with no tangible use"
        ),
        "Intellectual"),
    VocabWord("Discerning", "dis·CERN·ing", "adjective",
        "Having or showing good judgment",
        "A discerning editor knows exactly which pauses to cut and which ones to leave in for impact.",
        listOf(
            "Blindly deleting raw footage without reviewing it first; displaying a complete lack of critical editorial oversight",
            "Entirely unable to tell high-quality material from utterly useless garbage when reviewing the daily production dailies",
            "Rushing frantically through the editing process without looking closely at any of the actual fine details"
        ),
        "Intellectual"),
    VocabWord("Rational", "RASH·on·al", "adjective",
        "Based on or in accordance with reason or logic",
        "A rational breakdown of the controversy performed much better than an angry, screaming rant.",
        listOf(
            "Highly emotional and frequently screaming directly into the lens; utilizing rage as the primary form of engagement",
            "Completely illogical and entirely random; assembling arguments that actively contradict themselves within the same exact sentence",
            "Based entirely on debunked superstition and wild conspiracy theories rather than any form of verified objective truth"
        ),
        "Intellectual"),
    VocabWord("Philosophical", "phil·o·SOPH·i·cal", "adjective",
        "Relating or devoted to the study of the fundamental nature of knowledge, reality, and existence",
        "She took a philosophical approach to the hate comments, realizing they didn't matter.",
        listOf(
            "Highly reactive and deeply angry; interpreting every single minor critique as a massive and unforgivable personal attack",
            "Focused purely on shallow internet drama and constantly seeking to provoke unnecessary conflict with other established creators",
            "Unable to think deeply about complex subjects; demonstrating a profound lack of intellectual curiosity or extended focus"
        ),
        "Intellectual"),
    VocabWord("Acute", "a·CUTE", "adjective",
        "Having or showing a perceptive understanding or insight",
        "His acute awareness of audience retention metrics allowed him to hack the algorithm completely.",
        listOf(
            "Dull, slow, and entirely oblivious to rapidly changing trends that dictate success in the modern online marketplace",
            "Focused exclusively and entirely on physical strength training while entirely ignoring any form of intellectual development",
            "Lacking any fundamental understanding of how the core technology actually functions despite claiming to be an expert"
        ),
        "Intellectual"),
    VocabWord("Epiphany", "e·PIPH·a·ny", "noun",
        "An experience of a sudden and striking realization",
        "A great explainer video guides the viewer toward an epiphany rather than just listing facts.",
        listOf(
            "A long, incredibly boring explanation that actively discourages the audience from learning anything useful or practically applicable",
            "A complete and utter misunderstanding of the core concept that results in the spread of highly dangerous misinformation",
            "A physical feeling of intense fatigue that overcomes a viewer when subjected to poorly paced and unedited content"
        ),
        "Intellectual"),
    VocabWord("Esoteric", "es·o·TER·ic", "adjective",
        "Intended for or likely to be understood by only a small number of people",
        "He translated esoteric academic research into stories that absolutely anyone could understand.",
        listOf(
            "Understood perfectly and instantly by absolutely everyone; featuring concepts so simple they require zero actual formal explanation",
            "Highly emotional, deeply reactive, and completely lacking in any form of complex or nuanced underlying structural foundation",
            "Physically imposing, aggressively loud, and designed specifically to intimidate the listener rather than actually educate them"
        ),
        "Intellectual"),
    VocabWord("Pellucid", "pel·LU·cid", "adjective",
        "Translucently clear; easily understood",
        "She broke down the complex topic into pellucid explanations that delighted the viewers.",
        listOf(
            "Intentionally murky, highly confusing, and deliberately dark; designed specifically to obscure the true nature of the subject",
            "Overly long, intensely repetitive, and actively boring; dragging out a simple point to an excruciatingly painful degree",
            "Filled entirely with dense technical jargon that alienates ninety percent of the audience within the first thirty seconds"
        ),
        "Intellectual"),
    VocabWord("Methodical", "me·THOD·i·cal", "adjective",
        "Done according to a systematic or established form of procedure",
        "His methodical scripting process ensured that not a single second of the video was wasted.",
        listOf(
            "Highly chaotic, entirely random, and completely messy; operating without any sort of underlying structure or established professional workflow",
            "Rushed frantically at the very last possible minute, resulting in an end product filled with highly noticeable errors",
            "Completely improvised entirely on the spot without any prior preparation, research, or even a basic functional outline"
        ),
        "Intellectual"),
    VocabWord("Profound", "pro·FOUND", "adjective",
        "Very great or intense; having or showing great knowledge or insight",
        "Sometimes a ten-second pause can be vastly more profound than ten minutes of rapid talking.",
        listOf(
            "Extremely shallow and entirely lacking in any meaningful depth; offering insights that a small child would already understand",
            "Easily forgotten immediately after hearing it; possessing absolutely zero lasting impact on the mindset of the target audience",
            "Physically lightweight and entirely inconsequential; providing no structural support to the overarching thesis being actively presented"
        ),
        "Intellectual"),
    VocabWord("Puissant", "PWIS·ant", "adjective",
        "Having great power or influence",
        "The opening hook of the video was so puissant that viewer retention immediately skyrocketed.",
        listOf(
            "Entirely weak and completely ineffective; failing utterly to persuade even the most highly receptive and agreeable audience members",
            "Noticeably quiet and incredibly easily ignored; blending seamlessly into the background without making any sort of impression",
            "Lacking any significant emotional depth or measurable societal impact; existing purely as a temporary piece of throwaway media"
        ),
        "Powerful"),
    VocabWord("Redoubtable", "re·DOUBT·a·ble", "adjective",
        "Formidable, especially as an opponent",
        "He was a redoubtable debater, perfectly anticipating counterarguments before they were spoken.",
        listOf(
            "Easily and rapidly defeated in any form of logical argument; possessing no ability to successfully defend a core thesis",
            "Overly friendly and deeply accommodating to a severe fault; refusing to stand firm on any established personal boundary",
            "Lacking any strong, defined opinions entirely; drifting aimlessly based strictly on whoever spoke to them most recently"
        ),
        "Powerful"),
    VocabWord("Stalwart", "STAL·wart", "adjective",
        "Loyal, reliable, and hardworking",
        "His stalwart community defended him fiercely in the comment section against the trolls.",
        listOf(
            "Highly fickle and incredibly quick to betray close allies the very moment a situation becomes remotely difficult",
            "Deeply lazy and completely unreliable; frequently failing to meet established deadlines or honor basic professional commitments entirely",
            "Constantly and rapidly changing core opinions in a desperate attempt to continually appease the widest possible modern audience"
        ),
        "Powerful"),
    VocabWord("Dauntless", "DAUNT·less", "adjective",
        "Showing fearlessness and determination",
        "It takes a dauntless creator to entirely pivot their channel's niche after millions of subscribers.",
        listOf(
            "Absolutely terrified of taking any risks whatsoever; preferring to remain stagnant rather than attempting anything remotely new",
            "Easily and severely intimidated by negative internet comments; frequently abandoning projects at the first sign of public pushback",
            "Intensely lazy and completely unmotivated; refusing to put in the basic foundational effort required to achieve high-level success"
        ),
        "Powerful"),
    VocabWord("Impervious", "im·PER·vi·ous", "adjective",
        "Unable to be affected by",
        "A true professional remains impervious to off-camera distractions while rolling.",
        listOf(
            "Easily and completely distracted by absolutely anything in the environment; incapable of maintaining focus for more than a minute",
            "Highly and visibly sensitive to even the mildest form of constructive criticism; taking every minor note entirely personally",
            "Prone to breaking down and crying on set when faced with completely standard and highly expected minor production delays"
        ),
        "Powerful"),
    VocabWord("Unflinching", "un·FLINCH·ing", "adjective",
        "Not showing fear or hesitation in the face of danger or difficulty",
        "She maintained an unflinching gaze with the camera lens, connecting deeply with the viewer.",
        listOf(
            "Constantly and nervously looking away from the camera lens; completely failing to establish any form of digital eye contact",
            "Trembling visibly with severe anxiety during the entire duration of the required public speaking engagement or live presentation",
            "Easily distracted by rapid background movement and entirely unable to deliver a single sentence without losing their place"
        ),
        "Powerful"),
    VocabWord("Resolute", "REZ·o·lute", "adjective",
        "Admirably purposeful, determined, and unwavering",
        "Deliver your conclusion with a resolute tone so the audience knows exactly what to do next.",
        listOf(
            "Highly hesitant and deeply unsure of themselves; frequently pausing to second-guess the validity of their own presented facts",
            "Constantly and unpredictably changing their mind regarding the core message; confusing the audience entirely by the very end",
            "Speaking entirely in an upward questioning tone; making established facts sound like unverified and highly suspicious personal opinions"
        ),
        "Powerful"),
    VocabWord("Steadfast", "STED·fast", "adjective",
        "Resolutely or dutifully firm and unwavering",
        "Maintain steadfast eye contact with the lens to perfectly simulate a one-on-one conversation.",
        listOf(
            "Looking around the room highly nervously; refusing to engage directly with the audience or maintain a strong posture",
            "Incredibly quick to completely abandon a primary point the moment a single audience member displays minor visible confusion",
            "Easily distracted by the production crew; frequently breaking character and ruining the illusion of a highly polished presentation"
        ),
        "Powerful"),
    VocabWord("Unyielding", "un·YIELD·ing", "adjective",
        "Not giving way to pressure; hard or solid",
        "An unyielding commitment to upload consistency is the ultimate secret to algorithmic success.",
        listOf(
            "Giving up entirely at the very first sign of a minor obstacle; refusing to push through totally standard challenges",
            "Soft, malleable, and easily broken by external pressure; possessing absolutely zero internal drive or strong personal boundaries",
            "Constantly and chaotically changing established release schedules; actively confusing the audience and destroying long-term subscriber trust entirely"
        ),
        "Powerful"),
    VocabWord("Relentless", "re·LENT·less", "adjective",
        "Oppressively constant; incessant",
        "The relentless pacing of the edit ensured that viewer retention never dipped for a second.",
        listOf(
            "Stopping frequently for exceptionally long, unedited pauses that entirely destroy the momentum of the finalized video product",
            "Giving up entirely easily on a project halfway through production; completely abandoning the work due to minor creative friction",
            "Moving at an incredibly sluggish, boring pace that guarantees the audience will click away before the halfway mark"
        ),
        "Powerful"),
    VocabWord("Valiant", "VAL·yant", "adjective",
        "Possessing or showing courage or determination",
        "She made a valiant effort to save the livestream after the power outage.",
        listOf(
            "Running immediately away from the core problem; absolutely refusing to address a highly public failure or massive mistake",
            "Complaining loudly and endlessly without taking any measurable action to actually fix the underlying issue currently causing trouble",
            "Showing absolutely zero effort during a critical presentation; clearly demonstrating a complete lack of respect for the audience"
        ),
        "Powerful"),
    VocabWord("Inexorable", "in·EX·o·ra·ble", "adjective",
        "Impossible to stop or prevent",
        "The speaker built their argument with an inexorable logic that left absolutely no room for doubt.",
        listOf(
            "Easily stopped or severely discouraged by the most minor, insignificant counterarguments presented by totally uninformed audience members",
            "Moving incredibly slowly and highly hesitantly; demonstrating a profound lack of actual confidence in the presented research material",
            "Frequently and chaotically changing direction mid-sentence; completely unable to construct a single, cohesive, and logically sound overall narrative"
        ),
        "Powerful"),
    VocabWord("Intrepid", "in·TREP·id", "adjective",
        "Resolute fearlessness, endurance, and fortitude",
        "The intrepid journalist asked the precise questions everyone else was too afraid to voice.",
        listOf(
            "Deeply and profoundly afraid of public speaking; refusing absolutely to appear on camera under any possible professional circumstances",
            "Easily and visibly intimidated by established authority figures; refusing to ask difficult or necessary questions during formal interviews",
            "Cautious to an extreme and crippling fault; entirely unwilling to take even the most minor and highly calculated risk"
        ),
        "Powerful"),
    VocabWord("Fervent", "FER·vent", "adjective",
        "Having or displaying a passionate intensity",
        "A fervent delivery will often completely cover up minor mistakes in the script itself.",
        listOf(
            "Showing absolutely no emotion at all; delivering the entire presentation in a robotic, totally flat, and deeply boring monotone",
            "Coldly calculating and highly precise; focusing entirely on raw data while completely ignoring the human element of storytelling",
            "Easily and continually distracted from the primary goal; demonstrating a profound lack of authentic passion for the chosen subject"
        ),
        "Powerful"),
    VocabWord("Vigorous", "VIG·or·ous", "adjective",
        "Strong, healthy, and full of energy",
        "Use vigorous hand gestures to emphasize the most critical points of your presentation.",
        listOf(
            "Deeply lethargic and appearing entirely exhausted; projecting a severe lack of basic physical energy to the watching audience",
            "Barely moving at all during the entire presentation; standing stiffly and rigidly like a completely frozen physical statue",
            "Noticeably quiet and highly reserved; refusing entirely to project their voice or command the physical space of the stage"
        ),
        "Powerful"),
    VocabWord("Commanding", "com·MAND·ing", "adjective",
        "Indicating or expressing authority; imposing",
        "A commanding stage presence ensures that no one checks their phone while you are speaking.",
        listOf(
            "Highly timid and incredibly easily ignored; blending seamlessly into the background and failing to capture any audience attention",
            "Actively pleading for attention in a desperate and highly unprofessional manner; completely undermining their own perceived expert authority",
            "Whispering nervously into the microphone; entirely unable to project their voice or speak with any measure of confidence"
        ),
        "Powerful"),
    VocabWord("Fierce", "FEERS", "adjective",
        "Having or displaying an intense or ferocious aggressiveness",
        "She brought a fierce energy to the debate that left her opponent completely speechless.",
        listOf(
            "Exceptionally mild, deeply gentle, and overly quiet; completely failing to assert any form of dominance during a public confrontation",
            "Visibly bored and entirely apathetic; demonstrating absolutely zero passion or genuine interest in the outcome of the ongoing debate",
            "Literally falling asleep at the podium; demonstrating a historically unprofessional lack of basic respect for the live audience"
        ),
        "Powerful"),
    VocabWord("Unstoppable", "un·STOP·pa·ble", "adjective",
        "Impossible to stop or prevent",
        "Once he found his speaking rhythm, the momentum of the presentation was completely unstoppable.",
        listOf(
            "Easily and entirely derailed by the most minor and completely expected technical issues during a standard live video broadcast",
            "Highly hesitant, intensely slow, and visibly unsure; completely unable to build or maintain any form of narrative momentum",
            "Lacking any real internal drive or personal ambition; entirely willing to abandon the project at the first sign of friction"
        ),
        "Powerful"),
    VocabWord("Omnipotent", "om·NIP·o·tent", "adjective",
        "Having unlimited power; able to do anything",
        "To a beginner, a master orator can appear almost omnipotent in their control of the room.",
        listOf(
            "Completely lacking the basic ability to speak clearly in public; suffering from intense, highly crippling, and entirely unmanaged stage fright",
            "Easily and visibly confused by standard audience questions; completely incapable of improvising or thinking critically on the spot",
            "Entirely dependent on highly detailed written notes; completely unable to formulate a cohesive sentence without looking directly down"
        ),
        "Powerful")
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
    val transcriptionReadyDismissed: Boolean = false,

    val isTtsSpeaking: Boolean = false,
    val isTtsReady: Boolean = false
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

    object SpeakCurrentWord : TpIntent()
    object SpeakCurrentWordSlow : TpIntent()
    object StopSpeaking : TpIntent()
    data class TtsReady(val success: Boolean) : TpIntent()
}

class TpViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tp_prefs", Context.MODE_PRIVATE)
    private val KEY_SCRIPT = "last_script"

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

    private var tts: TextToSpeech? = null
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        // Load saved script from SharedPreferences
        val saved = prefs.getString(KEY_SCRIPT, "") ?: ""
        if (saved.isNotBlank()) {
            _state.update { it.copy(scriptText = saved) }
        }

        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.update { it.copy(isTtsSpeaking = true) }
                        }
                        override fun onDone(utteranceId: String?) {
                            _state.update { it.copy(isTtsSpeaking = false) }
                        }
                        override fun onError(utteranceId: String?) {
                            _state.update { it.copy(isTtsSpeaking = false) }
                        }
                    })
                    dispatch(TpIntent.TtsReady(true))
                } else {
                    dispatch(TpIntent.TtsReady(false))
                }
            } else {
                dispatch(TpIntent.TtsReady(false))
            }
        }
    }

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }

    private fun speakWord(rate: Float) {
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
            audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            return
        }

        tts?.setSpeechRate(rate)
        val wordToSpeak = VOCAB_WORDS[_state.value.currentVocabIndex].word
        tts?.speak(wordToSpeak, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun dispatch(intent: TpIntent) {
        when (intent) {
            is TpIntent.UpdateText -> {
                _state.update { it.copy(scriptText = intent.text) }
                if (intent.text.length <= 50_000) {
                    prefs.edit().putString(KEY_SCRIPT, intent.text).apply()
                }
            }
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

            TpIntent.NextVocabWord -> {
                tts?.stop()
                _state.update {
                    var nextIdx = VOCAB_WORDS.indices.random()
                    while (nextIdx == it.currentVocabIndex && VOCAB_WORDS.size > 1) {
                        nextIdx = VOCAB_WORDS.indices.random()
                    }
                    it.copy(
                        currentVocabIndex = nextIdx,
                        vocabAnswered = false,
                        vocabSelectedAnswer = "",
                        vocabShowingResult = false,
                        isTtsSpeaking = false
                    )
                }
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

            is TpIntent.TtsReady -> _state.update { it.copy(isTtsReady = intent.success) }
            TpIntent.SpeakCurrentWord -> speakWord(1.0f)
            TpIntent.SpeakCurrentWordSlow -> speakWord(0.75f)
            TpIntent.StopSpeaking -> {
                tts?.stop()
                _state.update { it.copy(isTtsSpeaking = false) }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardForestGreen)
                            .padding(vertical = 10.dp, horizontal = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📝 Paste Script", color = SoftWarmWhite, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                        Text("  →  ", color = GoldenrodYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("▶ Press Play", color = SoftWarmWhite, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                        Text("  →  ", color = GoldenrodYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("📷 Read on Camera", color = SoftWarmWhite, fontSize = 11.sp, fontFamily = FontFamily.SansSerif)
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text(text = "MANUAL SCRIPT".uppercase(), fontSize = 11.sp, color = GoldenrodYellow.copy(alpha = 0.7f), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.scriptText,
                            onValueChange = { viewModel.dispatch(TpIntent.UpdateText(it)) },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            placeholder = { Text("Or paste your speech here...", color = SoftWarmWhite.copy(alpha = 0.4f), fontFamily = TpTheme.fonts) },
                            textStyle = LocalTextStyle.current.copy(color = SoftWarmWhite, fontFamily = TpTheme.fonts, fontSize = 15.sp, lineHeight = 22.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = GoldenrodYellow.copy(alpha = 0.3f),
                                focusedBorderColor = GoldenrodYellow,
                                cursorColor = GoldenrodYellow
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val wordCount = state.scriptText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                        val estSecs = if (wordCount == 0) 0 else (wordCount * 60f / state.wpm).roundToInt()
                        Text(
                            text = "$wordCount words · ~$estSecs s at ${state.wpm} WPM",
                            color = SoftWarmWhite.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    }
                }

                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        PrimaryCrimsonButton(text = "▶  Start Teleprompter", onClick = { viewModel.dispatch(TpIntent.StartReading) }, enabled = state.scriptText.isNotBlank())
                    }
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
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(BorderStroke(UltraThinBorder, SaturatedCrimson.copy(alpha = 0.5f))).background(CardForestGreen).padding(16.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "OFFLINE VIDEO REPLICA", fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SaturatedCrimson)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = SaturatedCrimson, modifier = Modifier.size(16.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SaturatedCrimson.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("ADVANCED", color = SaturatedCrimson, fontSize = 9.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                                }
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

    val wordLength = vocabWord.word.length
    val vocabFontSize = when {
        wordLength >= 13 -> 28.sp
        wordLength >= 10 -> 32.sp
        wordLength >= 8  -> 40.sp
        else             -> 48.sp
    }
    val pronunFontSize = 14.sp

    var swipeOffsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 100f

    MaterialTheme(colorScheme = TpTheme.studioColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepForestGreen)
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffsetX > swipeThreshold) {
                                viewModel.dispatch(TpIntent.NextVocabWord)
                            }
                            swipeOffsetX = 0f
                        },
                        onDragCancel = { swipeOffsetX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0) {
                                swipeOffsetX += dragAmount
                            }
                        }
                    )
                }
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = vocabWord.pronunciation,
                                    color = categoryColor.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = pronunFontSize,
                                )

                                if (state.isTtsReady) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Hear pronunciation",
                                        tint = if (state.isTtsSpeaking) categoryColor else categoryColor.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { viewModel.dispatch(TpIntent.SpeakCurrentWord) },
                                                    onLongPress = { viewModel.dispatch(TpIntent.SpeakCurrentWordSlow) }
                                                )
                                            }
                                    )
                                }
                            }
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