package com.supermartijn642.core.gui.widget;

import com.supermartijn642.core.ClientUtils;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Created 23/07/2022 by SuperMartijn642
 */
public abstract class ItemBaseWidget extends ObjectBaseWidget<ItemStack> {

    protected final Supplier<ItemStack> stackSupplier;
    protected final Predicate<ItemStack> stackValidator;

    public ItemBaseWidget(int x, int y, int width, int height, Supplier<ItemStack> stackSupplier, Predicate<ItemStack> stackValidator){
        super(x, y, width, height, true);
        this.stackSupplier = stackSupplier;
        this.stackValidator = stackValidator;
    }

    public ItemBaseWidget(int x, int y, int width, int height, int slotIndex, Predicate<ItemStack> stackValidator){
        this(x, y, width, height, () -> ClientUtils.getPlayer().inventory.getStackInSlot(slotIndex), stackValidator);
    }

    public ItemBaseWidget(int x, int y, int width, int height, int slotIndex, Item itemType){
        this(x, y, width, height, () -> ClientUtils.getPlayer().inventory.getStackInSlot(slotIndex), stack -> stack.getItem() == itemType);
    }

    public ItemBaseWidget(int x, int y, int width, int height, EnumHand hand, Predicate<ItemStack> stackValidator){
        this(x, y, width, height, () -> ClientUtils.getPlayer().getHeldItem(hand), stackValidator);
    }

    public ItemBaseWidget(int x, int y, int width, int height, EnumHand hand, Item itemType){
        this(x, y, width, height, () -> ClientUtils.getPlayer().getHeldItem(hand), stack -> stack.getItem() == itemType);
    }

    @Override
    protected ItemStack getObject(ItemStack oldObject){
        return this.stackSupplier.get();
    }

    @Override
    protected boolean validateObject(ItemStack object){
        return object != null && this.stackValidator.test(object);
    }
}
