package dev.astroclient.module;

public enum Category {
    MOVEMENT("Movement"),
    RENDER("Render"),
    HUD("HUD"),
    MISC("Misc");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
