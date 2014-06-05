package pl.edu.agh.dsrg.sr.chat.gui;

public class MessageListener {

	private MainGUI gui;
	
	public MessageListener(MainGUI gui) {
		this.gui = gui;
	}
	
	public void onMessageReceive(String message) {
		gui.appendMessage(message);
	}

	public void onClientAction() {
		gui.roomStateChanged();
	}
	
	public void onNewChannel(String channelName) {
		gui.addChannel(channelName);
	}
	
	public void onChannelRemove(String channelName) {
		gui.removeChannel(channelName);
	}
}
