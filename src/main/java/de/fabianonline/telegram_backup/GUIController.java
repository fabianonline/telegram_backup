/* Telegram_Backup
 * Copyright (C) 2016 Fabian Schlenz
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package de.fabianonline.telegram_backup;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

public class GUIController {
	private CommandLineOptions options;

	public GUIController(CommandLineOptions options) {
		this.options = options;
		showAccountChooserDialog();
	}

	private void showAccountChooserDialog() {
		final JDialog accountChooser = new JDialog();
		accountChooser.setTitle("Choose account");
		accountChooser.setSize(400, 200);
		JPanel vert = new JPanel();
		vert.setLayout(new BorderLayout());
		vert.add(new JLabel("Please select the account to use or create a new one."), BorderLayout.NORTH);
		Vector<String> accounts = Utils.getAccounts();
		JList<String> list = new JList<String>(accounts);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		vert.add(list, BorderLayout.CENTER);
		JPanel bottom = new JPanel(new GridLayout(1, 2));
		JButton btnAddAccount = new JButton("Add account");
		bottom.add(btnAddAccount);
		final JButton btnLogin = new JButton("Login");
		btnLogin.setEnabled(false);
		bottom.add(btnLogin);
		vert.add(bottom, BorderLayout.SOUTH);
		accountChooser.add(vert);
		accountChooser.setVisible(true);
		accountChooser.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		list.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				btnLogin.setEnabled(true);
			}
		});

		btnAddAccount.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				accountChooser.setVisible(false);
				accountChooser.dispose();
				addAccountDialog();
			}
		});
	}

	private void addAccountDialog() {
		JDialog loginDialog = new JDialog();
		loginDialog.setTitle("Add an account");
		loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel sections = new JPanel();
		sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(new JLabel("Please enter your phone number in international format:"));
		top.add(new JTextField("+49123773212"));

		sections.add(top);
		sections.add(Box.createVerticalStrut(5));
		sections.add(new JSeparator(SwingConstants.HORIZONTAL));

		JPanel middle = new JPanel();
		middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
		middle.add(new JLabel("Telegram sent you a code. Enter it here:"));
		middle.add(new JTextField());
		middle.setEnabled(false);

		sections.add(middle);
		sections.add(Box.createVerticalStrut(5));
		sections.add(new JSeparator(SwingConstants.HORIZONTAL));

		loginDialog.add(sections);
		loginDialog.setVisible(true);
	}
}
