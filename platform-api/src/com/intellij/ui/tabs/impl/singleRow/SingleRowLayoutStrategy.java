package com.intellij.ui.tabs.impl.singleRow;

import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.ShapeTransform;

import java.awt.*;

public abstract class SingleRowLayoutStrategy {

  SingleRowLayout myLayout;
  JBTabsImpl myTabs;

  protected SingleRowLayoutStrategy(final SingleRowLayout layout) {
    myLayout = layout;
    myTabs = myLayout.myTabs;
  }

  abstract int getMoreRectAxisSize();

  public abstract int getStartPosition(final SingleRowPassInfo data);

  public abstract int getToFitLength(final SingleRowPassInfo data);

  public abstract int getLengthIncrement(final Dimension dimension);

  public abstract int getMaxPosition(final Rectangle bounds);

  public abstract int getFixedFitLength(final SingleRowPassInfo data);

  public abstract Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength);

  public abstract int getFixedPosition(final SingleRowPassInfo data);

  public abstract Rectangle getMoreRect(final SingleRowPassInfo data);

  public abstract boolean isToCenterTextWhenStretched();

  public abstract Dimension getCompSizeDelta(SingleRowPassInfo data);

  public abstract ShapeTransform createShapeTransform(Rectangle rectangle);

  public abstract boolean canBeStretched();

  public abstract void layoutComp(SingleRowPassInfo data);

  public boolean isSideComponentOnTabs() {
    return false;
  }

  abstract static class Horizontal extends SingleRowLayoutStrategy {
    protected Horizontal(final SingleRowLayout layout) {
      super(layout);
    }

    public boolean isToCenterTextWhenStretched() {
      return true;
    }

    @Override
    public boolean canBeStretched() {
      return true;
    }

    public int getMoreRectAxisSize() {
      return myLayout.myMoreIcon.getIconWidth() + 6;
    }

    public int getToFitLength(final SingleRowPassInfo data) {
      if (data.hToolbar != null) {
        return myTabs.getWidth() - data.insets.left - data.insets.right - data.hToolbar.getMinimumSize().width;  
      } else {
        return myTabs.getWidth() - data.insets.left - data.insets.right;
      }
    }

    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.width;
    }

    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxX();
    }

    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.height;
    }

    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(position, fixedPos, length, fixedFitLength);
    }

    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

  }

  static class Top extends Horizontal {

    Top(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public boolean isSideComponentOnTabs() {
      return !myTabs.isSideComponentVertical();
    }

    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Top(labelRec);
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      final int x = data.position + (data.lastGhostVisible ? data.lastGhost.width : 0);
      return new Rectangle(x, data.insets.top + myTabs.getSelectionTabVShift(),
                                            data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height - 1);
    }


    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        final int x = data.vToolbar != null ? data.vToolbar.getPreferredSize().width + 1 : 0;
        final int y = data.compPosition + myTabs.myHeaderFitSize.height + 1;

        if (data.hToolbar != null) {
          myTabs.layoutComp(x, y, data.comp, 0, 0);
          int toolbarX = data.moreRect != null ? (int)data.moreRect.getMaxX() + myTabs.getToolbarInset() : (data.position + myTabs.getToolbarInset());
          final Rectangle rec =
            new Rectangle(toolbarX, data.insets.top + 1, myTabs.getSize().width - data.insets.left - toolbarX, myTabs.myHeaderFitSize.height);
          myTabs.layout(data.hToolbar, rec);
        } else if (data.vToolbar != null) {
          final Rectangle compBounds = myTabs.layoutComp(x, y, data.comp, 0, 0);
          final int toolbarWidth = data.vToolbar.getPreferredSize().width;
          myTabs.layout(data.vToolbar, compBounds.x - toolbarWidth - 1, compBounds.y, toolbarWidth, compBounds.height);
        } else {
          myTabs.layoutComp(x, y, data.comp, 0, 0);
        }
      }
    }

    public Dimension getCompSizeDelta(SingleRowPassInfo data) {
      return new Dimension();
    }

  }

  static class Bottom extends Horizontal {
    Bottom(final SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        myTabs.layoutComp(data, 0, 0, 0, -(myTabs.myHeaderFitSize.height + myTabs.getTabsBorder().getEffectiveBorder().top + 1));
      }
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return myTabs.getSize().height - data.insets.bottom - myTabs.myHeaderFitSize.height - 1;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(myTabs.getWidth() - data.insets.right - data.moreRectAxisSize + 2, getFixedPosition(data),
                                            data.moreRectAxisSize - 1, myTabs.myHeaderFitSize.height - 1);
    }

    public Dimension getCompSizeDelta(SingleRowPassInfo data) {
      return new Dimension(0, -(myTabs.myHeaderFitSize.height + 1));
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Bottom(labelRec);
    }
  }

  abstract static class Vertical extends SingleRowLayoutStrategy {
    protected Vertical(SingleRowLayout layout) {
      super(layout);
    }

    public boolean isToCenterTextWhenStretched() {
      return false;
    }

    int getMoreRectAxisSize() {
      return myLayout.myMoreIcon.getIconHeight() + 4;
    }

    public Dimension getCompSizeDelta(SingleRowPassInfo data) {
      return new Dimension();
    }

    @Override
    public boolean canBeStretched() {
      return false;
    }

    public int getStartPosition(final SingleRowPassInfo data) {
      return data.insets.top;
    }

    public int getToFitLength(final SingleRowPassInfo data) {
      return myTabs.getHeight() - data.insets.top - data.insets.bottom;
    }

    public int getLengthIncrement(final Dimension labelPrefSize) {
      return labelPrefSize.height;
    }

    public int getMaxPosition(final Rectangle bounds) {
      return (int)bounds.getMaxY();
    }

    public int getFixedFitLength(final SingleRowPassInfo data) {
      return myTabs.myHeaderFitSize.width;
    }

  }

  static class Left extends Vertical {
    Left(final SingleRowLayout layout) {
      super(layout);
    }


    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        myTabs.layoutComp(data, myTabs.myHeaderFitSize.width + myTabs.getTabsBorder().getEffectiveBorder().right + 1, 0, 0, 0);
      }
    }

    @Override
    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Left(labelRec);
    }

    public Rectangle getLayoutRec(final int position, final int fixedPos, final int length, final int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength, length);
    }

    public int getFixedPosition(final SingleRowPassInfo data) {
      return data.insets.left;
    }

    public Rectangle getMoreRect(final SingleRowPassInfo data) {
      return new Rectangle(data.insets.left + myTabs.getSelectionTabVShift(),
                           myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize - 1,
                           myTabs.myHeaderFitSize.width - 1,
                           data.moreRectAxisSize - 1);
    }

  }

  static class Right extends Vertical {
    Right(SingleRowLayout layout) {
      super(layout);
    }

    @Override
    public void layoutComp(SingleRowPassInfo data) {
      if (myTabs.isHideTabs()) {
        myTabs.layoutComp(data, 0, 0, 0, 0);
      } else {
        myTabs.layoutComp(data, 0, 0, -(myTabs.myHeaderFitSize.width + myTabs.getTabsBorder().getEffectiveBorder().left), 0);
      }
    }

    public ShapeTransform createShapeTransform(Rectangle labelRec) {
      return new ShapeTransform.Right(labelRec);
    }

    public Rectangle getLayoutRec(int position, int fixedPos, int length, int fixedFitLength) {
      return new Rectangle(fixedPos, position, fixedFitLength - 1, length);
    }

    public int getFixedPosition(SingleRowPassInfo data) {
      return data.laayoutSize.width - myTabs.myHeaderFitSize.width - data.insets.right;
    }

    public Rectangle getMoreRect(SingleRowPassInfo data) {
      return new Rectangle(data.laayoutSize.width - myTabs.myHeaderFitSize.width,
                        myTabs.getHeight() - data.insets.bottom - data.moreRectAxisSize - 1,
                        myTabs.myHeaderFitSize.width - 1,
                        data.moreRectAxisSize - 1);
    }

    @Override
    public Dimension getCompSizeDelta(SingleRowPassInfo data) {
      return new Dimension(-myTabs.myHeaderFitSize.width, 0);
    }
  }

}