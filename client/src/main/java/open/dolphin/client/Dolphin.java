package open.dolphin.client;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import open.dolphin.common.util.StampRenderingHints;
import open.dolphin.dao.SqlDaoBean;
import open.dolphin.delegater.PatientDelegater;
import open.dolphin.delegater.StampDelegater;
import open.dolphin.delegater.UserDelegater;
import open.dolphin.helper.ComponentMemory;
import open.dolphin.helper.MenuSupport;
import open.dolphin.helper.ProgressMonitorWorker;
import open.dolphin.helper.WindowSupport;
import open.dolphin.impl.login.LoginDialog;
import open.dolphin.impl.rezept.RezeptViewer;
//import open.dolphin.impl.server.StandAlonePVTServer;
import open.dolphin.impl.tempkarte.TempKarteCheckDialog;
import open.dolphin.infomodel.ChartEventModel;
import open.dolphin.infomodel.FacilityModel;
import open.dolphin.infomodel.IStampTreeModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.PatientVisitModel;
import open.dolphin.infomodel.RoleModel;
import open.dolphin.infomodel.StampTreeModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.plugin.PluginLoader;
import open.dolphin.project.AbstractProjectFactory;
import open.dolphin.project.Project;
import open.dolphin.project.ProjectStub;
//import open.dolphin.server.PVTServer;
import open.dolphin.setting.MiscSettingPanel;
import open.dolphin.setting.ProjectSettingDialog;
import open.dolphin.stampbox.StampBoxPlugin;

/**
 * アプリケーションのメインウインドウクラス。
 *
 * @author Kazushi Minagawa, Digital Globe, Inc.
 */
public class Dolphin implements MainWindow, IChartEventListener {

    // Window と Menu サポート
    private WindowSupport windowSupport;

    // Mediator
    private Mediator mediator;

    // 状態制御
    private StateManager stateMgr;

    // プラグインのプロバイダ
    private HashMap<String, MainService> providers;

    // プリンターセットアップはMainWindowのみで行い、設定された PageFormat各プラグインが使用する
    private PageFormat pageFormat;

    // 環境設定用の Properties
    private Properties saveEnv;

    // BlockGlass
    private BlockGlass blockGlass;

    // StampBox
    private StampBoxPlugin stampBox;

    // 受付受信サーバ
    //private PVTServer pvtServer;

    // CLAIM リスナ
    private ClaimMessageListener sendClaim;

    // MML リスナ
    private MmlMessageListener sendMml;

    private final int maxEstimation = 120*1000; // 120 秒
    private final int delay = 300;      // 300 mmsec

    // VIEW
    private MainView view;

//masuda^
    // SchemaBox
    private ImageBox imageBox;
    // PacsService
    private PacsService pacsService;

    // 状態変化リスナー
    private ChartEventListener cel;
    
    // clientのUUID
    private String clientUUID;

    public String getClientUUID() {
        return clientUUID;
    }
    
    // ChartMediatorを経由せずに、Dolphin.getInstance().getStampBox()でスタンプ箱を取得できるようにする。
    // AbstractCodeHelper, SOA/PCodeHelperから使用
    public StampBoxPlugin getStampBox() {
        return stampBox;
    }

    // Dolphinをstatic instanceにする
    private static Dolphin instance;
    
    public static Dolphin getInstance() {
        return instance;
    }
    
    // 共通のExecutorService
    private final ExecutorService exec;
    
    public ExecutorService getExecutorService() {
        return exec;
    }
//masuda$
    
    /**
     * Creates new MainWindow
     */
    public Dolphin() {
        exec = Executors.newFixedThreadPool(2);
    }

    public void start(boolean pro) {

        // Mac Application Menu
        if (ClientContext.isMac()){
            enableMacApplicationMenu();
        }

        // 排他処理用のUUIDを決める
        clientUUID = UUID.randomUUID().toString();

        //------------------------------
        // ログインダイアログを表示する
        //------------------------------
        PluginLoader<ILoginDialog> loader = PluginLoader.load(ILoginDialog.class);
        Iterator<ILoginDialog> iter = loader.iterator();
        final ILoginDialog login = iter.next();
        login.addPropertyChangeListener(LoginDialog.LOGIN_PROP, new PropertyChangeListener() {
           
            @Override
            public void propertyChange(PropertyChangeEvent e) {

                LoginDialog.LoginStatus result = (LoginDialog.LoginStatus) e.getNewValue();
                login.close();

                switch (result) {
                    case AUTHENTICATED:
                        registerLogin();
                        startServices();
                        loadStampTree();
                        break;
                    case NOT_AUTHENTICATED:
                        shutdown();
                        break;
                    case CANCELD:
                        shutdown();
                        break;
                }
            }
            
        });
        login.start();
    }
    
    // 排他処理、another implementation
    private void registerLogin() {
        try {
            UserModel user = Project.getUserModel();
            String fidUid = user.getUserId();
            String uuid = UserDelegater.getInstance().login(fidUid, clientUUID, false);
            if (!clientUUID.equals(uuid)) {
                // ダイアログで確認する
                String[] options = {"ログアウト", "ならない", "プライマリになる"};
                String msg = "他端末で同一ユーザーがログイン中です。\n"
                        + "スタンプ箱の編集はできません。\n"
                        + "不整合を避けるため同時ログインはお勧めしません。";
                int val = JOptionPane.showOptionDialog(
                        null, msg, "警告：同時ログイン",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[0]);
                switch (val) {
                    case 1:     // プライマリにならない
                        break;
                    case 2:     // プライマリになる
                        UserDelegater.getInstance().login(fidUid, clientUUID, true);
                        user.setClientUUID(clientUUID);
                        break;
                    default:    // ログアウト
                        System.exit(0);
                        break;
                }
            } else {
                user.setClientUUID(clientUUID);
            }
        } catch (Exception ex) {
        }
    }

    /**
     * 起動時のバックグラウンドで実行されるべきタスクを行う。
     */
    private void startServices() {

        // プラグインのプロバイダマップを生成する
        setProviders(new HashMap<String, MainService>());

        // 環境設定ダイアログで変更される場合があるので保存する
        saveEnv = new Properties();

//        // PVT Sever を起動する
//        if (Project.getBoolean(Project.USE_AS_PVT_SERVER)) {
//            startPvtServer();
//
//        } else {
//            saveEnv.put(GUIConst.KEY_PVT_SERVER, GUIConst.SERVICE_NOT_RUNNING);
//        }

        // CLAIM送信を生成する
        if (Project.getBoolean(Project.SEND_CLAIM)) {
            startSendClaim();

        } else {
            saveEnv.put(GUIConst.KEY_SEND_CLAIM, GUIConst.SERVICE_NOT_RUNNING);
        }
        if (Project.getString(Project.CLAIM_ADDRESS) != null) {
            saveEnv.put(GUIConst.ADDRESS_CLAIM, Project.getString(Project.CLAIM_ADDRESS));
        }

        // MML送信を生成する
        if (Project.getBoolean(Project.SEND_MML)) {
            startSendMml();

        } else {
            saveEnv.put(GUIConst.KEY_SEND_MML, GUIConst.SERVICE_NOT_RUNNING);
        }
        if (Project.getCSGWPath() != null) {
            saveEnv.put(GUIConst.CSGW_PATH, Project.getCSGWPath());
        }
        
//masuda^   PACS service
        boolean usePacs = Project.getBoolean(MiscSettingPanel.USE_PACS, MiscSettingPanel.DEFAULT_USE_PACS);
        if (usePacs) {
            startPacsService();
        } else {
            saveEnv.put(GUIConst.KEY_PACS_SERVICE, GUIConst.SERVICE_NOT_RUNNING);
        }

        String remoteHost = Project.getString(MiscSettingPanel.PACS_REMOTE_HOST, MiscSettingPanel.DEFAULT_PACS_REMOTE_HOST);
        if (remoteHost != null) {
            saveEnv.put(GUIConst.KEY_PACS_SETTING, getPacsSettingString());
        }
//masuda$
        
        //ClientContext.getBootLogger().debug("services did start");
    }

    /**
     * ユーザーのStampTreeをロードする。
     */
    private void loadStampTree() {
        
        String message = "初期化";
        String note = "スタンプを読み込んでいます...";
        Component c = null;
        
        ProgressMonitorWorker worker = new ProgressMonitorWorker<List<IStampTreeModel>, Void>(
                c, message, note, maxEstimation, delay) {

            @Override
            protected List<IStampTreeModel> doInBackground() throws Exception {

                // ログインユーザーの PK
                long userPk = Project.getUserModel().getId();

                // ユーザーのStampTreeを検索する
                StampDelegater stampDel = StampDelegater.getInstance();

                List<IStampTreeModel> treeList = stampDel.getTrees(userPk);

                // User用のStampTreeが存在しない新規ユーザの場合、そのTreeを生成する
                boolean hasTree = false;
                if (treeList != null && !treeList.isEmpty()) {
                    for (IStampTreeModel tree : treeList) {
                        if (tree != null) {
                            long id = tree.getUserModel().getId();
                            if (id == userPk && tree instanceof open.dolphin.infomodel.StampTreeModel) { // 注意
                                hasTree = true;
                                break;
                            }
                        }
                    }
                }

                // 新規ユーザでデータベースに個人用のStampTreeが存在しなかった場合
                if (!hasTree && treeList != null) {
                    ClientContext.getBootLogger().debug("新規ユーザー、スタンプツリーをリソースから構築");
                    
                    try (InputStream in = ClientContext.getResourceAsStream("stamptree-seed.xml");
                            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                        
                        String line;
                        StringBuilder sb = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        String treeXml = sb.toString();

                        // Tree情報を設定し保存する
                        IStampTreeModel tm = new StampTreeModel();       // 注意
                        tm.setUserModel(Project.getUserModel());
                        tm.setName(ClientContext.getString("stampTree.personal.box.name"));
                        tm.setDescription(ClientContext.getString("stampTree.personal.box.tooltip"));
                        FacilityModel facility = Project.getUserModel().getFacilityModel();
                        tm.setPartyName(facility.getFacilityName());
                        String url = facility.getUrl();
                        if (url != null) {
                            tm.setUrl(url);
                        }
                        tm.setTreeXml(treeXml);
                        in.close();
                        reader.close();

                        // 一度登録する
                        long treePk = stampDel.putTree(tm);

                        tm.setId(treePk);

                        // リストの先頭へ追加する
                        treeList.add(0, tm);
                    }
                }

                return treeList;
            }

            @Override
            protected void succeeded(final List<IStampTreeModel> result) {
                initComponents(result);
            }

            @Override
            protected void failed(Throwable e) {
                String fatalMsg = e.getMessage();
                fatalMsg = fatalMsg!=null ? fatalMsg : "初期化に失敗しました。";
                ClientContext.getBootLogger().fatal(fatalMsg);
                ClientContext.getBootLogger().fatal(e.getMessage());
                JOptionPane.showMessageDialog(null, fatalMsg, ClientContext.getFrameTitle("初期化"), JOptionPane.WARNING_MESSAGE);
                System.exit(1);
            }

            @Override
            protected void cancelled() {
                ClientContext.getBootLogger().debug("cancelled");
                System.exit(0);
            }
        };

        worker.execute();
    }

    /**
     * GUIを初期化する。
     */
    private void initComponents(List<IStampTreeModel> result) {

        // /open/dolphin/client/resources/Dolphin.properties
        ResourceBundle resource = ClientContext.getBundle(this.getClass());

        // 設定に必要な定数をコンテキストから取得する
        String windowTitle = resource.getString("title");
        Rectangle setBounds = new Rectangle(0, 0, 1000, 690);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int defaultX = (screenSize.width - setBounds.width) / 2;
        int defaultY = (screenSize.height - setBounds.height) / 2;
        int defaultWidth = 666;
        int defaultHeight = 678;

        // WindowSupport を生成する この時点で Frame,WindowMenu を持つMenuBar が生成されている
        String title = ClientContext.getFrameTitle(windowTitle);
        windowSupport = WindowSupport.create(title);
        JFrame myFrame = windowSupport.getFrame();		// MainWindow の JFrame
        JMenuBar myMenuBar = windowSupport.getMenuBar();	// MainWindow の JMenuBar

        // Windowにこのクラス固有の設定をする
        Point loc = new Point(defaultX, defaultY);
        Dimension size = new Dimension(defaultWidth, defaultHeight);
        myFrame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                processExit(false);
            }
        });
        ComponentMemory cm = new ComponentMemory(myFrame, loc, size, Dolphin.this);
        cm.setToPreferenceBounds();

        // BlockGlass を設定する
        blockGlass = new BlockGlass();
        myFrame.setGlassPane(blockGlass);

        // mainWindowのメニューを生成しメニューバーに追加する
        mediator = new Mediator(this);
        AbstractMenuFactory appMenu = AbstractMenuFactory.getFactory();
        appMenu.setMenuSupports(mediator, null);
        appMenu.build(myMenuBar);
        mediator.registerActions(appMenu.getActionMap());
        
//masuda^   windowSupportにmediatorを登録する
        windowSupport.setMediator(mediator);
//masuda$

        // mainWindowのコンテントを生成しFrameに追加する
        StringBuilder sb = new StringBuilder();
        sb.append("ログイン ");
        sb.append(Project.getUserModel().getCommonName());
        sb.append("  ");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d(EEE) HH:mm");
        sb.append(sdf.format(new Date()));
        String loginInfo = sb.toString();
        view = new MainView();
        view.getDateLbl().setText(loginInfo);
        view.setOpaque(true);
        myFrame.setContentPane(view);

//masuda^
        // FocusPropertyChangeListenerを登録する
        FocusPropertyChangeListener.getInstance().register();

        // ChartStateListenerを開始する
        cel = ChartEventListener.getInstance();
        cel.start();
        cel.addListener(this);
        
        // StampRenderingHintsの初期設置をする
        int cellPadding = Project.getInt("stampHolderCellPadding", 0);
        StampRenderingHints.getInstance().setCellPadding(cellPadding);
        boolean laboFold = Project.getBoolean("laboFold", true);
        StampRenderingHints.getInstance().setLaboFold(laboFold);
//masuda$
        
        //----------------------------------------
        // タブペインに格納する Plugin をロードする
        //----------------------------------------
        List<MainComponent> list = new ArrayList<>(4);
        PluginLoader<MainComponent> loader = PluginLoader.load(MainComponent.class);
        Iterator<MainComponent> iter = loader.iterator();

        // mainWindow のタブに、受付リスト、患者検索 ... の純に格納する
        while (iter.hasNext()) {
            MainComponent plugin = iter.next();
            list.add(plugin);
        }
        ClientContext.getBootLogger().debug("main window plugin did load");

        // プラグインプロバイダに格納する
        // index=0 のプラグイン（受付リスト）は起動する
        int index = 0;
        for (MainComponent plugin : list) {

            if (index == 0) {
                plugin.setContext(this);
                plugin.start();
                getTabbedPane().addTab(plugin.getName(), plugin.getUI());
                providers.put(String.valueOf(index), plugin);
                mediator.addChain(plugin);

            } else {
                getTabbedPane().addTab(plugin.getName(), plugin.getUI());
                providers.put(String.valueOf(index), plugin);
            }

            index++;
        }
        list.clear();

        //-------------------------------------------
        // タブの切り替えで plugin.enter() をコールする
        //-------------------------------------------
        getTabbedPane().addChangeListener(new ChangeListener() {

            @Override
            public void stateChanged(ChangeEvent e) {
                getStatusLabel().setText("");
                int index = getTabbedPane().getSelectedIndex();
                MainComponent plugin = (MainComponent) providers.get(String.valueOf(index));
                if (plugin.getContext() == null) {
                    plugin.setContext(Dolphin.this);
                    plugin.start();
                    getTabbedPane().setComponentAt(index, plugin.getUI());
                } else {
                    plugin.enter();
                }
                mediator.addChain(plugin);
            }
        });

        // StaeMagrを使用してメインウインドウの状態を制御する
        stateMgr = new StateManager();
        stateMgr.processLogin(true);

        // ログインユーザーの StampTree を読み込む
        stampBox = new StampBoxPlugin();
        stampBox.setContext(Dolphin.this);
        stampBox.setStampTreeModels(result);
        stampBox.start();
        stampBox.getFrame().setVisible(true);
        providers.put("stampBox", stampBox);
        
        windowSupport.getFrame().setVisible(true);
        
//masuda^   仮保存カルテチェック
        TempKarteCheckDialog tempKarte = new TempKarteCheckDialog();
        if (tempKarte.isTempKarteExists()) {
            Toolkit.getDefaultToolkit().beep();
            tempKarte.setLocationRelativeTo(null);
            tempKarte.setVisible(true);
        } else {
            tempKarte.dispose();
        }
//masuda$
    }

    @Override
    public JLabel getStatusLabel() {
        return view.getStatusLbl();
    }

    @Override
    public JProgressBar getProgressBar() {
        return view.getProgressBar();
    }

    @Override
    public JLabel getDateLabel() {
        return view.getDateLbl();
    }

    @Override
    public JTabbedPane getTabbedPane() {
        return view.getTabbedPane();
    }

    @Override
    public Component getCurrentComponent() {
        return getTabbedPane().getSelectedComponent();
    }

    @Override
    public BlockGlass getGlassPane() {
        return blockGlass;
    }

    @Override
    public MainService getPlugin(String id) {
        return providers.get(id);
    }

    @Override
    public HashMap<String, MainService> getProviders() {
        return providers;
    }

    @Override
    public void setProviders(HashMap<String, MainService> providers) {
        this.providers = providers;
    }

    /**
     * カルテをオープンする。
     * @param pvt 患者来院情報
     */
    @Override
    public void openKarte(PatientVisitModel pvt) {
        
//masuda^   すでにChart, EditorFrameが開いていた時の処理はここで行う
        if (pvt == null) {
            return;
        }
        if (pvt.getStateBit(PatientVisitModel.BIT_CANCEL)) {
            return;
        }
        
        // このクライアントでChartImplとEditorFrameを開いていた場合の処理
        PatientModel pm = pvt.getPatientModel();
        if (pm == null) {
            return;
        }
        boolean opened = false;
        long ptId = pm.getId();
        for (ChartImpl chart : WindowSupport.getAllCharts()) {
            if (chart.getPatient().getId() == ptId) {
                chart.getFrame().setExtendedState(java.awt.Frame.NORMAL);
                chart.getFrame().toFront();
                opened = true;
                break;
            }
        }

        for (EditorFrame ef : WindowSupport.getAllEditorFrames()) {
            if (ef.getPatient().getId() == ptId) {
                ef.getFrame().setExtendedState(java.awt.Frame.NORMAL);
                ef.getFrame().toFront();
                break;
            }
        }
        if (opened) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        // まだ開いていない場合
        boolean readOnly = Project.isReadOnly();
        if (pm.getOwnerUUID() != null) {
            // ダイアログで確認する
            String ptName = pvt.getPatientName();
            String[] options = {"閲覧のみ", "強制的に編集", "キャンセル"};
            String msg = ptName + " 様のカルテは他の端末で編集中です。\n" +
                    "強制的に編集した場合、後から保存したカルテが最新カルテになります。";
            int val = JOptionPane.showOptionDialog(
                    getFrame(), msg, "カルテオープン",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            switch (val) {
                case 0:     // 閲覧のみは編集不可で所有権を設定しない
                    readOnly = true;
                    break;
                case 1:     // 強制的に編集するときは所有権横取り
                    pm.setOwnerUUID(clientUUID);
                    break;
                case 2:     // キャンセル
                case JOptionPane.CLOSED_OPTION:
                    return;
            }
        } else {
            // 誰も開いていないときは自分が所有者
            pvt.getPatientModel().setOwnerUUID(clientUUID);
        }
        
        // カルテオープン時にpvtHealthInsuranceが設定されてなかったら取得しに行く
        if (pm.getPvtHealthInsurances() == null) {
            try {
                PatientDelegater.getInstance().updateHealthInsurances(pm);
            } catch (Exception ex) {
            }
        }

        Chart chart = new ChartImpl();
        chart.setContext(this);
        chart.setPatientVisit(pvt);
        chart.setReadOnly(readOnly);
        chart.start();
        
        // ChartImplに移動
        // publish state
        //scl.publishKarteOpened(pvt);
//masuda$        
    }

    /**
     * 新規診療録を作成する。
     */
    @Override
    public void addNewPatient() {

        PluginLoader<NewKarte> loader = PluginLoader.load(NewKarte.class);
        Iterator<NewKarte> iter = loader.iterator();
        if (iter.hasNext()) {
            NewKarte newKarte = iter.next();
            newKarte.setContext(this);
            newKarte.start();
        }
    }

    @Override
    public MenuSupport getMenuSupport() {
        return mediator;
    }

    /**
     * MainWindow のアクションを返す。
     * @param name Action名
     * @return Action
     */
    @Override
    public Action getAction(String name) {
        return mediator.getAction(name);
    }

    @Override
    public JMenuBar getMenuBar() {
        return windowSupport.getMenuBar();
    }

    @Override
    public void registerActions(ActionMap actions) {
        mediator.registerActions(actions);
    }

    @Override
    public void enabledAction(String name, boolean b) {
        mediator.enabledAction(name, b);
    }

    public JFrame getFrame() {
        return windowSupport.getFrame();
    }

    @Override
    public PageFormat getPageFormat() {
        if (pageFormat == null) {
            PrinterJob printJob = PrinterJob.getPrinterJob();
            if (printJob != null) {
                pageFormat = printJob.defaultPage();
            }
        }
        return pageFormat;
    }

    /**
     * ブロックする。
     */
    @Override
    public void block() {
        blockGlass.block();
    }

    /**
     * ブロックを解除する。
     */
    @Override
    public void unblock() {
        blockGlass.unblock();
    }

    /**
     * PVTServer を開始する。
     */
//    private void startPvtServer() {
//        PluginLoader<PVTServer> loader = PluginLoader.load(PVTServer.class);
//        Iterator<PVTServer> iter = loader.iterator();
//        if (iter.hasNext()) {
//            pvtServer = iter.next();
//            pvtServer.setContext(this);
//            pvtServer.setBindAddress(Project.getString(Project.CLAIM_BIND_ADDRESS));
//            pvtServer.start();
//            providers.put("pvtServer", pvtServer);
//            saveEnv.put(GUIConst.KEY_PVT_SERVER, GUIConst.SERVICE_RUNNING);
//            ClientContext.getBootLogger().debug("pvtServer did  start");
//        }
//    }

    /**
     * CLAIM 送信を開始する。
     */
    private void startSendClaim() {

        PluginLoader<ClaimMessageListener> loader = PluginLoader.load(ClaimMessageListener.class);
        Iterator<ClaimMessageListener> iter = loader.iterator();
        if (iter.hasNext()) {
            sendClaim = iter.next();
            sendClaim.setContext(this);
            sendClaim.start();
            providers.put("sendClaim", sendClaim);
            saveEnv.put(GUIConst.KEY_SEND_CLAIM, GUIConst.SERVICE_RUNNING);
            ClientContext.getBootLogger().debug("sendClaim did start");
        }
    }

    /**
     * MML送信を開始する。
     */
    private void startSendMml() {
        PluginLoader<MmlMessageListener> loader = PluginLoader.load(MmlMessageListener.class);
        Iterator<MmlMessageListener> iter = loader.iterator();
        if (iter.hasNext()) {
            sendMml = iter.next();
            sendMml.setContext(this);
            sendMml.start();
            providers.put("sendMml", sendMml);
            saveEnv.put(GUIConst.KEY_SEND_MML, GUIConst.SERVICE_RUNNING);
            ClientContext.getBootLogger().debug("sendMml did  start");
        }
    }

    /**
     * プリンターをセットアップする。
     */
    public void printerSetup() {
//masuda^
        SwingWorker worker = new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                
                PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
                PrinterJob pj = PrinterJob.getPrinterJob();

                try {
                    pageFormat = pj.pageDialog(aset);
                } catch (HeadlessException e) {
                    e.printStackTrace(System.err);
                }
                return null;
            }
            
        };
        worker.execute();
//masuda$
    }

    /**
     * カルテの環境設定を行う。
     */
    public void setKarteEnviroment() {
        ProjectSettingDialog sd = new ProjectSettingDialog();
        sd.addPropertyChangeListener("SETTING_PROP", new PreferenceListener());
        sd.setLoginState(stateMgr.isLogin());
        sd.setProject("karteSetting");
        sd.start();
    }

    /**
     * 環境設定を行う。
     */
    public void doPreference() {
        ProjectSettingDialog sd = new ProjectSettingDialog();
        sd.addPropertyChangeListener("SETTING_PROP", new PreferenceListener());
        sd.setLoginState(stateMgr.isLogin());
        sd.setProject(null);
        sd.start();
    }

    /**
     * 環境設定のリスナクラス。環境設定が終了するとここへ通知される。
     */
    class PreferenceListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent e) {

            if ("SETTING_PROP".equals(e.getPropertyName())) {

                boolean valid = ((Boolean) e.getNewValue()).booleanValue();

                if (valid) {

                    // 設定の変化を調べ、サービスの制御を行う
                    List<String> messages = new ArrayList<>();

//                    // PvtServer
//                    boolean oldRunning = saveEnv.getProperty(GUIConst.KEY_PVT_SERVER).equals(GUIConst.SERVICE_RUNNING);
//                    boolean newRun = Project.getBoolean(Project.USE_AS_PVT_SERVER);
//                    boolean start = (!oldRunning && newRun);
//                    boolean stop = (oldRunning && !newRun);
//
//                    if (start) {
//                        startPvtServer();
//                        messages.add("受付受信を開始しました。");
//                    } else if (stop && pvtServer != null) {
//                        pvtServer.stop();
//                        pvtServer = null;
//                        saveEnv.put(GUIConst.KEY_PVT_SERVER, GUIConst.SERVICE_NOT_RUNNING);
//                        messages.add("受付受信を停止しました。");
//                    }

                    // SendClaim
                    boolean oldRunning = saveEnv.getProperty(GUIConst.KEY_SEND_CLAIM).equals(GUIConst.SERVICE_RUNNING);
                    boolean newRun = Project.getBoolean(Project.SEND_CLAIM);
                    boolean start = (!oldRunning && newRun);
                    boolean stop = (oldRunning && !newRun);

                    boolean restart = false;
                    String oldAddress = saveEnv.getProperty(GUIConst.ADDRESS_CLAIM);
                    String newAddress = Project.getString(Project.CLAIM_ADDRESS);
                    if (oldAddress != null && newAddress != null && (!oldAddress.equals(newAddress)) && newRun) {
                        restart = true;
                    }

                    if (start) {
                        startSendClaim();
                        saveEnv.put(GUIConst.ADDRESS_CLAIM, newAddress);
                        messages.add("CLAIM送信を開始しました。(送信アドレス=" + newAddress + ")");

                    } else if (stop && sendClaim != null) {
                        sendClaim.stop();
                        sendClaim = null;
                        saveEnv.put(GUIConst.KEY_SEND_CLAIM, GUIConst.SERVICE_NOT_RUNNING);
                        saveEnv.put(GUIConst.ADDRESS_CLAIM, newAddress);
                        messages.add("CLAIM送信を停止しました。");

                    } else if (restart) {
                        sendClaim.stop();
                        sendClaim = null;
                        startSendClaim();
                        saveEnv.put(GUIConst.ADDRESS_CLAIM, newAddress);
                        messages.add("CLAIM送信をリスタートしました。(送信アドレス=" + newAddress + ")");
                    }

                    // SendMML
                    oldRunning = saveEnv.getProperty(GUIConst.KEY_SEND_MML).equals(GUIConst.SERVICE_RUNNING);
                    newRun = Project.getBoolean(Project.SEND_MML);
                    start = (!oldRunning && newRun);
                    stop = (oldRunning && !newRun);

                    restart = false;
                    oldAddress = saveEnv.getProperty(GUIConst.CSGW_PATH);
                    newAddress = Project.getCSGWPath();
                    if (oldAddress != null && newAddress != null && (!oldAddress.equals(newAddress)) && newRun) {
                        restart = true;
                    }

                    if (start) {
                        startSendMml();
                        saveEnv.put(GUIConst.CSGW_PATH, newAddress);
                        messages.add("MML送信を開始しました。(送信アドレス=" + newAddress + ")");

                    } else if (stop && sendMml != null) {
                        sendMml.stop();
                        sendMml = null;
                        saveEnv.put(GUIConst.KEY_SEND_MML, GUIConst.SERVICE_NOT_RUNNING);
                        saveEnv.put(GUIConst.CSGW_PATH, newAddress);
                        messages.add("MML送信を停止しました。");

                    } else if (restart) {
                        sendMml.stop();
                        sendMml = null;
                        startSendMml();
                        saveEnv.put(GUIConst.CSGW_PATH, newAddress);
                        messages.add("MML送信をリスタートしました。(送信アドレス=" + newAddress + ")");
                    }

//masuda^   PacsService
                    oldRunning = saveEnv.getProperty(GUIConst.KEY_PACS_SERVICE).equals(GUIConst.SERVICE_RUNNING);
                    newRun = Project.getBoolean(MiscSettingPanel.USE_PACS, MiscSettingPanel.DEFAULT_USE_PACS);
                    start = (!oldRunning && newRun);
                    stop = (oldRunning && !newRun);
                    restart = false;

                    String oldSetting = saveEnv.getProperty(GUIConst.KEY_PACS_SETTING);
                    String newSetting = getPacsSettingString();

                    if (oldSetting != null && newSetting != null && (!oldSetting.equals(newSetting)) && newRun) {
                        restart = true;
                    }

                    if (start) {
                        startPacsService();
                        saveEnv.put(GUIConst.KEY_PACS_SETTING, newSetting);
                        messages.add("PACS Searviceを開始しました。\n(" + newSetting + ")");

                    } else if (stop && pacsService != null) {
                        pacsService.stop();
                        pacsService = null;
                        saveEnv.put(GUIConst.KEY_PACS_SERVICE, GUIConst.SERVICE_NOT_RUNNING);
                        saveEnv.put(GUIConst.KEY_PACS_SETTING, newSetting);
                        messages.add("PACS Serviceを停止しました。");

                    } else if (restart) {
                        pacsService.stop();
                        pacsService = null;
                        startPacsService();
                        saveEnv.put(GUIConst.KEY_PACS_SETTING, newSetting);
                        messages.add("PACS Serviceをリスタートしました。\n(" + newSetting + ")");
                    }
//masuda$
                    
                    if (!messages.isEmpty()) {
                        String[] msgArray = messages.toArray(new String[messages.size()]);
                        Object msg = msgArray;
                        Component cmp = null;
                        String title = ClientContext.getString("settingDialog.title");

                        JOptionPane.showMessageDialog(
                                cmp,
                                msg,
                                ClientContext.getFrameTitle(title),
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        }
    }

    private boolean isDirty() {

        // 未保存のカルテがある場合は警告しリターンする
        // カルテを保存または破棄してから再度実行する
        boolean dirty = false;

        // Chart を調べる
        List<ChartImpl> allCharts = WindowSupport.getAllCharts();
        if (allCharts != null && !allCharts.isEmpty()) {
            for (ChartImpl chart : allCharts) {
                if (chart.isDirty()) {
                    dirty = true;
                    break;
                }
            }
        }

        // 保存してないものがあればリターンする
        if (dirty) {
            return true;
        }

        // EditorFrameのチェックを行う
        List<EditorFrame> allEditorFrames = WindowSupport.getAllEditorFrames();
        if (allEditorFrames != null && !allEditorFrames.isEmpty()) {
            for (Chart chart : allEditorFrames) {
                if (chart.isDirty()) {
                    dirty = true;
                    break;
                }
            }
        }

        return dirty;
    }
    
    // MenuFactoryからよばれる
    public void processExit() {
        processExit(false);
    }

    private void processExit(boolean force) {
        if (!force) {
            if (isDirty()) {
                alertDirty();
                return;
            }
//pns^
            int ans = JOptionPane.showConfirmDialog(null,
                    "本当に終了しますか",
                    "終了確認",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (ans != JOptionPane.YES_OPTION) {
                this.getFrame().toFront();
                return;
            }
//pns$
        }
//masuda^   終了時は強制的に開いたままのChartImplとEditorFrameを閉じちゃう
        for (Chart chart : WindowSupport.getAllEditorFrames()) {
            chart.stop();
        }
        for (ChartImpl chart : WindowSupport.getAllCharts()) {
            chart.stop();
        }

        // ログアウト処理
        saveStampTree();
//masuda$        
    }

    private void saveStampTree() {
        
        // Stamp 保存
        final IStampTreeModel treeTosave = stampBox.getUsersTreeTosave();
        
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        String message = resource.getString("exitDolphin.taskTitle");
        String note = resource.getString("exitDolphin.savingNote");
        Component c = getFrame();
        
        ProgressMonitorWorker worker = new ProgressMonitorWorker<Void, Void>(
                c, message, note, maxEstimation, delay){

            @Override
            protected Void doInBackground() throws Exception {
                
                ClientContext.getBootLogger().debug("stampTask doInBackground");
                // logout処理もここで行う
                String fidUid = Project.getUserModel().getUserId();
                String uuid = UserDelegater.getInstance().logout(fidUid, clientUUID);
                // Stamp 保存
                if (clientUUID.equals(uuid)) {
                    StampDelegater dl = StampDelegater.getInstance();
                    dl.putTree(treeTosave);
                }
                return null;
            }

            @Override
            protected void succeeded(Void result) {
                ClientContext.getBootLogger().debug("stampTask succeeded");
                shutdown();
            }

            @Override
            protected void failed(Throwable cause) {
                doStoppingAlert();
                ClientContext.getBootLogger().warn("stampTask failed");
                ClientContext.getBootLogger().warn(cause);
            }
        };

        worker.execute();
    }
    
    /**
     * 未保存のドキュメントがある場合の警告を表示する。
     */
    private void alertDirty() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        String msg0 = resource.getString("exitDolphin.msg0"); //"未保存のドキュメントがあります。";
        String msg1 = resource.getString("exitDolphin.msg1"); //"保存または破棄した後に再度実行してください。";
        String taskTitle = resource.getString("exitDolphin.taskTitle");
        JOptionPane.showMessageDialog(
                (Component) null,
                new Object[]{msg0, msg1},
                ClientContext.getFrameTitle(taskTitle),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 終了処理中にエラーが生じた場合の警告をダイアログを表示する。
     * @param errorTask エラーが生じたタスク
     * @return ユーザの選択値
     */
    private void doStoppingAlert() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        String msg1 = resource.getString("exitDolphin.err.msg1");
        String msg2 = resource.getString("exitDolphin.err.msg2");
        String msg3 = resource.getString("exitDolphin.err.msg3");
        String msg4 = resource.getString("exitDolphin.err.msg4");
        Object message = new Object[]{msg1, msg2, msg3, msg4};

        // 終了する
        String exitOption = resource.getString("exitDolphin.exitOption");

        // キャンセルする
        String cancelOption = resource.getString("exitDolphin.cancelOption");

        // 環境保存
        String taskTitle = resource.getString("exitDolphin.taskTitle");

        String title = ClientContext.getFrameTitle(taskTitle);

        String[] options = new String[]{cancelOption, exitOption};

        int option = JOptionPane.showOptionDialog(
                null, message, title,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);

        if (option == 1) {
            shutdown();
        }
    }

    private void shutdown() {

        if (providers != null) {

            try {
                Iterator iter = providers.values().iterator();
                while (iter != null && iter.hasNext()) {
                    MainService pl = (MainService) iter.next();
                    pl.stop();
                }

                //----------------------------------------
                // UserDefaults 保存 stop で保存するものあり
                //----------------------------------------
                Project.saveUserDefaults();

            } catch (Exception e) {
                e.printStackTrace(System.err);
                ClientContext.getBootLogger().warn(e.toString());
            }
        }

        if (windowSupport != null) {
            JFrame myFrame = windowSupport.getFrame();
            myFrame.setVisible(false);
            myFrame.dispose();
        }
        
        // ChartStateListenerを中止する
        if (cel != null) {
            cel.removeListener(this);
            cel.stop();
        }

        // FocusProperetyChangeListenerを破棄する
        FocusPropertyChangeListener.getInstance().dispose();
        
        // shutdown Executor
        exec.shutdownNow();
        
        // DAO DataSourceを閉じる
        SqlDaoBean.closeDao();
        
        ClientContext.getBootLogger().debug("アプリケーションを終了します");
        System.exit(0);
    }

    /**
     * ユーザのパスワードを変更する。
     */
    public void changePassword() {

        PluginLoader<ChangeProfile> loader = PluginLoader.load(ChangeProfile.class);
        Iterator<ChangeProfile> iter = loader.iterator();
        if (iter.hasNext()) {
            ChangeProfile cp = iter.next();
            cp.setContext(this);
            cp.start();
        }
    }

    /**
     * ユーザ登録を行う。管理者メニュー。
     */
    public void addUser() {

        PluginLoader<AddUser> loader = PluginLoader.load(AddUser.class);
        Iterator<AddUser> iter = loader.iterator();
        if (iter.hasNext()) {
            AddUser au = iter.next();
            au.setContext(this);
            au.start();
        }
    }

    /**
     * Pluginを起動する。
     * @param pluginClass 起動するプラグインクラス。
     */
    public void invokeToolPlugin(String pluginClass) {

        try {
            MainTool tool = (MainTool) Class.forName(pluginClass).newInstance();
            tool.setContext(this);
            tool.start();

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }


    /**
     * ドルフィンサポートをオープンする。
     */
    public void browseDolphinSupport() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        browseURL(resource.getString("menu.dolphinSupportUrl"));
    }

    /**
     * ドルフィンプロジェクトをオープンする。
     */
    public void browseDolphinProject() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        browseURL(resource.getString("menu.dolphinUrl"));
    }

    /**
     * MedXMLをオープンする。
     */
    public void browseMedXml() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        browseURL(resource.getString("menu.medXmlUrl"));
    }

    /**
     * SGをオープンする。
     */
    public void browseSeaGaia() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        browseURL(resource.getString("menu.seaGaiaUrl"));
    }
    
    public void browseGitHub() {
        ResourceBundle resource = ClientContext.getBundle(this.getClass());
        browseURL(resource.getString("menu.githubUrl"));
    }
    
    public void broadcastMsg() {
        ChartEventModel evt = new ChartEventModel();
        evt.setEventType(ChartEventModel.EVENT.MSG_BROADCAST);
        evt.setMsgUUID(UUID.randomUUID().toString());
        evt.setMsgTitle("ブロードキャスト　テスト");
        evt.setMsgOwner(getHostName());
        evt.setMsgContent("メンテナンスするぞ。早く仕事終えやがれ！");
        evt.setMsgOpts(new String[]{"わかった", "やだね"});
        cel.publishMsg(evt);
    }

    /**
     * URLをオープンする。
     * @param url URL
     */
    private void browseURL(String url) {

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(new URI(url));
                } catch (IOException | URISyntaxException ex) {
                    ClientContext.getBootLogger().warn(ex);
                }
            }
        }
    }

    /**
     * About を表示する。
     */
    public void showAbout() {
        AbstractProjectFactory f = Project.getProjectFactory();
        f.createAboutDialog();
    }

    /**
     * シェーマボックスを表示する。
     */
    @Override
    public void showSchemaBox() {
//masuda^
        if (imageBox == null) {
            imageBox = new ImageBox();
            imageBox.setContext(this);
        }
        imageBox.start();
//masuda$
    }

    /**
     * スタンプボックスを表示する。
     */
    @Override
    public void showStampBox() {
        if (stampBox != null) {
            stampBox.enter();
        }
    }

    /**
     * Mediator
     */
    protected final class Mediator extends MenuSupport {

        public Mediator(Object owner) {
            super(owner);
        }

        // global property の制御
        @Override
        public void menuSelected(MenuEvent e) {
        }

        @Override
        public void registerActions(ActionMap actions) {
            super.registerActions(actions);
        }
    }

    /**
     * MainWindowState
     */
    abstract class MainWindowState {

        public MainWindowState() {
        }

        public abstract void enter();

        public abstract boolean isLogin();
    }

    /**
     * LoginState
     */
    class LoginState extends MainWindowState {

        public LoginState() {
        }

        @Override
        public boolean isLogin() {
            return true;
        }

        @Override
        public void enter() {

            // Menuを制御する
            mediator.disableAllMenus();

            String[] enables = new String[]{
                GUIConst.ACTION_PRINTER_SETUP,
                GUIConst.ACTION_PROCESS_EXIT,
                GUIConst.ACTION_SET_KARTE_ENVIROMENT,
                GUIConst.ACTION_SHOW_STAMPBOX,
                GUIConst.ACTION_NEW_PATIENT,
                GUIConst.ACTION_SHOW_SCHEMABOX,
                GUIConst.ACTION_CHANGE_PASSWORD,
                GUIConst.ACTION_CONFIRM_RUN,
                GUIConst.ACTION_BROWS_DOLPHIN,
                GUIConst.ACTION_BROWS_DOLPHIN_PROJECT,
                GUIConst.ACTION_BROWS_MEDXML,
                GUIConst.ACTION_SHOW_ABOUT,
//masuda^   中止項目と採用薬編集
                GUIConst.ACTION_BROADCAST_MSG,
                GUIConst.ACTION_BROWS_GITHUB,
                GUIConst.ACTION_EDIT_DISCONITEM,
                GUIConst.ACTION_EDIT_USINGDRUG,
                GUIConst.ACTION_CHECK_TEMP_KARTE,
                GUIConst.ACTION_SHOW_REZETEN
//masuda$
            };
            mediator.enableMenus(enables);
            
            Action addUserAction = mediator.getAction(GUIConst.ACTION_ADD_USER);
            boolean admin = false;
            Collection<RoleModel> roles = Project.getUserModel().getRoles();
            for (RoleModel model : roles) {
                if (model.getRole().equals(GUIConst.ROLE_ADMIN)) {
                    admin = true;
                    break;
                }
            }
            addUserAction.setEnabled(admin);
        }
    }

    /**
     * LogoffState
     */
    class LogoffState extends MainWindowState {

        public LogoffState() {
        }

        @Override
        public boolean isLogin() {
            return false;
        }

        @Override
        public void enter() {
            mediator.disableAllMenus();
        }
    }

    /**
     * StateManager
     */
    private class StateManager {

        private final MainWindowState loginState = new LoginState();
        private final MainWindowState logoffState = new LogoffState();
        private MainWindowState currentState = logoffState;

        public StateManager() {
        }

        public boolean isLogin() {
            return currentState.isLogin();
        }

        public void processLogin(boolean b) {
            currentState = b ? loginState : logoffState;
            currentState.enter();
        }
    }

    public static void main(String[] args) {
//masuda^
        // アンチエイリアスの設定は最初がいい？
        System.setProperty("awt.useSystemAAFontSettings","on");
        settingForMac();
        
        boolean pro = false;
//        boolean server = false;
//        String userId = null;
//        String userPassword = null;
//        for (String arg : args) {
//            if ("pro".equals(arg.toLowerCase())) {
//                pro = true;
//            }
//            if (arg.startsWith("-U")) {
//                userId = arg.substring(2);
//            }
//            if (arg.startsWith("-P")) {
//                userPassword = arg.substring(2);
//            }
//            if (arg.startsWith("-S")) {
//                server = true;
//            }
//        }
//        
//        if (server) {
//            new StandAlonePVTServer(pro, null, userId, userPassword);
//            return;
//        }

        ClientContext.setClientContextStub(new ClientContextStub(pro));
        Project.setProjectStub(new ProjectStub());
        // configure LAF/UI
        ClientContext.getClientContextStub().setUI();
        
        instance = new Dolphin();
        //instance.start(pro);
        instance.start(false);
//masuda$
    }
    
//masuda^
    private static void settingForMac() {
        // Machintoshで使うためのメニュー設定、いっちばん最初にしておかないといけない
        if (!System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            return;
        }
        // MenuBar
        System.setProperty("apple.laf.useScreenMenuBar", String.valueOf(true));
        System.setProperty("com.apple.macos.smallTabs", String.valueOf(true));
        // スクリーンメニュー左端に表記されるアプリケーション名を設定する
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "OpenDolphin");
    }

    // Mac Application Menu
    private void enableMacApplicationMenu() {

        com.apple.eawt.Application fApplication = com.apple.eawt.Application.getApplication();

        // About
        fApplication.setAboutHandler(new com.apple.eawt.AboutHandler() {

            @Override
            public void handleAbout(com.apple.eawt.AppEvent.AboutEvent ae) {
                showAbout();
            }
        });

        // Preference
        fApplication.setPreferencesHandler(new com.apple.eawt.PreferencesHandler() {

            @Override
            public void handlePreferences(com.apple.eawt.AppEvent.PreferencesEvent pe) {
                doPreference();
            }
        });

        // Quit
        fApplication.setQuitHandler(new com.apple.eawt.QuitHandler() {

            @Override
            public void handleQuitRequestWith(com.apple.eawt.AppEvent.QuitEvent qe, com.apple.eawt.QuitResponse qr) {
                processExit(false);
            }
        });
        
        // Dock Icon
        ImageIcon icon = ClientContext.getImageIcon("OpenDolphin-m.png");
        fApplication.setDockIconImage(icon.getImage());
    }
    
    public void changeLookAndFeel(String lafClassName) {
        try {
            Project.setString("lookAndFeel", lafClassName);
            requestReboot();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private void requestReboot() {

        // LAFの変更やPropertyのインポート・初期化はいったん再起動させることとする
        final String msg = "LAFの設定を変更しました。再起動してください。";
        final String title = ClientContext.getFrameTitle("設定変更");
        JOptionPane.showMessageDialog(null, msg, title, JOptionPane.WARNING_MESSAGE);
        processExit(false);

    }
    
    //PacsServiceを開始する
    private void startPacsService() {
        PluginLoader<PacsService> loader = PluginLoader.load(PacsService.class);
        Iterator<PacsService> iter = loader.iterator();
        if (iter.hasNext()) {
            pacsService = iter.next();
            pacsService.setContext(this);
            pacsService.start();
            providers.put("pacsService", pacsService);
            saveEnv.put(GUIConst.KEY_PACS_SERVICE, GUIConst.SERVICE_RUNNING);
        }
    }

    private String getPacsSettingString() {
        
        String remoteHost = Project.getString(MiscSettingPanel.PACS_REMOTE_HOST, MiscSettingPanel.DEFAULT_PACS_REMOTE_HOST);
        int remotePort = Project.getInt(MiscSettingPanel.PACS_REMOTE_PORT, MiscSettingPanel.DEFAULT_PACS_REMOTE_PORT);
        String remoteAET = Project.getString(MiscSettingPanel.PACS_REMOTE_AE, MiscSettingPanel.DEFAULT_PACS_REMOTE_AE);
        String localHost = Project.getString(MiscSettingPanel.PACS_LOCAL_HOST, MiscSettingPanel.DEFAULT_PACS_LOCAL_HOST);
        int localPort = Project.getInt(MiscSettingPanel.PACS_LOCAL_PORT, MiscSettingPanel.DEFAULT_PACS_LOCAL_PORT);
        String localAET = Project.getString(MiscSettingPanel.PACS_LOCAL_AE, MiscSettingPanel.DEFAULT_PACS_LOCAL_AE);

        StringBuilder sb = new StringBuilder();
        sb.append("Remote Host = ");
        sb.append(remoteHost);
        sb.append(":");
        sb.append(remotePort);
        sb.append(" AET = ");
        sb.append(remoteAET);
        sb.append(" / Local Host = ");
        sb.append(localHost);
        sb.append(":");
        sb.append(localPort);
        sb.append(" AET = ");
        sb.append(localAET);
        return sb.toString();
    }
    
    //中止項目と採用薬編集
    public void editDisconItem() {
        DisconItemPanel panel = new DisconItemPanel();
        panel.enter();
    }

    public void editUsingDrug() {
        UsingDrugPanel panel = new UsingDrugPanel();
        panel.enter();
    }
    
    public void checkTempKarte() {
        try {
            TempKarteCheckDialog tempKarte = new TempKarteCheckDialog();
            tempKarte.setLocationRelativeTo(null);
            tempKarte.setVisible(true);
        } catch (Exception ex) {
        }
    }
    
    public void showRezeTen() {
        RezeptViewer viewer = new RezeptViewer();
        viewer.enter();
    }
    
    
    // broadcast messaging test
    @Override
    public void onEvent(ChartEventModel evt) throws Exception {
        
        if (clientUUID.equals(evt.getIssuerUUID())) {
            return;
        }
        
        ChartEventModel.EVENT eventType = evt.getEventType();
        
        switch (eventType) {
            case MSG_BROADCAST:
                showBroadcastMsg(evt);
                break;
            case MSG_REPLY:
                processReplyMsg(evt);
                break;
            case REQUEST_REBOOT:
                processExit(true);
                break;
            default:
                break;
        }
    }

    private void showBroadcastMsg(ChartEventModel evt) {
        
        String[] msgOpts = evt.getMsgOpts();
        String msgTitle = evt.getMsgTitle();
        String msgOwner = evt.getMsgOwner();
        String msgContent = evt.getMsgContent();
        StringBuilder sb = new StringBuilder();
        sb.append(msgOwner).append("からのメッセージ\n");
        sb.append(msgContent);
        String msg = sb.toString();

        // ダイアログ表示
        int val = JOptionPane.showOptionDialog(null, msg, msgTitle,
                JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, msgOpts, msgOpts[0]);
        
        // 返事作成
        String reply = (val == -1) ? msgOpts[0] : msgOpts[val];
        String replyToUUID = evt.getMsgUUID();
        String replyMsgUUID = UUID.randomUUID().toString();
        String replyMsgOwner = getHostName();
        String replyTitle = "お返事";
        
        ChartEventModel evtReply = new ChartEventModel();
        evtReply.setEventType(ChartEventModel.EVENT.MSG_REPLY);
        evtReply.setMsgOwner(replyMsgOwner);
        evtReply.setMsgUUID(replyMsgUUID);
        evtReply.setReplyToUUID(replyToUUID);
        evtReply.setMsgTitle(replyTitle);
        evtReply.setMsgContent(reply);
        
        cel.publishMsg(evtReply);
    }
    
    private void processReplyMsg(ChartEventModel evt) {
        
        String msgOwner = evt.getMsgOwner();
        String msgContent = evt.getMsgContent();
        String msgTitle =evt.getMsgTitle();
        StringBuilder sb = new StringBuilder();
        sb.append(msgOwner).append("からの返事\n");
        sb.append(msgContent).append("!!");
        String msg = sb.toString();
        JOptionPane.showMessageDialog(null, msg, msgTitle, JOptionPane.INFORMATION_MESSAGE);
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
        }
        return "Unknown host name.";
    }
//masuda$
}
