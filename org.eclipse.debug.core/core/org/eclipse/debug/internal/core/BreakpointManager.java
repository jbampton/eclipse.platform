package org.eclipse.debug.internal.core;

/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp.  All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
**********************************************************************/

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointListener;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.IBreakpointsListener;
import org.eclipse.debug.core.model.IBreakpoint;

/**
 * The breakpoint manager manages all registered breakpoints
 * for the debug plugin. It is instantiated by the debug plugin at startup.
 *
 * @see IBreakpointManager
 */
public class BreakpointManager implements IBreakpointManager, IResourceChangeListener {
	
	/**
	 * Constants for breakpoint add/remove/change updates
	 */
	private final static int ADDED = 0;
	private final static int REMOVED = 1;
	private final static int CHANGED = 2;
	
	/**
	 * String constants corresponding to XML extension keys
	 */
	private final static String CLASS = "class"; //$NON-NLS-1$
	
	/**
	 * Attribute name for the <code>"markerType"</code> attribute of
	 * a breakpoint extension.
	 */
	private static final String MARKER_TYPE= "markerType";	 //$NON-NLS-1$	

	/**
	 * A collection of breakpoints registered with this manager.
	 */
	private Vector fBreakpoints= null;
	
	/**
	 * Collection of breakpoints being added currently. Used to 
	 * suppress change notication of "REGISTERED" attribute when
	 * being added.	 */
	private List fSuppressChange = new ArrayList();
	
	/**
	 * A table of breakpoint extension points, keyed by
	 * marker type
	 * key: a marker type
	 * value: the breakpoint extension which corresponds to that marker type
	 */
	private HashMap fBreakpointExtensions;
	
	/**
	 * Collection of markers that associates markers to breakpoints
	 * key: a marker
	 * value: the breakpoint which contains that marker
	 */	
	private HashMap fMarkersToBreakpoints;

	/**
	 * Collection of breakpoint listeners.
	 */
	private ListenerList fBreakpointListeners= new ListenerList(6);
	
	/**
	 * Collection of (multi) breakpoint listeners.
	 */
	private ListenerList fBreakpointsListeners= new ListenerList(6);	

	/**
	 * Singleton resource delta visitor which handles marker
	 * additions, changes, and removals.
	 */
	private static BreakpointManagerVisitor fgVisitor;

	/**
	 * Constructs a new breakpoint manager.
	 */
	public BreakpointManager() {
		fMarkersToBreakpoints= new HashMap(10);	
		fBreakpointExtensions = new HashMap(15);	
	}

	/**
	 * Registers this manager as a resource change listener and
	 * initializes the collection of defined breakpoint extensions.
	 * 
	 * This method should only be called on initial startup of 
	 * the debug plugin.
	 */
	public void startup() throws CoreException {
		getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_AUTO_BUILD);
	}
	
	/**
	 * Loads all the breakpoints on the given resource.
	 * 
	 * @param resource the resource which contains the breakpoints
	 */
	private void loadBreakpoints(IResource resource) throws CoreException {
		initBreakpointExtensions();
		IMarker[] markers= getPersistedMarkers(resource);
		List added = new ArrayList();
		for (int i = 0; i < markers.length; i++) {
			IMarker marker= markers[i];
			try {
				IBreakpoint breakpoint = createBreakpoint(marker);
				if (breakpoint.isRegistered()) {
					added.add(breakpoint);
				}
			} catch (DebugException e) {
				DebugPlugin.log(e);
			}
		}
		addBreakpoints((IBreakpoint[])added.toArray(new IBreakpoint[added.size()]));
	}
	
	/**
	 * Returns the persisted markers associated with the given resource.
	 * 
	 * Delete any non-persisted/invalid breakpoint markers. This is done
	 * at startup rather than shutdown, since the changes made at
	 * shutdown are not persisted as the workspace state has already
	 * been saved. See bug 7683.
	 */
	protected IMarker[] getPersistedMarkers(IResource resource) throws CoreException {
		IMarker[] markers= resource.findMarkers(IBreakpoint.BREAKPOINT_MARKER, true, IResource.DEPTH_INFINITE);
		final List delete = new ArrayList();
		List persisted= new ArrayList();
		for (int i = 0; i < markers.length; i++) {
			IMarker marker= markers[i];
			// ensure the marker has a valid model identifier attribute
			// and delete the breakpoint if not
			String modelId = marker.getAttribute(IBreakpoint.ID, null);
			if (modelId == null) {
				// marker with old/invalid format - delete
				delete.add(marker);
			} else if (!marker.getAttribute(IBreakpoint.PERSISTED, true)) {
				// the breakpoint is marked as not to be persisted,
				// schedule for deletion
				delete.add(marker);
			} else {
				persisted.add(marker);
			}
		}
		// delete any markers that are not to be restored
		if (!delete.isEmpty()) {
			IWorkspaceRunnable wr = new IWorkspaceRunnable() {
				public void run(IProgressMonitor pm) throws CoreException {
					ResourcesPlugin.getWorkspace().deleteMarkers((IMarker[])delete.toArray(new IMarker[delete.size()]));
				}
			};
			fork(wr);
		}
		return (IMarker[])persisted.toArray(new IMarker[persisted.size()]);
	}
	
	/**
	 * Removes this manager as a resource change listener
	 * and removes all breakpoint listeners.
	 */
	public void shutdown() {
		getWorkspace().removeResourceChangeListener(this);
		fBreakpointListeners.removeAll();
	}

	/**
	 * Find the defined breakpoint extensions and cache them for use in recreating
	 * breakpoints from markers.
	 */
	private void initBreakpointExtensions() {
		IExtensionPoint ep= DebugPlugin.getDefault().getDescriptor().getExtensionPoint(DebugPlugin.EXTENSION_POINT_BREAKPOINTS);
		IConfigurationElement[] elements = ep.getConfigurationElements();
		for (int i= 0; i < elements.length; i++) {
			fBreakpointExtensions.put(elements[i].getAttribute(MARKER_TYPE), elements[i]);
		}
		
	}

	/**
	 * Convenience method to get the workspace
	 */
	private IWorkspace getWorkspace() {
		return ResourcesPlugin.getWorkspace();
	}

	/**
	 * @see IBreakpointManager#getBreakpoint(IMarker)
	 */
	public IBreakpoint getBreakpoint(IMarker marker) {
		// ensure that breakpoints are initialized
		getBreakpoints0();
		return (IBreakpoint)fMarkersToBreakpoints.get(marker);
	}

	/**
	 * @see IBreakpointManager#getBreakpoints()
	 */
	public IBreakpoint[] getBreakpoints() {
		Vector breakpoints= getBreakpoints0();
		IBreakpoint[] temp= new IBreakpoint[breakpoints.size()];
		breakpoints.copyInto(temp);
		return temp;
	}
	
	/**
	 * The BreakpointManager waits to load the breakpoints
	 * of the workspace until a request is made to retrieve the 
	 * breakpoints.
	 */
	private Vector getBreakpoints0() {
		if (fBreakpoints == null) {
			initializeBreakpoints();
		}
		return fBreakpoints;
	}	
	
	/**
	 * @see IBreakpointManager#getBreakpoints(String)
	 */
	public IBreakpoint[] getBreakpoints(String modelIdentifier) {
		Vector allBreakpoints= getBreakpoints0();
		ArrayList temp= new ArrayList(allBreakpoints.size());
		Iterator breakpoints= allBreakpoints.iterator();
		while (breakpoints.hasNext()) {
			IBreakpoint breakpoint= (IBreakpoint) breakpoints.next();
			String id= breakpoint.getModelIdentifier();
			if (id != null && id.equals(modelIdentifier)) {
				temp.add(breakpoint);
			}
		}
		return (IBreakpoint[]) temp.toArray(new IBreakpoint[temp.size()]);
	}

	/**
	 * Loads the list of breakpoints from the breakpoint markers in the
	 * workspace.
	 */
	private void initializeBreakpoints() {
		setBreakpoints(new Vector(10));
		try {
			loadBreakpoints(getWorkspace().getRoot());	
		} catch (CoreException ce) {
			DebugPlugin.log(ce);
			setBreakpoints(new Vector(0));
		} 
	}
	
	/**
	 * @see IBreakpointManager#isRegistered(IBreakpoint)
	 */
	public boolean isRegistered(IBreakpoint breakpoint) {
		return getBreakpoints0().contains(breakpoint);
	}

	
	/**
	 * @see IBreakpointManager#removeBreakpoint(IBreakpoint, boolean)
	 */
	public void removeBreakpoint(IBreakpoint breakpoint, boolean delete) throws CoreException {
		removeBreakpoints(new IBreakpoint[]{breakpoint}, delete);
	}
	
	/**
	 * @see IBreakpointManager#removeBreakpoints(IBreakpoint[], boolean)
	 */
	public void removeBreakpoints(IBreakpoint[] breakpoints, final boolean delete) throws CoreException {
		final List remove = new ArrayList(breakpoints.length);
		for (int i = 0; i < breakpoints.length; i++) {
			IBreakpoint breakpoint = breakpoints[i];
			if (getBreakpoints0().contains(breakpoint)) {
				remove.add(breakpoint);
			}
		}
		if (!remove.isEmpty()) {
			Iterator iter = remove.iterator();
			while (iter.hasNext()) {
				IBreakpoint breakpoint = (IBreakpoint)iter.next();
				getBreakpoints0().remove(breakpoint);
				fMarkersToBreakpoints.remove(breakpoint.getMarker());
			}
			fireUpdate(remove, null, REMOVED);
			IWorkspaceRunnable r = new IWorkspaceRunnable() {
				public void run(IProgressMonitor montitor) throws CoreException {
					Iterator iter = remove.iterator();
					while (iter.hasNext()) {
						IBreakpoint breakpoint = (IBreakpoint)iter.next();
						if (delete) {
							breakpoint.delete();
						} else {
							// if the breakpoint is being removed from the manager
							// because the project is closing, the breakpoint should
							// remain as registered, otherwise, the breakpoint should
							// be marked as deregistered
							IMarker marker = breakpoint.getMarker();
							if (marker.exists()) {
								IProject project = breakpoint.getMarker().getResource().getProject();
								if (project == null || project.isOpen()) {
									breakpoint.setRegistered(false);
								}
							}
						}
					}					
				}
			};
			getWorkspace().run(r, null);
		}
	}	
	
	/**
	 * Create a breakpoint for the given marker. The created breakpoint
	 * is of the type specified in the breakpoint extension associated
	 * with the given marker type.
	 * 
	 * @return a breakpoint on this marker
	 * @exception DebugException if breakpoint creation fails. Reasons for 
	 *  failure include:
	 * <ol>
	 * <li>The breakpoint manager cannot determine what kind of breakpoint
	 *     to instantiate for the given marker type</li>
	 * <li>A lower level exception occurred while accessing the given marker</li>
	 * </ol>
	 */
	private IBreakpoint createBreakpoint(IMarker marker) throws DebugException {
		IBreakpoint breakpoint= (IBreakpoint) fMarkersToBreakpoints.get(marker);
		if (breakpoint != null) {
			return breakpoint;
		}
		try {
			IConfigurationElement config = (IConfigurationElement)fBreakpointExtensions.get(marker.getType());
			if (config == null) {
				throw new DebugException(new Status(IStatus.ERROR, DebugPlugin.getUniqueIdentifier(), 
					DebugException.CONFIGURATION_INVALID, MessageFormat.format(DebugCoreMessages.getString("BreakpointManager.Missing_breakpoint_definition"), new String[] {marker.getType()}), null)); //$NON-NLS-1$
			}
			breakpoint = (IBreakpoint)config.createExecutableExtension(CLASS);
			breakpoint.setMarker(marker);
			return breakpoint;		
		} catch (CoreException e) {
			throw new DebugException(e.getStatus());
		}
	}	

	/**
	 * @see IBreakpointManager#addBreakpoint(IBreakpoint)
	 */
	public void addBreakpoint(IBreakpoint breakpoint) throws CoreException {
		addBreakpoints(new IBreakpoint[]{breakpoint});
	}
	
	/**
	 * @see IBreakpointManager#addBreakpoints(IBreakpoint[])
	 */
	public void addBreakpoints(IBreakpoint[] breakpoints) throws CoreException {
		final List added = new ArrayList(breakpoints.length);
		for (int i = 0; i < breakpoints.length; i++) {
			IBreakpoint breakpoint = breakpoints[i];
			if (!getBreakpoints0().contains(breakpoint)) {
				verifyBreakpoint(breakpoint);
				added.add(breakpoint);
			}	
		}
		if (!added.isEmpty()) {
			IWorkspaceRunnable r = new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					Iterator iter = added.iterator();
					while (iter.hasNext()) {
						IBreakpoint breakpoint = (IBreakpoint)iter.next();
						getBreakpoints0().add(breakpoint);
						breakpoint.setRegistered(true);
						fMarkersToBreakpoints.put(breakpoint.getMarker(), breakpoint);						
					}
				}
			};
			// Need to suppress change notification, since this is really
			// an add notification			
			fSuppressChange.addAll(added);
			getWorkspace().run(r, null);
			fSuppressChange.removeAll(added);
			fireUpdate(added, null, ADDED);
		}			
	}	
	
	/**
	 * Returns whether change notification is to be suppressed for the given breakpoint.
	 * Used when adding breakpoints and changing the "REGISTERED" attribute.
	 * 	 * @param breakpoint	 * @return boolean whether change notification is suppressed	 */
	protected boolean isChangeSuppressed(IBreakpoint breakpoint) {
		return fSuppressChange.contains(breakpoint);
	}
	
	/**
	 * @see IBreakpointManager#fireBreakpointChanged(IBreakpoint)
	 */
	public void fireBreakpointChanged(IBreakpoint breakpoint) {
		if (getBreakpoints0().contains(breakpoint)) {
			List changed = new ArrayList();
			changed.add(breakpoint);
			fireUpdate(changed, null, CHANGED);
		}
	}

	/**
	 * Verifies that the breakpoint marker has the minimal required attributes,
	 * and throws a debug exception if not.
	 */
	private void verifyBreakpoint(IBreakpoint breakpoint) throws DebugException {
		try {
			String id= breakpoint.getModelIdentifier();
			if (id == null) {
				throw new DebugException(new Status(IStatus.ERROR, DebugPlugin.getUniqueIdentifier(), 
					DebugException.CONFIGURATION_INVALID, DebugCoreMessages.getString("BreakpointManager.Missing_model_identifier"), null)); //$NON-NLS-1$
			}
		} catch (CoreException e) {
			throw new DebugException(e.getStatus());
		}
	}

	/**
	 * A resource has changed. Traverses the delta for breakpoint changes.
	 */
	public void resourceChanged(IResourceChangeEvent event) {
		if (!isRestored()) {
			// if breakpoints have not been restored, deltas
			// should not be processed (we are unable to restore
			// breakpoints in a resource callback, as that might
			// cause the resource tree to be modififed, which is
			// not allowed during notification).
			// @see bug 9327
			return;
		}
		IResourceDelta delta= event.getDelta();
		if (delta != null) {
			try {
				if (fgVisitor == null) {
					fgVisitor= new BreakpointManagerVisitor();
				}
				delta.accept(fgVisitor);
				fgVisitor.update();
			} catch (CoreException ce) {
				DebugPlugin.log(ce);
			}
		}
	}
	
	/**
	 * Returns whether breakpoints have been restored
	 * since the workbench was started.
	 * 
	 * @return whether breakpoints have been restored
	 * since the workbench was started
	 */
	protected boolean isRestored() {
		return fBreakpoints != null;
	}

	/**
	 * Visitor for handling resource deltas
	 */
	class BreakpointManagerVisitor implements IResourceDeltaVisitor {
		/**
		 * Moved markers		 */
		private List fMoved = new ArrayList();
		/**
		 * Removed breakpoints		 */
		private List fRemoved = new ArrayList();
		/**
		 * Changed breakpoints and associated marker deltas		 */
		private List fChanged = new ArrayList();
		private List fChangedDeltas = new ArrayList();
		
		/**
		 * Resets the visitor for a delta traversal - empties
		 * collections of removed/changed breakpoints.
		 */
		protected void reset() {
			fMoved.clear();
			fRemoved.clear();
			fChanged.clear();
			fChangedDeltas.clear();
		}
		
		/**
		 * Performs updates on accumlated changes, and fires change notification after
		 * a traversal. Accumlated updates are reset.
		 */
		public void update() {
			if (!fMoved.isEmpty()) {
				// delete moved markers
				IWorkspaceRunnable wRunnable= new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor) throws CoreException {
						getWorkspace().deleteMarkers((IMarker[])fMoved.toArray(new IMarker[fMoved.size()]));
					}
				};
				try {
					getWorkspace().run(wRunnable, null);
				} catch (CoreException e) {
				}
			}
			if (!fRemoved.isEmpty()) {
				try {
					removeBreakpoints((IBreakpoint[])fRemoved.toArray(new IBreakpoint[fRemoved.size()]), false);
				} catch (CoreException e) {
					DebugPlugin.log(e);
				}
			}
			if (!fChanged.isEmpty()) {
				fireUpdate(fChanged, fChangedDeltas, CHANGED);
			}
			reset();
		} 
				
		/**
		 * @see IResourceDeltaVisitor#visit(IResourceDelta)
		 */
		public boolean visit(IResourceDelta delta) {
			if (delta == null) {
				return false;
			}
			if (0 != (delta.getFlags() & IResourceDelta.OPEN)) {
				handleProjectResourceOpenStateChange(delta.getResource());
				return false;
			}
			IMarkerDelta[] markerDeltas= delta.getMarkerDeltas();
			for (int i= 0; i < markerDeltas.length; i++) {
				IMarkerDelta markerDelta= markerDeltas[i];
				if (markerDelta.isSubtypeOf(IBreakpoint.BREAKPOINT_MARKER)) {
					switch (markerDelta.getKind()) {
						case IResourceDelta.ADDED :
							handleAddBreakpoint(delta, markerDelta.getMarker(), markerDelta);
							break;
						case IResourceDelta.REMOVED :
							handleRemoveBreakpoint(markerDelta.getMarker(), markerDelta);
							break;
						case IResourceDelta.CHANGED :
							handleChangeBreakpoint(markerDelta.getMarker(), markerDelta);
							break;
					}
				}
			}

			return true;
		}		

		/**
		 * Wrapper for handling adds
		 */
		protected void handleAddBreakpoint(IResourceDelta rDelta, final IMarker marker, IMarkerDelta mDelta) {
			if (0 != (rDelta.getFlags() & IResourceDelta.MOVED_FROM)) {
				// This breakpoint has actually been moved - already removed
				// from the Breakpoint manager during the remove callback.
				// Schedule the marker associated with the new resource for deletion.
				fMoved.add(marker);
			} else {
				// do nothing - we do not add until explicitly added
			}
		}
		
		/**
		 * Wrapper for handling removes
		 */
		protected void handleRemoveBreakpoint(IMarker marker, IMarkerDelta delta) {
			IBreakpoint breakpoint= getBreakpoint(marker);
			if (breakpoint != null) {
				fRemoved.add(breakpoint);
			}
		}

		/**
		 * Wrapper for handling changes
		 */
		protected void handleChangeBreakpoint(IMarker marker, IMarkerDelta delta) {
			final IBreakpoint breakpoint= getBreakpoint(marker);
			if (breakpoint != null && isRegistered(breakpoint) && !isChangeSuppressed(breakpoint)) {
				fChanged.add(breakpoint);
				fChangedDeltas.add(delta);
			}
		}
		
		/**
		 * A project has been opened or closed.  Updates the breakpoints for
		 * that project
		 */
		private void handleProjectResourceOpenStateChange(final IResource project) {
			if (!project.isAccessible()) {
				//closed
				Enumeration breakpoints= ((Vector)getBreakpoints0().clone()).elements();
				while (breakpoints.hasMoreElements()) {
					IBreakpoint breakpoint= (IBreakpoint) breakpoints.nextElement();
					IResource markerResource= breakpoint.getMarker().getResource();
					if (project.getFullPath().isPrefixOf(markerResource.getFullPath())) {
						fRemoved.add(breakpoint);
					}
				}
				return;
			} else {
				try {
					loadBreakpoints(project);
				} catch (CoreException e) {
					DebugPlugin.log(e);
				}
			}
		}		
	}

	/**
	 * @see IBreakpointManager#addBreakpointListener(IBreakpointListener)
	 */
	public void addBreakpointListener(IBreakpointListener listener) {
		fBreakpointListeners.add(listener);
	}

	/**
	 * @see IBreakpointManager#removeBreakpointListener(IBreakpointListener)
	 */
	public void removeBreakpointListener(IBreakpointListener listener) {
		fBreakpointListeners.remove(listener);
	}
	
	/**
	 * Notifies listeners of the adds/removes/changes
	 * 	 * @param breakpoints associated breakpoints	 * @param deltas or <code>null</code>	 * @param update type of change	 */
	private void fireUpdate(List breakpoints, List deltas, int update) {
		IBreakpoint[] bpArray = (IBreakpoint[])breakpoints.toArray(new IBreakpoint[breakpoints.size()]);
		IMarkerDelta[] deltaArray = new IMarkerDelta[bpArray.length];
		if (deltas != null) {
			deltaArray = (IMarkerDelta[])deltas.toArray(deltaArray);
		}
		// single listeners
		Object[] copiedListeners= fBreakpointListeners.getListeners();
		for (int i= 0; i < copiedListeners.length; i++) {
			IBreakpointListener listener = (IBreakpointListener)copiedListeners[i];
			for (int j = 0; j < bpArray.length; j++) {
				IBreakpoint breakpoint = bpArray[j];
				IMarkerDelta delta = deltaArray[j];
				switch (update) {
					case ADDED:
						listener.breakpointAdded(breakpoint);
						break;
					case REMOVED:
						listener.breakpointRemoved(breakpoint, delta);
						break;
					case CHANGED:
						listener.breakpointChanged(breakpoint, delta);		
						break;
				}				
			}
		}
		
		// multi listeners
		copiedListeners = fBreakpointsListeners.getListeners();
		for (int i= 0; i < copiedListeners.length; i++) {
			IBreakpointsListener listener = (IBreakpointsListener)copiedListeners[i];
			switch (update) {
				case ADDED:
					listener.breakpointsAdded(bpArray);
					break;
				case REMOVED:
					listener.breakpointsRemoved(bpArray, deltaArray);
					break;
				case CHANGED:
					listener.breakpointsChanged(bpArray, deltaArray);		
					break;
			}
		}		
	}	

	protected void setBreakpoints(Vector breakpoints) {
		fBreakpoints = breakpoints;
	}
	
	protected void fork(final IWorkspaceRunnable wRunnable) {
		Runnable runnable= new Runnable() {
			public void run() {
				try {
					getWorkspace().run(wRunnable, null);
				} catch (CoreException ce) {
					DebugPlugin.log(ce);
				}
			}
		};
		new Thread(runnable).start();
	}		

	/**
	 * @see IBreakpointManager#hasBreakpoints()
	 */
	public boolean hasBreakpoints() {
		return !getBreakpoints0().isEmpty();
	}
	/**
	 * @see org.eclipse.debug.core.IBreakpointManager#addBreakpointListener(org.eclipse.debug.core.IBreakpointsListener)
	 */
	public void addBreakpointListener(IBreakpointsListener listener) {
		fBreakpointsListeners.add(listener);
	}

	/**
	 * @see org.eclipse.debug.core.IBreakpointManager#removeBreakpointListener(org.eclipse.debug.core.IBreakpointsListener)
	 */
	public void removeBreakpointListener(IBreakpointsListener listener) {
		fBreakpointsListeners.remove(listener);
	}

}

