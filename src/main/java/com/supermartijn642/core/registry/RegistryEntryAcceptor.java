package com.supermartijn642.core.registry;

import com.google.common.eventbus.Subscribe;
import com.supermartijn642.core.CommonUtils;
import com.supermartijn642.core.CoreLib;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import net.minecraftforge.fml.common.event.FMLModIdMappingEvent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Created 14/07/2022 by SuperMartijn642
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface RegistryEntryAcceptor {

    String namespace();

    String identifier();

    Registry registry();

    enum Registry {
        BLOCKS(Registries.BLOCKS),
        FLUIDS(Registries.FLUIDS),
        ITEMS(Registries.ITEMS),
        MOB_EFFECTS(Registries.MOB_EFFECTS),
        SOUND_EVENTS(Registries.SOUND_EVENTS),
        POTIONS(Registries.POTIONS),
        ENCHANTMENTS(Registries.ENCHANTMENTS),
        ENTITY_TYPES(Registries.ENTITY_TYPES),
        BLOCK_ENTITY_TYPES(Registries.BLOCK_ENTITY_TYPES),
        MENU_TYPES(Registries.MENU_TYPES),
        RECIPE_CONDITION_SERIALIZERS(Registries.RECIPE_CONDITION_SERIALIZERS);

        public final Registries.Registry<?> registry;

        Registry(Registries.Registry<?> registry){
            this.registry = registry;
        }
    }

    class Handler {

        private static final Map<Registries.Registry<?>,Map<ResourceLocation,Set<Field>>> FIELDS = new HashMap<>();
        private static final Map<Registries.Registry<?>,Map<ResourceLocation,Set<Method>>> METHODS = new HashMap<>();

        public static void gatherAnnotatedFields(ASMDataTable dataTable){
            for(ASMDataTable.ASMData annotationData : dataTable.getAll(RegistryEntryAcceptor.class.getName())){
                try{
                    String namespace = (String)annotationData.getAnnotationInfo().get("namespace");
                    if(!RegistryUtil.isValidNamespace(namespace))
                        throw new IllegalArgumentException("Namespace '" + namespace + "' must only contain characters [a-z0-9_.-]!");
                    String identifier = (String)annotationData.getAnnotationInfo().get("identifier");
                    if(!RegistryUtil.isValidPath(identifier))
                        throw new IllegalArgumentException("Identifier '" + identifier + "' must only contain characters [a-z0-9_./-]!");

                    Registry registry = Registry.valueOf(((ModAnnotation.EnumHolder)annotationData.getAnnotationInfo().get("registry")).getValue());

                    // Get the class the annotation is located in
                    Class<?> clazz = Class.forName(annotationData.getClassName(), false, RegistryEntryAcceptor.class.getClassLoader());

                    // Now get the targeted field or method
                    if(Arrays.stream(clazz.getFields()).anyMatch(field -> field.getName().equals(annotationData.getObjectName()))){
                        Field field = clazz.getDeclaredField(annotationData.getObjectName());

                        // Check if the field is static
                        if(!Modifier.isStatic(field.getModifiers()))
                            throw new RuntimeException("Field must be static!");
                        // Check if the field is non-final
                        if(Modifier.isFinal(field.getModifiers()))
                            throw new RuntimeException("Field must not be final!");
                        // Check if the field has the correct type
                        if(!registry.registry.getValueClass().isAssignableFrom(field.getType()))
                            throw new RuntimeException("Field must have a type assignable from '" + registry.registry.getValueClass().getName() + "'!");

                        // Make the field accessible
                        field.setAccessible(true);

                        // Add the field
                        FIELDS.computeIfAbsent(registry.registry, o -> new HashMap<>())
                            .computeIfAbsent(new ResourceLocation(namespace, identifier), o -> new HashSet<>())
                            .add(field);
                    }else if(Arrays.stream(clazz.getFields()).anyMatch(field -> field.getName().equals(annotationData.getObjectName()))){
                        Method method = clazz.getDeclaredMethod(annotationData.getObjectName());

                        // Check if the method is static
                        if(!Modifier.isStatic(method.getModifiers()))
                            throw new RuntimeException("Method must be static!");
                        // Check if the method has exactly one parameter
                        if(method.getParameterCount() != 1)
                            throw new RuntimeException("Method must have exactly 1 parameter!");
                        // Check if the parameter has the correct type
                        if(!registry.registry.getValueClass().isAssignableFrom(method.getParameterTypes()[0]))
                            throw new RuntimeException("Method parameter must have a type assignable from '" + registry.registry.getValueClass().getName() + "'!");

                        // Make the method accessible
                        method.setAccessible(true);

                        // Add the method
                        METHODS.computeIfAbsent(registry.registry, o -> new HashMap<>())
                            .computeIfAbsent(new ResourceLocation(namespace, identifier), o -> new HashSet<>())
                            .add(method);
                    }else
                        throw new RuntimeException("Could not find field or method with name '" + annotationData.getObjectName() + "'!");
                }catch(Exception e){
                    throw new RuntimeException("Failed to register @RegistryEntryAcceptor annotation target '" + annotationData.getObjectName() + "' in '" + annotationData.getClassName() + "'!", e);
                }
            }

            // Register event handlers
            CommonUtils.getEventBus("supermartijn642corelib").register(new Object() {
                @Subscribe
                public void handleIdMappingEvent(FMLModIdMappingEvent e){
                    Handler.onIdRemapping(e);
                }
            });
        }

        public static void onRegisterEvent(RegistryEvent.Register<?> e){
            Registries.Registry<?> registry = Registries.fromUnderlying(e.getRegistry());
            if(registry != null)
                onRegisterEvent(registry);
        }

        public static void onRegisterEvent(Registries.Registry<?> registry){
            applyToFields(registry);
            applyToMethods(registry);

            for(Registries.Registry<?> otherRegistry : Registries.REGISTRATION_ORDER_MAP.getOrDefault(registry, Collections.emptyList())){
                applyToFields(otherRegistry);
                applyToMethods(otherRegistry);
            }
        }

        public static void onIdRemapping(FMLModIdMappingEvent e){
            FIELDS.keySet().forEach(Handler::applyToFields);
            METHODS.keySet().forEach(Handler::applyToMethods);
        }

        private static <T> void applyToFields(Registries.Registry<T> registry){
            if(registry == null || !FIELDS.containsKey(registry))
                return;

            for(Map.Entry<ResourceLocation,Set<Field>> entry : FIELDS.get(registry).entrySet()){
                // Skip if no value is registered with the identifier
                if(!registry.hasIdentifier(entry.getKey())){
                    CoreLib.LOGGER.warn("Could not find value '" + entry.getKey() + "' in registry '" + registry.getRegistryIdentifier() + "' for @RegistryEntryAcceptor!");
                    continue;
                }

                // Get the value
                T value = registry.getValue(entry.getKey());
                // Apply the value to all fields
                for(Field field : entry.getValue()){
                    // Check if the value can be assigned to the field
                    if(!field.getType().isAssignableFrom(value.getClass())){
                        CoreLib.LOGGER.warn("@RegistryEntryAcceptor field '" + field.getDeclaringClass().getName() + "." + field.getName() + "' for '" + entry.getKey() + "' could not be assigned value of type '" + value.getClass() + "'.");
                        continue;
                    }
                    // Set the field's value
                    try{
                        field.set(null, value);
                    }catch(IllegalAccessException e){
                        CoreLib.LOGGER.error("Encountered an error when trying to apply @RegistryEntryAcceptor annotation on field '" + field.getDeclaringClass().getName() + "." + field.getName() + "'!", e);
                    }
                }
            }
        }

        private static <T> void applyToMethods(Registries.Registry<T> registry){
            if(registry == null || !METHODS.containsKey(registry))
                return;

            for(Map.Entry<ResourceLocation,Set<Method>> entry : METHODS.get(registry).entrySet()){
                // Skip if no value is registered with the identifier
                if(!registry.hasIdentifier(entry.getKey())){
                    CoreLib.LOGGER.warn("Could not find value '" + entry.getKey() + "' in registry '" + registry.getRegistryIdentifier() + "' for @RegistryEntryAcceptor!");
                    continue;
                }

                // Get the value
                T value = registry.getValue(entry.getKey());
                // Apply the value to all methods
                for(Method method : entry.getValue()){
                    // Check if the value can be passed to the method
                    if(!method.getParameterTypes()[0].isAssignableFrom(value.getClass())){
                        CoreLib.LOGGER.warn("@RegistryEntryAcceptor method '" + method.getDeclaringClass().getName() + "." + method.getName() + "' for '" + entry.getKey() + "' could not be assigned value of type '" + value.getClass() + "'.");
                        continue;
                    }
                    // Set the method's value
                    try{
                        method.invoke(null, value);
                    }catch(InvocationTargetException |
                           IllegalAccessException e){
                        CoreLib.LOGGER.error("Encountered an error when trying to apply @RegistryEntryAcceptor annotation on method '" + method.getDeclaringClass().getName() + "." + method.getName() + "'!", e);
                    }
                }
            }
        }
    }
}
