package io.wispforest.owo.ui.layout;

import io.wispforest.owo.ui.BaseParentComponent;
import io.wispforest.owo.ui.definitions.Component;
import io.wispforest.owo.ui.definitions.Size;
import io.wispforest.owo.ui.definitions.Sizing;
import net.minecraft.client.util.math.MatrixStack;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class GridLayout extends BaseParentComponent {

    protected final int rows, columns;

    protected final Component[] children;
    protected final Set<Component> nonNullChildren = new HashSet<>();

    protected Size contentSize = Size.zero();

    protected GridLayout(Sizing horizontalSizing, Sizing verticalSizing, int rows, int columns) {
        super(horizontalSizing, verticalSizing);

        this.rows = rows;
        this.columns = columns;

        this.children = new Component[rows * columns];
    }

    @Override
    protected void applyHorizontalContentSizing(Sizing sizing) {
        this.width = this.contentSize.width() + this.padding.get().right() + sizing.value;
    }

    @Override
    protected void applyVerticalContentSizing(Sizing sizing) {
        this.height = this.contentSize.height() + this.padding.get().bottom() + sizing.value;
    }

    @Override
    public void layout(Size space) {
        int[] columnSizes = new int[this.columns];
        int[] rowSizes = new int[this.rows];

        var childSpace = this.calculateChildSpace(space);
        for (var child : this.children) {
            if (child != null) {
                child.inflate(childSpace);
            }
        }

        this.determineSizes(columnSizes, false);
        this.determineSizes(rowSizes, true);

        var mountingOffset = this.childMountingOffset();
        var layoutX = new MutableInt(this.x + mountingOffset.width());
        var layoutY = new MutableInt(this.y + mountingOffset.height());

        for (int row = 0; row < this.rows; row++) {
            layoutX.setValue(this.x + mountingOffset.width());

            for (int column = 0; column < this.columns; column++) {
                int columnSize = columnSizes[column];
                int rowSize = rowSizes[row];

                this.mountChild(this.getChild(row, column), childSpace, child -> {
                    child.mount(
                            this,
                            layoutX.intValue() + child.margins().get().left() + this.horizontalAlignment().align(child.fullSize().width(), columnSize),
                            layoutY.intValue() + child.margins().get().top() + this.verticalAlignment().align(child.fullSize().height(), rowSize)
                    );
                });


                layoutX.add(columnSizes[column]);
            }

            layoutY.add(rowSizes[row]);
        }

        this.contentSize = Size.of(layoutX.intValue() - this.x, layoutY.intValue() - this.y);
    }

    @Override
    public void draw(MatrixStack matrices, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(matrices, mouseX, mouseY, partialTicks, delta);

        this.drawClipped(matrices, !this.allowOverflow, () -> {
            for (var child : this.nonNullChildren) {
                child.draw(matrices, mouseX, mouseY, partialTicks, delta);
            }
        });
    }

    protected @Nullable Component getChild(int row, int column) {
        return this.children[row * this.columns + column];
    }

    protected void determineSizes(int[] sizes, boolean rows) {
        if ((rows ? this.verticalSizing : this.horizontalSizing).get().method != Sizing.Method.CONTENT) {
            Arrays.fill(sizes, (rows ? this.height - this.padding().get().vertical() : this.width - this.padding().get().horizontal()) / (rows ? this.rows : this.columns));
        } else {
            for (int row = 0; row < this.rows; row++) {
                for (int column = 0; column < this.columns; column++) {
                    final var child = this.getChild(row, column);
                    if (child == null) continue;

                    if (rows) {
                        sizes[row] = Math.max(sizes[row], child.fullSize().height());
                    } else {
                        sizes[column] = Math.max(sizes[column], child.fullSize().width());
                    }
                }
            }
        }
    }

    public GridLayout child(Component child, int row, int column) {
        var previousChild = this.getChild(row, column);
        this.children[row * this.columns + column] = child;

        if (previousChild != child) {
            this.nonNullChildren.remove(previousChild);
            this.nonNullChildren.add(child);

            this.updateLayout();
        }

        return this;
    }

    @Override
    public Collection<Component> children() {
        return this.nonNullChildren;
    }
}
