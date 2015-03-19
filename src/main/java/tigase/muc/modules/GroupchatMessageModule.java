/*
 * GroupchatMessageModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.muc.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.DateUtil;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 *
 */
public class GroupchatMessageModule extends AbstractMucModule {

	private static final Criteria CRIT = ElementCriteria.nameType("message", "groupchat");

	private static final Criteria CRIT_CHAT_STAT = ElementCriteria.xmlns("http://jabber.org/protocol/chatstates");

	public static final String ID = "groupchat";

	private final Set<Criteria> allowedElements = new HashSet<Criteria>();

	/**
	 * @param room
	 * @param cData
	 * @param senderJID
	 * @param nickName
	 * @param sendDate
	 */
	protected void addMessageToHistory(Room room, final Element message, String body, JID senderJid,
			String senderNickname, Date time) {
		try {
			HistoryProvider historyProvider = context.getHistoryProvider();
			if (historyProvider != null) {
				historyProvider.addMessage(room, message, body, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add message to history!", e);
		}
		try {
			MucLogger mucLogger = context.getMucLogger();
			if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
				mucLogger.addMessage(room, body, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add message to log!", e);
		}
	}

	/**
	 * @param room
	 * @param cData
	 * @param senderJID
	 * @param nickName
	 * @param sendDate
	 */
	protected void addSubjectChangeToHistory(Room room, Element message, final String subject, JID senderJid,
			String senderNickname, Date time) {
		try {
			HistoryProvider historyProvider = context.getHistoryProvider();
			if (historyProvider != null) {

				historyProvider.addSubjectChange(room, message, subject, senderJid, senderNickname, time);

			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add subject change to history!", e);
		}

		try {
			MucLogger mucLogger = context.getMucLogger();
			if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
				mucLogger.addSubjectChange(room, subject, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add subject change to log!", e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.component.modules.AbstractModule#afterRegistration()
	 */
	@Override
	public void afterRegistration() {
		super.afterRegistration();
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		ArrayList<String> f = new ArrayList<String>();

		f.add("http://jabber.org/protocol/muc");

		if (isChatStateAllowed()) {
			f.add("http://jabber.org/protocol/chatstates");
		}

		return f.toArray(new String[] {});
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isChatStateAllowed() {
		return allowedElements.contains(CRIT_CHAT_STAT);
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 *
	 * @throws MUCException
	 */
	@Override
	public void process(Packet packet) throws MUCException {
		try {

			final JID senderJID = JID.jidInstance(packet.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID = BareJID.bareJIDInstance(packet.getAttributeStaticStr(Packet.TO_ATT));

			if (getNicknameFromJid(JID.jidInstance(packet.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST, "Groupchat message can't be addressed to occupant.");
			}

			final Room room = context.getMucRepository().getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "There is no such room.");
			}

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role role = room.getRole(nickName);
			final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());
			log.info("GROUPChat message nickname:" + nickName + " with role: " + role + " affiliantion: " + affiliation);
			
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Processing groupchat message. room=" + roomJID + "; senderJID=" + senderJID + "; senderNickname="
						+ nickName + "; role=" + role + "; affiliation=" + affiliation + ";");
			}

			if (!role.isSendMessagesToAll() || (room.getConfig().isRoomModerated() && (role == Role.visitor))) {
				if (log.isLoggable(Level.FINE))
					log.fine("Insufficient privileges to send grouchat message: role=" + role + "; roomModerated="
							+ room.getConfig().isRoomModerated() + "; stanza="
							+ packet.getElement().toStringNoChildren());
				//throw new MUCException(Authorization.FORBIDDEN, "Insufficient privileges to send groupchat message.");
			}

			Element body = null;
			Element subject = null;
			Element delay = null;
			final String id = packet.getAttributeStaticStr(Packet.ID_ATT);
			ArrayList<Element> content = new ArrayList<Element>();
			List<Element> ccs = packet.getElement().getChildren();

			if (ccs != null) {
				for (Element c : ccs) {
					if ("delay".equals(c.getName())) {
						delay = c;
					} else if ("body".equals(c.getName())) {
						body = c;
						content.add(c);
					} else if ("subject".equals(c.getName())) {
						subject = c;
						content.add(c);
					} else if (!context.isMessageFilterEnabled()) {
						content.add(c);
					} else if (context.isChatStateAllowed() && CRIT_CHAT_STAT.match(c)) {
						content.add(c);
					} else {
						for (Criteria crit : allowedElements) {
							if (crit.match(c)) {
								content.add(c);

								break;
							}
						}
					}
				}
			}

			final JID senderRoomJID = JID.jidInstance(roomJID, nickName);

			if (subject != null) {
				if (!(room.getConfig().isChangeSubject() && (role == Role.participant)) && !role.isModifySubject()) {
					if (log.isLoggable(Level.FINE))
						log.fine("Insufficient privileges to change subject: role=" + role + "; allowToChangeSubject="
								+ room.getConfig().isChangeSubject() + "; stanza="
								+ packet.getElement().toStringNoChildren());
					throw new MUCException(Authorization.FORBIDDEN, "Insufficient privileges to change subject.");
				}

				String msg = subject.getCData();

				room.setNewSubject(msg, nickName);
			}

			Date sendDate;

			if ((delay != null) && (affiliation == Affiliation.owner)) {
				sendDate = DateUtil.parse(delay.getAttributeStaticStr("stamp"));
			} else {
				sendDate = new Date();
			}

			Packet msg = preparePacket(id, content.toArray(new Element[] {}));

			if (body != null) {
				addMessageToHistory(room, msg.getElement(), body.getCData(), senderJID, nickName, sendDate);
			}
			if (subject != null) {
				addSubjectChangeToHistory(room, msg.getElement(), subject.getCData(), senderJID, nickName, sendDate);
			}

			if (sendDate != null) {

				msg.getElement().addChild(
						new Element("delay", new String[] { "xmlns", "stamp" }, new String[] { "urn:xmpp:delay",
								DateUtil.formatDatetime(sendDate) }));
			}

			sendMessagesToAllOccupants(room, senderRoomJID, msg);

		} catch (MUCException e1) {
			throw e1;
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	protected Packet preparePacket(String messageId, Element... content) throws TigaseStringprepException {
		Element e = new Element("message", new String[] { "type" }, new String[] { "groupchat" });
		if (messageId != null) {
			e.setAttribute("id", messageId);
		}
		if (content != null)
			e.addChildren(Arrays.asList(content));
		Packet message = Packet.packetInstance(e);
		message.setXMLNS(Packet.CLIENT_XMLNS);
		return message;
	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Element... content)
			throws TigaseStringprepException {
		Packet msg = preparePacket(null, content);
		sendMessagesToAllOccupants(room, fromJID, msg);
	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Packet msg)
			throws TigaseStringprepException {
		sendMessagesToAllOccupantsJids(room, fromJID, msg);

		room.fireOnMessageToOccupants(fromJID, msg);
	}

	public void sendMessagesToAllOccupantsJids(final Room room, final JID fromJID, final Packet msg)
			throws TigaseStringprepException {

		log.log(Level.INFO,
				"xgroupchat sending messages to all occupants Room: Subject:" + room.getSubject() + " //JID:" + room.getRoomJID() + " // OCcupants: " + room.getOccupantsNicknames());
		for (String nickname : room.getOccupantsNicknames()) {
			final Role role = room.getRole(nickname);
			log.info( nickname +"  has role to receive?: " + role.isReceiveMessages());
			if (!role.isReceiveMessages()) {
				//We don't give a shit right now. Go ahead and send the message
				//continue;
			}

			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			for (JID jid : occupantJids) {
				Packet message = msg.copyElementOnly();// Packet.packetInstance(e);
				message.initVars(fromJID, jid);
				message.setXMLNS(Packet.CLIENT_XMLNS);

				// we sent this to message amp or some other plugin to make sure
				// it's deliverred?
				log.log(Level.INFO, " sending message " + message + " to occupant " + jid + " //" + message.toStringFull());
				
				write(message);
				
			}
		}
	}
}
