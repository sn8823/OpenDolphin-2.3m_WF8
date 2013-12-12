package open.dolphin.order;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.table.TableColumn;
import open.dolphin.client.AutoKanjiListener;
import open.dolphin.client.AutoRomanListener;
import open.dolphin.client.Chart;
import open.dolphin.client.DefaultCellEditor2;
import open.dolphin.infomodel.*;
import open.dolphin.project.Project;
import open.dolphin.table.ListTableModel;
import open.dolphin.table.ListTableSorter;

/**
 * InjectionEditor
 * @author Kazushi Minagawa, Digital Globe, Inc.
 * @author modified by masuda, Masuda Naika
 */
public final class InjectionEditor extends AbstractStampEditor {
    
    private static final String[] COLUMN_NAMES = {"コード", "診療内容", "数 量", "単 位"};
    private static final String[] METHOD_NAMES = {"getCode", "getName", "getNumber", "getUnit"};
    private static final int[] COLUMN_WIDTH = {50, 200, 10, 10};
    private static final int NUMBER_COLUMN = 2;

//masuda^   yukoedymdを追加
    private static final String[] SR_COLUMN_NAMES =
    {"種別", "コード", "名 称", "単位", "点数", "診区", "病診", "入外", "社老", "有効期限"};
    private static final String[] SR_METHOD_NAMES = 
    {"getSlot", "getSrycd", "getName", "getTaniname", "getTenInteger",
        "getSrysyukbn", "getHospsrykbn", "getNyugaitekkbn", "getRoutekkbn","getYukoedymdStr"};
    private static final Class[] SR_CLASSES = 
    {String.class, String.class, String.class, String.class, Integer.class,
        String.class, String.class, String.class, String.class, String.class};
    private static final int[] SR_COLUMN_WIDTH = {10, 50, 200, 10, 10, 10, 5, 5, 5, 10};
    private static final int SR_NUM_ROWS = 1;
//masuda$

    private static final String NO_CHARGE = "（手技料なし）";   // カッコつけるｗ

    private InjectionView view;

    private ListTableModel<MasterItem> tableModel;

    private ListTableModel<TensuMaster> searchResultModel;
    private ListTableSorter<TensuMaster> sorter;


    public InjectionEditor(String entity) {
        this(entity, true);
    }

    public InjectionEditor(String entity, boolean mode) {
        super(entity, mode);
    }
    
    @Override
    protected String[] getColumnNames() {
        return COLUMN_NAMES;
    }

    @Override
    protected String[] getColumnMethods() {
        return METHOD_NAMES;
    }

    @Override
    protected int[] getColumnWidth() {
        return COLUMN_WIDTH;
    }

    @Override
    protected String[] getSrColumnNames() {
        return SR_COLUMN_NAMES;
    }

    @Override
    protected String[] getSrColumnMethods() {
        return SR_METHOD_NAMES;
    }

    @Override
    protected int[] getSrColumnWidth() {
        return SR_COLUMN_WIDTH;
    }
    
    @Override
    public JPanel getView() {
        return (JPanel) view;
    }

    @Override
    public void dispose() {

        if (tableModel != null) {
            tableModel.clear();
        }

        if (searchResultModel != null) {
            searchResultModel.clear();
        }

        super.dispose();
    }
    
//masuda^
    @Override
    public ModuleModel[] getNewValue() {

        // 常に新規のモデルとして返す
        ModuleModel retModel = new ModuleModel();
        ModuleInfoBean moduleInfo = retModel.getModuleInfoBean();
        moduleInfo.setEntity(entity);
        moduleInfo.setStampRole(IInfoModel.ROLE_P);

        // スタンプ名を設定する
        String text = view.getStampNameField().getText().trim();
        if (!text.isEmpty()) {
            moduleInfo.setStampName(text);
        } else {
            moduleInfo.setStampName(DEFAULT_STAMP_NAME);
        }

        // BundleDolphin を生成する
        BundleDolphin bundle = new BundleDolphin();

        // Dolphin Appli で使用するオーダ名称を設定する
        // StampHolder で使用される（タブ名に相当）
        bundle.setOrderName(orderName);

        // セットテーブルのマスターアイテムを取得する
        List<MasterItem> itemList = tableModel.getDataProvider();

        // 診療行為があるかどうかのフラグ
        boolean found = false;
        // 診療行為区分
        String c007 = view.getSelectedShinku();
        if (c007 != null) {
            found = true;
        }

        for (MasterItem masterItem : itemList) {

            // マスタアイテムを ClaimItem に変換する
            ClaimItem item = masterToClaimItem(masterItem);

            // 診区を設定する
            // 最初に見つかった手技の診区をあとで ClaimBundle に設定する
            if (!found && masterItem.getClassCode() == ClaimConst.SYUGI) {
                // 集計先をマスタアイテム自体へ持たせている
                c007 = getClaim007Code(masterItem.getClaimClassCode());
                if (c007 != null) {
                    found = true;
                }
            }
            bundle.addClaimItem(item);
        }
        
        // 診療行為区分を設定する
        if (c007 == null) {
            // 入院で手技なしの場合はコンボボックスで指定されたものがgetClassCodeで取得できる
            c007 = (classCode != null) ? classCode : implied007;
        }
        if (c007 == null) {
            c007 = ClaimConst.INJECTION_330;
        }
        // 外来で手技料なしの場合、再設定する 2010-10-27
        if (!isAdmission()) {
            String memo = view.getCommentField().getText().trim();
            // 手技料なしがチェックされている場合で皮下、静脈、点滴の場合
            if (view.getNoChargeChk().isSelected() && isInjection(c007)) {
                c007 = c007.substring(0, 2) + "1"; // 手技なしコードにする
                // memoする
                if (!text.contains(NO_CHARGE)) {
                    bundle.setMemo(memo + NO_CHARGE);
                }
            } else {
                memo = memo.replace(NO_CHARGE, "").trim();
                if ("".equals(memo)) {
                    bundle.setMemo(null);
                } else {
                    bundle.setMemo(memo);
                }
                c007 = c007.substring(0, 2) + "0";
            }
        }
        if (c007 != null) {
            bundle.setClassCode(c007);
            // Claim007 固定の値
            bundle.setClassCodeSystem(CLASS_CODE_ID);
            // 上記テーブルで定義されている診療行為の名称
            bundle.setClassName(MMLTable.getClaimClassCodeName(c007));
        }

        // バンドル数を設定
        String bundleNum =  view.getNumberField().getText().trim();
        if (bundleNum.isEmpty()) {
            bundleNum = "1";
        }
        bundle.setBundleNumber(bundleNum);
        
        // バンドルメモ復活
        String memo = view.getCommentField().getText().trim();
        if (!memo.isEmpty()) {
            bundle.setMemo(memo);
        }
        
        retModel.setModel((IModuleModel) bundle);

        return new ModuleModel[]{retModel};
    }
//masuda$

    @Override
     public void setValue(Object objValue) {
         
        // 連続して編集される場合があるのでテーブル内容等をクリアする
        clear();
        setOldValue(objValue);
        if (!(objValue instanceof ModuleModel[])) {
            return;
        }
        
        ModuleModel[] value = (ModuleModel[]) objValue;

        // 共通の設定
        BundleDolphin bundle = setModuleModels(value);
        if (bundle == null) {
            return;
        }

//masuda^
        // バンドル数を数量フィールドへ設定する
        String number = bundle.getBundleNumber();
        view.getNumberField().setText(number);
        
        // メモ復活
        String memo = bundle.getMemo();
        if (memo != null) {
            view.getCommentField().setText(memo);
        }
//masuda$
        
        
        //----------------------------
        // 手技料なしの場合
        //---------------------------
        String test = bundle.getClassCode();
        if (test != null && test.startsWith("3") && test.endsWith("1")) {
            view.getNoChargeChk().setSelected(true);
//masuda^
        //} else if (bundle.getMemo() != null && bundle.getMemo().equals(NO_CHARGE)) {
        } else if (bundle.getMemo() != null && bundle.getMemo().contains(NO_CHARGE)) {
//masuda$
            view.getNoChargeChk().setSelected(true);
        } else {
            view.getNoChargeChk().setSelected(false);
        }

        // Stateを変更する
        checkValidation();
    }
    

    @Override
    public void setContext(Chart chart) {
        super.setContext(chart);
        controlBtn();
    }
    
    // 外来か入院かに応じてボタンを制御する
    private void controlBtn() {
 
        boolean admission = isAdmission();
        if (!admission) {
            view.getShugiCmb().setEnabled(false);
            view.getNoChargeChk().setEnabled(true);
        } else {
            view.getNoChargeChk().setSelected(true);
            view.getNoChargeChk().setEnabled(false);
        }
    }
    
    @Override
    protected void checkValidation() {

        SwingUtilities.invokeLater(new Runnable() {

            @Override
            public void run() {
                boolean setIsEmpty = (tableModel.getObjectCount() == 0);

                if (setIsEmpty) {
                    view.getStampNameField().setText(DEFAULT_STAMP_NAME);
                }

                boolean setIsValid = true;

                int techCnt = 0;
                int other = 0;
                boolean noTech = view.getNoChargeChk().isSelected();

                List<MasterItem> itemList = tableModel.getDataProvider();

                for (MasterItem item : itemList) {

                    if (item.getClassCode() == ClaimConst.SYUGI) {
                        techCnt++;

                    } else {
                        other++;
                    }
                }

                if (noTech) {
                    setIsValid = setIsValid && (techCnt == 0);
                    setIsValid = setIsValid && (other > 0);
                } else {
                    setIsValid = setIsValid && (techCnt > 0);
                    setIsValid = setIsValid && (other > 0);
                }

                // チェックボックスの設定
                view.getTechChk().setSelected((techCnt > 0));

                // 通知する
                controlButtons(setIsEmpty, setIsValid);
            }
        });
    }

    @Override
    protected void addSelectedTensu(TensuMaster tm) {

        // 項目の受け入れ試験
        String test = tm.getSlot();
        if (passPattern == null || !passPattern.matcher(test).find()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        // 診療区分の受け入れ試験
        if (test.equals(ClaimConst.SLOT_SYUGI)) {
            String shinku = tm.getSrysyukbn();
            if (shinkuPattern == null || !shinkuPattern.matcher(shinku).find()) {
                Toolkit.getDefaultToolkit().beep();
                return;
            }
        }

        // MasterItem に変換する
        MasterItem item = tensuToMasterItem(tm);

        // 手技の場合にスタンプ名を設定する
        if (item.getClassCode() == ClaimConst.SYUGI) {
            String stName = view.getStampNameField().getText().trim();
            if (stName.isEmpty() || stName.equals(DEFAULT_STAMP_NAME)) {
                view.getStampNameField().setText(item.getName());
            }
        }

        // テーブルへ追加する
        tableModel.addObject(item);

        // バリデーションを実行する
        checkValidation();
    }

    @Override
    protected void search(final String text, boolean hitReturn) {

        boolean pass = true;
        pass = pass && ipOk();

        int searchType = getSearchType(text, hitReturn);

        pass = pass && (searchType != TT_INVALID);

        if (!pass) {
            return;
        }

        doSearch(text, searchType);
    }

    @Override
    protected final void initComponents() {

        // View
        //view = editorButtonTypeIsIcon() ? new InjectionView() : new InjectionViewText();
        view = new InjectionView();
        
        // Info Label
        view.getInfoLabel().setText(info);
        
       // 入院注射区分指示
        view.getShugiCmb().addActionListener(new ActionListener(){

            @Override
            public void actionPerformed(ActionEvent e) {
                // 選択されたClaimClassCodeを保存する。あとで使う
                classCode = view.getSelectedClassCode();
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        view.getNumberField().requestFocusInWindow();
                    }
                });
            }
        });

        //------------------------------------------
        // セットテーブルを生成する
        //------------------------------------------
        tableModel = new ListTableModel<MasterItem>(COLUMN_NAMES, START_NUM_ROWS, METHOD_NAMES, null) {

            // NUMBER_COLUMN を編集可能にする
            @Override
            public boolean isCellEditable(int row, int col) {
                
                // 元町皮膚科
                if (col == 1) {
                    String code = (String) this.getValueAt(row, 0);
                    isNameEditableComment(code);
                }
                
                // 数量
                if (col == NUMBER_COLUMN) {
                    String code = (String) this.getValueAt(row, 0);
                    return isEditableNumber(code);
                }
                return false;
            }

            // NUMBER_COLUMN に値を設定する
            @Override
            public void setValueAt(Object o, int row, int col) {

                MasterItem mItem = getObject(row);

                if (mItem == null) {
                    return;
                }

                String value = (String) o;
                if (o != null) {
                    value = value.trim();
                }

                // コメント編集 元町皮膚科
                if (col == 1 && isNameEditableComment(mItem.getCode())) {
                    mItem.setName(value);
                    return;
                }

                // 数量
                int code = mItem.getClassCode();

                if (value == null || value.isEmpty()) {

                    boolean test = (code==ClaimConst.SYUGI ||
                                    code==ClaimConst.OTHER ||
                                    code==ClaimConst.BUI);
                    if (test) {
                        mItem.setNumber(null);
                        mItem.setUnit(null);
                    }
                    checkValidation();
                    return;
                }

                mItem.setNumber(value);
                checkValidation();
            }
        };
        
        JTable setTable = view.getSetTable();
        setTable.setModel(tableModel);
        setTable.getTableHeader().setReorderingAllowed(false);
        
        // 数量カラムにセルエディタを設定する
        JTextField tf = new JTextField();
        tf.addFocusListener(AutoRomanListener.getInstance());
        TableColumn column = setTable.getColumnModel().getColumn(NUMBER_COLUMN);
        DefaultCellEditor de = new DefaultCellEditor2(tf);
        int ccts = Project.getInt("order.table.clickCountToStart", 1);
        de.setClickCountToStart(ccts);
        column.setCellEditor(de);

        // 診療内容カラム(column number = 1)にセルエディタを設定する 元町皮膚科
        JTextField tf2 = new JTextField();
        tf2.addFocusListener(AutoKanjiListener.getInstance());
        column = setTable.getColumnModel().getColumn(1);
        DefaultCellEditor de2 = new DefaultCellEditor2(tf2);
        de2.setClickCountToStart(ccts);
        column.setCellEditor(de2);
        
        //
        // 検索結果テーブルを生成する
        //
        searchResultModel = new ListTableModel<TensuMaster>(SR_COLUMN_NAMES, SR_NUM_ROWS, SR_METHOD_NAMES, SR_CLASSES) {

            @Override
            public Object getValueAt(int row, int col) {

                Object ret = super.getValueAt(row, col);

                if (ret != null) {
                    switch (col) {
                        case 6:
                            // 病診
                            int index = Integer.parseInt((String) ret);
                            ret = HOSPITAL_CLINIC_FLAGS[index];
                            break;
                        case 7:
                            // 入外
                            index = Integer.parseInt((String) ret);
                            ret = IN_OUT_FLAGS[index];
                            break;
                        case 8:
                            // 社老
                            index = Integer.parseInt((String) ret);
                            ret = OLD_FLAGS[index];
                            break;
                    }
                }
                
                return ret;
            }
        };
        
        // sorter設定
        JTable searchResultTable = view.getSearchResultTable();
        searchResultTable.getTableHeader().setReorderingAllowed(false);
        sorter = new ListTableSorter<>(searchResultModel);
        searchResultTable.setModel(sorter);
        sorter.setTableHeader(searchResultTable.getTableHeader());

        // スタンプ名フィールド
        view.getStampNameField().addFocusListener(AutoKanjiListener.getInstance());

//        // コメントフィールド
//        view.getCommentField().addFocusListener(AutoKanjiListener.getInstance());

        // 手技なし
        //view.getNoChargeChk().setEnabled(false); // <-enableしなくていいの？ masuda

//masuda^   コメントボタン
        JButton btn_comment = view.getCommentBtn();
        btn_comment.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                doSearch(REGEXP_COMMENT_ALL, TT_CODE_SEARCH);
            }

        });
        
        // 数量フィールドに期間入力ポップアップを付ける
        view.getNumberField().addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                mabeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mabeShowPopup(e);
            }

            private void mabeShowPopup(MouseEvent e) {
                if (isAdmission() && e.isPopupTrigger()) {
                    PeriodSelectDialog dialog = new PeriodSelectDialog();
                    dialog.setLocationRelativeTo(view);
                    dialog.pack();
                    dialog.setVisible(true);
                    String value = dialog.getValue();
                    dialog.dispose();
                    if (value != null && !value.isEmpty()) {
                        view.getNumberField().setText(value);
                    }
                }
            }
        });
//masuda$
        
        // 共通の設定
        setupOrderComponents();
        
        // SearchTextFieldにフォーカスをあてる
        setFocusOnSearchTextFld();
    }
}
