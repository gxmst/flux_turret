package com.mymod.flux_turret.item;

public enum TurretUpgradeType {
    ARMOR_PIERCING_ROUNDS("armor_piercing_rounds"),
    FIRE_ROUNDS("fire_rounds"),
    SLOW_ROUNDS("slow_rounds"),
    CHAIN_JUMP("chain_jump"),
    EMP_SLOW("emp_slow"),
    OVERLOAD_BURST("overload_burst"),
    FOCUSED_BEAM("focused_beam"),
    REFRACTION_BEAM("refraction_beam"),
    REMOTE_SUPPORT("remote_support"),
    SEISMIC_SHOCK("seismic_shock"),
    ARMOR_BREAK("armor_break"),
    CLUSTER_SHELLS("cluster_shells");

    private final String id;

    TurretUpgradeType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public int getMask() {
        return 1 << ordinal();
    }

    public String getDescriptionKey() {
        return "upgrade.flux_turret." + id;
    }
}
