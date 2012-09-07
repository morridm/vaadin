/*
 * Copyright 2011 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.ui.orderedlayout;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Element;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ConnectorHierarchyChangeEvent;
import com.vaadin.client.Util;
import com.vaadin.client.communication.RpcProxy;
import com.vaadin.client.communication.StateChangeEvent;
import com.vaadin.client.communication.StateChangeEvent.StateChangeHandler;
import com.vaadin.client.ui.AbstractFieldConnector;
import com.vaadin.client.ui.AbstractLayoutConnector;
import com.vaadin.client.ui.LayoutClickEventHandler;
import com.vaadin.client.ui.layout.ElementResizeEvent;
import com.vaadin.client.ui.layout.ElementResizeListener;
import com.vaadin.client.ui.orderedlayout.VOrderedLayout.CaptionPosition;
import com.vaadin.client.ui.orderedlayout.VOrderedLayout.Slot;
import com.vaadin.shared.AbstractFieldState;
import com.vaadin.shared.ComponentConstants;
import com.vaadin.shared.communication.URLReference;
import com.vaadin.shared.ui.AlignmentInfo;
import com.vaadin.shared.ui.LayoutClickRpc;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.orderedlayout.AbstractOrderedLayoutServerRpc;
import com.vaadin.shared.ui.orderedlayout.AbstractOrderedLayoutState;

/**
 * Base class for vertical and horizontal ordered layouts
 */
public abstract class AbstractOrderedLayoutConnector extends
        AbstractLayoutConnector {

    AbstractOrderedLayoutServerRpc rpc;

    /*
     * Handlers & Listeners
     */

    private LayoutClickEventHandler clickEventHandler = new LayoutClickEventHandler(
            this) {

        @Override
        protected ComponentConnector getChildComponent(Element element) {
            return Util.getConnectorForElement(getConnection(), getWidget(),
                    element);
        }

        @Override
        protected LayoutClickRpc getLayoutClickRPC() {
            return rpc;
        };
    };

    private StateChangeHandler childStateChangeHandler = new StateChangeHandler() {
        @Override
        public void onStateChanged(StateChangeEvent stateChangeEvent) {

            ComponentConnector child = (ComponentConnector) stateChangeEvent
                    .getConnector();

            // We need to update the slot size if the component size is changed
            // to relative
            Slot slot = getWidget().getSlot(child.getWidget());
            slot.setRelativeWidth(child.isRelativeWidth());
            slot.setRelativeHeight(child.isRelativeHeight());

            updateSlotListeners(child);
            // updateAllSlotListeners();

            updateLayoutHeight();
        }
    };

    private ElementResizeListener slotCaptionResizeListener = new ElementResizeListener() {
        @Override
        public void onElementResize(ElementResizeEvent e) {

            // Get all needed element references
            Element captionElement = (Element) e.getElement().cast();

            // Caption position determines if the widget element is the first or
            // last child inside the caption wrap
            CaptionPosition pos = getWidget().getCaptionPositionFromElement(
                    (Element) captionElement.getParentElement().cast());

            // The default is the last child
            Element widgetElement = captionElement.getParentElement()
                    .getLastChild().cast();

            // ...but if caption position is bottom or right, the widget is the
            // first child
            if (pos == CaptionPosition.BOTTOM || pos == CaptionPosition.RIGHT) {
                widgetElement = captionElement.getParentElement()
                        .getFirstChildElement().cast();
            }

            if (captionElement == widgetElement) {
                // Caption element already detached
                rmeoveResizeListener(captionElement, slotCaptionResizeListener);
                childCaptionElementHeight.remove(widgetElement);
                return;
            }

            String widgetWidth = widgetElement.getStyle().getWidth();
            String widgetHeight = widgetElement.getStyle().getHeight();

            if (widgetHeight.endsWith("%")
                    && (pos == CaptionPosition.TOP || pos == CaptionPosition.BOTTOM)) {
                getWidget().updateCaptionOffset(captionElement);
            } else if (widgetWidth.endsWith("%")
                    && (pos == CaptionPosition.LEFT || pos == CaptionPosition.RIGHT)) {
                getWidget().updateCaptionOffset(captionElement);
            }

            int h = getLayoutManager().getOuterHeight(captionElement)
                    - getLayoutManager().getMarginHeight(captionElement);
            childCaptionElementHeight.put(widgetElement, h);

            updateLayoutHeight();

            if (needsExpand()) {
                getWidget().updateExpand();
            }
        }
    };

    private ElementResizeListener childComponentResizeListener = new ElementResizeListener() {
        @Override
        public void onElementResize(ElementResizeEvent e) {
            updateLayoutHeight();
            if (needsExpand()) {
                getWidget().updateExpand();
            }
        }
    };

    private ElementResizeListener spacingResizeListener = new ElementResizeListener() {
        @Override
        public void onElementResize(ElementResizeEvent e) {
            if (needsExpand()) {
                getWidget().updateExpand();
            }
        }
    };

    /*
     * (non-Javadoc)
     * 
     * @see com.vaadin.client.ui.AbstractComponentConnector#init()
     */
    @Override
    public void init() {
        super.init();
        rpc = RpcProxy.create(AbstractOrderedLayoutServerRpc.class, this);
        getWidget().setLayoutManager(getLayoutManager());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.vaadin.client.ui.AbstractLayoutConnector#getState()
     */
    @Override
    public AbstractOrderedLayoutState getState() {
        return (AbstractOrderedLayoutState) super.getState();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.vaadin.client.ui.AbstractComponentConnector#getWidget()
     */
    @Override
    public VOrderedLayout getWidget() {
        return (VOrderedLayout) super.getWidget();
    }

    /**
     * For bookkeeping. Used to determine if extra calculations are needed for
     * horizontal layout.
     */
    private HashSet<ComponentConnector> hasVerticalAlignment = new HashSet<ComponentConnector>();

    /**
     * For bookkeeping. Used to determine if extra calculations are needed for
     * horizontal layout.
     */
    private HashSet<ComponentConnector> hasRelativeHeight = new HashSet<ComponentConnector>();

    /**
     * For bookkeeping. Used to determine if extra calculations are needed for
     * horizontal layout.
     */
    private HashSet<ComponentConnector> hasExpandRatio = new HashSet<ComponentConnector>();

    /**
     * For bookkeeping. Used in extra calculations for horizontal layout.
     */
    private HashSet<Element> needsMeasure = new HashSet<Element>();

    /**
     * For bookkeeping. Used in extra calculations for horizontal layout.
     */
    private HashMap<Element, Integer> childCaptionElementHeight = new HashMap<Element, Integer>();

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.vaadin.client.ComponentContainerConnector#updateCaption(com.vaadin
     * .client.ComponentConnector)
     */
    @Override
    public void updateCaption(ComponentConnector child) {
        Slot slot = getWidget().getSlot(child.getWidget());

        String caption = child.getState().caption;
        URLReference iconUrl = child.getState().resources
                .get(ComponentConstants.ICON_RESOURCE);
        String iconUrlString = iconUrl != null ? iconUrl.toString() : null;
        List<String> styles = child.getState().styles;
        String error = child.getState().errorMessage;
        boolean showError = error != null;
        if (child.getState() instanceof AbstractFieldState) {
            AbstractFieldState abstractFieldState = (AbstractFieldState) child
                    .getState();
            showError = showError && !abstractFieldState.hideErrors;
        }
        boolean required = false;
        if (child instanceof AbstractFieldConnector) {
            required = ((AbstractFieldConnector) child).isRequired();
        }
        boolean enabled = child.getState().enabled;

        slot.setCaption(caption, iconUrlString, styles, error, showError,
                required, enabled);

        slot.setRelativeWidth(child.isRelativeWidth());
        slot.setRelativeHeight(child.isRelativeHeight());

        if (slot.hasCaption()) {
            CaptionPosition pos = slot.getCaptionPosition();
            getLayoutManager().addElementResizeListener(
                    slot.getCaptionElement(), slotCaptionResizeListener);
            if (child.isRelativeHeight()
                    && (pos == CaptionPosition.TOP || pos == CaptionPosition.BOTTOM)) {
                getWidget().updateCaptionOffset(slot.getCaptionElement());
            } else if (child.isRelativeWidth()
                    && (pos == CaptionPosition.LEFT || pos == CaptionPosition.RIGHT)) {
                getWidget().updateCaptionOffset(slot.getCaptionElement());
            }
        } else {
            childCaptionElementHeight.remove(child.getWidget().getElement());
        }

        updateLayoutHeight();

        if (needsExpand()) {
            getWidget().updateExpand();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.vaadin.client.ui.AbstractComponentContainerConnector#
     * onConnectorHierarchyChange
     * (com.vaadin.client.ConnectorHierarchyChangeEvent)
     */
    @Override
    public void onConnectorHierarchyChange(ConnectorHierarchyChangeEvent event) {
        super.onConnectorHierarchyChange(event);

        List<ComponentConnector> previousChildren = event.getOldChildren();
        int currentIndex = 0;
        VOrderedLayout layout = getWidget();

        for (ComponentConnector child : getChildComponents()) {
            Slot slot = layout.getSlot(child.getWidget());
            if (slot.getParent() != layout) {
                child.addStateChangeHandler(childStateChangeHandler);
            }
            layout.addOrMoveSlot(slot, currentIndex++);
        }

        for (ComponentConnector child : previousChildren) {
            if (child.getParent() != this) {
                Slot slot = layout.getSlot(child.getWidget());
                hasVerticalAlignment.remove(child);
                hasRelativeHeight.remove(child);
                hasExpandRatio.remove(child);
                needsMeasure.remove(child.getWidget().getElement());
                childCaptionElementHeight
                        .remove(child.getWidget().getElement());
                getLayoutManager().removeElementResizeListener(
                        child.getWidget().getElement(),
                        childComponentResizeListener);
                if (slot.hasCaption()) {
                    getLayoutManager()
                            .removeElementResizeListener(
                                    slot.getCaptionElement(),
                                    slotCaptionResizeListener);
                }
                if (slot.getSpacingElement() != null) {
                    getLayoutManager().removeElementResizeListener(
                            slot.getSpacingElement(), spacingResizeListener);
                }
                child.removeStateChangeHandler(childStateChangeHandler);
                layout.removeWidget(child.getWidget());
            }
        }

        // If some component is added/removed, we need to recalculate the expand
        if (needsExpand()) {
            getWidget().updateExpand();
        } else {
            getWidget().clearExpand();
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.vaadin.client.ui.AbstractComponentConnector#onStateChanged(com.vaadin
     * .client.communication.StateChangeEvent)
     */
    @Override
    public void onStateChanged(StateChangeEvent stateChangeEvent) {
        super.onStateChanged(stateChangeEvent);

        clickEventHandler.handleEventHandlerRegistration();
        getWidget().setMargin(new MarginInfo(getState().marginsBitmask));
        getWidget().setSpacing(getState().spacing);

        hasExpandRatio.clear();
        hasVerticalAlignment.clear();
        hasRelativeHeight.clear();
        needsMeasure.clear();

        boolean equalExpandRatio = getWidget().vertical ? !isUndefinedHeight()
                : !isUndefinedWidth();
        for (ComponentConnector child : getChildComponents()) {
            double expandRatio = getState().childData.get(child).expandRatio;
            if (expandRatio > 0) {
                equalExpandRatio = false;
                break;
            }
        }

        for (ComponentConnector child : getChildComponents()) {
            Slot slot = getWidget().getSlot(child.getWidget());

            AlignmentInfo alignment = new AlignmentInfo(
                    getState().childData.get(child).alignmentBitmask);
            slot.setAlignment(alignment);

            double expandRatio = getState().childData.get(child).expandRatio;

            if (equalExpandRatio) {
                expandRatio = 1;
            } else if (expandRatio == 0) {
                expandRatio = -1;
            }
            slot.setExpandRatio(expandRatio);

            // Bookkeeping to identify special cases that need extra
            // calculations
            if (alignment.isVerticalCenter() || alignment.isBottom()) {
                hasVerticalAlignment.add(child);
            }

            if (expandRatio > 0) {
                hasExpandRatio.add(child);
            }
        }

        updateAllSlotListeners();

        updateLayoutHeight();
    }

    /**
     * Does the layout need a fixed height?
     */
    private boolean needsFixedHeight() {
        if (!getWidget().vertical
                && isUndefinedHeight()
                && (hasRelativeHeight.size() > 0 || (hasVerticalAlignment
                        .size() > 0 && hasVerticalAlignment.size() < getChildren()
                        .size()))) {
            return true;
        }
        return false;
    }

    /**
     * Does the layout need to expand?
     */
    private boolean needsExpand() {
        boolean canApplyExpand = (getWidget().vertical && !isUndefinedHeight())
                || (!getWidget().vertical && !isUndefinedWidth());
        return hasExpandRatio.size() > 0 && canApplyExpand;
    }

    /**
     * Add slot listeners
     */
    private void updateAllSlotListeners() {
        for (ComponentConnector child : getChildComponents()) {
            updateSlotListeners(child);
        }
    }

    /**
     * Add/remove necessary ElementResizeListeners for one slot. This should be
     * called after each update to the slot's or it's widget.
     */
    private void updateSlotListeners(ComponentConnector child) {
        Slot slot = getWidget().getSlot(child.getWidget());

        // Clear all possible listeners first
        rmeoveResizeListener(slot.getWidget().getElement(),
                childComponentResizeListener);
        if (slot.hasCaption()) {
            rmeoveResizeListener(slot.getCaptionElement(),
                    slotCaptionResizeListener);
        }
        if (slot.hasSpacing()) {
            rmeoveResizeListener(slot.getSpacingElement(),
                    spacingResizeListener);
        }

        // Add all necessary listeners
        if (needsFixedHeight()) {
            addResizeListener(slot.getWidget().getElement(),
                    childComponentResizeListener);
            if (slot.hasCaption()) {
                addResizeListener(slot.getCaptionElement(),
                        slotCaptionResizeListener);
            }
        } else if ((child.isRelativeHeight() || child.isRelativeWidth())
                && slot.hasCaption()) {
            // If the slot has caption, we need to listen for it's size changes
            // in order to update the padding/margin offset for relative sized
            // components
            addResizeListener(slot.getCaptionElement(),
                    slotCaptionResizeListener);
        }

        if (needsExpand()) {
            addResizeListener(slot.getWidget().getElement(),
                    childComponentResizeListener);
            if (slot.hasSpacing()) {
                addResizeListener(slot.getSpacingElement(),
                        spacingResizeListener);
            }
        }

        if (child.isRelativeHeight()) {
            hasRelativeHeight.add(child);
            needsMeasure.remove(child.getWidget().getElement());
        } else {
            hasRelativeHeight.remove(child);
            needsMeasure.add(child.getWidget().getElement());
        }

    }

    /**
     * Re-calculate the layout height
     */
    private void updateLayoutHeight() {
        if (needsFixedHeight()) {
            int h = getMaxHeight();
            h += getLayoutManager().getBorderHeight(getWidget().getElement())
                    + getLayoutManager().getPaddingHeight(
                            getWidget().getElement());
            getWidget().getElement().getStyle().setHeight(h, Unit.PX);
            getLayoutManager().setNeedsMeasure(this);
        }
    }

    /**
     * Measures the maximum height of the layout in pixels
     */
    private int getMaxHeight() {
        int highestNonRelative = -1;
        int highestRelative = -1;

        for (ComponentConnector child : getChildComponents()) {
            // TODO would be more efficient to measure the slot element if both
            // caption and child widget elements need to be measured. Keeping
            // track of what to measure is the most difficult part of this
            // layout.
            Element el = child.getWidget().getElement();
            CaptionPosition pos = getWidget().getCaptionPositionFromElement(
                    (Element) el.getParentElement().cast());
            if (needsMeasure.contains(el)) {
                int h = getLayoutManager().getOuterHeight(el);
                String sHeight = el.getStyle().getHeight();
                // Only add the caption size to the height of the slot if
                // coption position is top or bottom
                if (childCaptionElementHeight.containsKey(el)
                        && (sHeight == null || !sHeight.endsWith("%"))
                        && (pos == CaptionPosition.TOP || pos == CaptionPosition.BOTTOM)) {
                    h += childCaptionElementHeight.get(el);
                }
                if (h > highestNonRelative) {
                    highestNonRelative = h;
                }
            } else {
                int h = getLayoutManager().getOuterHeight(el);
                if (childCaptionElementHeight.containsKey(el)
                        && (pos == CaptionPosition.TOP || pos == CaptionPosition.BOTTOM)) {
                    h += childCaptionElementHeight.get(el);
                }
                if (h > highestRelative) {
                    highestRelative = h;
                }
            }
        }
        return highestNonRelative > -1 ? highestNonRelative : highestRelative;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.vaadin.client.ui.AbstractComponentConnector#onUnregister()
     */
    @Override
    public void onUnregister() {
        // Cleanup all ElementResizeListeners
        for (ComponentConnector child : getChildComponents()) {
            Slot slot = getWidget().getSlot(child.getWidget());
            if (slot.hasCaption()) {
                rmeoveResizeListener(slot.getCaptionElement(),
                        slotCaptionResizeListener);
            }

            if (slot.getSpacingElement() != null) {
                rmeoveResizeListener(slot.getSpacingElement(),
                        spacingResizeListener);
            }

            rmeoveResizeListener(slot.getWidget().getElement(),
                    childComponentResizeListener);
        }

        super.onUnregister();
    }

    /**
     * Helper method to add a resize listener to an element
     * 
     * @param el
     *            The element to add the resize listener to
     * @param listener
     *            The listener to add
     */
    private void addResizeListener(Element el, ElementResizeListener listener) {
        getLayoutManager().addElementResizeListener(el, listener);
    }

    /**
     * Helper method to remove a resize listener to an element
     * 
     * @param el
     *            The element from where the resize listener should be removed
     * @param listener
     *            THe listener to remove
     */
    private void rmeoveResizeListener(Element el, ElementResizeListener listener) {
        getLayoutManager().removeElementResizeListener(el, listener);
    }

}