package org.lolicode.moemusic.ncmlite

import org.lolicode.moemusic.api.plugin.Plugin
import org.lolicode.moemusic.api.plugin.PluginProvider

class NCMPluginProvider: PluginProvider {
    override fun plugins(): Iterable<Plugin> = listOf(NCMPlugin)
}
