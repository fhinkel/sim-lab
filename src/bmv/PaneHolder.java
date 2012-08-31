package bmv;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Queue;
import java.util.regex.Pattern;

import javax.net.ssl.SSLEngineResult.Status;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.PanelUI;
import javax.swing.plaf.basic.BasicBorders.SplitPaneBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import javax.swing.text.View;

import org.omg.CORBA.VersionSpecHelper;

import bmv.LoadingPanel.STATUS;
import bmv.TrajPanel.MODE;

public class PaneHolder extends JPanel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected enum MODE {
		EDIT, TRAJ, MULTITRAJ, RESULTS, MULTIRESULTS
	};

	protected enum OS {
		LINUX, MAC, WINDOWS
	};

	protected enum ARCH {
		BIT32, BIT64
	};

	final static int DEFAULT_OPEN_MODEL_BUFFER = 5;

	protected static final Dimension VIEW_SIZE = new Dimension(800, 600);
	protected static final int NEW_MODEL = 0, NEW_EXP = 1, LOAD_MODEL = 2,
			LOAD_EXP = 3, LOAD_RESULT = 4, IMPORT_MODEL = 5, SAVE_MODEL = 6,
			SAVE_MODEL_AS = 7, SAVE_EXP = 8, SAVE_EXP_AS = 9, ADD_NODE = 0,
			ADD_ACT_EDGE = 1, ADD_INH_EDGE = 2, JOINTED_LINES = 3, DELETE = 4,
			EDIT_NAME = 5, EDIT_COLOR = 6, EDIT_STATES = 7, DRAW = 8,
			SIMULATE_MODEL = 9, KNOCKOUT = 0, RUN_EXP = 1, RUN_KO = 2,
			RUN_RANDOM = 3, CONTINUOUS = 0;
	protected static final int MENU_FILE = 0, MENU_MODEL = 1, MENU_EXP = 2,
			MENU_OPTIONS = 3, MENU_HELP = 4, BUTTON_MENUBAR = 5;

	JLayeredPane layeredPanes;
	EditPanel editPanel;
	TrajPanel trajPanel;
	ResultsPanel resultPanel;
	ModelHolder model;
	Trajectory traj;
	boolean lastResultsWereMulti;
	MainFrame frame;
	JSplitPane mainPane, sidePane;
	MODE curMode;
	FileBrowser fileBrowser;
	JMenuBar menuBar;
	JTextArea textPanel;
	StateVocabulary vocab;
	Parser parser;
	Viewport viewport;
	BMVPanel curPanel;
	ARCH arch;
	OS os;
	LoadingPanel loadingPanel;

	public PaneHolder(MainFrame mainframe, JLayeredPane framePane) {

		setLayout(new BorderLayout());
		frame = mainframe;

		fileBrowser = new FileBrowser("Models", this);
		fileBrowser.setBounds(0, 0, 200, 800);
		PlainDocument doc = new PlainDocument();

		textPanel = new JTextArea(doc);
		textPanel.setVisible(true);
		textPanel.setBorder(BorderFactory.createTitledBorder("NOTES"));

		layeredPanes = new JLayeredPane();
		layeredPanes.setBounds(0, 0, 600, 800);

		JScrollPane notePane = new JScrollPane(textPanel);
		notePane.setVisible(true);
		notePane.setPreferredSize(new Dimension(200, 200));

		sidePane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileBrowser,
				notePane);
		sidePane.setDividerLocation((int) 600);

		// view.setBounds(0, 0, 400, 400);
		mainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePane,
				layeredPanes);
		mainPane.addPropertyChangeListener("dividerLocation",
				new PropertyChangeListener() {
					@Override
					public void propertyChange(PropertyChangeEvent evt) {
						if (curPanel != null) {
							curPanel.update();
						}
					}
				});
		mainPane.setBounds(0, 0, 1000, 800);
		mainPane.setDividerLocation(200);

		initializePanelsAndMenu();
		layeredPanes.add(menuBar);
		menuBar.setOpaque(true);
		layeredPanes.add(editPanel, new Integer(2));
		lastResultsWereMulti = false;

		this.add(menuBar, BorderLayout.NORTH);
		this.add(mainPane);
		// layeredPanes.setSize(new Dimension(mainPane.getSize().width
		// - mainPane.getDividerLocation(), mainPane.getSize().height));
		curMode = MODE.EDIT;
		viewport.update(
				new Rectangle(
						(model.getTotalSize().width - layeredPanes.getSize().width) / 2,
						(model.getTotalSize().height - layeredPanes.getSize().height) / 2,
						VIEW_SIZE.width, VIEW_SIZE.height), layeredPanes
						.getSize());
		mainframe.setContentPane(this);
	}

	private void initializePanelsAndMenu() {
		getSystemProperties();

		viewport = new Viewport();
		vocab = new StateVocabulary();
		parser = new Parser(this, viewport);
		model = new ModelHolder(new Model(this, viewport), textPanel, this);
		traj = new Trajectory(model.getCurModel());

		editPanel = new EditPanel(model, viewport, traj, this);
		editPanel.update();
		curPanel = editPanel;
		addComponentListener(new ComponentHandler());
		trajPanel = new TrajPanel(model, viewport, traj, this);
		resultPanel = new ResultsPanel(model, viewport, traj, this);

		menuBar = new JMenuBar();
		menuBar.setPreferredSize(new Dimension(200, 20));
		// <<< <<< FILE MENU >>> >>> //
		JMenu menu = new JMenu("File");
		menu.setMnemonic(KeyEvent.VK_F);
		menuBar.add(menu);
		JMenuItem item = new JMenuItem("New Model (N)");
		item.setMnemonic(KeyEvent.VK_N);
		item.setToolTipText("Create a new model");
		item.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				fileBrowser.addModel();
			}
		});
		menu.add(item);

		item = new JMenuItem("New Experiment");
		item.setToolTipText("Create a new experiment in this model");
		item.setMnemonic(KeyEvent.VK_N);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				fileBrowser.newExperiment(model.getModelName());
			}
		});
		menu.add(item);

		item = new JMenuItem("Import Model (.txt)");
		item.setToolTipText("Build a model from a text description (I)");
		item.setMnemonic(KeyEvent.VK_I);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				importModel(null);
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Load Model");
		item.setToolTipText("Load a model file (L)");
		item.setMnemonic(KeyEvent.VK_L);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.loadModel(null);
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Load Experiment");
		item.setToolTipText("Load an experiment file (L)");
		item.setMnemonic(KeyEvent.VK_L);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				trajPanel.getTrajectory().loadTraj(false);
				switchToTrajPanel(true);
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Load Result");
		item.setToolTipText("Load a results file (L)");
		item.setMnemonic(KeyEvent.VK_L);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				resultPanel.newFile(null);
				switchToResultsPanel(null);
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Save Model");
		item.setMnemonic(KeyEvent.VK_S);
		item.setToolTipText("Save this model (S)");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				model.saveModel();
			}
		});
		item.setEnabled(false);
		menu.add(item);

		item = new JMenuItem("Save Model As");
		item.setMnemonic(KeyEvent.VK_S);
		item.setToolTipText("Save this model by another name (S)");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				saveModelAs(JOptionPane.showInputDialog("Save as..."));
			}
		});
		item.setEnabled(false);
		menu.add(item);

		item = new JMenuItem("Save Experiment");
		item.setMnemonic(KeyEvent.VK_S);
		item.setToolTipText("Save this experiment (S)");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (trajPanel.getTrajectory() != null) {
					trajPanel.getTrajectory().saveTraj(true);
				}
			}
		});
		item.setEnabled(false);
		menu.add(item);

		item = new JMenuItem("Save Experiment As");
		item.setMnemonic(KeyEvent.VK_S);
		item.setToolTipText("Save this experiment (S)");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (trajPanel.getTrajectory() != null) {
					saveExpAs(JOptionPane.showInputDialog("Save as..."));
				}
			}
		});
		item.setEnabled(false);
		menu.add(item);

		item = new JMenuItem("Exit");
		item.setMnemonic(KeyEvent.VK_ESCAPE);
		item.setToolTipText("Save this experiment (ESC)");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (Frame frame : Frame.getFrames()) {
					if (frame.isActive()) {
						WindowEvent windowClosing = new WindowEvent(frame,
								WindowEvent.WINDOW_CLOSING);
						frame.dispatchEvent(windowClosing);
					}
				}
			}
		});
		item.setEnabled(true);
		menu.add(item);
		menuBar.add(menu);
		// <<< <<< FILE MENU >>> >>> //

		// <<< <<< MODEL MENU >>> >>> //
		menu = new JMenu("Model");
		menu.setMnemonic(KeyEvent.VK_M);
		item = new JMenuItem("Add Node");
		item.setToolTipText("Add a node to the model (Q)");
		item.setMnemonic(KeyEvent.VK_Q);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.addNodeClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Add Activating Edge");
		item.setToolTipText("Add an activating edge to the model (W)");
		item.setMnemonic(KeyEvent.VK_W);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.addActEdgeClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Add Inhibiting Edge");
		item.setToolTipText("Add an inhibiting edge to the model (E)");
		item.setMnemonic(KeyEvent.VK_E);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.addInhEdgeClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);

		JCheckBoxMenuItem citem = new JCheckBoxMenuItem("Jointed Lines");
		citem.setToolTipText("Toggle whether new lines will have joints (J)");
		citem.setMnemonic(KeyEvent.VK_J);
		item.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent arg0) {
				editPanel.setMultiJointed(arg0.getStateChange() == ItemEvent.SELECTED);
			}
		});
		item.setEnabled(true);
		item.setSelected(true);
		menu.add(citem);

		item = new JMenuItem("Delete");
		item.setToolTipText("Delete a node or edge (D)");
		item.setMnemonic(KeyEvent.VK_D);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.deleteClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Edit Name");
		item.setToolTipText("Edit the name of a node (Z)");
		item.setMnemonic(KeyEvent.VK_Z);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.editNameClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Edit Color");
		item.setToolTipText("Edit the color of a node (X)");
		item.setMnemonic(KeyEvent.VK_X);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.editColorClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("Edit States");
		item.setToolTipText("Edit the number of states of a node (C)");
		item.setMnemonic(KeyEvent.VK_C);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.editStatesClicked();
			}
		});
		item.setEnabled(true);

		menu.add(item);
		item = new JMenuItem("Draw Box");
		item.setToolTipText("Draw a box to help organize the model (B)");
		item.setMnemonic(KeyEvent.VK_B);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.drawClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);
		menuBar.add(menu);
		item = new JMenuItem("Simulate Model");
		item.setToolTipText("Run a full simulation of the model to find equilibrium points and cycles (R)");
		item.setMnemonic(KeyEvent.VK_R);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				editPanel.simulateModelClicked();
			}
		});
		item.setEnabled(true);

		menu.add(item);
		menuBar.add(menu);
		// <<< <<< MODEL MENU >>> >>> //

		// <<< <<< EXPERIMENT MENU >>> >>> //
		menu = new JMenu("Experiment");
		menu.setMnemonic(KeyEvent.VK_E);
		item = new JMenuItem("Knockout Node");
		item.setToolTipText("Fully knockdown this node for the experiment (K)");
		item.setMnemonic(KeyEvent.VK_K);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				trajPanel.knockoutClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);
		item = new JMenuItem("Run Experiment");
		item.setToolTipText("Simulate this experiment in the model (E)");
		item.setMnemonic(KeyEvent.VK_E);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				trajPanel.runExpClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);
		item = new JMenuItem("Run All Single Knockouts");
		item.setToolTipText("Simulate this experiment in the model with each node knocked out one at a time (B)");
		item.setMnemonic(KeyEvent.VK_B);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				trajPanel.runKOClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);
		item = new JMenuItem("Run Random Update");
		item.setToolTipText("Simulate this experiment updating a single random variable each time (R)");
		item.setMnemonic(KeyEvent.VK_R);
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				trajPanel.runRandomClicked();
			}
		});
		item.setEnabled(true);
		menu.add(item);
		menuBar.add(menu);
		// <<< <<< EXPERIMENT MENU >>> >>> //

		// <<< <<< OPTIONS MENU >>> >>> //
		menu = new JMenu("Options");
		menu.setMnemonic(KeyEvent.VK_O);
		citem = new JCheckBoxMenuItem("Enforce Continuity");
		citem.setToolTipText("Toggle whether each update state in a table must be within one of its previous state (C)");
		citem.setMnemonic(KeyEvent.VK_C);
		citem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				model.setContinuous(!model.isContinuous());
			}
		});
		citem.setSelected(true);
		citem.setEnabled(true);
		menu.add(citem);

		citem = new JCheckBoxMenuItem("Show Edge Names");
		citem.setToolTipText("Toggle whether edge names are displayed or not (N)");
		citem.setMnemonic(KeyEvent.VK_N);
		citem.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				model.setDrawEdgeNames(!model.isDrawEdgeNames());
			}
		});
		citem.setSelected(true);
		citem.setEnabled(true);
		menu.add(citem);
		menuBar.add(menu);
		// <<< <<< OPTIONS MENU >>> >>> //

		// <<< <<< RESULT MENU >>> >>> //
		// <<< <<< RESULT MENU >>> >>> //

		// <<< <<< HELP MENU >>> >>> //
		menu = new JMenu("Help");
		item = new JMenuItem("About");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane
						.showMessageDialog(
								null,
								"PlantSimLab was created by Paul Vines, Franziska\nHinkelmann, and Reinhard Laubenbacher at the\nVirginia Bioinformatics Institute. (vbi.vt.edu)\nSome icons by Yusuke Kamiyamane.\nAll rights reserved.");
			}
		});
		item.setEnabled(true);
		menu.add(item);
		item = new JMenuItem("Using Import(.txt)");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane
						.showMessageDialog(
								null,
								"The Import feature is used to convert a text description of a model into a model in PlantSimLab.\nThe description should be formatted as follows:\nEvery sentence must be ended by a period.\nPeriods can only be used to end sentences (not abbreviate).\nEach sentence should begin with the name of a node.\nA list of nodes should have comma's placed between each node name and ended with 'and'\nbefore the last node name.\nEach sentence should contain the names of at least two nodes.\nEach sentence should contain at least one word between the node names.\nCapitalization of node names matters (Anode is not anode).\nIf the sentence is simple the edge will be properly named.\nOtherwise the edge will be named \"regulates\"\n\nExample Statements:\n\tA upregulates B.\n\tB is upregulated by A.\n\"A, B, and C are upregulated by D and E\"");
			}
		});
		item.setEnabled(true);
		menu.add(item);

		item = new JMenuItem("System Properties");
		item.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JOptionPane.showMessageDialog(null, "OS: " + os + "\nArch: "
						+ arch);
			}
		});
		item.setEnabled(true);
		menu.add(item);

		menuBar.add(menu);
		// <<< <<< HELP MENU >>> >>> //
	}

	private void getSystemProperties() {

		String osString = System.getProperties().getProperty("os.name");
		if (osString.matches(".*[M|m][A|a][C|c].*")) {
			os = OS.MAC;
			System.out.println("OS Identified as: MAC");
		} else if (osString.matches(".*[W|w][I|i][N|n][D|d][O|o][W|w][S|s].*")) {
			os = OS.WINDOWS;
			System.out.println("OS Identified as: WINDOWS");
		} else {
			os = OS.LINUX;
			System.out.println("OS Identified as: LINUX");
		}

		String archString = System.getProperties().getProperty("os.arch");
		if (Pattern.matches(".*64.*", archString)) {
			System.out.println("Architecture Identified as: 64-bit");
			arch = ARCH.BIT64;
		} else {
			System.out.println("Architecture Identified as: 32-bit");
			arch = ARCH.BIT32;
		}
	}

	protected void updateMenuBar() {
		JMenu submenu = null;

		// << MODEL MODE ADJUSTMENTS >>
		submenu = menuBar.getMenu(MENU_FILE);
		submenu.getItem(SAVE_MODEL).setEnabled(curMode == MODE.EDIT);
		submenu.getItem(SAVE_MODEL_AS).setEnabled(curMode == MODE.EDIT);
		menuBar.getMenu(MENU_MODEL).setEnabled(curMode == MODE.EDIT);

		// << TRAJT MODE ADJUSTMENTS >>
		submenu = menuBar.getMenu(MENU_FILE);
		submenu.getItem(SAVE_EXP).setEnabled(curMode == MODE.TRAJ);
		submenu.getItem(SAVE_EXP_AS).setEnabled(curMode == MODE.TRAJ);
		menuBar.getMenu(MENU_EXP).setEnabled(curMode == MODE.TRAJ);

		// << RESULTS MODE ADJUSTMENTS >>

	}

	protected void switchToResultsPanel(File resultsFile) {

		resultPanel.update();
		layeredPanes.remove(curPanel);
		if (resultsFile != null) {
			resultPanel.newFile(resultsFile.getPath());
		}
		layeredPanes.add(resultPanel, new Integer(1));
		lastResultsWereMulti = false;
		curMode = MODE.RESULTS;
		curPanel = resultPanel;
		updateMenuBar();

		mainPane.transferFocus();
	}

	protected void switchToTrajPanel(boolean switchingWithFile) {
		layeredPanes.remove(curPanel);
		layeredPanes.add(trajPanel, new Integer(1));
		curMode = MODE.TRAJ;
		curPanel = trajPanel;
		trajPanel.update();
		mainPane.transferFocus();
	}

	protected void switchToEditPanel() {
		editPanel.update();
		layeredPanes.remove(curPanel);
		layeredPanes.add(editPanel, new Integer(1));
		curMode = MODE.EDIT;
		curPanel = editPanel;
		mainPane.transferFocus();
	}

	protected void saveCurrent() {
		if (curMode == MODE.TRAJ) {
			trajPanel.getTrajectory().saveTraj(true);
		} else if (curMode == MODE.EDIT
				&& !model.getModelName().equals("Unsaved")) {
			model.saveModel();
		} else if (curMode == MODE.RESULTS) {
			resultPanel.saveNotes();
		}

	}

	protected void removeNameDisplayPanel() {
		if (curPanel.nameDisplayPanel != null) {
			layeredPanes.remove(curPanel.nameDisplayPanel);
		}
	}

	protected void newFile(File newFile) {
		removeNameDisplayPanel();
		if (curMode == MODE.RESULTS) {
			resultPanel.closeBars();
			resultPanel.closeLines();
			resultPanel.saveNotes();
		} else if (curMode == MODE.TRAJ) {
			trajPanel.getTrajectory().saveTraj(true);
		} else if (curMode == MODE.EDIT
				&& !model.getModelName().equals("Unsaved")) {
		}

		if (newFile.getName().endsWith(".res")
				|| newFile.getName().endsWith(".randres")) {
			if (curMode != MODE.RESULTS) {
				switchToResultsPanel(newFile);
			} else {
				resultPanel.newFile(newFile.getPath());
			}
		} else if (newFile.getParentFile().getName().equals("Experiments")) {
			trajPanel.trajectory.loadTraj(newFile);
			trajPanel.update();
			if (curMode != MODE.TRAJ) {
				switchToTrajPanel(true);
			}
		} else {
			model.loadModel(newFile.getName());
			resetZoom();
			if (curMode != MODE.EDIT) {
				switchToEditPanel();
			}
			editPanel.update();
		}
		model.resize();
		mainPane.transferFocus();
		updateMenuBar();
	}

	protected void resetZoom() {
		viewport.update(
				new Rectangle(
						(model.getTotalSize().width - layeredPanes.getSize().width) / 2,
						(model.getTotalSize().height - layeredPanes.getSize().height) / 2,
						VIEW_SIZE.width, VIEW_SIZE.height), layeredPanes
						.getSize());
	}

	protected void updateNotesPane(String text) {
		System.out.println("Discarding: " + textPanel.getText());
		System.out.println("SETTING: " + text);
		textPanel.setText(text);
	}

	protected void updatePanels() {
		resultPanel.update();
		trajPanel.update();
		editPanel.update();
	}

	protected void updateBrowser() {
		fileBrowser.update();
		// splitPane.add(fileBrowser, 0);
		mainPane.setLeftComponent(sidePane);
	}

	protected void setNewResult(String modelname, String filename,
			boolean newResult) {
		FileNode modelNode = ((FileNode) fileBrowser
				.getModelNodeByName(modelname));
		fileBrowser.getNodeByName(filename, modelNode).setNewResult(newResult);
	}

	protected void importModel(File importFile) {
		File file = importFile;
		if (file == null || !file.canRead()) {
			JFileChooser jfc;
			jfc = new JFileChooser(new File("Models/"));
			jfc.setDialogTitle("Select a model file");
			jfc.showOpenDialog(null);
			file = jfc.getSelectedFile();
		}
		if (file != null) {
			Model tempModel = new Model(this, viewport);
			Thread newParseThread = new Thread(new Parser(tempModel, file));
			newParseThread.start();
		} else {
			System.out.println("No file selected to import!");
		}
	}

	protected void saveModelAs(String newModelName) {
		File oldExperiments = new File("Models/" + model.getModelName()
				+ "/Experiments");
		model.setModelName(newModelName);
		model.saveModel();
		File newModel = new File("Models/" + newModelName);
		newFile(newModel);
		try {
			fileBrowser.copyExperiments(oldExperiments, newModel);
		} catch (IOException e) {
			e.printStackTrace();
		}
		fileBrowser.update();

	}

	protected void saveExpAs(String newExpName) {
		if (trajPanel.getTrajectory() != null) {
			trajPanel.getTrajectory().setTrajName(newExpName);
			trajPanel.getTrajectory().saveTraj(true);
			newFile(new File("Models/" + model.getModelName() + "/Experiments/"
					+ newExpName + ".exp"));
			fileBrowser.update();
		}
	}

	private class ComponentHandler implements ComponentListener {

		@Override
		public void componentHidden(ComponentEvent e) {
			// TODO Auto-generated method stub

		}

		@Override
		public void componentMoved(ComponentEvent e) {
			// TODO Auto-generated method stub

		}

		@Override
		public void componentResized(ComponentEvent e) {
			viewport.update(layeredPanes.getSize());
			model.resize();
			mainPane.setSize(getSize());
			mainPane.updateUI();
			editPanel.setSize(getSize());
			editPanel.resize();
			trajPanel.setSize(getSize());
			trajPanel.resize();
			resultPanel.setSize(getSize());
			resultPanel.resize();

		}

		@Override
		public void componentShown(ComponentEvent e) {
			// TODO Auto-generated method stub

		}
	}

	/**
	 * PRE: frame is defined POST: RV = frame
	 */
	public MainFrame getFrame() {
		return frame;
	}

	/**
	 * PRE: frame is defined POST: frame = frame
	 */
	public void setFrame(MainFrame frame) {
		this.frame = frame;
	}

	protected void startLoading() {
		loadingPanel = new LoadingPanel(layeredPanes);
		layeredPanes.add(loadingPanel, new Integer(3));
	}

	protected void updateLoading() {
		loadingPanel.status = LoadingPanel.STATUS.values()[loadingPanel.status
				.ordinal() + 1];
	}

	protected void doneLoading() {
		layeredPanes.remove(loadingPanel);
		loadingPanel = null;
	}
}
