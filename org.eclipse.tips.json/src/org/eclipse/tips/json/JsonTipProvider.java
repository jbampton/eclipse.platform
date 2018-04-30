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
package org.eclipse.tips.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.tips.core.Tip;
import org.eclipse.tips.core.TipImage;
import org.eclipse.tips.core.TipProvider;
import org.eclipse.tips.core.internal.LogUtil;
import org.eclipse.tips.json.internal.JsonConstants;
import org.eclipse.tips.json.internal.JsonHTMLTip;
import org.eclipse.tips.json.internal.JsonUrlTip;
import org.eclipse.tips.json.internal.Util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A special TipProvider that gets instantiated from a JSon file.
 *
 */
@SuppressWarnings("restriction")
public abstract class JsonTipProvider extends TipProvider {

	private static final String SPACE = " "; //$NON-NLS-1$
	private URL fJsonUrl;
	private String fDescription;
	private String fImage;
	private JsonObject fJsonObject;

	/**
	 * A method to set the a url containing a JSon file that describes this tip
	 * provider.
	 *
	 * @param jsonUrl the uRL to the Json file describing the provider and tips
	 * @throws MalformedURLException in case of an incorrect URL
	 */
	public void setJsonUrl(String jsonUrl) throws MalformedURLException {
		fJsonUrl = new URL(jsonUrl);
	}

	/**
	 * Returns a specific portion of the underlying json file as a json object, if
	 * the json object was not yet fetched it will be done here, making it a
	 * potential costly operation. The passed part can be an array to indicate a
	 * structure e.g. {"provider","variables"}.
	 * 
	 * @param part one or more keys of the underlying json file, may not be null.
	 * @return the JSon Object as a string
	 * @throws IOException
	 * @see {@link #loadNewTips(IProgressMonitor)}
	 */
	public synchronized String getJsonObject(String... part) throws IOException {
		if (fJsonObject == null) {
			fJsonObject = loadJsonObject();
		}
		JsonObject temp = fJsonObject.getAsJsonObject(part[0]);
		for (int i = 1; i < part.length; i++) {
			temp = temp.getAsJsonObject(part[0]);

		}
		return temp.getAsString();
	}

	/**
	 *
	 * {@inheritDoc}
	 * 
	 * <p>
	 * <b>Implementation Details</b><br>
	 * The implementation of this method in this provider will fetch the json file
	 * and store it locally.
	 * 
	 */
	@Override
	public synchronized IStatus loadNewTips(IProgressMonitor monitor) {
		SubMonitor subMonitor = SubMonitor.convert(monitor);
		ArrayList<Tip> result = new ArrayList<>();
		try {
			subMonitor.beginTask(getDescription() + SPACE + Messages.JsonTipProvider_1, -1);
			fJsonObject = loadJsonObject();
			JsonObject provider = fJsonObject.getAsJsonObject(JsonConstants.P_PROVIDER);
			fDescription = Util.getValueOrDefault(provider, JsonConstants.P_DESCRIPTION, "not set"); //$NON-NLS-1$
			fImage = Util.getValueOrDefault(provider, JsonConstants.P_IMAGE, null);
			setExpression(Util.getValueOrDefault(provider, JsonConstants.P_EXPRESSION, null));
			JsonArray tips = provider.getAsJsonArray(JsonConstants.P_TIPS);
			subMonitor.beginTask(getDescription() + SPACE + Messages.JsonTipProvider_2, -1);
			tips.forEach(parm -> result.add(createJsonTip(parm)));
		} catch (Exception e) {
			Status status = new Status(IStatus.ERROR, "org.eclipse.tips.json", e.getMessage(), e); //$NON-NLS-1$
			getManager().log(status);
			return status;
		}
		getManager().log(LogUtil.info(MessageFormat.format(Messages.JsonTipProvider_4, result.size() + ""))); //$NON-NLS-1$
		setTips(result);
		subMonitor.done();
		return Status.OK_STATUS;
	}

	private JsonObject loadJsonObject() throws IOException {
		try (InputStream stream = fJsonUrl.openStream(); InputStreamReader reader = new InputStreamReader(stream)) {
			return (JsonObject) new JsonParser().parse(reader);
		}
	}

	@Override
	public TipImage getImage() {
		if (fImage == null) {
			return null;
		}
		return new TipImage(fImage);
	}

	@Override
	public String getDescription() {
		return fDescription;
	}

	private Tip createJsonTip(JsonElement parm) {
		JsonObject json = (JsonObject) parm;
		replaceVariables(json);
		try {
			if (json.get(JsonConstants.T_URL) != null) {
				return new JsonUrlTip(getID(), json);
			} else {
				return new JsonHTMLTip(getID(), json);
			}
		} catch (ParseException e) {
			getManager().log(LogUtil.error(getClass(), e));
			throw new RuntimeException(e);
		}
	}

	private void replaceVariables(JsonObject pJson) {
		String url = Util.getValueOrDefault(pJson, JsonConstants.T_URL, null);
		String html = Util.getValueOrDefault(pJson, JsonConstants.T_HTML, null);
		JsonObject vars = fJsonObject.getAsJsonObject(JsonConstants.P_PROVIDER)
				.getAsJsonObject(JsonConstants.T_VARIABLES);
		if (vars != null) {
			if (url != null) {
				url = Util.replace(vars, url);
				pJson.remove(JsonConstants.T_URL);
				pJson.addProperty(JsonConstants.T_URL, url);
			}
			if (html != null) {
				html = Util.replace(vars, html);
				pJson.remove(JsonConstants.T_HTML);
				pJson.addProperty(JsonConstants.T_HTML, html);
			}
		}
	}

	@Override
	public void dispose() {
	}
}