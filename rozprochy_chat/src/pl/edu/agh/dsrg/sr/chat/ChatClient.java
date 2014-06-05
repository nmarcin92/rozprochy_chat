package pl.edu.agh.dsrg.sr.chat;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.protocols.BARRIER;
import org.jgroups.protocols.FD_ALL;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.FRAG2;
import org.jgroups.protocols.MERGE2;
import org.jgroups.protocols.MFC;
import org.jgroups.protocols.PING;
import org.jgroups.protocols.UDP;
import org.jgroups.protocols.UFC;
import org.jgroups.protocols.UNICAST2;
import org.jgroups.protocols.VERIFY_SUSPECT;
import org.jgroups.protocols.pbcast.FLUSH;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.protocols.pbcast.STATE_TRANSFER;
import org.jgroups.stack.ProtocolStack;

import pl.edu.agh.dsrg.sr.chat.gui.MessageListener;
import pl.edu.agh.dsrg.sr.chat.protos.ChatOperationProtos.ChatAction;
import pl.edu.agh.dsrg.sr.chat.protos.ChatOperationProtos.ChatAction.ActionType;
import pl.edu.agh.dsrg.sr.chat.protos.ChatOperationProtos.ChatMessage;
import pl.edu.agh.dsrg.sr.chat.protos.ChatOperationProtos.ChatState;

import com.google.protobuf.InvalidProtocolBufferException;

public class ChatClient {

	private static final String CHAT_MANAGEMENT_CLUSTER_NAME = "ChatManagement768264";

	private static final long STATE_RECEIVE_TIMEOUT = 30000;
	
	private Map<String, List<String>> chatRoomsMap;
	
	private String nickname;
	private List<JChannel> channels;
	private JChannel managementChannel;
	private byte[] buffer;
	
	private final MessageListener messageListener;
	
	public void joinChannel(String channelAddress) throws Exception {
		if (channelAddress == null) {
			throw new Exception("Channel address can't be null");
		}
		channels.add(initChannel(channelAddress));
		
		sendChatAction(ActionType.JOIN, channelAddress); 
		messageListener.onNewChannel(channelAddress);
	}
	
	public void leaveChannel(String channelAddress) throws Exception {
		for (JChannel channel : channels) {
			if (channel.getClusterName().equals(channelAddress)) {
				sendChatAction(ActionType.LEAVE, channelAddress);
				channel.close();
				channels.remove(channel);
				messageListener.onChannelRemove(channelAddress);
				break;
			}
		}
	}
	
	private void sendChatAction(ActionType type, String channelAddress) throws Exception {
		ChatAction chatAction = ChatAction.newBuilder().setAction(type).setChannel(channelAddress).setNickname(nickname).build();
		
		managementChannel.send(new Message(null, null, chatAction.toByteArray()));
	}
	
	private JChannel initChannel(String channelName) throws Exception {
		JChannel channel = new JChannel(false);
		
		if (channelName != null) {
			channel.setReceiver(new ChatRoomReceiverAdapter(channelName));
		} else {
			channel.setReceiver(new ManagementReceiverAdapter());
		}
		
		ProtocolStack stack = new ProtocolStack();
		channel.setProtocolStack(stack);
		stack.addProtocol(channelName != null ? new UDP().setValue("mcast_group_addr",InetAddress.getByName(channelName)) : new UDP())
			 .addProtocol(new PING())
			 .addProtocol(new MERGE2())
			 .addProtocol(new FD_SOCK())
			 .addProtocol(new FD_ALL().setValue("timeout", 12000).setValue("interval", 3000))
			 .addProtocol(new VERIFY_SUSPECT())
			 .addProtocol(new BARRIER())
			 .addProtocol(new NAKACK())
			 .addProtocol(new UNICAST2())
			 .addProtocol(new STABLE())
			 .addProtocol(new GMS())
			 .addProtocol(new UFC())
			 .addProtocol(new MFC())
			 .addProtocol(new FRAG2())
			 .addProtocol(new STATE_TRANSFER())
			 .addProtocol(new FLUSH());
		stack.init();
		channel.setName(nickname);
		channel.connect(channelName != null ? channelName : CHAT_MANAGEMENT_CLUSTER_NAME);
		
		return channel;
	}
	
	public ChatClient(MessageListener messageListener, String nickname) throws Exception {
		this.nickname = nickname;
		this.messageListener = messageListener;
		channels = new LinkedList<JChannel>();
		
		managementChannel = initChannel(null);
		chatRoomsMap = new HashMap<String, List<String>>();
		synchronizeState();
	}
	
	public void sendMessage(String message, String channelName) throws Exception {
		for (JChannel channel : channels) {
			if (channel.getClusterName().equals(channelName)) {
				ChatMessage chatMessage = ChatMessage.newBuilder().setMessage(message).build();
				buffer = chatMessage.toByteArray();

				Message msg = new Message(null, null, buffer);
				channel.send(msg);
				break;
			}
		}
	}

	public void shutdown() {
		for (JChannel channel : channels) {
			channel.close();
		}
	}

	public List<String> getChannelNames() {
		return new ArrayList<String>(chatRoomsMap.keySet());
	}

	public List<String> getChannelUserNames(String channelName) {
		if (chatRoomsMap.containsKey(channelName)) {
			return chatRoomsMap.get(channelName);
		}
		return new ArrayList<String>();
	}

	private void synchronizeState() throws Exception {
		managementChannel.getState(null, STATE_RECEIVE_TIMEOUT);
	}
	
	private class ChatRoomReceiverAdapter extends ReceiverAdapter {
			
		private String channelName;
		
		public ChatRoomReceiverAdapter(String channelName) {
			this.channelName = channelName;
		}
		
		@Override
		public void viewAccepted(View view) {
			boolean changed = false;
			synchronized(chatRoomsMap) {
				if (chatRoomsMap.containsKey(channelName)) {
					for (String user : chatRoomsMap.get(channelName)) {
						boolean found = false;
						for (Address member : view.getMembers()) {
							if (member.toString().equals(user)) {
								found = true;
								break;
							}
						}
						if (!found) {
							chatRoomsMap.get(channelName).remove(user);
							changed = true;
						}
					}
				
					if (changed) {
						messageListener.onClientAction();
					}
				}
			}
		}
		
		@Override
		public void receive(Message msg) {
			try {
				ChatMessage message = ChatMessage.parseFrom(msg.getBuffer());
				messageListener.onMessageReceive("[" + channelName + " / " + msg.getSrc() + "] " + message.getMessage() + "\n");
			} catch (InvalidProtocolBufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private class ManagementReceiverAdapter extends ReceiverAdapter {
			
		@Override
		public void getState(OutputStream output) throws Exception {
			synchronized(chatRoomsMap) {
				ChatState.Builder builder = ChatState.newBuilder();
				for (Entry<String, List<String>> entry : chatRoomsMap.entrySet()) {
					for (String nick : entry.getValue()) {
						builder.addState(ChatAction.newBuilder().setAction(ActionType.JOIN).setNickname(nick).setChannel(entry.getKey()).build());
					}
				}
				builder.build().writeTo(output);
			}
		}
		
		@Override
		public void setState(InputStream input) throws Exception {
			synchronized(chatRoomsMap) {
				ChatState chatState = ChatState.parseFrom(input);
				chatRoomsMap.clear();
				for (ChatAction action : chatState.getStateList()) {
					System.out.println(action.getChannel() + " / " + action.getNickname());
					if (chatRoomsMap.containsKey(action.getChannel())) {
						chatRoomsMap.get(action.getChannel()).add(action.getNickname());
					} else {
						chatRoomsMap.put(action.getChannel(), new ArrayList<String>(Arrays.asList(action.getNickname())));
					}
				}
			}
		}
		
		@Override
		public void receive(Message msg) {
			ChatAction action;
			try {
				action = ChatAction.parseFrom(msg.getBuffer());
			} catch (InvalidProtocolBufferException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return;
			}
			synchronized(chatRoomsMap) {
				if (ActionType.JOIN.equals(action.getAction())) {
					if (chatRoomsMap.containsKey(action.getChannel())) {
						chatRoomsMap.get(action.getChannel()).add(action.getNickname());
					} else {
						chatRoomsMap.put(action.getChannel(), new ArrayList<String>(Arrays.asList(action.getNickname())));
					}
				} else {
					if (chatRoomsMap.containsKey(action.getChannel())) {
						chatRoomsMap.get(action.getChannel()).remove(action.getNickname());
					}
				}
			}
			
			messageListener.onClientAction();
		}
		
	}

	public boolean isJoined(String channelName) {
		for (JChannel channel : channels) {
			if (channel.getClusterName().equals(channelName))
				return true;
		}
		return false;
	}
}
