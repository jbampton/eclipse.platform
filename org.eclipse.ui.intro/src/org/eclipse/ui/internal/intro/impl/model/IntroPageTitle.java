/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.internal.intro.impl.model;

import org.osgi.framework.*;
import org.w3c.dom.*;

/**
 * An intro element that represents a page title.
 */
public class IntroPageTitle extends IntroText {

    protected static final String TAG_TITLE = "title";

    IntroPageTitle(Element element, Bundle bundle) {
        super(element, bundle);
    }

    /**
     * @return Returns the Title text.
     */
    public String getTitle() {
        return getText();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.ui.internal.intro.impl.model.IntroElement#getType()
     */
    public int getType() {
        return AbstractIntroElement.PAGE_TITLE;
    }

}