/*
 * MediatedInvitationModule.java
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

import java.util.List;
import java.util.logging.Logger;

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * @author bmalkow
 * 
 */
public class MediatedInvitationModule extends AbstractMucModule {

	private static final Criteria CRIT = ElementCriteria.name("message").add(
			ElementCriteria.name("x", "http://jabber.org/protocol/muc#user").add(
					new Or(ElementCriteria.name("invite"), ElementCriteria.name("decline"))));

	public static final String ID = "invitations";
	protected static final Logger log = Logger.getLogger(MediatedInvitationModule.class.getName());

	/**
	 * @param senderJID
	 * @param roomJID
	 * @throws TigaseStringprepException
	 * 
	 */
	private void doDecline(Element decline, BareJID roomJID, JID senderJID) throws TigaseStringprepException {
		final Element reason = decline.getChild("reason");
		final JID recipient = JID.jidInstance(decline.getAttributeStaticStr(Packet.TO_ATT));

		Packet resultMessage = Packet.packetInstance(new Element("message", new String[] { Packet.FROM_ATT,
				Packet.TO_ATT }, new String[] { roomJID.toString(), recipient.toString() }));
		resultMessage.setXMLNS(Packet.CLIENT_XMLNS);

		final Element resultX = new Element("x", new String[] { Packet.XMLNS_ATT },
				new String[] { "http://jabber.org/protocol/muc#user" });
		resultMessage.getElement().addChild(resultX);
		final Element resultDecline = new Element("decline", new String[] { "from" },
				new String[] { senderJID.toString() });
		resultX.addChild(resultDecline);
		if (reason != null) {
			resultDecline.addChild(reason.clone());
		}
		write(resultMessage);
	}

	/**
	 * @param message
	 * @param senderAffiliation
	 * @param senderJID
	 * @param roomJID
	 * @param room
	 * @param invite
	 * @param senderRole
	 * @throws TigaseStringprepException
	 * @throws RepositoryException
	 * @throws MUCException
	 * 
	 */
	private void doInvite(Packet message, Element invite, Room room, BareJID roomJID, JID senderJID, Role senderRole,
			Affiliation senderAffiliation) throws RepositoryException, TigaseStringprepException, MUCException {

		if (room == null)
			throw new MUCException(Authorization.ITEM_NOT_FOUND);

		if (!senderRole.isInviteOtherUsers())
			throw new MUCException(Authorization.NOT_ALLOWED);

		if (room.getConfig().isRoomMembersOnly() && !senderAffiliation.isEditMemberList())
			throw new MUCException(Authorization.FORBIDDEN);

		final Element reason = invite.getChild("reason");
		final Element cont = invite.getChild("continue");
		final JID recipient = JID.jidInstance(invite.getAttributeStaticStr(Packet.TO_ATT));
		final Affiliation affiliation = Affiliation.member;
		final Role newRole = getDefaultRole(room.getConfig(), affiliation);

		try {
			Element pe = new Element("presence");
			room.addOccupantByJid(senderJID, recipient.toString(), newRole, pe);
		} catch (Exception ex) {
			ex.printStackTrace();
			log.warning(" error adding coccupants");
		}

		Packet resultMessage = Packet.packetInstance(new Element("message", new String[] { Packet.FROM_ATT,
				Packet.TO_ATT }, new String[] { roomJID.toString(), recipient.toString() }));
		resultMessage.setXMLNS(Packet.CLIENT_XMLNS);

		final Element resultX = new Element("x", new String[] { Packet.XMLNS_ATT },
				new String[] { "http://jabber.org/protocol/muc#user" });

		resultMessage.getElement().addChild(resultX);
		if (room.getConfig().isRoomMembersOnly() && senderAffiliation.isEditMemberList()) {
			room.addAffiliationByJid(recipient.getBareJID(), Affiliation.member);
		}

		final Element resultInvite = new Element("invite", new String[] { "from" },
				new String[] { senderJID.toString() });

		resultX.addChild(resultInvite);
		if (room.getConfig().isPasswordProtectedRoom()) {
			resultX.addChild(new Element("password", room.getConfig().getPassword()));
		}
		if (reason != null) {
			resultInvite.addChild(reason.clone());
		}
		if (cont != null) {
			resultInvite.addChild(cont.clone());
		}

		Element bdy = message.getElement().getChild("body");
		if (bdy != null) {
			resultMessage.getElement().addChild(bdy.clone());
		}
		log.info("inviting child " + resultMessage);
		write(resultMessage);
	}

	private static Role getDefaultRole(final RoomConfig config, final Affiliation affiliation) {
		Role newRole;

		if (config.isRoomModerated() && (affiliation == Affiliation.none)) {
			newRole = Role.visitor;
		} else {
			switch (affiliation) {
			case admin:
				newRole = Role.moderator;

				break;

			case member:
				newRole = Role.participant;

				break;

			case none:
				newRole = Role.participant;

				break;

			case outcast:
				newRole = Role.none;

				break;

			case owner:
				newRole = Role.moderator;

				break;

			default:
				newRole = Role.none;

				break;
			}
		}

		return newRole;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
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
	 * @param element
	 * 
	 * @throws MUCException
	 */
	@Override
	public void process(Packet element) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));

			if (getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = context.getMucRepository().getRoom(roomJID);

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role senderRole = room.getRole(nickName);
			final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());

			final Element x = element.getElement().getChild("x", "http://jabber.org/protocol/muc#user");
			log.info(" Processing invitation listener message for nickname: " + nickName + " room: " + room
					+ " senderjjid " + senderJID);
			List<Element> ch = x.getChildren();
			for (Element child : ch) {
				if (element.getType() == StanzaType.error && "invite".equals(child.getName())) {
					processInvitationErrorResponse(child, element.getErrorCondition(), roomJID, senderJID);
				} else if ("invite".equals(child.getName()) && element.getType() != StanzaType.error) {
					doInvite(element, child, room, roomJID, senderJID, senderRole, senderAffiliation);
				} else if ("decline".equals(child.getName()) && element.getType() != StanzaType.error) {
					doDecline(child, roomJID, senderJID);
				}
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	private void processInvitationErrorResponse(Element invite, String errorCondition, BareJID roomJID, JID senderJID)
			throws TigaseStringprepException {
		final JID recipient = JID.jidInstance(invite.getAttributeStaticStr(Packet.FROM_ATT));

		Packet resultMessage = Packet.packetInstance(new Element("message", new String[] { Packet.FROM_ATT,
				Packet.TO_ATT }, new String[] { roomJID.toString(), recipient.toString() }));
		resultMessage.setXMLNS(Packet.CLIENT_XMLNS);

		final Element resultX = new Element("x", new String[] { Packet.XMLNS_ATT },
				new String[] { "http://jabber.org/protocol/muc#user" });
		resultMessage.getElement().addChild(resultX);
		final Element resultDecline = new Element("decline", new String[] { "from" },
				new String[] { senderJID.toString() });
		resultX.addChild(resultDecline);

		Element reason = new Element("reason", "Your invitation is returned with error"
				+ (errorCondition == null ? "." : (": " + errorCondition)));

		resultDecline.addChild(reason);
		write(resultMessage);
	}
}