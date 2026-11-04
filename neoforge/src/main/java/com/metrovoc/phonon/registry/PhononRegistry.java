package com.metrovoc.phonon.registry;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.item.BoomboxItem;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

public class PhononRegistry {
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(BuiltInRegistries.BLOCK, Constants.MOD_ID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, Constants.MOD_ID);

    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(BuiltInRegistries.MENU, Constants.MOD_ID);

    public static final Supplier<Block> SPEAKER_BLOCK =
        BLOCKS.register("speaker", SpeakerBlock::new);

    public static final Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("speaker", () ->
            BlockEntityType.Builder.of(
                SpeakerBlockEntity::new,
                SPEAKER_BLOCK.get()
            ).build(null)
        );

    public static final Supplier<Item> SPEAKER_ITEM =
        ITEMS.register("speaker", () ->
            new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties())
        );

    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);

    public static final Supplier<DataComponentType<UUID>> BOOMBOX_ID = COMPONENTS.registerComponentType("boombox_id",
        builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final Supplier<DataComponentType<Boolean>> BOOMBOX_PLAYING = COMPONENTS.registerComponentType("boombox_playing",
        builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final Supplier<? extends Item> BOOMBOX_ITEM =
        ITEMS.register("boombox", () -> new BoomboxItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<MenuType<SpeakerMenu>> SPEAKER_MENU =
        MENU_TYPES.register("speaker", () ->
            new MenuType<>((id, inv) -> new SpeakerMenu(id, inv, new Receiver.Block(BlockPos.ZERO)), FeatureFlags.DEFAULT_FLAGS)
        );

    /** Beside the blocks players would look for it next to. */
    public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        ItemStack speaker = new ItemStack(SPEAKER_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.insertAfter(new ItemStack(Items.MUSIC_DISC_5), new ItemStack(BOOMBOX_ITEM.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.insertAfter(new ItemStack(Items.JUKEBOX), speaker, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } else if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.insertAfter(new ItemStack(Items.NOTE_BLOCK), speaker, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
    }

    public static void register(IEventBus modBus) {
        SpeakerBlockEntity.setTypeSupplier(SPEAKER_BLOCK_ENTITY);
        SpeakerMenu.setTypeSupplier(SPEAKER_MENU);
        BoomboxItem.setComponents(BOOMBOX_ID, BOOMBOX_PLAYING);
        COMPONENTS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        MENU_TYPES.register(modBus);
    }
}
