package io.wispforest.owo.config.ui;

import io.wispforest.owo.Owo;
import io.wispforest.owo.config.ConfigWrapper;
import io.wispforest.owo.config.Option;
import io.wispforest.owo.config.annotation.ExcludeFromScreen;
import io.wispforest.owo.config.annotation.Expanded;
import io.wispforest.owo.config.annotation.RestartRequired;
import io.wispforest.owo.config.annotation.SectionHeader;
import io.wispforest.owo.config.ui.component.*;
import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.VerticalFlowLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.ui.parsing.UIParsing;
import io.wispforest.owo.ui.util.UISounds;
import io.wispforest.owo.util.NumberReflection;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Predicate;

// TODO docs
public class ConfigScreen extends BaseUIModelScreen<FlowLayout> {

    private static final Map<Predicate<Option<?>>, OptionComponentFactory<?>> DEFAULT_FACTORIES = new HashMap<>();
    protected final Map<Predicate<Option<?>>, OptionComponentFactory<?>> extraFactories = new HashMap<>();

    protected final Screen parent;
    protected final ConfigWrapper<?> config;
    @SuppressWarnings("rawtypes") protected final Map<Option, OptionComponent> options = new HashMap<>();

    // TODO allow customizing model file
    public ConfigScreen(ConfigWrapper<?> config, @Nullable Screen parent) {
        super(FlowLayout.class, DataSource.asset(new Identifier("owo", "config")));
//      super(FlowLayout.class, DataSource.file("config.xml"));
        this.parent = parent;
        this.config = config;
    }

    public ConfigScreen(ConfigWrapper<?> config) {
        this(config, null);
    }

    @Override
    @SuppressWarnings({"ConstantConditions", "unchecked"})
    protected void build(FlowLayout rootComponent) {
        this.options.clear();

        rootComponent.childById(LabelComponent.class, "title").text(Text.translatable("text.config." + this.config.name() + ".title"));
        if (this.client.world == null) {
            rootComponent.surface(Surface.OPTIONS_BACKGROUND);
        }

        rootComponent.childById(ButtonWidget.class, "done-button").onPress(button -> this.close());
        rootComponent.childById(ButtonWidget.class, "reload-button").onPress(button -> {
            this.config.load();
            this.clearAndInit();
        });

        var optionPanel = rootComponent.childById(VerticalFlowLayout.class, "option-panel");
        var sections = new LinkedHashMap<Component, Text>();

        var containers = new HashMap<Option.Key, VerticalFlowLayout>();
        containers.put(Option.Key.ROOT, optionPanel);

        this.config.forEachOption(option -> {
            if (option.backingField().hasAnnotation(ExcludeFromScreen.class)) return;

            var parentKey = option.key().parent();
            if (!parentKey.isRoot() && this.config.fieldForKey(parentKey).isAnnotationPresent(ExcludeFromScreen.class)) return;

            var factory = this.factoryForOption(option);
            if (factory == null) {
                Owo.LOGGER.warn("Could not create UI component for config option {}", option);
            }

            var result = factory.make(this.model, option);
            this.options.put(option, result.optionContainer());

            var expanded = !parentKey.isRoot() && this.config.fieldForKey(parentKey).isAnnotationPresent(Expanded.class);
            var container = containers.getOrDefault(
                    parentKey,
                    Containers.collapsible(
                            Sizing.fill(100), Sizing.content(),
                            Text.translatable("text.config." + this.config.name() + ".category." + parentKey.asString()),
                            expanded
                    )
            );

            if (!containers.containsKey(parentKey) && containers.containsKey(parentKey.parent())) {
                containers.put(parentKey, container);
                containers.get(parentKey.parent()).child(container);
            }

            var tooltipTranslationKey = option.translationKey() + ".tooltip";
            if (I18n.hasTranslation(tooltipTranslationKey)) {
                var tooltipText = this.client.textRenderer.wrapLines(Text.translatable(tooltipTranslationKey), Integer.MAX_VALUE);
                result.baseComponent().tooltip(tooltipText.stream().map(TooltipComponent::of).toList());
            }

            if (option.backingField().hasAnnotation(SectionHeader.class)) {
                var translationKey = "text.config." + this.config.name() + ".section."
                        + option.backingField().getAnnotation(SectionHeader.class).value();

                final var header = this.model.expandTemplate(FlowLayout.class, "section-header", Map.of());
                header.childById(LabelComponent.class, "header").text(Text.translatable(translationKey).formatted(Formatting.YELLOW, Formatting.BOLD));

                sections.put(header, Text.translatable(translationKey));

                container.child(header);
            }

            container.child(result.baseComponent());
        });

        if (!sections.isEmpty()) {
            var panelContainer = rootComponent.childById(FlowLayout.class, "option-panel-container");
            var panelScroll = rootComponent.childById(ScrollContainer.class, "option-panel-scroll");
            panelScroll.margins(Insets.right(10));

            var buttonPanel = this.model.expandTemplate(FlowLayout.class, "section-buttons", Map.of());
            sections.forEach((component, text) -> {
                var hoveredText = text.copy().formatted(Formatting.YELLOW);

                final var label = Components.label(text);
                label.cursorStyle(CursorStyle.HAND).margins(Insets.of(5));

                label.mouseEnter().subscribe(() -> label.text(hoveredText));
                label.mouseLeave().subscribe(() -> label.text(text));

                label.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    panelScroll.scrollTo(component);
                    UISounds.playInteractionSound();
                    return true;
                });

                buttonPanel.child(label);
            });

            var expandPanelAnimation = buttonPanel.horizontalSizing().animate(650, Easing.CUBIC, Sizing.fill(15));
            var contractOptionsAnimation = panelContainer.horizontalSizing().animate(650, Easing.CUBIC, Sizing.fill(85));

            var closeButton = Components.label(Text.literal("<").formatted(Formatting.BOLD));
            closeButton.positioning(Positioning.relative(100, 50)).cursorStyle(CursorStyle.HAND).margins(Insets.right(2));

            panelContainer.child(closeButton);
            panelContainer.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (mouseX < panelContainer.width() - 10) return false;

                expandPanelAnimation.reverse();
                contractOptionsAnimation.reverse();

                closeButton.text(Text.literal(closeButton.text().getString().equals(">") ? "<" : ">").formatted(Formatting.BOLD));

                UISounds.playInteractionSound();
                return true;
            });

            rootComponent.childById(FlowLayout.class, "main-panel").child(buttonPanel);
        }
    }

    @Override
    public void close() {
        var shouldRestart = new MutableBoolean();
        this.options.forEach((option, component) -> {
            if (!option.backingField().hasAnnotation(RestartRequired.class)) return;
            if (Objects.equals(option.value(), component.parsedValue())) return;

            shouldRestart.setTrue();
        });

        this.client.setScreen(shouldRestart.booleanValue() ? new RestartRequiredScreen(this.parent) : this.parent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void removed() {
        this.options.forEach((option, component) -> {
            if (!component.isValid()) return;
            option.set(component.parsedValue());
        });
        super.removed();
    }

    @SuppressWarnings("rawtypes")
    protected @Nullable OptionComponentFactory factoryForOption(Option<?> option) {
        for (var predicate : this.extraFactories.keySet()) {
            if (!predicate.test(option)) continue;
            return this.extraFactories.get(predicate);
        }

        for (var predicate : DEFAULT_FACTORIES.keySet()) {
            if (!predicate.test(option)) continue;
            return DEFAULT_FACTORIES.get(predicate);
        }

        return null;
    }

    static {
        DEFAULT_FACTORIES.put(option -> NumberReflection.isNumberType(option.clazz()), OptionComponentFactory.NUMBER);
        DEFAULT_FACTORIES.put(option -> option.clazz() == String.class, OptionComponentFactory.STRING);
        DEFAULT_FACTORIES.put(option -> option.clazz() == Boolean.class || option.clazz() == boolean.class, OptionComponentFactory.BOOLEAN);
        DEFAULT_FACTORIES.put(option -> isStringOrNumberList(option.backingField().field()), OptionComponentFactory.LIST);
        DEFAULT_FACTORIES.put(option -> option.clazz().isEnum(), OptionComponentFactory.ENUM);

        UIParsing.registerFactory("config-slider", element -> new ConfigSlider());
        UIParsing.registerFactory("config-toggle-button", element -> new ConfigToggleButton());
        UIParsing.registerFactory("config-enum-button", element -> new ConfigEnumButton());
        UIParsing.registerFactory("config-text-box", element -> new ConfigTextBox());
    }

    private static boolean isStringOrNumberList(Field field) {
        if (field.getType() != List.class) return false;

        var type = field.getGenericType();
        if (!(type instanceof ParameterizedType parameterizedType)) return false;

        var listType = parameterizedType.getActualTypeArguments()[0];
        if (!(listType instanceof Class<?> listTypeClass)) return false;

        return String.class == listTypeClass || NumberReflection.isNumberType(listTypeClass);
    }
}
