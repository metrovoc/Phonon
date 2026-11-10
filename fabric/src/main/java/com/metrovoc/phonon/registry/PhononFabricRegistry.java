package com.metrovoc.phonon.registry;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.item.BoomboxItem;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;
import java.util.UUID;

/** Registers what NeoForge's PhononRegistry registers, under the same names. */
public final class PhononFabricRegistry {
    private PhononFabricRegistry() {}

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, name);
    }

    public static void register() {
        ResourceKey<Block> speakerKey = ResourceKey.create(Registries.BLOCK, id("speaker"));
        Block speaker = Registry.register(BuiltInRegistries.BLOCK, speakerKey,
            new SpeakerBlock(BlockBehaviour.Properties.of().setId(speakerKey)));

        BlockEntityType<SpeakerBlockEntity> speakerEntity = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("speaker"),
            new BlockEntityType<>(SpeakerBlockEntity::new, Set.of(speaker)));

        ResourceKey<Item> speakerItemKey = ResourceKey.create(Registries.ITEM, id("speaker"));
        Item speakerItem = Registry.register(BuiltInRegistries.ITEM, speakerItemKey,
            new BlockItem(speaker, new Item.Properties().setId(speakerItemKey).useBlockDescriptionPrefix()));

        DataComponentType<UUID> boomboxId = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("boombox_id"),
            DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());
        DataComponentType<Boolean> boomboxPlaying = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("boombox_playing"),
            DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

        ResourceKey<Item> boomboxKey = ResourceKey.create(Registries.ITEM, id("boombox"));
        Item boombox = Registry.register(BuiltInRegistries.ITEM, boomboxKey,
            new BoomboxItem(new Item.Properties().setId(boomboxKey).stacksTo(1)));

        MenuType<SpeakerMenu> speakerMenu = Registry.register(BuiltInRegistries.MENU, id("speaker"),
            new MenuType<>((id, inv) -> new SpeakerMenu(id, inv, new Receiver.Block(BlockPos.ZERO)), FeatureFlags.DEFAULT_FLAGS));

        SpeakerBlockEntity.setTypeSupplier(() -> speakerEntity);
        SpeakerMenu.setTypeSupplier(() -> speakerMenu);
        BoomboxItem.setComponents(() -> boomboxId, () -> boomboxPlaying);

        // Beside the blocks players would look for it next to.
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output ->
            output.insertAfter(Items.MUSIC_DISC_5, boombox));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->
            output.insertAfter(Items.JUKEBOX, speakerItem));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output ->
            output.insertAfter(new ItemStack(Items.NOTE_BLOCK), java.util.List.of(new ItemStack(speakerItem)),
                CreativeModeTab.TabVisibility.PARENT_TAB_ONLY));
    }
}
