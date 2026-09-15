package org.lolicode.moemusic.ncmlite.platform

import net.neoforged.fml.common.Mod
import org.lolicode.moemusic.api.MoeMusicApi
import org.lolicode.moemusic.ncmlite.NCMPlugin

/**
 * NeoForge mod entrypoint.
 *
 * Instantiated by NeoForge FML during mod loading to register [NCMPlugin]
 * with MoeMusic's public plugin API.
 */
@Mod(NCMPlugin.MOD_ID)
class NeoForgeEntrypoint {
    init {
        MoeMusicApi.registerPlugin(NCMPlugin)
    }
}
