package com.metrovoc.phonon.registry;

import com.metrovoc.phonon.Constants;
import com.metrovoc.phonon.block.SpeakerBlock;
import com.metrovoc.phonon.block.SpeakerBlockEntity;
import com.metrovoc.phonon.menu.SpeakerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

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
        BLOCKS.registerBlock("speaker", SpeakerBlock::new);

    public static final Supplier<BlockEntityType<SpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("speaker", () -> new BlockEntityType<>(
            SpeakerBlockEntity::new,
            SPEAKER_BLOCK.get()
        ));

    public static final Supplier<? extends Item> SPEAKER_ITEM =
        ITEMS.registerSimpleBlockItem("speaker", SPEAKER_BLOCK);

    public static final Supplier<MenuType<SpeakerMenu>> SPEAKER_MENU =
        MENU_TYPES.register("speaker", () ->
            new MenuType<>((id, inv) -> new SpeakerMenu(id, inv, BlockPos.ZERO), FeatureFlags.DEFAULT_FLAGS)
        );

    public static void register(IEventBus modBus) {
        SpeakerBlockEntity.setTypeSupplier(SPEAKER_BLOCK_ENTITY);
        SpeakerMenu.setTypeSupplier(SPEAKER_MENU);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        MENU_TYPES.register(modBus);
    }
}
