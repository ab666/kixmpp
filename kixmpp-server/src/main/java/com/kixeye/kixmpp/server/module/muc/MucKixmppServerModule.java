package com.kixeye.kixmpp.server.module.muc;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jdom2.Element;
import org.jdom2.Namespace;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.handler.KixmppStanzaHandler;
import com.kixeye.kixmpp.server.KixmppServer;
import com.kixeye.kixmpp.server.module.KixmppServerModule;

/**
 * Handles presence.
 * 
 * @author ebahtijaragic
 */
public class MucKixmppServerModule implements KixmppServerModule {
	private KixmppServer server;
	
	private Map<KixmppJid, MucRoom> rooms = new ConcurrentHashMap<>();
	
	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#install(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void install(KixmppServer server) {
		this.server = server;
		
		this.server.getEventEngine().register("presence", null, JOIN_ROOM_HANDLER);
		this.server.getEventEngine().register("message", null, ROOM_MESSAGE_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#uninstall(com.kixeye.kixmpp.server.KixmppServer)
	 */
	public void uninstall(KixmppServer server) {
		this.server.getEventEngine().unregister("presence", null, JOIN_ROOM_HANDLER);
		this.server.getEventEngine().unregister("message", null, ROOM_MESSAGE_HANDLER);
	}

	/**
	 * @see com.kixeye.kixmpp.server.module.KixmppModule#getFeatures()
	 */
	public List<Element> getFeatures() {
		return Collections.emptyList();
	}
	
	private KixmppStanzaHandler JOIN_ROOM_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			Element x = stanza.getChild("x", Namespace.getNamespace("http://jabber.org/protocol/muc"));
			
			if (x != null) {
				KixmppJid fullRoomJid = KixmppJid.fromRawJid(stanza.getAttributeValue("to"));
				KixmppJid roomJid = fullRoomJid.withoutResource();
				
				MucRoom room = null;
				
				synchronized (roomJid.getBaseJid().intern()) {
					room = rooms.get(roomJid);
					
					if (room == null) {
						room = new MucRoom(roomJid);
						rooms.put(roomJid, room);
					}
				}
				
				room.join(channel, fullRoomJid.getResource());
			}
		}
	};
	
	private KixmppStanzaHandler ROOM_MESSAGE_HANDLER = new KixmppStanzaHandler() {
		/**
		 * @see com.kixeye.kixmpp.server.KixmppStanzaHandler#handle(io.netty.channel.Channel, org.jdom2.Element)
		 */
		public void handle(Channel channel, Element stanza) {
			if ("groupchat".equals(stanza.getAttributeValue("type"))) {
				MucRoom room = rooms.get(KixmppJid.fromRawJid(stanza.getAttributeValue("to")).withoutResource());

				if (room != null) {
					room.broadcast(channel, stanza);
				} // TODO handle else
			}
		}
	};
}
