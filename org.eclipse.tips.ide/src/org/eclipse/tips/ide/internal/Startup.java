/*******************************************************************************
 * Copyright (c) 2018 Remain Software
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     wim.jongman@remainsoftware.com - initial API and implementation
 *******************************************************************************/
package org.eclipse.tips.ide.internal;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.tips.core.TipProvider;
import org.eclipse.tips.json.internal.ProviderLoader;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Early startup to run the TipManager in the IDE.
 *
 */
@SuppressWarnings("restriction")
public class Startup implements IStartup {

	private static final String BCKSLASH = "\""; //$NON-NLS-1$
	private static final String EQ = "="; //$NON-NLS-1$
	private static final String SLASH = "/"; //$NON-NLS-1$
	private static final String LT = "<"; //$NON-NLS-1$
	private static final String EMPTY = ""; //$NON-NLS-1$
	private static final String GT = ">"; //$NON-NLS-1$
	private static final String SPACE = " "; //$NON-NLS-1$

	@Override
	public void earlyStartup() {
		loadProviders();
		openManager();
	}

	/**
	 * Reloads the tip providers.
	 */
	public static void loadProviders() {
		loadInternalProviders();
		loadExternalProviders();
	}

	private static void loadInternalProviders() {
		getInternalProvidersJob().schedule();
	}

	private static Job getInternalProvidersJob() {
		Job job = new Job(Messages.Startup_0) {

			@Override
			protected IStatus run(IProgressMonitor pArg0) {
				String baseURL = System.getProperty("org.eclipse.tips.ide.provider.url"); //$NON-NLS-1$
				if (baseURL == null) {
					baseURL = "http://www.eclipse.org/downloads/download.php?r=1&file=/e4/tips/"; //$NON-NLS-1$
				}
				try {
					ProviderLoader.loadProviderData(IDETipManager.getInstance(), baseURL,
							IDETipManager.getStateLocation());
				} catch (Exception e) {
					Status status = new Status(IStatus.ERROR, FrameworkUtil.getBundle(Startup.class).getSymbolicName(),
							Messages.Startup_3, e);
					IDETipManager.getInstance().log(status);
					return status;
				}
				return Status.OK_STATUS;
			};
		};
		return job;
	}

	private static void loadExternalProviders() {
		IConfigurationElement[] elements = Platform.getExtensionRegistry()
				.getConfigurationElementsFor("org.eclipse.tips.core.tips"); //$NON-NLS-1$
		for (IConfigurationElement element : elements) {
			if (element.getName().equals("provider")) { //$NON-NLS-1$
				try {
					TipProvider provider = (TipProvider) element.createExecutableExtension("class"); //$NON-NLS-1$
					provider.setExpression(getExpression(element));
					IDETipManager.getInstance().register(provider);
				} catch (CoreException e) {
					log(e);
				}
			}
		}
	}

	/**
	 * @return the core expression
	 */
	private static String getExpression(IConfigurationElement element) {
		IConfigurationElement[] enablements = element.getChildren("enablement"); //$NON-NLS-1$
		if (enablements.length == 0) {
			return null;
		}
		IConfigurationElement enablement = enablements[0];
		String result = getXML(enablement.getChildren());
		return result;
	}

	private static String getXML(IConfigurationElement[] children) {
		String result = EMPTY;
		for (IConfigurationElement element : children) {
			IConfigurationElement[] myChildren = element.getChildren();
			result += LT + element.getName() + SPACE + getXMLAttributes(element) + GT;
			if (myChildren.length > 0) {
				result += getXML(myChildren);
			} else {
				String value = element.getValue();
				result += value == null ? EMPTY : value;
			}
			result += LT + SLASH + element.getName() + GT;
		}
		return result;
	}

	private static String getXMLAttributes(IConfigurationElement element) {
		String result = EMPTY;
		for (String name : element.getAttributeNames()) {
			result += name;
			result += EQ + BCKSLASH;
			result += element.getAttribute(name);
			result += BCKSLASH + SPACE;
		}
		return result;
	}

	private static void openManager() {
		if (IDETipManager.getInstance().hasContent()) {
			getOpenUIJob().schedule();
		} else {
			getWaitJob().schedule();
		}
	}

	private static Job getWaitJob() {
		Job waitJob = new Job(Messages.Startup_18) {

			@Override
			protected IStatus run(IProgressMonitor pMonitor) {
				int attempts = 3;
				SubMonitor monitor = SubMonitor.convert(pMonitor, attempts);
				for (int i = 0; i < attempts; i++) {
					monitor.setTaskName(Messages.Startup_19 + i);
					if (openOrSleep(monitor)) {
						if (monitor.isCanceled()) {
							return Status.CANCEL_STATUS;
						} else {
							monitor.done();
							return Status.OK_STATUS;
						}
					}
					monitor.worked(1);
				}
				monitor.done();
				return Status.OK_STATUS;
			}

			private boolean openOrSleep(SubMonitor pMonitor) {
				if (IDETipManager.getInstance().hasContent()) {
					getOpenUIJob().schedule();
					return true;
				}
				if (sleep(1000)) {
					pMonitor.setCanceled(true);
					return true;
				}
				return false;
			}

			private boolean sleep(int millis) {
				try {
					Thread.sleep(millis);
					return false;
				} catch (InterruptedException e) {
					return true;
				}
			}
		};
		return waitJob;
	}

	private static UIJob getOpenUIJob() {
		UIJob uiJob = new UIJob(PlatformUI.getWorkbench().getDisplay(), Messages.Startup_20) {
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				IDETipManager.getInstance().open(true);
				return Status.OK_STATUS;
			}
		};
		return uiJob;
	}

	private static void log(CoreException e) {
		Bundle bundle = FrameworkUtil.getBundle(Startup.class);
		Status status = new Status(IStatus.ERROR, bundle.getSymbolicName(), e.getMessage(), e);
		Platform.getLog(bundle).log(status);
	}
}