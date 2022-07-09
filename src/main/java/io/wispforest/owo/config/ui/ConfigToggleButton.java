package io.wispforest.owo.config.ui;

import io.wispforest.owo.ui.definitions.Sizing;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfigToggleButton extends ButtonWidget implements OptionComponent {

    protected static final Text ENABLED_MESSAGE = Text.translatable("text.config.boolean_toggle.enabled");
    protected static final Text DISABLED_MESSAGE = Text.translatable("text.config.boolean_toggle.disabled");

    protected boolean enabled = false;

    public ConfigToggleButton() {
        super(0, 0, 0, 0, Text.empty(), button -> {});
        this.verticalSizing(Sizing.fixed(20));
        this.updateMessage();
    }

    @Override
    public void onPress() {
        this.enabled = !this.enabled;
        this.updateMessage();
        super.onPress();
    }

    protected void updateMessage() {
        this.setMessage(this.enabled ? ENABLED_MESSAGE : DISABLED_MESSAGE);
    }

    public ConfigToggleButton enabled(boolean enabled) {
        this.enabled = enabled;
        this.updateMessage();
        return this;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Object parsedValue() {
        return this.enabled;
    }
}
