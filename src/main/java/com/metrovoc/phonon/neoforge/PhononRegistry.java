package com.metrovoc.phonon.neoforge;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.audio.Receiver;
import com.metrovoc.phonon.item.BoomboxItem;
import com.metrovoc.phonon.menu.SpeakerMenu;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
    private static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(Constants.MOD_ID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    private static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(Constants.MOD_ID);

    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(net.minecraft.core.registries.Registries.MENU, Constants.MOD_ID);

    public static final Supplier<Block> SPEAKER_BLOCK =
        //? if >=1.21.10 {
        BLOCKS.registerBlock("speaker", SpeakerBlock::new);
        //?} else
        /*BLOCKS.registerBlock("speaker", SpeakerBlock::new, net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());*/

    public static final Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
        //? if >=1.21.2 {
        BLOCK_ENTITIES.register("speaker", () -> new BlockEntityType<>(
            SpeakerBlockEntity::new,
            SPEAKER_BLOCK.get()
        ));
        //?} else
        /*BLOCK_ENTITIES.register("speaker", () -> BlockEntityType.Builder.of(SpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null));*/

    public static final Supplier<? extends Item> SPEAKER_ITEM =
        ITEMS.registerSimpleBlockItem("speaker", SPEAKER_BLOCK);

    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);

    public static final Supplier<DataComponentType<UUID>> BOOMBOX_ID = COMPONENTS.registerComponentType("boombox_id",
        builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final Supplier<DataComponentType<Boolean>> BOOMBOX_PLAYING = COMPONENTS.registerComponentType("boombox_playing",
        builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final Supplier<? extends Item> BOOMBOX_ITEM =
        //? if >=1.21.10 {
        ITEMS.registerItem("boombox", BoomboxItem::new, properties -> properties.stacksTo(1));
        //?} else
        /*ITEMS.registerItem("boombox", BoomboxItem::new, new Item.Properties().stacksTo(1));*/

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
