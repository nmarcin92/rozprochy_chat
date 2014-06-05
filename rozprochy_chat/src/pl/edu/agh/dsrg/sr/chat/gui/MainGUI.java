package pl.edu.agh.dsrg.sr.chat.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import pl.edu.agh.dsrg.sr.chat.ChatClient;

public class MainGUI {

	private static final String LOGIN_FRAME_TITLE = "Nickname";
	private static final int LOGIN_WIDTH = 330;
	private static final int LOGIN_HEIGHT = 100;
	private static final String MAIN_FRAME_TITLE = "JGroups Chat";
	private static final int MAIN_WIDTH = 800;
	private static final int MAIN_HEIGHT = 600;
	protected static final int BUSY_LABEL_HEIGHT = 30;
	private JFrame mainFrame;
	private JFrame loginFrame;
	
	
	private JTextField nicknameTextField;
	private ChatClient client;
	private String nickname;
	
	private JTextArea textArea;
	private JTree roomTree;
	private DefaultMutableTreeNode topRoomTreeNode;
	private JTextField messageTextField;
	private JComboBox<String> sendComboBox;
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				MainGUI gui = new MainGUI();
				gui.loginFrameDisplay();
			}
		});
		
	}

	private void loginFrameDisplay() {
		loginFrame = new JFrame();
		loginFrame.setTitle(LOGIN_FRAME_TITLE);
		loginFrame.setSize(LOGIN_WIDTH, LOGIN_HEIGHT);
		loginFrame.setLocationRelativeTo(null);
		
		JLabel label = new JLabel("Type your nickname: ");
		nicknameTextField = new JTextField(15);
		JButton button = new JButton("OK");

		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (nicknameTextField.getText() != null) {
					nickname = nicknameTextField.getText();
					initializeChatClient();
					loginFrame.setVisible(false);
					displayMainFrame();
				}
			}
		});
		
		final JPanel panel = new JPanel();
		panel.add(label);
		panel.add(nicknameTextField);
		panel.setSize(LOGIN_WIDTH, LOGIN_HEIGHT / 2);
		
		loginFrame.add(panel, BorderLayout.NORTH);
		loginFrame.add(button, BorderLayout.CENTER);
		
		loginFrame.setVisible(true);
	}

	private void initializeChatClient() {
		try {
			client = new ChatClient(new MessageListener(this), nickname);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

	private void displayMainFrame() {
		mainFrame = new JFrame();
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setTitle(MAIN_FRAME_TITLE + " / " + nickname);
		mainFrame.setSize(MAIN_WIDTH, MAIN_HEIGHT);
		mainFrame.setLocationRelativeTo(null);
		
		topRoomTreeNode = new DefaultMutableTreeNode("Available chat rooms");
		roomTree = new JTree(topRoomTreeNode);
		roomTree.setBackground(Color.LIGHT_GRAY);
		roomTree.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		roomTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				super.mouseClicked(e);
				TreePath tp = roomTree.getPathForLocation(e.getX(), e.getY());
			    if (tp != null && tp.getPath().length == 2 && !client.isJoined(tp.getLastPathComponent().toString())) {
			    	try {
			    		client.joinChannel(tp.getLastPathComponent().toString());
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
			    }

			}
		});
		
		updateRooms();
		ScrollPane roomTreePane = new ScrollPane();
		roomTreePane.setSize(150, MAIN_HEIGHT);
		roomTreePane.add(roomTree);
		mainFrame.add(roomTreePane, BorderLayout.EAST);
		
		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
		textArea.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		mainFrame.add(textArea, BorderLayout.CENTER);
		
		messageTextField = new JTextField(30);
		JButton sendButton = new JButton("Send");
		sendButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (messageTextField.getText() != null && !"No channel selected".equals((String) sendComboBox.getSelectedItem())) {
					try {
						client.sendMessage(messageTextField.getText(), (String) sendComboBox.getSelectedItem());
						messageTextField.setText("");
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
				}
			}
		});
		
		sendComboBox = new JComboBox<String>();
		sendComboBox.addItem("No channel selected");
		
		final JButton leaveButton = new JButton("Leave");
		leaveButton.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!("No channel selected".equals((String)sendComboBox.getSelectedItem()))) {
					try {
						client.leaveChannel((String)sendComboBox.getSelectedItem());
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		});
		
		final JLabel newChannelLabel = new JLabel("New channel name: ");
		final JTextField newChannelTextField = new JTextField(10);
		final JButton newChannelButton = new JButton("Add");
		newChannelButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (newChannelTextField.getText() != null) {
					try {
						client.joinChannel(newChannelTextField.getText());
						
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					} 
				}
			}
			
		});
		
		JPanel messagePane = new JPanel();
		messagePane.add(messageTextField);
		messagePane.add(sendButton);
		messagePane.add(sendComboBox);
		messagePane.add(leaveButton);
		messagePane.add(newChannelLabel);
		messagePane.add(newChannelTextField);
		messagePane.add(newChannelButton);
		messagePane.setSize(MAIN_WIDTH, 60);
		
		mainFrame.add(messagePane, BorderLayout.SOUTH);
		
		mainFrame.setVisible(true);
	}

	@SuppressWarnings("unchecked") 
	private void updateRooms() {
		
		topRoomTreeNode.removeAllChildren();
		
		DefaultMutableTreeNode channelNode = null;
		DefaultMutableTreeNode childNode = null;
		
		for (String channelName : client.getChannelNames()) {	
			channelNode = new DefaultMutableTreeNode(channelName);
			topRoomTreeNode.insert(channelNode, topRoomTreeNode.getChildCount());
			for (String channelUser : client.getChannelUserNames(channelName)) {
				childNode = new DefaultMutableTreeNode(channelUser);
				channelNode.insert(childNode, channelNode.getChildCount());
			}
		}
				
		((DefaultTreeModel) roomTree.getModel()).reload();
		TreeUtils.expandAll(roomTree, true);
	
	}
	
	public void roomStateChanged() {
		if (roomTree != null) {
			updateRooms();
		}
	}

	public void appendMessage(String message) {
		textArea.setText(textArea.getText() + message);
	}
	
	public void addChannel(String channelName) {
		sendComboBox.addItem(channelName);
	}
	
	public void removeChannel(String channelName) {
		sendComboBox.removeItem(channelName);
	}
	
}
