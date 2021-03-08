package com.supermartijn642.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Created 3/7/2021 by SuperMartijn642
 * <p>
 * Copied from Forge 1.16.3-34.1.42
 */
public class ToolType {
    private static final Pattern VALID_NAME = Pattern.compile("[^a-z_]"); //Only a-z and _ are allowed, meaning names must be lower case. And use _ to separate words.
    private static final Map<String,ToolType> VALUES = new ConcurrentHashMap<>();

    public static final ToolType AXE = get("axe");
    public static final ToolType HOE = get("hoe");
    public static final ToolType PICKAXE = get("pickaxe");
    public static final ToolType SHOVEL = get("shovel");

    /**
     * Gets the ToolType for the specified name, or creates a new one if none for that name does yet exist.
     * This method can be called during parallel loading
     */
    public static ToolType get(String name){
        return VALUES.computeIfAbsent(name, k ->
        {
            if(VALID_NAME.matcher(name).find())
                throw new IllegalArgumentException("ToolType.get() called with invalid name: " + name);
            return new ToolType(name);
        });
    }

    private final String name;

    private ToolType(String name){
        this.name = name;
    }

    public String getName(){
        return name;
    }
}
