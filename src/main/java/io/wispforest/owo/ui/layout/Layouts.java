package io.wispforest.owo.ui.layout;

import io.wispforest.owo.ui.definitions.Sizing;

public class Layouts {

    public static VerticalFlowLayout verticalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new VerticalFlowLayout(horizontalSizing, verticalSizing);
    }

    public static HorizontalFlowLayout horizontalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new HorizontalFlowLayout(horizontalSizing, verticalSizing);
    }

    public static GridLayout grid(Sizing horizontalSizing, Sizing verticalSizing, int rows, int columns) {
        return new GridLayout(horizontalSizing, verticalSizing, rows, columns);
    }

}
