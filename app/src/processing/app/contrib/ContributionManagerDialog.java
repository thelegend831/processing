/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along 
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.SocketTimeoutException;
import java.util.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;

import processing.app.*;


public class ContributionManagerDialog {
  static final String ANY_CATEGORY = Language.text("contrib.all");

  JFrame dialog;
  ContributionFilter filter;
  JComboBox categoryChooser;
  JScrollPane scrollPane;
  ContributionListPanel contributionListPanel;
  StatusPanel status;
  FilterField filterField;
  JButton restartButton;

  // the calling editor, so updates can be applied
  Editor editor;
  String category;
  ContributionListing contribListing;


  public ContributionManagerDialog(ContributionType type) {
    if (type == null) {
      filter = ContributionType.createUpdateFilter();
    } else {
      filter = type.createFilter();    
    }
    contribListing = ContributionListing.getInstance();
    contributionListPanel = new ContributionListPanel(this, filter);
    contribListing.addContributionListener(contributionListPanel);
  }


  public boolean hasUpdates() {
    return contribListing.hasUpdates();
  }


  public boolean hasUpdates(Base base) {
    return contribListing.hasUpdates(base);
  }
  
  public void showFrame(final Editor editor) {
    this.editor = editor;

    if (dialog == null) {
      dialog = new JFrame(Language.text("contributions"));

      restartButton = new JButton(Language.text("contrib.restart"));
      restartButton.setVisible(false);
      restartButton.addActionListener(new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent arg0) {

          Iterator<Editor> iter = editor.getBase().getEditors().iterator();
          while (iter.hasNext()) {
            Editor ed = iter.next();
            if (ed.getSketch().isModified()) {
              int option = Base
                .showYesNoQuestion(editor, Language.text("contributions"),
                                   Language.text("contributions.unsaved_changes"),
                                   Language.text("contributions.unsaved_changes.prompt"));

              if (option == JOptionPane.NO_OPTION)
                return;
              else
                break;
            }
          }

          // Thanks to http://stackoverflow.com/a/4160543
          StringBuilder cmd = new StringBuilder();
          cmd.append(System.getProperty("java.home") + File.separator + "bin"
            + File.separator + "java ");
          for (String jvmArg : ManagementFactory.getRuntimeMXBean()
            .getInputArguments()) {
            cmd.append(jvmArg + " ");
          }
          cmd.append("-cp ")
            .append(ManagementFactory.getRuntimeMXBean().getClassPath())
            .append(" ");
          cmd.append(Base.class.getName());

          try {
            Runtime.getRuntime().exec(cmd.toString());
            System.exit(0);
          } catch (IOException e) {
            e.printStackTrace();
          }

        }

      });
      
      Toolkit.setIcon(dialog);
      createComponents();
      registerDisposeListeners();

      dialog.pack();
      dialog.setLocationRelativeTo(null);
      contributionListPanel.grabFocus();
    }
    dialog.setVisible(true);

    if (contribListing.hasDownloadedLatestList()) {
      updateContributionListing();

    } else {
      contribListing.downloadAvailableList(new ProgressMonitor() {
        
        public void finished() {
          super.finished();
          
          updateContributionListing();
          updateCategoryChooser();
          if (error) {
            if (exception instanceof SocketTimeoutException) {
              status.setErrorMessage(Language.text("contrib.errors.list_download.timeout"));
            } else {
              status.setErrorMessage(Language.text("contrib.errors.list_download"));
            }
            exception.printStackTrace();
          }
        }
      });
    }
  }

  
  /**
   * Close the window after an OK or Cancel.
   */
  protected void disposeFrame() {
    status.clear();
    dialog.dispose();
    editor = null;
  }
  

  /** Creates and arranges the Swing components in the dialog. */
  private void createComponents() {
    dialog.setResizable(true);

    Container pane = dialog.getContentPane();
//    pane.setLayout(new GridBagLayout());
//
//    { // Shows "Filter by Category" and the combo box for selecting a category
//      GridBagConstraints c = new GridBagConstraints();
//      c.gridx = 0;
//      c.gridy = 0;
    pane.setLayout(new BorderLayout());

      JPanel filterPanel = new JPanel();
      filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.X_AXIS));
//      pane.add(filterPanel, c);
      pane.add(filterPanel, BorderLayout.NORTH);

      filterPanel.add(Box.createHorizontalStrut(6));

      JLabel categoryLabel = new JLabel(Language.text("contrib.category"));
      filterPanel.add(categoryLabel);

      filterPanel.add(Box.createHorizontalStrut(5));

      categoryChooser = new JComboBox();
      categoryChooser.setMaximumRowCount(20);
      updateCategoryChooser();
//      filterPanel.add(categoryChooser, c);
      filterPanel.add(categoryChooser);
      categoryChooser.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          category = (String) categoryChooser.getSelectedItem();
          if (ContributionManagerDialog.ANY_CATEGORY.equals(category)) {
            category = null;
          }
          filterLibraries(category, filterField.filters);
          contributionListPanel.updateColors();
        }
      });
      
      filterPanel.add(Box.createHorizontalStrut(5));
//      filterPanel.add(Box.createHorizontalGlue());
      filterField = new FilterField();
      filterPanel.add(filterField);
//      filterPanel.add(Box.createHorizontalGlue());
//    }
      //filterPanel.setBorder(new EmptyBorder(13, 13, 13, 13));
      filterPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
      
//    { // The scroll area containing the contribution listing and the status bar.
//      GridBagConstraints c = new GridBagConstraints();
//      c.fill = GridBagConstraints.BOTH;
//      c.gridx = 0;
//      c.gridy = 1;
//      c.gridwidth = 2;
//      c.weighty = 1;
//      c.weightx = 1;

      scrollPane = new JScrollPane();
      scrollPane.setPreferredSize(new Dimension(300, 300));
      scrollPane.setViewportView(contributionListPanel);
//      scrollPane.getViewport().setOpaque(true);
//      scrollPane.getViewport().setBackground(contributionListPanel.getBackground());
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
//      scrollPane.setBorder(new EmptyBorder(0, 7, 0, 7));
      pane.add(scrollPane, BorderLayout.CENTER);
      
      pane.add(Box.createHorizontalStrut(10), BorderLayout.WEST);
      pane.add(Box.createHorizontalStrut(10), BorderLayout.EAST);

      status = new StatusPanel();
//      status.setBorder(new EmptyBorder(7, 7, 7, 7));
      
      JPanel statusRestartPane = new JPanel();
      statusRestartPane.setLayout(new BorderLayout());
      
      statusRestartPane.setBorder(new EmptyBorder(7, 7, 7, 7));
      statusRestartPane.setOpaque(false);
      
      statusRestartPane.add(status, BorderLayout.WEST);
      statusRestartPane.add(restartButton, BorderLayout.EAST);
      
      pane.add(statusRestartPane, BorderLayout.SOUTH);

      
//      status = new StatusPanel();
//      status.setBorder(BorderFactory.createEtchedBorder());

//      final JLayeredPane layeredPane = new JLayeredPane();
//      layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
//      layeredPane.add(status, JLayeredPane.PALETTE_LAYER);
//
//      layeredPane.addComponentListener(new ComponentAdapter() {
//
//        void resizeLayers() {
//          scrollPane.setSize(layeredPane.getSize());
//          scrollPane.updateUI();
//        }
//
//        public void componentShown(ComponentEvent e) {
//          resizeLayers();
//        }
//
//        public void componentResized(ComponentEvent arg0) {
//          resizeLayers();
//        }
//      });
//
//      final JViewport viewport = scrollPane.getViewport();
//      viewport.addComponentListener(new ComponentAdapter() {
//        void resizeLayers() {
//          status.setLocation(0, viewport.getHeight() - 18);
//
//          Dimension d = viewport.getSize();
//          d.height = 20;
//          d.width += 3;
//          status.setSize(d);
//        }
//        public void componentShown(ComponentEvent e) {
//          resizeLayers();
//        }
//        public void componentResized(ComponentEvent e) {
//          resizeLayers();
//        }
//      });
//
//      pane.add(layeredPane, c);
//    }

//    { // The filter text area
//      GridBagConstraints c = new GridBagConstraints();
//      c.gridx = 0;
//      c.gridy = 2;
//      c.gridwidth = 2;
//      c.weightx = 1;
//      c.fill = GridBagConstraints.HORIZONTAL;
//      filterField = new FilterField();
//
//      pane.add(filterField, c);
//    }

    dialog.setMinimumSize(new Dimension(450, 400));
  }
  

  private void updateCategoryChooser() {
    if (categoryChooser != null) {
      ArrayList<String> categories;
      categoryChooser.removeAllItems();
      categories = new ArrayList<String>(contribListing.getCategories(filter));
//      for (int i = 0; i < categories.size(); i++) {
//        System.out.println(i + " category: " + categories.get(i));
//      }
      Collections.sort(categories);
//    categories.add(0, ContributionManagerDialog.ANY_CATEGORY);
      boolean categoriesFound = false;
      categoryChooser.addItem(ContributionManagerDialog.ANY_CATEGORY);
      for (String s : categories) {
        categoryChooser.addItem(s);
        if (!s.equals("Unknown")) {
          categoriesFound = true;
        }
      }
      categoryChooser.setEnabled(categoriesFound);
    }
  }

  
  private void registerDisposeListeners() {
    dialog.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        disposeFrame();
      }
    });
    // handle window closing commands for ctrl/cmd-W or hitting ESC.
    Toolkit.registerWindowCloseKeys(dialog.getRootPane(), new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        disposeFrame();
      }
    });

    dialog.getContentPane().addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        //System.out.println(e);
        KeyStroke wc = Toolkit.WINDOW_CLOSE_KEYSTROKE;
        if ((e.getKeyCode() == KeyEvent.VK_ESCAPE) ||
            (KeyStroke.getKeyStrokeForEvent(e).equals(wc))) {
          disposeFrame();
        }
      }
    });
  }

  
  protected void filterLibraries(String category, List<String> filters) {
    List<Contribution> filteredLibraries = 
      contribListing.getFilteredLibraryList(category, filters);
    contributionListPanel.filterLibraries(filteredLibraries);
  }

  
  protected void updateContributionListing() {
    if (editor != null) {
      ArrayList<Contribution> contributions = new ArrayList<Contribution>();

      ArrayList<Library> libraries = 
        new ArrayList<Library>(editor.getMode().contribLibraries);
      contributions.addAll(libraries);

      ArrayList<ToolContribution> tools = editor.contribTools;
      contributions.addAll(tools);
      
      ArrayList<ModeContribution> modes = editor.getBase().getModeContribs();
      contributions.addAll(modes);

//    ArrayList<LibraryCompilation> compilations = LibraryCompilation.list(libraries);
//
//    // Remove libraries from the list that are part of a compilations
//    for (LibraryCompilation compilation : compilations) {
//      Iterator<Library> it = libraries.iterator();
//      while (it.hasNext()) {
//        Library current = it.next();
//        if (compilation.getFolder().equals(current.getFolder().getParentFile())) {
//          it.remove();
//        }
//      }
//    }

      contribListing.updateInstalledList(contributions);
    }
  }

  
  protected void setFilterText(String filter) {
    if (filter == null || filter.isEmpty()) {
      filterField.setText("");
      filterField.showingHint = true;
    } else {
      filterField.setText(filter);
      filterField.showingHint = false;
    }
    filterField.applyFilter();
  }
  
  
//  private JPanel getPlaceholder() {
//    return contributionListPanel.statusPlaceholder;
//  }
  

  class FilterField extends JTextField {
    String filterHint;
    boolean showingHint;
    List<String> filters;

    public FilterField () {
      super(Language.text("contrib.filter_your_search"));
      filterHint = Language.text("contrib.filter_your_search");
      
      showingHint = true;
      filters = new ArrayList<String>();
      updateStyle();

      addFocusListener(new FocusListener() {
        public void focusLost(FocusEvent focusEvent) {
          if (filterField.getText().isEmpty()) {
            showingHint = true;
          }
          updateStyle();
        }

        public void focusGained(FocusEvent focusEvent) {
          if (showingHint) {
            showingHint = false;
            filterField.setText("");
          }
          updateStyle();
        }
      });

      getDocument().addDocumentListener(new DocumentListener() {
        public void removeUpdate(DocumentEvent e) {
          applyFilter();
        }

        public void insertUpdate(DocumentEvent e) {
          applyFilter();
        }

        public void changedUpdate(DocumentEvent e) {
          applyFilter();
        }
      });
    }

    public void applyFilter() {
      String filter = filterField.getFilterText();
      filter = filter.toLowerCase();

      // Replace anything but 0-9, a-z, or : with a space
      filter = filter.replaceAll("[^\\x30-\\x39^\\x61-\\x7a^\\x3a]", " ");
      filters = Arrays.asList(filter.split(" "));
      filterLibraries(category, filters);

      contributionListPanel.updateColors();
    }

    public String getFilterText() {
      return showingHint ? "" : getText();
    }

    public void updateStyle() {
      if (showingHint) {
        setText(filterHint);
        // setForeground(UIManager.getColor("TextField.light")); // too light
        setForeground(Color.gray);
        setFont(getFont().deriveFont(Font.ITALIC));
      } else {
        setForeground(UIManager.getColor("TextField.foreground"));
        setFont(getFont().deriveFont(Font.PLAIN));
      }
    }
  }

  
  public boolean hasAlreadyBeenOpened() {
    return dialog != null;
  }
}
