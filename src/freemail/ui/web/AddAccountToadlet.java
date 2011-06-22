/*
 * AddAccountToadlet.java
 * This file is part of Freemail
 * Copyright (C) 2011 Martin Nyhus
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package freemail.ui.web;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import javax.naming.SizeLimitExceededException;

import freemail.AccountManager;
import freemail.utils.Logger;
import freemail.wot.OwnIdentity;
import freemail.wot.WoTConnection;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker;
import freenet.clients.http.PageNode;
import freenet.clients.http.SessionManager;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;

public class AddAccountToadlet extends WebPage {
	private final PluginRespirator pluginRespirator;
	private final WoTConnection wotConnection;
	private final AccountManager accountManager;

	AddAccountToadlet(HighLevelSimpleClient client, PageMaker pageMaker, SessionManager sessionManager, PluginRespirator pluginRespirator, WoTConnection wotConnection, AccountManager accountManager) {
		super(client, pageMaker, sessionManager);

		this.pluginRespirator = pluginRespirator;
		this.wotConnection = wotConnection;
		this.accountManager = accountManager;
	}

	@Override
	public boolean isEnabled(ToadletContext ctx) {
		return true;
	}

	@Override
	void makeWebPage(URI uri, HTTPRequest req, ToadletContext ctx, HTTPMethod method, PageNode page) throws ToadletContextClosedException, IOException {
		switch(method) {
		case GET:
			makeWebPageGet(ctx);
			break;
		case POST:
			makeWebPagePost(ctx, req);
			break;
		default:
			//This will only happen if a new value is added to HTTPMethod, so log it and send an
			//error message
			assert false : "HTTPMethod has unknown value: " + method;
			Logger.error(this, "HTTPMethod has unknown value: " + method);
			writeHTMLReply(ctx, 200, "OK", "Unknown HTTP method " + method + ". This is a bug in Freemail");
		}
	}

	private void makeWebPageGet(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		writeTemporaryRedirect(ctx, "Redirecting to login page", "/Freemail/Login");
	}

	private void makeWebPagePost(ToadletContext ctx, HTTPRequest req) throws ToadletContextClosedException, IOException {
		//Check the form password
		String pass;
		try {
			pass = req.getPartAsStringThrowing("formPassword", 32);
		} catch(SizeLimitExceededException e) {
			writeHTMLReply(ctx, 403, "Forbidden", "Form password too long");
			return;
		} catch(NoSuchElementException e) {
			writeHTMLReply(ctx, 403, "Forbidden", "Missing form password");
			return;
		}

		if((pass.length() == 0) || !pass.equals(pluginRespirator.getNode().clientCore.formPassword)) {
			writeHTMLReply(ctx, 403, "Forbidden", "Invalid form password.");
			return;
		}

		//Get the identity id
		String identity;
		try {
			identity = req.getPartAsStringThrowing("OwnIdentityID", 64);
		} catch(SizeLimitExceededException e) {
			//Someone is deliberately passing bad data, or there is a bug in the PUT code
			Logger.error(this, "Got OwnIdentityID that was too long. First 100 bytes: " + req.getPartAsStringFailsafe("OwnIdentityID", 100));

			//TODO: Write a better message
			writeHTMLReply(ctx, 200, "OK", "The request contained bad data. This is probably a bug in Freemail");
			return;
		} catch(NoSuchElementException e) {
			//Someone is deliberately passing bad data, or there is a bug in the PUT code
			Logger.error(this, "Got POST request without OwnIdentityID");

			//TODO: Write a better message
			writeHTMLReply(ctx, 200, "OK", "The request didn't contain the expected data. This is probably a bug in Freemail");
			return;
		}

		//Fetch identity from WoT
		OwnIdentity ownIdentity = null;
		for(OwnIdentity oid : wotConnection.getAllOwnIdentities()) {
			if(oid.getIdentityID().equals(identity)) {
				ownIdentity = oid;
				break;
			}
		}

		if(ownIdentity == null) {
			Logger.error(this, "Requested identity (" + identity + ") doesn't exist");

			//TODO: Write a better message
			writeHTMLReply(ctx, 200, "OK", "The specified identitiy doesn't exist");
			return;
		}

		List<OwnIdentity> toAdd = new LinkedList<OwnIdentity>();
		toAdd.add(ownIdentity);
		accountManager.addIdentities(toAdd);

		writeTemporaryRedirect(ctx, "Account added, redirecting to login page", "/Freemail/Login");
	}

	@Override
	boolean requiresValidSession() {
		return false;
	}

	@Override
	public String path() {
		return "/Freemail/AddAccount";
	}
}
