package com.supermartijn642.core.mixin;

import com.supermartijn642.core.registry.RegistrationHandler;
import com.supermartijn642.core.registry.Registries;
import com.supermartijn642.core.registry.RegistryEntryAcceptor;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IConditionFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Created 27/08/2022 by SuperMartijn642
 */
@Mixin(CraftingHelper.class)
public class CraftingHelperMixin {

    @Inject(
        method = "register(Lnet/minecraft/util/ResourceLocation;Lnet/minecraftforge/common/crafting/IConditionFactory;)V",
        at = @At("TAIL"),
        remap = false
    )
    private static void registerConditionSerializer(ResourceLocation identifier, IConditionFactory conditionSerializer, CallbackInfo ci){
        Registries.onRecipeConditionSerializerAdded(identifier, conditionSerializer);
    }

    @Inject(
        method = "loadRecipes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/fml/common/Loader;setActiveModContainer(Lnet/minecraftforge/fml/common/ModContainer;)V",
            shift = At.Shift.AFTER,
            ordinal = 0
        ),
        remap = false
    )
    private static void loadRecipes(CallbackInfo ci){
        RegistrationHandler.handleResourceConditionSerializerRegistryEvent();
        RegistryEntryAcceptor.Handler.onRegisterEvent(Registries.RECIPE_CONDITION_SERIALIZERS);
    }
}
