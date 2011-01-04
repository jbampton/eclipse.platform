/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.help.ui.internal.views;

import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;


public class LocalScopeDialog extends TrayDialog {

	private static final String ORG_ECLIPSE_HELP_UI_LOCAL_SEARCH = "org.eclipse.help.ui.localSearch"; //$NON-NLS-1$
	private EngineDescriptorManager descManager;
	private IPreferenceStore preferenceStore;
	private PreferencePage localHelpPage;
	private ScopeSet scopeSet;

	public LocalScopeDialog(Shell parentShell, PreferenceManager manager, EngineDescriptorManager descManager,
			ScopeSet set) {
		super(parentShell);
		this.descManager = descManager;
		this.preferenceStore = set.getPreferenceStore();
		this.scopeSet = set;
	}
	
	protected Control createDialogArea(Composite parent) {
		EngineDescriptor localSearchDesc = null;
		for (int i = 0; localSearchDesc == null && i < descManager.getDescriptors().length; i++) {
			if (ORG_ECLIPSE_HELP_UI_LOCAL_SEARCH.equals(descManager.getDescriptors()[i].getEngineTypeId())) {
				localSearchDesc = descManager.getDescriptors()[i];
			}
		}
		String id = localSearchDesc.getEngineTypeId();
		localHelpPage = localSearchDesc.createRootPage(scopeSet.getName());
    	localHelpPage.setTitle(localSearchDesc.getLabel() + '_' + id);
    	localHelpPage.setImageDescriptor(localSearchDesc.getImageDescriptor());
    	localHelpPage.setDescription(localSearchDesc.getDescription());
    	localHelpPage.setPreferenceStore(preferenceStore);
    	localHelpPage.createControl(parent);
		return localHelpPage.getControl();
	}
	
	protected void okPressed() {
		localHelpPage.performOk();
		super.okPressed();
	}

}
