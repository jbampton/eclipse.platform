/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.ContextEvent;
import org.eclipse.ui.contexts.ContextManagerFactory;
import org.eclipse.ui.contexts.IContext;
import org.eclipse.ui.contexts.IContextListener;
import org.eclipse.ui.contexts.IMutableContextManager;
import org.eclipse.ui.contexts.NotDefinedException;

/**
 * A context listener which automatically opens/closes/activates views in
 * response to debug context changes.
 */
public class LaunchViewContextListener implements IContextListener, IPartListener, IPageListener {
	
	public static final String ID_CONTEXT_VIEW_BINDINGS= "contextViewBindings"; //$NON-NLS-1$
	public static final String ID_DEBUG_MODEL_CONTEXT_BINDINGS= "debugModelContextBindings"; //$NON-NLS-1$
	public static final String ATTR_CONTEXT_ID= "contextId"; //$NON-NLS-1$
	public static final String ATTR_VIEW_ID= "viewId"; //$NON-NLS-1$
	public static final String ATTR_DEBUG_MODEL_ID= "debugModelId"; //$NON-NLS-1$
	public static final String ATTR_AUTO_OPEN= "autoOpen"; //$NON-NLS-1$
	public static final String ATTR_AUTO_CLOSE= "autoClose"; //$NON-NLS-1$
	
	private Map modelsToContext= new HashMap();
	private Map contextViews= new HashMap();
	private IMutableContextManager contextManager= ContextManagerFactory.getMutableContextManager();
	private String currentContext= null;
	/**
	 * Collection of all views that might be opened or closed automatically.
	 * This collection starts out containing all views associated with a context.
	 * As views are manually opened and closed by the user, they're removed.
	 */
	private Set managedViewIds= new HashSet();
	private Set viewIdsToNotOpen= new HashSet();
	private Set viewIdsToNotClose= new HashSet();
	
	public LaunchViewContextListener() {
		loadDebugModelContextExtensions();
		loadContextToViewExtensions();
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().addPageListener(this);
	}
	
	private void loadContextToViewExtensions() {
		IExtensionPoint extensionPoint = DebugUIPlugin.getDefault().getDescriptor().getExtensionPoint(ID_CONTEXT_VIEW_BINDINGS);
		IConfigurationElement[] configurationElements = extensionPoint.getConfigurationElements();
		for (int i = 0; i < configurationElements.length; i++) {
			IConfigurationElement element = configurationElements[i];
			String contextId = element.getAttribute(ATTR_CONTEXT_ID);
			String viewId = element.getAttribute(ATTR_VIEW_ID);
			if (contextId == null || viewId == null) {
				continue;
			}
			IContext context= contextManager.getContext(contextId);
			context.addContextListener(this);
			List elements= (List) contextViews.get(contextId);
			if (elements == null) {
				elements= new ArrayList();
				contextViews.put(contextId, elements);
			}
			elements.add(element);
			managedViewIds.add(viewId);
			String autoOpen= element.getAttribute(ATTR_AUTO_OPEN);
			if (autoOpen != null && !Boolean.valueOf(autoOpen).booleanValue()) {
				viewIdsToNotOpen.add(viewId);
			}
			String autoClose= element.getAttribute(ATTR_AUTO_CLOSE);
			if (autoClose != null && !Boolean.valueOf(autoClose).booleanValue()) {
				viewIdsToNotClose.add(viewId);
			}
		}
		
	}

	private void loadDebugModelContextExtensions() {
		IExtensionPoint extensionPoint = DebugUIPlugin.getDefault().getDescriptor().getExtensionPoint(ID_DEBUG_MODEL_CONTEXT_BINDINGS);
		IConfigurationElement[] configurationElements = extensionPoint.getConfigurationElements();
		for (int i = 0; i < configurationElements.length; i++) {
			IConfigurationElement element = configurationElements[i];
			String modelIdentifier = element.getAttribute(ATTR_DEBUG_MODEL_ID);
			String context = element.getAttribute(ATTR_CONTEXT_ID);
			if (modelIdentifier != null && context != null) {
				modelsToContext.put(modelIdentifier, context);
			}
		}
	}
	
	public String getDebugModelContext(String debugModelIdentifier) {
		return (String) modelsToContext.get(debugModelIdentifier);
	}
	
	public IMutableContextManager getContextManager() {
		return contextManager;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.contexts.IContextListener#contextChanged(org.eclipse.ui.contexts.ContextEvent)
	 */
	public void contextChanged(ContextEvent contextEvent) {
		if (contextEvent.hasEnabledChanged()) {
			IContext context = contextEvent.getContext();
			if (context.isEnabled()) {
				contextActivated(context.getId());
			}
		}
	}
	
	/**
	 * The context with the given ID has been activated.
	 * If the given context ID is the same as the current
	 * context, do nothing. Otherwise, activate the appropriate
	 * views.
	 * 
	 * @param contextId the ID of the context that has been
	 * 	activated
	 */
	public void contextActivated(String contextId) {
		if (contextId.equals(currentContext)) {
			return;
		}
		currentContext= contextId;
		List configurationElements= getConfigurationElements(contextId);
		if (configurationElements.isEmpty()) {
			return;
		}
		IWorkbenchPage page= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		page.removePartListener(this); // Stop listening before closing/opening/activating views
		List viewsToActivate= new ArrayList();
		List viewsToOpen= new ArrayList();
		Iterator iterator= configurationElements.iterator();
		while (iterator.hasNext()) {
			IConfigurationElement element = (IConfigurationElement) iterator.next();
			String viewId= element.getAttribute(ATTR_VIEW_ID);
			if (viewId == null) {
				continue;
			}
			IViewPart view = page.findView(viewId);
			if (view != null) {
				viewsToActivate.add(view);
			} else {
				// Don't open automatically if specified not to.
				if (!viewIdsToNotOpen.contains(viewId)) {
					viewsToOpen.add(viewId);
				}
			}
		}
		if (!viewsToActivate.isEmpty() || !viewsToOpen.isEmpty()) {
			IViewReference[] references = page.getViewReferences();
			for (int i = 0; i < references.length; i++) {
				IViewReference reference = references[i];
				String viewId= reference.getId();
				IViewPart view= reference.getView(true);
				if (view == null || !managedViewIds.contains(viewId)) {
					// Only close views that are associated with another context and which
					// the user hasn't manually opened.
					continue;
				}
				if (!viewsToActivate.contains(view) && !viewsToOpen.contains(viewId) &&
						!viewIdsToNotClose.contains(viewId)) {
					// Close all views that aren't applicable, unless specified not to
					page.hideView(view);
				}
			}
		}
		iterator= viewsToOpen.iterator();
		while (iterator.hasNext()) {
			String viewId = (String) iterator.next();
			try {
				viewsToActivate.add(page.createView(viewId));
			} catch (PartInitException e) {
				DebugUIPlugin.log(e.getStatus());
			}
		}
		// Until we have an API to open views "underneath" (bug 50618), first iterate
		// to remove views using the stack information, then open views, then activate.
		// When the "open underneath" API is provided, only iterate once.
		ListIterator listIterator= viewsToActivate.listIterator();
		while (listIterator.hasNext()) {
			boolean activate= true;
			IViewPart view = (IViewPart) listIterator.next();
			IViewPart[] stackedViews = page.getViewStack(view);
			for (int i = 0; i < stackedViews.length; i++) {
				IViewPart stackedView= stackedViews[i];
				if (view != stackedView && viewsToActivate.contains(stackedView) && page.isPartVisible(stackedView)) {
					// If this view is currently obscured by an appropriate view that is already visible,
					// don't activate it (let the visible view stay visible).
					activate= false;
					break;
				}
			}
			if (activate) {
				page.bringToTop(view);
			}
		}
		page.addPartListener(this); // Start listening again for close/open
	}
	
	/**
	 * Lists the contextViews configuration elements for the
	 * given context ID and all its parent context IDs.
	 * 
	 * @param contextId the context ID
	 * @return the configuration elements for the given context ID and
	 * 	all parent context IDs. 
	 */
	private List getConfigurationElements(String contextId) {
		List allElements= new ArrayList();
		while (contextId != null) {
			List elements= (List) contextViews.get(contextId);
			if (elements != null) {
				allElements.addAll(elements);
			}
			IContext context = contextManager.getContext(contextId);
			if (context != null) {
				try {
					contextId= context.getParentId();
				} catch (NotDefinedException e) {
					contextId= null;
				}
			}
		}
		return allElements;		 
	}

	public void partClosed(IWorkbenchPart part) {
		if (part instanceof IViewPart) {
			String id = ((IViewPart) part).getViewSite().getId();
			if (managedViewIds.remove(id)) {
				viewIdsToNotOpen.add(id);
			}
		}
	}
	public void partOpened(IWorkbenchPart part) {
		if (part instanceof IViewPart) {
			String id = ((IViewPart) part).getViewSite().getId();
			if (managedViewIds.remove(id)) {
				viewIdsToNotClose.add(id);
			}
		}
	}
	public void partActivated(IWorkbenchPart part) {
	}
	public void partBroughtToTop(IWorkbenchPart part) {
	}
	public void partDeactivated(IWorkbenchPart part) {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPageListener#pageActivated(org.eclipse.ui.IWorkbenchPage)
	 */
	public void pageActivated(IWorkbenchPage page) {
		page.addPartListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPageListener#pageClosed(org.eclipse.ui.IWorkbenchPage)
	 */
	public void pageClosed(IWorkbenchPage page) {
		page.removePartListener(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.IPageListener#pageOpened(org.eclipse.ui.IWorkbenchPage)
	 */
	public void pageOpened(IWorkbenchPage page) {
	}

}
