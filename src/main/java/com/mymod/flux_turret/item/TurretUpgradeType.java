package com.mymod.flux_turret.item;

public enum TurretUpgradeType {
    ARMOR_PIERCING_ROUNDS("armor_piercing_rounds", Slot.WEAPON),
    FIRE_ROUNDS("fire_rounds", Slot.WEAPON),
    SLOW_ROUNDS("slow_rounds", Slot.UTILITY),
    CHAIN_JUMP("chain_jump", Slot.WEAPON),
    EMP_SLOW("emp_slow", Slot.UTILITY),
    OVERLOAD_BURST("overload_burst", Slot.WEAPON),
    FOCUSED_BEAM("focused_beam", Slot.WEAPON),
    REFRACTION_BEAM("refraction_beam", Slot.WEAPON),
    REMOTE_SUPPORT("remote_support", Slot.UTILITY),
    SEISMIC_SHOCK("seismic_shock", Slot.UTILITY),
    ARMOR_BREAK("armor_break", Slot.WEAPON),
    CLUSTER_SHELLS("cluster_shells", Slot.WEAPON);

    public enum Slot {
        WEAPON,
        UTILITY
    }

    private final String id;
    private final Slot slot;

    TurretUpgradeType(String id, Slot slot) {
        this.id = id;
        this.slot = slot;
    }

    public String getId() {
        return id;
    }

    public int getMask() {
        return 1 << ordinal();
    }

    public Slot getSlot() {
        return slot;
    }

    public String getDescriptionKey() {
        return "upgrade.flux_turret." + id;
    }
}
