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

package de.fabianonline.telegram_backup

import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.util.Vector

class GUIController {
    init {
        showAccountChooserDialog()
    }

    private fun showAccountChooserDialog() {
        val accountChooser = JDialog()
        accountChooser.setTitle("Choose account")
        accountChooser.setSize(400, 200)
        val vert = JPanel()
        vert.setLayout(BorderLayout())
        vert.add(JLabel("Please select the account to use or create a new one."), BorderLayout.NORTH)
        val accounts = Utils.getAccounts()
        val list = JList<String>(accounts)
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        vert.add(list, BorderLayout.CENTER)
        val bottom = JPanel(GridLayout(1, 2))
        val btnAddAccount = JButton("Add account")
        bottom.add(btnAddAccount)
        val btnLogin = JButton("Login")
        btnLogin.setEnabled(false)
        bottom.add(btnLogin)
        vert.add(bottom, BorderLayout.SOUTH)
        accountChooser.add(vert)
        accountChooser.setVisible(true)
        accountChooser.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)

        list.addListSelectionListener(object : ListSelectionListener() {
            @Override
            fun valueChanged(e: ListSelectionEvent) {
                btnLogin.setEnabled(true)
            }
        })

        btnAddAccount.addActionListener(object : ActionListener() {
            @Override
            fun actionPerformed(e: ActionEvent) {
                accountChooser.setVisible(false)
                accountChooser.dispose()
                addAccountDialog()
            }
        })
    }

    private fun addAccountDialog() {
        val loginDialog = JDialog()
        loginDialog.setTitle("Add an account")
        loginDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE)

        val sections = JPanel()
        sections.setLayout(BoxLayout(sections, BoxLayout.Y_AXIS))

        val top = JPanel()
        top.setLayout(BoxLayout(top, BoxLayout.Y_AXIS))
        top.add(JLabel("Please enter your phone number in international format:"))
        top.add(JTextField("+49123773212"))

        sections.add(top)
        sections.add(Box.createVerticalStrut(5))
        sections.add(JSeparator(SwingConstants.HORIZONTAL))

        val middle = JPanel()
        middle.setLayout(BoxLayout(middle, BoxLayout.Y_AXIS))
        middle.add(JLabel("Telegram sent you a code. Enter it here:"))
        middle.add(JTextField())
        middle.setEnabled(false)

        sections.add(middle)
        sections.add(Box.createVerticalStrut(5))
        sections.add(JSeparator(SwingConstants.HORIZONTAL))

        loginDialog.add(sections)
        loginDialog.setVisible(true)
    }
}
