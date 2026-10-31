
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

## 中文说明 — Jellyfin Zidoo（Z9X 4K 适配版）

### 这是什么

基于**官方 Jellyfin Android TV 客户端 0.14.10** 修改的 **Zidoo 播放器专用版**。它把视频交给芝杜自带的硬件播放器播放，从而绕开官方客户端在这类 Realtek 机型上播放 4K/HDR 会花屏的问题。

### 适用范围

| 项目 | 范围 |
| --- | --- |
| 播放器设备 | Zidoo Z9X / Realtek RTD1619（Android 9，32 位 `armeabi-v7a`） |
| Jellyfin 服务端 | **10.11.x**（已在 **10.11.11** 实测通过） |
| 上游基线 | jellyfin-androidtv 0.14.10 |
| 维护版本 | `0.14.10-zidoo-xjn.3` |

其他 Zidoo/Realtek 机型可能可用但**未实测**。服务端低于 10.11 的版本沿用上游兼容性，本次未验证。

### 安装

1. 下载 Release 中的 `jellyfin-zidoo-*-xjn.3-*.apk`
2. 用 U 盘、芝杜文件管理器或 `adb install -r` 安装
3. 首次启动按提示完成 **Quick Connect** 登录（或用账号密码）

包名 `org.jellyfin.androidtv.zidoo`，与官方客户端**互不冲突**，可并存；若不再使用官方版可自行卸载。

### 使用说明

- **播放**：在片库选择影片或剧集 → 点「播放」。视频会自动交给芝杜原生播放器，遥控器操作、字幕、音轨均由原生播放器接管。
- **续播**：退出后进度自动保存，下次点「自 XX:XX 恢复」即可继续。
- **字幕**：默认跟随 Jellyfin 个人资料里的字幕偏好（`SubtitleMode` / 字幕语言）；未设置时优先选中文件标记为「默认」的字幕轨，多条中文字幕并存时**优先简体**。
- **音轨**：默认自动挑选最佳音轨（如 TrueHD 7.1）；可在设置中强制指定语言或关闭某类音轨。
- **转码**：播放设置里的「最大串流码率」低于片源码率时，服务端会自动转码后交给原生播放器。
- **桌面**：建议设为开机/默认桌面（Aerial 等），本客户端不接管桌面功能。

### 优点

- **4K / HDR10 原画直放**，画面正常，不受官方客户端花屏问题影响（杜比视界**未实测**）
- **PGS 位图字幕**正确渲染（内嵌/外挂均可）
- **TrueHD / Atmos 等无损音轨透传**
- 进度保存、续播、已看状态、下一集队列等 Jellyfin 原有能力均保留
- **服务端硬件转码**可用（**VAAPI 已实测**；NVENC 等其他硬件后端未实测），转码流也能交给原生播放器
- 字幕选轨跟随 Jellyfin 个人设置，简体优先、结果确定可预期
- 与官方客户端包名不同，可并存，不覆盖原有配置

### 已知缺陷与限制

- **转码流播放过程中无法拖动进度**：芝杜原生播放器对分段 HTTP 转码流的 seek 存在固件限制，只能线性播放。这是设备/固件限制，客户端侧无法修复。绕法：直放（画质设置保持不限速）或退回官方播放器。
- **转码流的播放时间显示从 0 开始**：原生播放器显示的是相对当前流起点的时间；Jellyfin 内记录的片内位置是正确的。
- **依赖芝杜的 `control.center` 服务**（9529 端口）来控制原生播放器，请勿停用该系统服务。
- **硬件解码不依赖本客户端**：任何 App 都能调用 Realtek 的 `OMX.realtek` 解码器；保留原生播放器是为了绕开官方客户端的花屏、以及获得 PGS 字幕与无损透传。
- **HDMI 输出**为 1080p SDR 时，即使片源是 4K HDR10 也按 SDR 输出；分辨率/HDR 切换是设备设置，本应用不修改。
- 上游作者记录的其他限制（如转码流字幕需回退一次才显示）可能仍存在。

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

### Verified on（实测记录）

在 **ZIDOO Z9X**（Realtek RTD1619，Android 9，固件 `v6.4.42_G`）+ Jellyfin 服务端 **10.11.11** 上，调用芝杜原生播放器（`com.android.gallery3d/.app.MovieActivity`）实测：

- **登录**：Quick Connect 完成登录并加载片库；账号、会话与能力字段解析正常
- **直放**：3840x2160 HEVC Main10 HDR10（约 75 Mbps，TrueHD 7.1）通过原生播放器播放；进度保存、退出后恢复、续播均正常
- **转码**：服务端码率低于片源时可转码，原生播放器可播放转码流，服务端回报 `PlayMethod=Transcode` 且启用硬件加速（VAAPI）；进度保存与续播正常
- **转码限制**：转码流播放中无法 seek（原生播放器固件限制，上游作者亦有记录），只能线性播放
- **字幕**：跟随 Jellyfin 个人资料的 `SubtitleMode` / 字幕语言；同分时确定性取第一条，简体优先于繁体。已在两类真实片库实测：5 条字幕全为默认的 PGS 集（原先选中「繁體」，现选中「中文(简体)」），以及 39 条 SRT 且简繁排在最后的剧集（原先选中「Traditional」，现选中「中文(简体)」）。音轨选择保持不变（TrueHD 7.1 仍正确选中）

测试环境的 HDMI 输出为 1080p SDR（即使片源是 4K HDR10）；分辨率/HDR 切换属于设备设置，本应用不修改。


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
