package dev.eternalbox.ytmusicapi

data class YoutubeMusicSearchResponse(val contents: YTMSRContents)
data class YTMSRContents(val sectionListRenderer: YTMSRCSectionListRenderer)
data class YTMSRCSectionListRenderer(val contents: Array<YTMSRCSLRContents>)
data class YTMSRCSLRContents(val musicShelfRenderer: YTMSRCSLRCMusicShelfRenderer)
data class YTMSRCSLRCMusicShelfRenderer(val title: YTMSRCSLRCMSRTitle, val contents: Array<UnknownJsonObj>)
data class YTMSRCSLRCMSRTitle(val runs: Array<YTMText>)

data class YTMSRSongContents(val musicResponsiveListItemRenderer: YTMSRSCMusicResponsiveListItemRenderer)
data class YTMSRSCMusicResponsiveListItemRenderer(val overlay: YTMSRSCMRLIROverlay, val flexColumns: Array<YTMSRSCMRLIRFlexColumn>)

data class YTMSRSCMRLIROverlay(val musicItemThumbnailOverlayRenderer: YTMSRSCMRLIROMusicItemThumbnailOverlayRenderer)
data class YTMSRSCMRLIROMusicItemThumbnailOverlayRenderer(val content: YTMSRSCMRLIROMITORContent)
data class YTMSRSCMRLIROMITORContent(val musicPlayButtonRenderer: YTMSRSCMRLIROMITORCMusicPlayButtonRenderer)
data class YTMSRSCMRLIROMITORCMusicPlayButtonRenderer(val playNavigationEndpoint: YTMSRSCMRLIROMITORCMPBRPlayNavigationEndpoint)
data class YTMSRSCMRLIROMITORCMPBRPlayNavigationEndpoint(val watchEndpoint: YTMSRSCMRLIROMITORCMPBRPNEWatchEndpoint)
data class YTMSRSCMRLIROMITORCMPBRPNEWatchEndpoint(val videoId: String, val playlistId: String, val params: String)

data class YTMSRSCMRLIRFlexColumn(val musicResponsiveListItemFlexColumnRenderer: YTMSRSCMRLIRFCMusicResponsiveListItemFlexColumnRenderer)
data class YTMSRSCMRLIRFCMusicResponsiveListItemFlexColumnRenderer(val text: YTMSRSCMRLIRFCMRLIFCRText)
data class YTMSRSCMRLIRFCMRLIFCRText(val runs: Array<YTMSRSCMRLIRFCMRLIFCRTRun>)
data class YTMSRSCMRLIRFCMRLIFCRTRun(val text: String, val navigationEndpoint: YTMSRSCMRLIRFCMRLIFCRTNavigationEndpoint? = null)
data class YTMSRSCMRLIRFCMRLIFCRTNavigationEndpoint(val browseEndpoint: YTMSRSCMRLIRFCMRLIFCRTNEBrowseEndpoint)
data class YTMSRSCMRLIRFCMRLIFCRTNEBrowseEndpoint(val browseId: String, val browseEndpointContextSupportedConfigs: YTMSRSCMRLIRFCMRLIFCRTNEBEBrowseEndpointContextSupportedConfigs)
data class YTMSRSCMRLIRFCMRLIFCRTNEBEBrowseEndpointContextSupportedConfigs(val browseEndpointContextMusicConfig: YTMSRSCMRLIRFCMRLIFCRTNEBEBECSCBrowseEndpointContextMusicConfig)
data class YTMSRSCMRLIRFCMRLIFCRTNEBEBECSCBrowseEndpointContextMusicConfig(val pageType: String)

data class YTMTextRuns(val runs: Array<YTMText>)
data class YTMText(val text: String)

data class YTMSong(val songID: String, val playlistID: String, val params: String, val artist: YTMArtist?, val album: YTMAlbum?)
data class YTMArtist(val artistName: String, val channelID: String)
data class YTMAlbum(val albumName: String, val albumID: String)

//data class MusicMenuItem(val toggleMenuServiceItemRenderer: YoutubeMusicSearchResponseContents.ToggleMenuServiceItemRenderer)