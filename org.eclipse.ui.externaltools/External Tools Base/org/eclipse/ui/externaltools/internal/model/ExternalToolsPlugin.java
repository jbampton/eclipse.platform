package org.eclipse.ui.externaltools.internal.model;

/**********************************************************************
Copyright (c) 2002 IBM Corp. and others. All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
 
Contributors:
**********************************************************************/

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPluginDescriptor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.externaltools.internal.registry.ArgumentVariableRegistry;
import org.eclipse.ui.externaltools.internal.registry.PathLocationVariableRegistry;
import org.eclipse.ui.externaltools.internal.registry.RefreshScopeVariableRegistry;
import org.eclipse.ui.externaltools.model.IExternalToolConstants;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * External tools plug-in class
 */
public final class ExternalToolsPlugin extends AbstractUIPlugin {
	/**
	 * Status representing no problems encountered during operation.
	 */
	public static final IStatus OK_STATUS = new Status(IStatus.OK, IExternalToolConstants.PLUGIN_ID, 0, "", null); //$NON-NLS-1$

	private static ExternalToolsPlugin plugin;

	private RefreshScopeVariableRegistry refreshVarRegistry;
	private PathLocationVariableRegistry fileLocVarRegistry;
	private PathLocationVariableRegistry dirLocVarRegistry;
	private ArgumentVariableRegistry argumentVarRegistry;

	/**
	 * Create an instance of the External Tools plug-in.
	 */
	public ExternalToolsPlugin(IPluginDescriptor descriptor) {
		super(descriptor);
		plugin = this;
	}

	/**
	 * Returns the default instance of the receiver.
	 * This represents the runtime plugin.
	 */
	public static ExternalToolsPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns a new <code>IStatus</code> for this plug-in
	 */
	public static IStatus newErrorStatus(String message, Throwable exception) {
		return new Status(Status.ERROR, IExternalToolConstants.PLUGIN_ID, 0, message, exception);
	}

	/**
	 * Returns a new <code>CoreException</code> for this plug-in
	 */
	public static CoreException newError(String message, Throwable exception) {
		return new CoreException(new Status(Status.ERROR, IExternalToolConstants.PLUGIN_ID, 0, message, exception));
	}

	/**
	 * Returns the registry of refresh scope variables.
	 */
	public ArgumentVariableRegistry getArgumentVariableRegistry() {
		if (argumentVarRegistry == null)
			argumentVarRegistry = new ArgumentVariableRegistry();
		return argumentVarRegistry;
	}

	/**
	 * Returns the registry of directory location variables.
	 */
	public PathLocationVariableRegistry getDirectoryLocationVariableRegistry() {
		if (dirLocVarRegistry == null)
			dirLocVarRegistry = new PathLocationVariableRegistry(IExternalToolConstants.EXTENSION_POINT_DIRECTORY_VARIABLES);
		return dirLocVarRegistry;
	}

	/**
	 * Returns the registry of file location variables.
	 */
	public PathLocationVariableRegistry getFileLocationVariableRegistry() {
		if (fileLocVarRegistry == null)
			fileLocVarRegistry = new PathLocationVariableRegistry(IExternalToolConstants.EXTENSION_POINT_FILE_VARIABLES);
		return fileLocVarRegistry;
	}

	/**
	 * Returns the registry of refresh scope variables.
	 */
	public RefreshScopeVariableRegistry getRefreshVariableRegistry() {
		if (refreshVarRegistry == null)
			refreshVarRegistry = new RefreshScopeVariableRegistry();
		return refreshVarRegistry;
	}

	/**
	 * Writes the message to the plug-in's log
	 * 
	 * @param message the text to write to the log
	 */
	public void log(String message, Throwable exception) {
		IStatus status = newErrorStatus(message, exception);
		getLog().log(status);
	}

	/**
	 * Returns the ImageDescriptor for the icon with the given path
	 * 
	 * @return the ImageDescriptor object
	 */
	public ImageDescriptor getImageDescriptor(String path) {
		try {
			URL installURL = getDescriptor().getInstallURL();
			URL url = new URL(installURL, path);
			return ImageDescriptor.createFromURL(url);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	/* (non-Javadoc)
	 * Method declared in AbstractUIPlugin.
	 */
	protected void initializeDefaultPreferences(IPreferenceStore prefs) {
		prefs.setDefault(IPreferenceConstants.PROMPT_FOR_MIGRATION, true);
		
		PreferenceConverter.setDefault(prefs, IPreferenceConstants.CONSOLE_ERROR_RGB, new RGB(255, 0, 0)); // red - exactly the same as debug Consol
		PreferenceConverter.setDefault(prefs, IPreferenceConstants.CONSOLE_WARNING_RGB, new RGB(255, 100, 0)); // orange
		PreferenceConverter.setDefault(prefs, IPreferenceConstants.CONSOLE_INFO_RGB, new RGB(0, 0, 255)); // blue
		PreferenceConverter.setDefault(prefs, IPreferenceConstants.CONSOLE_VERBOSE_RGB, new RGB(0, 200, 125)); // green
		PreferenceConverter.setDefault(prefs, IPreferenceConstants.CONSOLE_DEBUG_RGB, new RGB(0, 0, 0)); // black
	}

	public static IWorkbenchWindow getActiveWorkbenchWindow() {
		return getDefault().getWorkbench().getActiveWorkbenchWindow();
	}

	/**
	 * Returns the standard display to be used. The method first checks, if
	 * the thread calling this method has an associated display. If so, this
	 * display is returned. Otherwise the method returns the default display.
	 */
	public static Display getStandardDisplay() {
		Display display = Display.getCurrent();
		if (display == null) {
			display = Display.getDefault();
		}
		return display;
	}

	/**
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#createImageRegistry()
	 */
	protected ImageRegistry createImageRegistry() {
		return ExternalToolsImages.initializeImageRegistry();
	}

	/**
	 * @see org.eclipse.core.runtime.Plugin#startup()
	 */
	public void startup() throws CoreException {
		super.startup();
		VariableContextManager.getDefault();
	}

	/**
	 * @see org.eclipse.core.runtime.Plugin#shutdown()
	 */
	public void shutdown() throws CoreException {
		super.shutdown();
		ColorManager.getDefault().dispose();
	}

	/**
	 * Returns the preference color, identified by the given preference.
	 */
	public static Color getPreferenceColor(String pref) {
		return ColorManager.getDefault().getColor(PreferenceConverter.getColor(getDefault().getPreferenceStore(), pref));
	}

}
