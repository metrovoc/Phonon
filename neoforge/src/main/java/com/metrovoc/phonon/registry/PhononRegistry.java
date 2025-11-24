package com.metrovoc.phonon.registry;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.menu.SpeakerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class PhononRegistry {
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(BuiltInRegistries.BLOCK, Constants.MOD_ID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);

    private static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, Constants.MOD_ID);

    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Constants.MOD_ID);

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

    public static final Supplier<SoundEvent> SPEAKER_SOUND =
        SOUND_EVENTS.register("speaker", () ->
            SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "speaker")
            )
        );

    public static final Supplier<MenuType<SpeakerMenu>> SPEAKER_MENU =
        MENU_TYPES.register("speaker", () ->
            new MenuType<>((id, inv) -> new SpeakerMenu(id, inv, BlockPos.ZERO), FeatureFlags.DEFAULT_FLAGS)
        );

    public static void register(IEventBus modBus) {
        SpeakerBlockEntity.setTypeSupplier(SPEAKER_BLOCK_ENTITY);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUND_EVENTS.register(modBus);
        MENU_TYPES.register(modBus);
    }
}
