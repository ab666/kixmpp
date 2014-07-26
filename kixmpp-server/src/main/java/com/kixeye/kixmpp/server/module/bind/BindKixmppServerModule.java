package com.kixeye.kixmpp.server.module.bind;

/*
 * #%L
 * KIXMPP
 * %%
 * Copyright (C) 2014 KIXEYE, Inc
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.LinkedList;
import java.util.List;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.handler.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.module.KixmppServerModule;

/**
 * Handles binds.
 * 
 * @author ebahtijaragic
 */
public class BindKixmppServerModule implements KixmppServerModule {
	public static AttributeKey<KixmppJid> JID = AttributeKey.valueOf("JID");
	
	private KixmppServer server;
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getEventEngine().register("iq", null, BIND_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getEventEngine().unregister("iq", null, BIND_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#getFeatures()
	 */
	public List<Element> getFeatures() {
		List<Element> features = new LinkedList<>();
		
		Element bind = new Element("bind", null, "urn:ietf:params:xml:ns:xmpp-bind");
		
		features.add(bind);
		
		return features;
	}
	
	private KixmppStanzaHandler BIND_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			Element bind = stanza.getChild("bind", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
			
			if (bind != null) {
				// handle the bind
				String resource = bind.getChildText("resource", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
				
				if (resource != null) {
					channel.attr(BindKixmppServerModule.JID).set(channel.attr(BindKixmppServerModule.JID).get().withResource(resource));
				}
				
				Element iq = new Element("iq");
				iq.setAttribute("type", "result");
				
				String id = stanza.getAttributeValue("id");
				
				if (id != null) {
					iq.setAttribute("id", id);
				}
				
				bind = new Element("bind", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind"));
				bind.addContent(new Element("jid", Namespace.getNamespace("urn:ietf:params:xml:ns:xmpp-bind")).setText(channel.attr(BindKixmppServerModule.JID).get().toString()));
				
				iq.addContent(bind);
				
				channel.writeAndFlush(iq);
			}
		}
	};
}
