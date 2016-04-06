package org.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**--------------------------------------------------------------------------------------------------------
 * Client implementation.
 * --------------------------------------------------------------------------------------------------------*/
public class Client
{
	/**--------------------------------------------------------------------------------------------
	 * GUI.
	 * --------------------------------------------------------------------------------------------*/
	JFrame frame = new JFrame("Client");
	JPanel mainPanel = new JPanel();
	JLabel statLabel1 = new JLabel();
	JLabel statLabel2 = new JLabel();
	JLabel statLabel3 = new JLabel();
	JLabel iconLabel = new JLabel();
	ImageIcon icon;

	/**--------------------------------------------------------------------------------------------
	 * Constructor
	 * --------------------------------------------------------------------------------------------*/
	public Client()
	{
		// Statistics
		statLabel1.setText("Total Bytes Received: 0");
		statLabel2.setText("Packets Lost: 0");
		statLabel3.setText("Data Rate (bytes/sec): 0");

		// Image display label
		iconLabel.setIcon(null);

		// frame layout
		mainPanel.setLayout(null);
		mainPanel.add(iconLabel);
		mainPanel.add(statLabel1);
		mainPanel.add(statLabel2);
		mainPanel.add(statLabel3);
		iconLabel.setBounds(0,0,380,280);
		statLabel1.setBounds(0,330,380,20);
		statLabel2.setBounds(0,350,380,20);
		statLabel3.setBounds(0,370,380,20);

		frame.getContentPane().add(mainPanel, BorderLayout.CENTER);
		frame.setSize(new Dimension(380,420));
		frame.setVisible(true);
	}
}
//end of Class Client
