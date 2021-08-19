package de.dafuqs.spectrum.blocks.pedestal;

import de.dafuqs.spectrum.InventoryHelper;
import de.dafuqs.spectrum.SpectrumCommon;
import de.dafuqs.spectrum.Support;
import de.dafuqs.spectrum.enums.GemstoneColor;
import de.dafuqs.spectrum.interfaces.PlayerOwned;
import de.dafuqs.spectrum.inventories.AutoCraftingInventory;
import de.dafuqs.spectrum.inventories.PedestalScreenHandler;
import de.dafuqs.spectrum.items.misc.CraftingTabletItem;
import de.dafuqs.spectrum.networking.SpectrumS2CPackets;
import de.dafuqs.spectrum.progression.SpectrumAdvancementCriteria;
import de.dafuqs.spectrum.recipe.SpectrumRecipeTypes;
import de.dafuqs.spectrum.recipe.pedestal.PedestalCraftingRecipe;
import de.dafuqs.spectrum.registries.SpectrumBlockEntityRegistry;
import de.dafuqs.spectrum.registries.SpectrumBlocks;
import de.dafuqs.spectrum.registries.SpectrumItems;
import de.dafuqs.spectrum.sound.SpectrumSoundEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.*;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Random;
import java.util.UUID;

public class PedestalBlockEntity extends LockableContainerBlockEntity implements RecipeInputProvider, SidedInventory, PlayerOwned, ExtendedScreenHandlerFactory {

    private UUID ownerUUID;
    private String ownerName;
    private PedestalBlock.PedestalVariant pedestalVariant;

    protected DefaultedList<ItemStack> inventory;

    private float storedXP;
    private int craftingTime;
    private int craftingTimeTotal;

    protected final PropertyDelegate propertyDelegate;
    private static final RecipeType<? extends PedestalCraftingRecipe> recipeType = SpectrumRecipeTypes.PEDESTAL;
    private Recipe lastRecipe;

    private static AutoCraftingInventory autoCraftingInventory;

    public static final int INVENTORY_SIZE = 16; // 9 crafting, 5 gems, 1 craftingTablet, 1 output
    public static final int CRAFTING_TABLET_SLOT_ID = 14;
    public static final int OUTPUT_SLOT_ID = 15;

    public PedestalBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(SpectrumBlockEntityRegistry.PEDESTAL, blockPos, blockState);

        if(blockState.getBlock() instanceof PedestalBlock) {
            this.pedestalVariant = blockState.get(PedestalBlock.VARIANT);
        } else {
            this.pedestalVariant = PedestalBlock.PedestalVariant.BASIC_AMETHYST;
        }

        if(autoCraftingInventory == null) {
            autoCraftingInventory = new AutoCraftingInventory(3, 3);
        }

        this.inventory  = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);

        this.propertyDelegate = new PropertyDelegate() {
            public int get(int index) {
                return switch (index) {
                    case 0 -> PedestalBlockEntity.this.craftingTime;
                    default -> PedestalBlockEntity.this.craftingTimeTotal;
                };
            }

            public void set(int index, int value) {
                switch (index) {
                    case 0 -> PedestalBlockEntity.this.craftingTime = value;
                    case 1 -> PedestalBlockEntity.this.craftingTimeTotal = value;
                }
            }

            public int size() {
                return 2;
            }
        };
    }

    public PedestalBlock.PedestalVariant getVariant() {
        return this.pedestalVariant;
    }

    public void setVariant(PedestalBlock.PedestalVariant pedestalVariant) {
        this.pedestalVariant = pedestalVariant;
        this.propertyDelegate.set(2, pedestalVariant.ordinal());
    }

    @Override
    public Text getContainerName() {
        return new TranslatableText("block.spectrum.pedestal");
    }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory playerInventory) {
        return new PedestalScreenHandler(syncId, playerInventory, this, this.propertyDelegate, this.pedestalVariant.ordinal());
    }

    @Override
    public int size() {
        return this.inventory.size();
    }

    @Override
    public boolean isEmpty() {
        Iterator<ItemStack> var1 = this.inventory.iterator();

        ItemStack itemStack;
        do {
            if (!var1.hasNext()) {
                return true;
            }

            itemStack = var1.next();
        } while(itemStack.isEmpty());

        return false;
    }

    @Override
    public ItemStack getStack(int slot) {
        return this.inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(this.inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(this.inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        ItemStack itemStack = this.inventory.get(slot);
        boolean isSimilarItem = !stack.isEmpty() && stack.isItemEqualIgnoreDamage(itemStack) && ItemStack.areTagsEqual(stack, itemStack);
        this.inventory.set(slot, stack);
        if (stack.getCount() > this.getMaxCountPerStack()) {
            stack.setCount(this.getMaxCountPerStack());
        }

        if (slot < CRAFTING_TABLET_SLOT_ID && !isSimilarItem) {
            this.craftingTimeTotal = getCraftingTime(this.world, recipeType, this);
            this.craftingTime = 0;
            this.markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (this.world.getBlockEntity(this.pos) != this) {
            return false;
        } else {
            return player.squaredDistanceTo((double)this.pos.getX() + 0.5D, (double)this.pos.getY() + 0.5D, (double)this.pos.getZ() + 0.5D) <= 64.0D;
        }
    }

    @Override
    public void provideRecipeInputs(RecipeMatcher recipeMatcher) {
        for (ItemStack itemStack : this.inventory) {
            recipeMatcher.addInput(itemStack);
        }
    }

    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
        this.inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
        Inventories.readNbt(tag, this.inventory);
        this.storedXP = tag.getFloat("StoredXP");
        this.craftingTime = tag.getShort("CraftingTime");
        this.craftingTimeTotal = tag.getShort("CraftingTimeTotal");
        if(tag.contains("OwnerUUID")) {
            this.ownerUUID = tag.getUuid("OwnerUUID");
        } else {
            this.ownerUUID = null;
        }
        if(tag.contains("OwnerName")) {
            this.ownerName = tag.getString("OwnerName");
        } else {
            this.ownerName = "???";
        }
    }

    public NbtCompound writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putFloat("StoredXP", this.storedXP);
        tag.putShort("CraftingTime", (short)this.craftingTime);
        tag.putShort("CraftingTimeTotal", (short)this.craftingTimeTotal);

        if(this.ownerUUID != null) {
            tag.putUuid("OwnerUUID", this.ownerUUID);
        }
        if(this.ownerName != null) {
            tag.putString("OwnerName", this.ownerName);
        }
        Inventories.writeNbt(tag, this.inventory);
        return tag;
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    private boolean isCrafting() {
        return this.craftingTime > 0;
    }

    public static void serverTick(World world, BlockPos blockPos, BlockState blockState, PedestalBlockEntity pedestalBlockEntity) {
        // only craft when there is redstone power
        Block block = world.getBlockState(blockPos).getBlock();
        if(block.equals(SpectrumBlocks.PEDESTAL) && blockState.get(PedestalBlock.STATE) == PedestalBlock.PedestalState.POWERED) {
            // check recipe crafted last tick => performance
            PedestalCraftingRecipe pedestalCraftingRecipe = null;
            CraftingRecipe craftingRecipe = null;
            boolean shouldMarkDirty = false;

            Recipe validRecipe = calculateRecipe(world, pedestalBlockEntity);
            if(validRecipe instanceof PedestalCraftingRecipe) {
                pedestalCraftingRecipe = (PedestalCraftingRecipe) validRecipe;
                pedestalBlockEntity.lastRecipe = validRecipe;
                pedestalBlockEntity.craftingTimeTotal = pedestalCraftingRecipe.getCraftingTime();
            } else if(validRecipe instanceof CraftingRecipe) {
                craftingRecipe = (CraftingRecipe) validRecipe;
                pedestalBlockEntity.lastRecipe = validRecipe;
                pedestalBlockEntity.craftingTimeTotal = 20;
            } else {
                // no valid recipe
                pedestalBlockEntity.craftingTime = 0;
            }

            int maxCountPerStack = pedestalBlockEntity.getMaxCountPerStack();
            boolean crafting = pedestalBlockEntity.isCrafting();

            // Pedestal crafting
            boolean craftingFinished = false;
            if (canAcceptRecipeOutput(pedestalCraftingRecipe, pedestalBlockEntity.inventory, maxCountPerStack)) {
                pedestalBlockEntity.craftingTime++;
                if (pedestalBlockEntity.craftingTime == pedestalBlockEntity.craftingTimeTotal) {
                    pedestalBlockEntity.craftingTime = 0;
                    craftingFinished = craftPedestalRecipe(pedestalBlockEntity, pedestalCraftingRecipe, pedestalBlockEntity.inventory, maxCountPerStack);
                    shouldMarkDirty = true;
                }
            // Vanilla crafting
            } else if(canAcceptRecipeOutput(craftingRecipe, pedestalBlockEntity.inventory, maxCountPerStack)) {
                pedestalBlockEntity.craftingTime++;
                if (pedestalBlockEntity.craftingTime == pedestalBlockEntity.craftingTimeTotal) {
                    pedestalBlockEntity.craftingTime = 0;
                    craftingFinished = pedestalBlockEntity.craftVanillaRecipe(craftingRecipe, pedestalBlockEntity.inventory, maxCountPerStack);
                    shouldMarkDirty = true;
                }
            // No crafting
            } else {
                pedestalBlockEntity.craftingTime = 0;
            }

            if (crafting != pedestalBlockEntity.isCrafting()) {
                shouldMarkDirty = true;
            }

            // try to output the currently stored output stack
            ItemStack outputItemStack = pedestalBlockEntity.inventory.get(OUTPUT_SLOT_ID);
            if (outputItemStack != ItemStack.EMPTY) {
                if (world.getBlockState(blockPos.up()).isAir()) {
                    spawnOutputAsItemEntity(world, blockPos, pedestalBlockEntity, outputItemStack, craftingRecipe, pedestalCraftingRecipe);
                    playCraftingFinishedSoundEvent(pedestalBlockEntity, craftingRecipe, pedestalCraftingRecipe);
                } else {
                    BlockEntity aboveBlockEntity = world.getBlockEntity(blockPos.up());
                    if (aboveBlockEntity instanceof Inventory) {
                        boolean putIntoAboveInventorySuccess = tryPutOutputIntoAboveInventory(world, blockPos, pedestalBlockEntity, (Inventory) aboveBlockEntity, outputItemStack, craftingRecipe, pedestalCraftingRecipe);
                        if(putIntoAboveInventorySuccess) {
                            playCraftingFinishedSoundEvent(pedestalBlockEntity, craftingRecipe, pedestalCraftingRecipe);
                        } else {
                            // play sound when the entity can not put it's output anywhere
                            if (craftingFinished) {
                                pedestalBlockEntity.playSound(SoundEvents.BLOCK_LAVA_EXTINGUISH);
                            }
                        }
                    } else {
                        // play sound when the entity can not put it's output anywhere
                        if (craftingFinished) {
                            pedestalBlockEntity.playSound(SoundEvents.BLOCK_LAVA_EXTINGUISH);
                        }
                    }
                }
            }

            if (shouldMarkDirty) {
                markDirty(world, blockPos, blockState);
            }
        }
    }

    public static PedestalBlock.PedestalVariant getVariant(PedestalBlockEntity pedestalBlockEntity) {
        return pedestalBlockEntity.pedestalVariant;
    }

    public static void spawnOutputAsItemEntity(World world, BlockPos blockPos, PedestalBlockEntity pedestalBlockEntity, ItemStack outputItemStack, CraftingRecipe craftingRecipe, PedestalCraftingRecipe pedestalCraftingRecipe) {
        // spawn crafting output
        ItemEntity itemEntity = new ItemEntity(world, pedestalBlockEntity.pos.getX() + 0.5, pedestalBlockEntity.pos.getY() + 1, pedestalBlockEntity.pos.getZ() + 0.5, outputItemStack);
        itemEntity.addVelocity(0, 0.1, 0);
        world.spawnEntity(itemEntity);
        pedestalBlockEntity.inventory.set(OUTPUT_SLOT_ID, ItemStack.EMPTY);

        // spawn XP
        if (pedestalBlockEntity.storedXP > 0) {
            int spawnedXPAmount = Support.getWholeIntFromFloatWithChance(pedestalBlockEntity.storedXP, pedestalBlockEntity.getWorld().random);
            ExperienceOrbEntity experienceOrbEntity = new ExperienceOrbEntity(world, pedestalBlockEntity.pos.getX() + 0.5, pedestalBlockEntity.pos.getY() + 1, pedestalBlockEntity.pos.getZ() + 0.5, spawnedXPAmount);
            world.spawnEntity(experienceOrbEntity);
            pedestalBlockEntity.storedXP = 0;
        }

        // only triggered on server side. Therefore, has to be sent to client via S2C packet
        SpectrumS2CPackets.sendPlayPedestalCraftingFinishedParticle(world, blockPos, outputItemStack);
    }

    public static boolean tryPutOutputIntoAboveInventory(World world, BlockPos blockPos, PedestalBlockEntity pedestalBlockEntity, Inventory targetInventory, ItemStack outputItemStack, CraftingRecipe craftingRecipe, PedestalCraftingRecipe pedestalCraftingRecipe) {
        ItemStack remainingStack = InventoryHelper.addToInventory(outputItemStack, targetInventory, Direction.DOWN);
        if(remainingStack.isEmpty()) {
            pedestalBlockEntity.inventory.set(OUTPUT_SLOT_ID, ItemStack.EMPTY);
            return true;
        } else {
            pedestalBlockEntity.inventory.set(OUTPUT_SLOT_ID, remainingStack);
            return false;
        }
    }

    public static void playCraftingFinishedSoundEvent(PedestalBlockEntity pedestalBlockEntity, @Nullable CraftingRecipe craftingRecipe, @Nullable PedestalCraftingRecipe pedestalCraftingRecipe) {
        if (craftingRecipe != null) {
            pedestalBlockEntity.playSound(SpectrumSoundEvents.PEDESTAL_CRAFT_GENERIC);
        } else if (pedestalCraftingRecipe != null) {
            pedestalBlockEntity.playSound(pedestalCraftingRecipe.getSoundEvent(pedestalBlockEntity.world.random));
        }
    }

    public static Recipe calculateRecipe(World world, PedestalBlockEntity pedestalBlockEntity) {
        if (pedestalBlockEntity.lastRecipe instanceof PedestalCraftingRecipe && ((PedestalCraftingRecipe) pedestalBlockEntity.lastRecipe).matches(pedestalBlockEntity, world)) {
            return pedestalBlockEntity.lastRecipe;
        } else {
            autoCraftingInventory.setInputInventory(pedestalBlockEntity.inventory.subList(0, 9));
            if(pedestalBlockEntity.lastRecipe instanceof CraftingRecipe && ((CraftingRecipe) pedestalBlockEntity.lastRecipe).matches(autoCraftingInventory, world)) {
                return pedestalBlockEntity.lastRecipe;
            } else {
                // current inventory does not match last recipe
                // => search valid recipe
                pedestalBlockEntity.craftingTime = 0;

                PedestalCraftingRecipe pedestalCraftingRecipe = world.getRecipeManager().getFirstMatch(recipeType, pedestalBlockEntity, world).orElse(null);
                if (pedestalCraftingRecipe != null) {
                    if (pedestalCraftingRecipe.canCraft(pedestalBlockEntity.getOwnerUUID())) {
                        pedestalBlockEntity.lastRecipe = pedestalCraftingRecipe;
                        pedestalBlockEntity.craftingTimeTotal = pedestalCraftingRecipe.getCraftingTime();
                        return pedestalCraftingRecipe;
                    } else {
                        return null;
                    }
                } else {
                    CraftingRecipe craftingRecipe = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, autoCraftingInventory, world).orElse(null);
                    if (craftingRecipe != null) {
                        pedestalBlockEntity.lastRecipe = craftingRecipe;
                        pedestalBlockEntity.craftingTimeTotal = 20;
                        return craftingRecipe;
                    } else {
                        pedestalBlockEntity.lastRecipe = null;
                        return null;
                    }
                }
            }
        }
    }

    private void playSound(SoundEvent soundEvent) {
        Random random = world.random;
        world.playSound(null, pos.getX(), pos.getY(), pos.getZ(), soundEvent, SoundCategory.BLOCKS, 0.9F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F);
    }

    private static int getCraftingTime(World world, RecipeType<? extends PedestalCraftingRecipe> recipeType, Inventory inventory) {
        return world.getRecipeManager().getFirstMatch(recipeType, inventory, world).map(PedestalCraftingRecipe::getCraftingTime).orElse(20);
    }

    private static boolean canAcceptRecipeOutput(@Nullable Recipe<?> recipe, DefaultedList<ItemStack> defaultedList, int maxCountPerStack) {
        if (recipe != null) {
            ItemStack output = recipe.getOutput();
            if (output.isEmpty()) {
                return false;
            } else {
                ItemStack existingOutput = defaultedList.get(OUTPUT_SLOT_ID);
                if (existingOutput.isEmpty()) {
                    return true;
                } else if (!existingOutput.isItemEqualIgnoreDamage(output)) {
                    return false;
                } else if (existingOutput.getCount() < maxCountPerStack && existingOutput.getCount() < existingOutput.getMaxCount()) {
                    return true;
                } else {
                    return existingOutput.getCount() < output.getMaxCount();
                }
            }
        } else {
            return false;
        }
    }

    private static boolean craftPedestalRecipe(PedestalBlockEntity pedestalBlockEntity, @Nullable PedestalCraftingRecipe recipe, DefaultedList<ItemStack> defaultedList, int maxCountPerStack) {
        if (canAcceptRecipeOutput(recipe, defaultedList, maxCountPerStack)) {

            // -1 for all crafting inputs
            for(int i = 0; i < 9; i++) {
                ItemStack itemStack = defaultedList.get(i);
                if(!itemStack.isEmpty()) {
                    Item recipeReminderItem = itemStack.getItem().getRecipeRemainder();
                    if(recipeReminderItem == null) {
                        itemStack.decrement(1);
                    } else {
                        pedestalBlockEntity.inventory.set(i, new ItemStack(recipeReminderItem, 1));
                    }
                }
            }

            // -X for all the pigment inputs
            for(GemstoneColor gemstoneColor : GemstoneColor.values()) {
                int gemstonePowderAmount = recipe.getGemstonePowderAmount(gemstoneColor);
                pedestalBlockEntity.inventory.get(getSlotForGemstonePowder(gemstoneColor)).decrement(gemstonePowderAmount);
            }

            ItemStack recipeOutput = recipe.getOutput();

            // if it was a recipe to upgrade the pedestal itself
            // => upgrade
            PedestalBlock.PedestalVariant newPedestalVariant = PedestalCraftingRecipe.getPedestalVariantForOutput(recipeOutput);
            if(newPedestalVariant != null && newPedestalVariant.ordinal() > getVariant(pedestalBlockEntity).ordinal()) {
                // It is an upgrade recipe (output is an pedestal block item)
                // => Upgrade
                PedestalBlock.upgradeToVariant(pedestalBlockEntity.world, pedestalBlockEntity.getPos(), newPedestalVariant);
            } else {
                // Not an upgrade recipe => Add output to output slot
                ItemStack existingOutput = defaultedList.get(OUTPUT_SLOT_ID);
                if (existingOutput.isEmpty()) {
                    defaultedList.set(OUTPUT_SLOT_ID, recipeOutput.copy());
                } else {
                    existingOutput.increment(recipeOutput.getCount());
                    defaultedList.set(OUTPUT_SLOT_ID, existingOutput);
                }
            }

            // Add recipe XP
            pedestalBlockEntity.storedXP = recipe.getExperience();

            // if the recipe unlocks an advancement unlock it
            pedestalBlockEntity.grantPlayerCraftingAdvancement(recipe);

            return true;
        } else {
            return false;
        }
    }

    private boolean craftVanillaRecipe(@Nullable CraftingRecipe recipe, DefaultedList<ItemStack> defaultedList, int maxCountPerStack) {
        if (canAcceptRecipeOutput(recipe, defaultedList, maxCountPerStack)) {

            // -1 for all crafting inputs
            for(int i = 0; i < 9; i++) {
                ItemStack itemStack = defaultedList.get(i);
                if(!itemStack.isEmpty()) {
                    Item recipeReminderItem = itemStack.getItem().getRecipeRemainder();
                    if(recipeReminderItem == null) {
                        itemStack.decrement(1);
                    } else {
                        inventory.set(i, new ItemStack(recipeReminderItem, 1));
                    }
                }
            }

            ItemStack recipeOutput = recipe.getOutput();
            ItemStack existingOutput = defaultedList.get(OUTPUT_SLOT_ID);
            if (existingOutput.isEmpty()) {
                defaultedList.set(OUTPUT_SLOT_ID, recipeOutput.copy());
            } else {
                existingOutput.increment(recipeOutput.getCount());
                defaultedList.set(OUTPUT_SLOT_ID, existingOutput);
            }

            return true;
        } else {
            return false;
        }
    }

    private void grantPlayerCraftingAdvancement(PedestalCraftingRecipe recipe) {
        ServerPlayerEntity serverPlayerEntity = SpectrumCommon.minecraftServer.getPlayerManager().getPlayer(this.ownerUUID);
        if(serverPlayerEntity != null) {
            SpectrumAdvancementCriteria.PEDESTAL_CRAFTING.trigger(serverPlayerEntity, recipe.getOutput());
        }
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if(slot < 9) {
            return true;
        } else if(slot == CRAFTING_TABLET_SLOT_ID && stack.isOf(SpectrumItems.CRAFTING_TABLET)) {
            return true;
        } else {
            return stack.getItem().equals(getSpectrumItemForSlot(slot));
        }
    }

    public static Item getSpectrumItemForSlot(int slot) {
        return switch (slot) {
            case 9 -> SpectrumItems.TOPAZ_POWDER;
            case 10 -> SpectrumItems.AMETHYST_POWDER;
            case 11 -> SpectrumItems.CITRINE_POWDER;
            case 12 -> SpectrumItems.ONYX_POWDER;
            case 13 -> SpectrumItems.MOONSTONE_POWDER;
            default -> Items.AIR;
        };
    }

    public static int getSlotForGemstonePowder(GemstoneColor gemstoneColor) {
        return switch (gemstoneColor) {
            case CYAN -> 9;
            case MAGENTA -> 10;
            case YELLOW -> 11;
            case BLACK -> 12;
            default -> 13; // WHITE
        };
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        if(side == Direction.DOWN) {
            return new int[]{OUTPUT_SLOT_ID};
        } else if(side == Direction.UP) {
            return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        } else {
            switch (this.pedestalVariant) {
                case BASIC_AMETHYST, BASIC_CITRINE, BASIC_TOPAZ, ALL_BASIC -> {
                    return new int[]{9, 10, 11};
                }
                case ONYX -> {
                    return new int[]{9, 10, 11, 12};
                }
                default -> {
                    return new int[]{9, 10, 11, 12, 13};
                }
            }
        }
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if(stack.isOf(getSpectrumItemForSlot(slot))) {
            return true;
        }

        if(slot < 9 && inventory.get(CRAFTING_TABLET_SLOT_ID).isOf(SpectrumItems.CRAFTING_TABLET)) {
            ItemStack craftingTabletItem = inventory.get(CRAFTING_TABLET_SLOT_ID);

            if(inventory.get(slot).getCount() > 0) {
                return false;
            }

            Recipe storedRecipe = CraftingTabletItem.getStoredRecipe(this.world, craftingTabletItem);

            int width = 3;
            if(storedRecipe instanceof ShapedRecipe) {
                ShapedRecipe shapedRecipe = (ShapedRecipe) storedRecipe;
                width = shapedRecipe.getWidth();
                if(slot % 3 >= width) {
                    return false;
                }
            } else if(storedRecipe instanceof PedestalCraftingRecipe) {
                PedestalCraftingRecipe pedestalCraftingRecipe = (PedestalCraftingRecipe) storedRecipe;
                width = pedestalCraftingRecipe.getWidth();
                if(slot % 3 >= width) {
                    return false;
                }
            } else if(storedRecipe instanceof ShapelessRecipe) {
                // just put it in already
            } else {
                return false;
            }

            int resultRecipeSlot = getCraftingRecipeSlotDependingOnWidth(slot, width, 3);
            if(resultRecipeSlot < storedRecipe.getIngredients().size()) {
                Ingredient ingredient = (Ingredient) storedRecipe.getIngredients().get(resultRecipeSlot);
                return ingredient.test(stack);
            } else {
                return false;
            }
        } else {
            return slot < CRAFTING_TABLET_SLOT_ID;
        }
    }

    private int getCraftingRecipeSlotDependingOnWidth(int slot, int recipeWidth, int gridWidth) {
        int line = slot / gridWidth;
        int posInLine = slot % gridWidth;
        return line * recipeWidth + posInLine;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == OUTPUT_SLOT_ID;
    }

    @Override
    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    @Override
    public String getOwnerName() {
        return this.ownerName;
    }

    @Override
    public void setOwner(PlayerEntity playerEntity) {
        this.ownerUUID = playerEntity.getUuid();
        this.ownerName = playerEntity.getName().asString();
        setCustomName(new TranslatableText("block.spectrum.pedestal.title_with_owner", ownerName));
    }

    public ItemStack getCraftingOutput() {
        Recipe recipe = calculateRecipe(this.world, this);
        if(recipe == null) {
            return ItemStack.EMPTY;
        } else {
            return recipe.getOutput();
        }
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeInt(this.pedestalVariant.ordinal());
    }
}
