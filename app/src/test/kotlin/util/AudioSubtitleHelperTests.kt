package org.jellyfin.androidtv.util

import android.text.TextUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.jellyfin.androidtv.preference.constant.LanguagesAudio
import org.jellyfin.androidtv.preference.constant.LanguagesSubtitle
import org.jellyfin.androidtv.ui.playback.PlayerApiHelpers
import org.jellyfin.apiclient.model.entities.MediaStream
import org.jellyfin.apiclient.model.entities.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.UserConfiguration

class AudioSubtitleHelperTests : FunSpec({

	// The legacy apiclient's MediaStream.getDisplayTitle()/Util.normalizeLanguageCode call
	// android.text.TextUtils, which is an unmocked stub in plain JVM unit tests.
	beforeSpec {
		mockkStatic(TextUtils::class)
		every { TextUtils.isEmpty(any()) } answers { (firstArg<CharSequence?>() ?: "").isEmpty() }
		every { TextUtils.equals(any(), any()) } answers {
			val a = firstArg<CharSequence?>(); val b = arg<CharSequence?>(1)
			a?.contentEquals(b ?: "") ?: (b == null)
		}
	}

	afterSpec {
		unmockkStatic(TextUtils::class)
	}

	fun audioStream(
		index: Int,
		codec: String = "aac",
		channels: Int = 2,
		lang: String = "en",
		isDefault: Boolean = true,
	) = MediaStream().apply {
		setType(MediaStreamType.Audio)
		setIndex(index)
		setCodec(codec)
		setChannels(channels)
		setSampleRate(48000)
		setBitDepth(16)
		setLanguage(lang)
		setIsDefault(isDefault)
		setIsForced(false)
		setIsExternal(false)
	}

	fun subtitleStream(
		index: Int,
		title: String? = null,
		codec: String = "subrip",
		lang: String = "zh",
		isDefault: Boolean = false,
		isForced: Boolean = false,
	) = MediaStream().apply {
		setType(MediaStreamType.Subtitle)
		setIndex(index)
		setTitle(title)
		setCodec(codec)
		setLanguage(lang)
		setIsDefault(isDefault)
		setIsForced(isForced)
		setIsExternal(false)
	}

	fun prefs(
		audioLang: LanguagesAudio = LanguagesAudio.AUTO,
		subtitleLang: LanguagesSubtitle = LanguagesSubtitle.AUTO,
	) = AudioSubtitleHelper.AudioSubPref(audioLang, subtitleLang, false, false, false, false, "none")

	fun jellyfinConfig(
		subtitleMode: SubtitlePlaybackMode = SubtitlePlaybackMode.DEFAULT,
		subtitleLanguagePreference: String = "",
	) = UserConfiguration(
		audioLanguagePreference = "",
		playDefaultAudioTrack = true,
		subtitleLanguagePreference = subtitleLanguagePreference,
		displayMissingEpisodes = false,
		groupedFolders = emptyList(),
		subtitleMode = subtitleMode,
		displayCollectionsView = false,
		enableLocalPassword = false,
		orderedViews = emptyList(),
		latestItemsExcludes = emptyList(),
		myMediaExcludes = emptyList(),
		hidePlayedInLatest = false,
		rememberAudioSelections = false,
		rememberSubtitleSelections = false,
		enableNextEpisodeAutoPlay = false,
		castReceiverId = "",
	)

	fun mediaStreams(vararg streams: MediaStream) = ArrayList<MediaStream>(streams.asList())

	// real library case A (Stranger Things): 5 same-merit all-IsDefault PGS tracks, old last-wins TreeMap pick chose "繁體"
	test("case A/(e): five equal-merit all-default tracks with Simplified/Traditional titles select the pure Simplified track") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "简英", codec = "pgssub", isDefault = true),
			subtitleStream(2, title = "繁英", codec = "pgssub", isDefault = true),
			subtitleStream(3, title = "简体", codec = "pgssub", isDefault = true),
			subtitleStream(4, title = "繁體", codec = "pgssub", isDefault = true),
			subtitleStream(5, title = null, codec = "pgssub", lang = "en", isDefault = true),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.title shouldBe "简体"
		subIdx.second shouldBe 2 // natural subtitle index
	}

	// real library case B (斯特林角): the lone IsDefault track is a forced one and must not win as regular subtitle
	test("case B: an IsDefault forced track never wins as regular subtitle, Simplified beats Traditional and equal-merit untitled tracks") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "Forced", lang = "ita", isDefault = true, isForced = true),
			subtitleStream(2, lang = "eng"),
			subtitleStream(3, lang = "ita"),
			subtitleStream(4, title = "Simplified", lang = "zh"),
			subtitleStream(5, title = "Traditional", lang = "zh"),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.title shouldBe "Simplified"
		subIdx.second shouldBe 3 // natural subtitle index, the forced track consumes 0
	}

	test("(a): equal-merit CHS then CHT subtitle pair resolves to the first CHS track") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "CHS", isDefault = true),
			subtitleStream(2, title = "CHT", isDefault = true),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.title shouldBe "CHS"
		subIdx.second shouldBe 0
	}

	test("(b): SubtitleMode DEFAULT prefers the IsDefault flagged track over a higher codec merit track") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, codec = "ass", lang = "en", isDefault = false),
			subtitleStream(2, codec = "srt", lang = "en", isDefault = true),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.codec shouldBe "srt"
		subIdx.second shouldBe 1
	}

	test("(c): explicit app-local subtitle language overrides the server SubtitleMode DEFAULT off rule") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "English", lang = "en"),
			subtitleStream(2, title = "CHS", lang = "zh"),
			subtitleStream(3, title = "CHT", lang = "zh"),
		)
		// server config alone would select no subtitle (no IsDefault track), the explicit app-local language wins
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(
			streams, prefs(subtitleLang = LanguagesSubtitle.Chinese), null, jellyfinConfig()
		)
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.title shouldBe "CHS"
		subIdx.second shouldBe 1
	}

	test("(d): SubtitleMode DEFAULT without any IsDefault flagged track selects no subtitle (off)") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "CHS"),
			subtitleStream(2, title = "CHT"),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first shouldBe null
		subIdx.second shouldBe PlayerApiHelpers.SUBTITLE_DISABLED
	}

	test("subtitle tie-break: equal-merit neutral titled tracks resolve to the first stream in natural order") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, isDefault = true),
			subtitleStream(2, isDefault = true),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		result.second.shouldNotBeNull().second shouldBe 0
	}

	test("subtitle tie-break: Simplified titles win equal-merit ties over Traditional without IsDefault flags") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "繁英", lang = "zh"),
			subtitleStream(2, title = "简体", lang = "zh"),
			subtitleStream(3, title = "繁體", lang = "zh"),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(
			streams, prefs(), null,
			jellyfinConfig(subtitleMode = SubtitlePlaybackMode.ALWAYS, subtitleLanguagePreference = "zh")
		)
		result.shouldNotBeNull()
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.title shouldBe "简体"
		subIdx.second shouldBe 1
	}

	test("DEVICE audio library override no longer suppresses subtitle selection") {
		val streams = mediaStreams(
			audioStream(0),
			subtitleStream(1, title = "繁體", codec = "pgssub", isDefault = true),
			subtitleStream(2, title = "简体", codec = "pgssub", isDefault = true),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(
			streams, prefs(audioLang = LanguagesAudio.DEVICE), null, jellyfinConfig()
		)
		result.shouldNotBeNull()
		result.first shouldBe null // audio stays player-side
		val subIdx = result.second.shouldNotBeNull()
		subIdx.first.shouldNotBeNull()
		subIdx.first.title shouldBe "简体"
		subIdx.second shouldBe 1
	}

	test("audio selection unchanged: TrueHD 7.1 still beats AC3 5.1") {
		val streams = mediaStreams(
			audioStream(0, codec = "ac3", channels = 6, isDefault = false),
			audioStream(1, codec = "truehd", channels = 8, isDefault = false),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		val audIdx = result.first.shouldNotBeNull()
		audIdx.first.shouldNotBeNull()
		audIdx.first.codec shouldBe "truehd"
		audIdx.second shouldBe 1
	}

	test("audio tie-break: equal-merit audio tracks resolve to the first stream in natural order") {
		val streams = mediaStreams(
			audioStream(0, codec = "aac", isDefault = false),
			audioStream(1, codec = "aac", isDefault = false),
		)
		val result = AudioSubtitleHelper.getBestAudioSubtitleIdx(streams, prefs(), null, jellyfinConfig())
		result.shouldNotBeNull()
		result.first.shouldNotBeNull().second shouldBe 0
	}
})
