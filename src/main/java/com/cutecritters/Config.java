package com.cutecritters;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue TIME_SCALE;
    public static final ForgeConfigSpec.BooleanValue LOUD_SURPRISE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Cute Critters+ settings").push("critters");

        ENABLED = b.comment("Set to false to switch off all the special surprises.")
                .define("surprisesEnabled", true);

        TIME_SCALE = b.comment("How fast the surprises build up. 1.0 = normal, 2.0 = twice as fast, 0.5 = twice as slow.")
                .defineInRange("timeScale", 1.0D, 0.1D, 100.0D);

        LOUD_SURPRISE = b.comment("Allow the loudest surprise (the final one).")
                .define("loudSurprise", true);

        b.pop();
        SPEC = b.build();
    }
}
