package routing;

import java.util.ArrayList;
import java.util.List;

import core.*;


public class ZRouter extends ActiveRouter {
	
	/** List of all routers in this node group */
	private static List<ZRouter> allRouters;

	static {
		DTNSim.registerForReset(ZRouter.class.getCanonicalName());
		reset();
	}
	
	
	public ZRouter(Settings s) {
		super(s);		
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected ZRouter(ZRouter r) {
		super(r);
		allRouters.add(this);
	}
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		
		if (con.isUp()) {
			DTNHost peer = con.getOtherNode(getHost());
			List<Message> newMessages = new ArrayList<Message>();
			
			for (Message m : peer.getMessageCollection()) {
				if (!this.hasMessage(m.getId())) {
					newMessages.add(m);
				}
			}
			for (Message m : newMessages) {
				/* try to start transfer from peer */
				if (con.startTransfer(peer, m) == RCV_OK) {
					con.finalizeTransfer(); /* and finalize it right away */
				}
			}
		}
	}

	private void sendMessageToConnected(Message m) {
		DTNHost host = getHost();
		
		for (Connection c : getConnections()) {
			if (c.isReadyForTransfer() && c.startTransfer(host, m) == RCV_OK) {
				c.finalizeTransfer(); /* and finalize it right away */
			}			
		}
	}
		
	public boolean createNewMessage(Message m) {
		boolean ok = super.createNewMessage(m);
		
		if (!ok) {
			throw new SimError("Can't create message " + m);
		}

		sendMessageToConnected(m);
		
		return true;
	}
	
	
	public void removeDeliveredMessage(String id) {
		if (this.hasMessage(id)) {
			for (Connection c : this.sendingConnections) {
				/* if sending the message-to-be-removed, cancel transfer */
				if (c.getMessage().getId().equals(id)) {
					c.abortTransfer();
				}
			}
			this.deleteMessage(id, false);			
		}
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);

		if (m.getTo() == this.getHost()) {
			for (ZRouter r : allRouters) {
				if (r != this && r != from.getRouter()) {
					r.removeDeliveredMessage(id);
				}
			}
		} else {
			sendMessageToConnected(m);
		}
		
		return m;
	}
	
	protected int checkReceiving(Message m) {
		if ( isIncomingMessage(m.getId()) || hasMessage(m.getId()) || 
				isDeliveredMessage(m) ){
			return DENIED_OLD; // already seen this message -> reject it
		}
		
		if (m.getTtl() <= 0 && m.getTo() != getHost()) {
			/* TTL has expired and this host is not the final recipient */
			return DENIED_TTL; 
		}

		/* remove oldest messages but not the ones being sent */
		if (!makeRoomForMessage(m.getSize())) {
			return DENIED_NO_SPACE; // couldn't fit into buffer -> reject
		}
		
		return RCV_OK;
	}
	
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		
		if (m == null) {
			core.Debug.p("Null message for con " + con);
			return;
		}
		
		/* was the message delivered to the final recipient? */
		if (m.getTo() == con.getOtherNode(getHost())) { 
			this.deleteMessage(m.getId(), false);
		}
	}
	
	@Override
	public void update() {
		/* nothing to do; all transfers are started only when new connections
		   are created or new messages are created or received, and transfers
		   are finalized immediately */
	}
	
	
	@Override
	public ZRouter replicate() {
		return new ZRouter(this);
	}
	
	
	public static void reset() {
		allRouters = new ArrayList<ZRouter>();
	}

}