
<h1 align="center">a Jellyfin TV Client, optimized for Zidoo media players aka "Zidoo Edition"</h1>  
<h3 align="center">based on the <a href="https://github.com/jellyfin/jellyfin-androidtv">official Jellyfin TV Client</a></h3>  
  
---  
  
<p align="center">  
<a href="https://github.com/jellyfin/jellyfin-androidtv">  
<img alt="GPL 2.0 License" src="https://img.shields.io/github/license/jellyfin/jellyfin-androidtv.svg"/>  
</a>  
<a href="https://opencollective.com/jellyfin">  
<img alt="Donate" src="https://img.shields.io/opencollective/all/jellyfin.svg?label=backers"/>  
</a>  
<a href="https://features.jellyfin.org">  
<img alt="Feature Requests" src="https://img.shields.io/badge/fider-vote%20on%20features-success.svg"/>  
</a>  
<a href="https://matrix.to/#/+jellyfin:matrix.org">  
<img alt="Chat on Matrix" src="https://img.shields.io/matrix/jellyfin:matrix.org.svg?logo=matrix"/>  
</a>  
<a href="https://www.reddit.com/r/jellyfin">  
<img alt="Join our Subreddit" src="https://img.shields.io/badge/reddit-r%2Fjellyfin-%23FF5700.svg"/>  
</a>  

## xjn maintenance builds

This fork is packaged as **Jellyfin Zidoo**, separate from the official Jellyfin Android TV app:

| Variant | Application ID | Launcher name |
| --- | --- | --- |
| Release | `org.jellyfin.androidtv.zidoo` | Jellyfin Zidoo |
| Debug | `org.jellyfin.androidtv.zidoo.debug` | Jellyfin Zidoo Debug |

The code namespace remains `org.jellyfin.androidtv`. Account types, search providers and intent resources follow each variant's application ID, so these packages can coexist with the official client. The maintenance version is `0.14.10-zidoo-xjn.3`; the existing `getVersionCode` convention uses the final segment as its build number (`.3` → `141003`).

### Build requirements

- **JDK 17** must run Gradle and the Java/Kotlin toolchains. Set `JAVA_HOME` to your JDK 17 installation before building. AGP 8.1.2, Gradle 8.4 and Kotlin 1.9.20 remain pinned.
- Install Android SDK Platform **34**, Build Tools **33.0.1** and the existing NDK **26.1.10909125**. Set the SDK location using `ANDROID_HOME` or `sdk.dir` in the ignored `local.properties`.
- All three Android modules default to `compileSdk = 34`, `minSdk = 23`, `targetSdk = 32`, Java source/target **17** and Kotlin JVM target **17**. Existing source code uses Java 17 syntax; AGP's D8/desugaring produces Android DEX without raising `minSdk`. The `armeabi-v7a` ABI is retained.
- Existing `local.properties` overrides still apply. Remove old Java 8 overrides or use `JAVA_VERSION_NR=17`, `JAVA_VERSION=VERSION_17`, `KOTLIN_JVM_TARGET=17` and `COMPILE_SDK_NR=34` when overriding these defaults.

From the repository root, use the same debug build command as CI:

```sh
./gradlew --build-cache --no-daemon --info :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Debug builds use Android's debug key and require no release keystore or `local.properties`.

### Release signing

For a locally signed release, supply all four existing properties in the ignored `local.properties`: `STOREFILE` (your keystore path), `KEYALIAS`, `KEYPASSWORD` and `STOREPASSWORD`. Keep the keystore outside the repository and retain the same key for future upgrades. Never commit these values or the keystore.

Without `STOREFILE`, the release variant is unsigned for external signing; it never falls back to the debug key. Once a keystore is configured, all four signing properties must be valid. The inherited publish workflow remains restricted to the official upstream repository; it is not enabled for this fork and does not publish to Jellyfin's infrastructure from this fork.

### Jellyfin 10.11 authentication models

The observed Jellyfin 10.11.11 authentication response omits the obsolete `SessionInfo.Capabilities.SupportsContentUploading` and `SupportsSync` fields. Kotlin SDK 1.4.4 incorrectly required them and failed to deserialize an otherwise successful Quick Connect authentication response. This fork uses SDK **1.5.5**, whose published models make both deprecated fields optional with SDK-provided defaults. No fields are inserted into server responses and the Quick Connect authentication flow is not bypassed.

SDK 1.5.5 stays on Kotlin 1.9 metadata (published with stdlib 1.9.24 and serialization 1.6.3), compatible with this project's Kotlin 1.9.20 compiler; SDK 1.6's Kotlin 2.0 migration is not included. Its generated enums and WebSocket Flow API replace the removed SDK APIs, while the legacy Java apiclient bridge and Zidoo native-player route are retained.

SDK 1.5 removed the automatic bitrate helper rather than moving it to another module. `AutoBitrate` retains the original 4/6/8/10 MB probe sequence through `getBitrateTestBytes`, fully consumes each streamed response and stores the measured result in bits per second; no fixed-rate replacement is used. App-only `SeriesTimers` navigation folders keep the legacy DTO/Gson format consumed by the existing browse views instead of being forced into a server collection enum.

An authentication-response regression exercises the SDK's real `RawResponse` deserializer with the captured capability fields omitted exactly as the server returns them. The on-device acceptance run below covers login, library browsing, native playback and progress/resume.

### Verified on

Tested against Jellyfin server **10.11.11** on a **ZIDOO Z9X** (Realtek RTD1619, Android 9, firmware `v6.4.42_G`) driving the internal Zidoo player (`com.android.gallery3d/.app.MovieActivity`):

- **Sign-in**: Quick Connect completes and loads the media library; account/session and capabilities are parsed correctly.
- **Direct play**: a 3840x2160 HEVC Main10 HDR10 (~75 Mbps, TrueHD 7.1) source plays through the internal player, with playback position, exit progress save and resume all working.
- **Transcoding**: works when the server is asked for a bitrate below the source. The Zidoo player plays the resulting HTTP/MKV stream and the server reports `PlayMethod=Transcode` with hardware acceleration. Progress is saved and a saved resume point is honoured on restart.
- **Transcode limitation**: seeking *inside* a running transcode does not work — the Zidoo player keeps playing linearly. This is a device limitation of the internal player (also noted by the original fork author) and is not fixable client-side.
- The Zidoo player reports a stream-relative position for transcodes, so its on-screen time can start near zero even though the item position is correct.
- **Subtitle selection follows the Jellyfin user profile.** With the app-local subtitle language left on AUTO, the client honours `UserConfiguration.SubtitleMode` / `SubtitleLanguagePreference` before falling back to its own heuristic; equal-merit tracks resolve deterministically to the first match, with Simplified Chinese preferred over Traditional. Verified on a 5-track all-`IsDefault` PGSSUB episode (previously picked `繁體`, now `中文(简体)`) and a 39-track SRT episode whose `Simplified`/`Traditional` tracks were last (previously picked `Traditional`, now `中文(简体)`). Audio-track selection is unchanged (TrueHD 7.1 still wins).

HDMI output was 1080p SDR in the test setup even for a 4K HDR10 source; resolution/HDR switching is a device setting and is not changed by this app.

## Original upstream maintenance status

The following development and compatibility notes describe Andy2244's original fork, not validation of the xjn maintenance build:

<h1 align="center">Update: Development of the original fork has stopped!</h1>

### I moved to CoreElec + Amlogic + Jellyfin for Kodi plugin
- main issue is time to merge all the upstream changes and motivation to work on the fork while not using Zidoo devices anymore
- there are issues/bug's that also will probably never fixed by Zidoo via there API
    - seeking for transcodes (multisegmented http stream issue)
    - proper enabled subtitles on play (always need to seek-back at least once until ASS show-up)
- so far everything still works with JF 10.9.11
    - need to edit/add path data to \jellyfin\root\default\options.xml for new direct-path libs, since the Ui option was removed, yet direct-path still works

---  
"Zidoo-Edition" is a unofficial community fork, based on the official Jellyfin Android TV client, adapted to better run on [Zidoo media players](https://www.zidoo.tv).
### Release [downloads here](https://github.com/Andy2244/jellyfin-androidtv-zidoo/releases)
TIP: you can directly install from your browser via `http://zidoo_ip:18888` web interface
### App ONLY works with latest 10.8.1+ JF server's.
### Firmware 6.(4/7).40+ is also recommended, but not required.
- feature roadmap
    - ~add smart "original language" audio selection logic based on tmdb id's~ _done_
    - ~add better transcoding support and fallback's for unsupported codecs~ _done_
    - ~add more audio-only transcoding options (DD only, PCM 5.1 -> DD5.1)~ _done_
    - seekable transcode playback (not possible, wont be fixed by Zidoo)
    - ~improve layouts~ _done_
    - ~small useability improvements _(smart screen views)_~ _done
      - ~focus selection border~ _done_
      - ~add missing vertical-grid features _(genre)_~ _done_
    - ~use new Android API from latest beta FW~ _done_
    - script hotkey action's _(xml kodi compatible)_
    - ~fix all NFS mount cases~ _done_
### Added Features/Fixes:
 - Playback integration with the internal Zidoo player
   - working resume, seek, watched states and server playback reporting 
   - http streaming support
   - smb, nfs support via `Direct Path` option
   - "smart" audio/subtitle selection logic
   - working tarnscoding for unsupported formats/codec's
 - UI layout fixes, changes
	 - cutoff grids, ui scaling fixed
### How to use with "Direct Path" option (smb/nfs):
- setup your Jellyfin server [library's](https://jellyfin.org/docs/general/server/libraries.html) with network paths or path substitution via `Shared network folder:` option
	- Formats
		- `smb://smb_user:smb_password@server_ip/share/folder`
		- `smb://smb_user@server_ip/share/folder`
		- `nfs://server_ip/nfs_export/:`
		- `nfs://server_ip/nfs_export/:/tv`
			- nfs needs the `/:` special marker right after the actual nfs export name/path portion!
	- Examples: 
		- `smb://andy:123456@192.168.1.101/htpc-share/series`
		- `smb://andy@192.168.1.101/htpc-share/series`
		- `nfs://192.168.1.101/mnt/media/movies/:` -> with nfs export `/mnt/media/movies`
 		- `nfs://192.168.1.101/mnt/media/:/movies` -> with nfs export `/mnt/media`
![setting](https://user-images.githubusercontent.com/5340247/174437861-c1db621a-d4b2-4696-b33c-5152c0c67fb6.png)
- enable the `Direct Path` option in the JellyfinTv client
### NOTE: starting with firmware 6.(4/7).30+ you dont need the special `/:` NFS root-marker anymore!
### Community
- **for major bugs/issues regarding the Zidoo-Edition, please open a github issue**
- for questions, suggestions or help use the [Zidoo forum](http://forum.zidoo.tv/index.php)
	- [Support Post](http://forum.zidoo.tv/index.php?threads/jellyfintv-zidoo-edition-support-post.93902/) for JellyfinTv Zidoo-Edition
- german Community [Zidoo forum](https://www.android-mediaplayer.de/forum/index.php?board/82-zidoo-player-x6-pro-x8-x9s-z9s-z9x-x10-z10-z10pro-x20-x20pro-z1000-z1000pro-uhd2/)
- AVS forum [Zidoo Post](https://www.avsforum.com/threads/zidoo-z9x-rtd1619-thread.3140924/page-999)
- Zidoo Community software site www.mcbluna.net
