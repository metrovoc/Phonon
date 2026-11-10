stonecutter active "26.3-neoforge"

stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)

    // Version- and loader-specific properties from stonecutter.properties.toml
    properties {
        tags(current.version, loader)
    }

    // `//? if fabric {` and `//? if neoforge {`
    constants {
        match(loader, "fabric", "neoforge")
    }

    // Renames that touch many lines. The source has the newest names; nodes before `until` get the old ones.
    // Stonecutter applies the reverse on newer nodes, so each name only matches as a whole word.
    replacements {
        fun pattern(name: String): String {
            val start = if (name.first().isLetterOrDigit()) "(?<![\\w])" else ""
            val end = if (name.last().isLetterOrDigit()) "(?![\\w(])" else ""
            return start + Regex.escape(name) + end
        }
        fun rename(until: String, vararg pairs: Pair<String, String>) {
            for ((new, old) in pairs) regex(current.parsed < until) {
                replace(pattern(new), Regex.escapeReplacement(old), pattern(old), Regex.escapeReplacement(new))
            }
        }

        rename("26.2",
            "mc.gui.screen()" to "mc.screen",
            "getInstance().gui.screen()" to "getInstance().screen",
            "mc.gui.setScreen(" to "mc.setScreen(",
            "minecraft.gui.setScreen(" to "minecraft.setScreen(",
            "getInstance().gui.setScreen(" to "getInstance().setScreen(",
            "mc.gui.overlay()" to "mc.getOverlay()",
            "mc.gui.toastManager()" to "mc.getToastManager()",
            "mc.gameRenderer.mainRenderTarget()" to "mc.getMainRenderTarget()")
        rename("26.1",
            "GuiGraphicsExtractor" to "GuiGraphics",
            "graphics.text(" to "graphics.drawString(",
            "graphics.centeredText(" to "graphics.drawCenteredString(",
            "ChunkPos.containing(" to "new ChunkPos(",
            "ContainerInput" to "ClickType",
            "handleContainerInput(" to "handleInventoryMouseClick(",
            "mc.resizeGui()" to "mc.resizeDisplay()",
            // Fabric API names before it moved to Mojang's
            "PayloadTypeRegistry.clientboundConfiguration()" to "PayloadTypeRegistry.configurationS2C()",
            "PayloadTypeRegistry.clientboundPlay()" to "PayloadTypeRegistry.playS2C()",
            "PayloadTypeRegistry.serverboundPlay()" to "PayloadTypeRegistry.playC2S()",
            "creativetab.v1.CreativeModeTabEvents" to "itemgroup.v1.ItemGroupEvents",
            "CreativeModeTabEvents.modifyOutputEvent(" to "ItemGroupEvents.modifyEntriesEvent(",
            "output.insertAfter(" to "output.addAfter(")
        rename("1.21.11",
            "Identifier" to "ResourceLocation", "animal.parrot.Parrot" to "animal.Parrot")
        rename("1.21.9", "getWindow().handle()" to "getWindow().getWindow()",
            "isClientSide()" to "isClientSide",
            "FMLEnvironment.getDist()" to "FMLEnvironment.dist")
        rename("1.21.6", "ServerLevel level = player.level()" to "ServerLevel level = player.serverLevel()",
            "getPlayer(id).level()" to "getPlayer(id).serverLevel()")
        rename("1.21.2", "BuiltInRegistries.ITEM.getValue(" to "BuiltInRegistries.ITEM.get(",
            "BuiltInRegistries.ENTITY_TYPE.getValue(" to "BuiltInRegistries.ENTITY_TYPE.get(",
            "BuiltInRegistries.CREATIVE_MODE_TAB.getValue(" to "BuiltInRegistries.CREATIVE_MODE_TAB.get(")
    }
}
