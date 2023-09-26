package com.mjhylkema.SpiritTreeMap.ui;

/**
 * A listener interface for receiving UI component events
 * Credit to Antipixel for the original implementation
 */
public interface ComponentEventListener
{
	/**
	 * Invoked upon a component event
	 * @param src the source component responsible for the event
	 */
	void onComponentEvent(UIComponent src);
}