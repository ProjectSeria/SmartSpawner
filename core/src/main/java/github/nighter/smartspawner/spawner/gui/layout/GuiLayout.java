package github.nighter.smartspawner.spawner.gui.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GuiLayout {
    private final Map<String, GuiButton> buttons = new HashMap<>();
    private final Map<Integer, String> slotToButtonType = new HashMap<>();

    /** Inventory size in slots. Only the main GUI reads it; defaults to three rows. */
    public static final int DEFAULT_SIZE = 27;
    private int size = DEFAULT_SIZE;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void addButton(String buttonType, GuiButton button) {
        // Remove old button if it exists
        GuiButton oldButton = buttons.get(buttonType);
        if (oldButton != null) {
            slotToButtonType.remove(oldButton.getSlot());
        }

        buttons.put(buttonType, button);
        slotToButtonType.put(button.getSlot(), buttonType);
    }

    public GuiButton getButton(String buttonType) {
        return buttons.get(buttonType);
    }

    public Optional<String> getButtonTypeAtSlot(int slot) {
        return Optional.ofNullable(slotToButtonType.get(slot));
    }

    public Optional<GuiButton> getButtonAtSlot(int slot) {
        String buttonType = slotToButtonType.get(slot);
        return buttonType != null ? Optional.ofNullable(buttons.get(buttonType)) : Optional.empty();
    }

    public boolean hasButton(String buttonType) {
        return buttons.containsKey(buttonType) && buttons.get(buttonType).isEnabled();
    }

    public Set<String> getButtonTypes() {
        return buttons.keySet();
    }

    public Map<String, GuiButton> getAllButtons() {
        return new HashMap<>(buttons);
    }

    public Set<Integer> getUsedSlots() {
        return slotToButtonType.keySet();
    }

    public boolean isSlotUsed(int slot) {
        return slotToButtonType.containsKey(slot);
    }
}