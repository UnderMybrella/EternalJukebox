package dev.eternalbox.eternaljukebox.data

object WebApiResponseMessages {
    //4xx
    const val API_ROUTE_NOT_FOUND = "route_not_found"
    const val API_METHOD_NOT_ALLOWED_FOR_ROUTE = "method_not_allowed_for_route"

    const val INVALID_ANALYSIS_SERVICE = "invalid_analysis_service"
    const val INVALID_ANALYSIS_DATA = "invalid_analysis_data"
    const val INVALID_UPLOADED_ANALYSIS = "invalid_uploaded_analysis"

    const val ANALYSIS_MISSING_BARS = "analysis_missing_bars"
    const val ANALYSIS_MISSING_SEGMENTS = "analysis_missing_segments"
    const val ANALYSIS_MISSING_BEATS = "analysis_missing_beats"
    const val ANALYSIS_MISSING_SECTIONS = "analysis_missing_sections"
    const val ANALYSIS_MISSING_TATUMS = "analysis_missing_tatums"

    const val INVALID_AUDIO_SERVICE = "invalid_audio_service"

    //5xx
    const val API_NOT_IMPLEMENTED = "api_not_implemented"

    const val NO_ANALYSIS_PROVIDER = "no_analysis_provider"
    const val ANALYSIS_NOT_STORED = "analysis_not_stored"

    const val NO_AUDIO_PROVIDER = "no_audio_provider"
    const val AUDIO_NOT_STORED = "audio_not_stored"

    const val NO_CONFIGURE_FILE = "no_configure_file"
}