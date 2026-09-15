package org.lolicode.moemusic.ncmlite.platform

import net.minecraftforge.fml.common.Mod
import org.lolicode.moemusic.api.MoeMusicApi
import org.lolicode.moemusic.ncmlite.NCMPlugin

/**
 * Minecraft Forge mod entrypoint.
 *
 * Instantiated by Forge FML during mod loading to register [NCMPlugin]
 * with MoeMusic's public plugin API.
 */
@Mod(NCMPlugin.MOD_ID)
class ForgeEntrypoint {
    init {
        MoeMusicApi.registerPlugin(NCMPlugin)
    }
}
