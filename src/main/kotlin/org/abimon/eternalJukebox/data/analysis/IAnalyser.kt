package org.abimon.eternalJukebox.data.analysis

import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.eternalJukebox.objects.JukeboxTrack

interface IAnalyser {
    /**
     * Search for tracks based on the provided query.
     * @return An array of track information that matches the query
     */
    fun search(query: String, clientInfo: ClientInfo?): Array<JukeboxInfo>

    /**
     * Analyse the given ID
     */
    fun analyse(id: String, clientInfo: ClientInfo?): JukeboxTrack?

    /**
     * Get track information from an ID
     */
    fun getInfo(id: String, clientInfo: ClientInfo?): JukeboxInfo?
}