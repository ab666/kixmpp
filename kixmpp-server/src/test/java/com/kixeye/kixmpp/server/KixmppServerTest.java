package com.kixeye.kixmpp.server;

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


import io.netty.handler.ssl.SslContext;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.util.io.pem.PemWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.junit.Assert;
import org.junit.Test;

import com.kixeye.kixmpp.KixmppJid;
import com.kixeye.kixmpp.client.KixmppClient;
import com.kixeye.kixmpp.client.module.muc.MucJoin;
import com.kixeye.kixmpp.client.module.muc.MucKixmppClientModule;
import com.kixeye.kixmpp.client.module.muc.MucListener;
import com.kixeye.kixmpp.client.module.muc.MucMessage;
import com.kixeye.kixmpp.client.module.presence.Presence;
import com.kixeye.kixmpp.client.module.presence.PresenceKixmppClientModule;
import com.kixeye.kixmpp.client.module.presence.PresenceListener;
import com.kixeye.kixmpp.server.module.auth.InMemoryAuthenticationService;
import com.kixeye.kixmpp.server.module.auth.SaslKixmppServerModule;
import com.kixeye.kixmpp.server.module.muc.MucKixmppServerModule;

/**
 * Tests the {@link KixmppServer}
 * 
 * @author ebahtijaragic
 */
@SuppressWarnings("deprecation")
public class KixmppServerTest {
	@Test
	public void testSimpleUsingKixmpp() throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();
       
        X509V3CertificateGenerator v3CertGen =  new X509V3CertificateGenerator();
        v3CertGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        v3CertGen.setIssuerDN(new X509Principal("CN=cn, O=o, L=L, ST=il, C= c"));
        v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24));
        v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365*10)));
        v3CertGen.setSubjectDN(new X509Principal("CN=cn, O=o, L=L, ST=il, C= c"));
        v3CertGen.setPublicKey(keyPair.getPublic());
        v3CertGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
        X509Certificate cert = v3CertGen.generateX509Certificate(keyPair.getPrivate());
        
        File keyFile = File.createTempFile("key" ,null);
        File certChainFile = File.createTempFile("cert" ,null);
        
        try (FileWriter writer = new FileWriter(keyFile)) {
        	try (PemWriter pemWriter = new PemWriter(writer)) {
            	pemWriter.writeObject(new JcaPKCS8Generator(keyPair.getPrivate(), null));
        	}
        }
        
        try (FileWriter writer = new FileWriter(certChainFile)) {
        	try (PemWriter pemWriter = new PemWriter(writer)) {
            	pemWriter.writeObject(new JcaMiscPEMGenerator(cert));
        	}
        }
        
		try (KixmppServer server = new KixmppServer("testChat", SslContext.newServerContext(certChainFile, keyFile))) {
			Assert.assertNotNull(server.start().await(2, TimeUnit.SECONDS));
			
			((InMemoryAuthenticationService)server.module(SaslKixmppServerModule.class).getAuthenticationService()).addUser("testUser", "testPassword");
			server.module(MucKixmppServerModule.class).addService("conference").addRoom("someRoom");
			
			try (KixmppClient client = new KixmppClient(SslContext.newClientContext())) {
				final LinkedBlockingQueue<Presence> presences = new LinkedBlockingQueue<>();
				final LinkedBlockingQueue<MucJoin> mucJoins = new LinkedBlockingQueue<>();
				final LinkedBlockingQueue<MucMessage> mucMessages = new LinkedBlockingQueue<>();

				Assert.assertNotNull(client.connect("localhost", server.getBindAddress().getPort(), server.getDomain()).await(2, TimeUnit.SECONDS));

				client.module(PresenceKixmppClientModule.class).addPresenceListener(new PresenceListener() {
					public void handle(Presence presence) {
						presences.offer(presence);
					}
				});
				
				client.module(MucKixmppClientModule.class).addJoinListener(new MucListener<MucJoin>() {
					public void handle(MucJoin event) {
						mucJoins.offer(event);
					}
				});
				
				client.module(MucKixmppClientModule.class).addMessageListener(new MucListener<MucMessage>() {
					public void handle(MucMessage event) {
						mucMessages.offer(event);
					}
				});
				
				Assert.assertNotNull(client.login("testUser", "testPassword", "testResource").await(2, TimeUnit.SECONDS));
				client.module(PresenceKixmppClientModule.class).updatePresence(new Presence());
				
				Assert.assertNotNull(presences.poll(2, TimeUnit.SECONDS));
				
				client.module(MucKixmppClientModule.class).joinRoom(KixmppJid.fromRawJid("someRoom@conference.testChat"), "testNick");
				
				MucJoin mucJoin = mucJoins.poll(2, TimeUnit.SECONDS);
				
				Assert.assertNotNull(mucJoin);
				
				client.module(MucKixmppClientModule.class).sendRoomMessage(mucJoin.getRoomJid(), "someMessage", "testNick");

				MucMessage mucMessage = mucMessages.poll(2, TimeUnit.SECONDS);

				Assert.assertNotNull(mucMessage);
				Assert.assertEquals("someMessage", mucMessage.getBody());
			}
		}
	}
	
	@Test
	public void testSimpleUsingSmack() throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();
       
        X509V3CertificateGenerator v3CertGen =  new X509V3CertificateGenerator();
        v3CertGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        v3CertGen.setIssuerDN(new X509Principal("CN=cn, O=o, L=L, ST=il, C= c"));
        v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24));
        v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365*10)));
        v3CertGen.setSubjectDN(new X509Principal("CN=cn, O=o, L=L, ST=il, C= c"));
        v3CertGen.setPublicKey(keyPair.getPublic());
        v3CertGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
        X509Certificate cert = v3CertGen.generateX509Certificate(keyPair.getPrivate());
        
        File keyFile = File.createTempFile("key" ,null);
        File certChainFile = File.createTempFile("cert" ,null);
        
        try (FileWriter writer = new FileWriter(keyFile)) {
        	try (PemWriter pemWriter = new PemWriter(writer)) {
            	pemWriter.writeObject(new JcaPKCS8Generator(keyPair.getPrivate(), null));
        	}
        }
        
        try (FileWriter writer = new FileWriter(certChainFile)) {
        	try (PemWriter pemWriter = new PemWriter(writer)) {
            	pemWriter.writeObject(new JcaMiscPEMGenerator(cert));
        	}
        }
        
		try (KixmppServer server = new KixmppServer("testChat", SslContext.newServerContext(certChainFile, keyFile))) {
			Assert.assertNotNull(server.start().await(2, TimeUnit.SECONDS));
			
			((InMemoryAuthenticationService)server.module(SaslKixmppServerModule.class).getAuthenticationService()).addUser("testUser", "testPassword");
			server.module(MucKixmppServerModule.class).addService("conference").addRoom("someRoom");
			
			XMPPConnection connection = new XMPPTCPConnection(new ConnectionConfiguration(server.getBindAddress().getHostName(), server.getBindAddress().getPort(), server.getDomain()));
				
			try {
				connection.connect();
				
				connection.login("testUser", "testPassword");
				
				final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
				
				MessageListener messageListener = new MessageListener() {
					public void processMessage(Chat chat, Message message) {
						messages.offer(message);
					}
				};
				
				Chat chat = ChatManager.getInstanceFor(connection).createChat("someRoom@conference.testChat", messageListener);
				
				chat.sendMessage("hello!");
			} finally {
				connection.disconnect();
			}
		}
	}
}
