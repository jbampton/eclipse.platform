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
package org.eclipse.help.ui.internal;
/**
 * Interface for holding Help UI plug-in constants
 */
public interface IHelpUIConstants {
	// Help UI pluging id with a "." for convenience.
	public static final String HELP_UI_PLUGIN_ID = HelpUIPlugin.PLUGIN_ID + "."; //$NON-NLS-1$
	// F1 ids
	public static final String F1_SHELL = HELP_UI_PLUGIN_ID + "f1Shell"; //$NON-NLS-1$
	public static final String PREF_PAGE_BROWSERS = HELP_UI_PLUGIN_ID
			+ "prefPageBrowsers"; //$NON-NLS-1$
	public static final String PREF_PAGE_APPSERVER = HELP_UI_PLUGIN_ID
			+ "prefPageAppServer"; //$NON-NLS-1$
	public static final String PREF_PAGE_CUSTOM_BROWSER_PATH = HELP_UI_PLUGIN_ID
			+ "prefPageCustomBrowserPath"; //$NON-NLS-1$
	public static final String IMAGE_FILE_F1TOPIC = "obj16/topic_small.gif"; //$NON-NLS-1$
	
	// Help view images
	public static final String IMAGE_CONTAINER = "obj16/container_obj.gif"; //$NON-NLS-1$
	public static final String IMAGE_TOC_CLOSED= "obj16/toc_closed.gif"; //$NON-NLS-1$
	public static final String IMAGE_TOC_OPEN = "obj16/toc_open.gif"; //$NON-NLS-1$
	public static final String IMAGE_HELP_SEARCH = "etool16/search_menu.gif"; //$NON-NLS-1$
	public static final String IMAGE_HELP = "etool16/help.gif"; //$NON-NLS-1$
	public static final String IMAGE_CLEAR = "elcl16/clear.gif"; //$NON-NLS-1$
	public static final String IMAGE_NW = "elcl16/browser.gif"; //$NON-NLS-1$
	public static final String IMAGE_SHOW_CATEGORIES = "elcl16/show_categories.gif"; //$NON-NLS-1$
	public static final String IMAGE_SHOW_DESC = "elcl16/desc_obj.gif";
	public static final String IMAGE_REMOVE_ALL = "elcl16/search_remall.gif";
	// Help view constants
	public static final String HV_SEARCH = "search";
	public static final String HV_FSEARCH = "fsearch";
	public static final String HV_SEARCH_RESULT = "search-result";
	public static final String HV_FSEARCH_RESULT = "fsearch-result";
	
	public static final String HV_TOPIC_TREE = "topic-tree";
	public static final String HV_SEE_ALSO = "see-also";
	public static final String HV_BROWSER = "browser";
	public static final String HV_CONTEXT_HELP = "context-help";
	//Help view pages
	public static final String HV_SEARCH_PAGE = "search-page";
	public static final String HV_FSEARCH_PAGE = "fsearch-page";
	public static final String HV_FSEARCH_RESULT_PAGE = "fsearch-result-page";
	public static final String HV_ALL_TOPICS_PAGE = "all-topics-page";
	public static final String HV_BROWSER_PAGE = "browser-page";
	public static final String HV_CONTEXT_HELP_PAGE = "context-help-page";

	static final String ENGINE_EXP_ID = "org.eclipse.help.ui.searchEngine";
	static final String ATT_ID = "id";
	static final String ATT_LABEL ="label"; //$NON-NLS-1$
	static final String ATT_ICON = "icon";//$NON-NLS-1$
	static final String ATT_CLASS = "class";//$NON-NLS-1$
	static final String ATT_ENABLED = "enabled";
	static final String ATT_PAGE_CLASS = "pageClass";//$NON-NLS-1$
	static final String ATT_CATEGORY = "category";//$NON-NLS-1$
	static final String EL_DESC = "description"; //$NON-NLS-1$
	static final String ATT_SCOPE_FACTORY = "scopeFactory";
}
