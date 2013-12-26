package open.dolphin.impl.rezept;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import open.dolphin.client.ClientContext;

/**
 * RezeptView
 * 
 * @author masuda, Masuda Naika
 */
public class RezeptView extends JPanel {
    
    private static final String ICON_FORWARD = "icon_arrow_right_small";
    private static final String ICON_BACK = "icon_arrow_left_small";
    private static final ImageIcon PERV_ICON = ClientContext.getImageIconAlias(ICON_BACK);
    private static final ImageIcon NEXT_ICON = ClientContext.getImageIconAlias(ICON_FORWARD);
    
    private JTabbedPane tabbedPane;
    private JButton importBtn;
    private JButton checkBtn;
    private JButton prevBtn;
    private JButton nextBtn;
    
    private JTextField insTypeField;
    private JTextField pubIns1Field;
    private JTextField pubIns1NumField;
    private JTextField pubIns2Field;
    private JTextField pubIns2NumField;
    private JTextField pubIns3Field;
    private JTextField pubIns3NumField;
    private JTextField insField;
    private JTextField insSymbolField;
    private JTextField insNumberField;
    
    private JTextField ptNameField;
    private JTextField ptBirthdayField;
    private JTextField ptSexField;
    private JTextField ptAgeField;
    
    private JTable diagTable;
    private JTextField diagCountField;
    private JTable itemTable;
    private JTextField numDayField;
    private JTextField tenField;
    
    private JTextArea commentArea;
    private JTextArea infoArea;
    
    
    public RezeptView() {
        initComponents();
    }

    private void initComponents() {
        
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        
        // left panel
        JPanel leftPanel = createYBoxPanel();
        
        JPanel btnPanel = createXBoxPanel();
        importBtn = new JButton("取込");
        checkBtn = new JButton("点検");
        btnPanel.add(importBtn);
        btnPanel.add(checkBtn);
        prevBtn = new JButton(PERV_ICON);
        nextBtn = new JButton(NEXT_ICON);
        btnPanel.add(prevBtn);
        btnPanel.add(nextBtn);
        leftPanel.add(btnPanel);
        leftPanel.add(Box.createVerticalStrut(5));
        
        leftPanel.setPreferredSize(new Dimension(230, 400));
        tabbedPane = new JTabbedPane();
        leftPanel.add(tabbedPane);
        add(leftPanel);
        
        // rightPanel
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());

        // north
        JPanel north = createXBoxPanel();
        north.setBorder(BorderFactory.createEtchedBorder());
        
        // pt info
        JPanel p1 = createYBoxPanel();
        JPanel p11 =createXBoxPanel();
        p11.add(new JLabel("氏名"));
        ptNameField = createTextField(10);
        p11.add(ptNameField);
        p1.add(p11);
        JPanel p12 = createXBoxPanel();
        p12.add(new JLabel("性別"));
        ptSexField = createTextField(2);
        p12.add(ptSexField);
        p12.add(new JLabel("年齢"));
        ptAgeField = createTextField(2);
        p12.add(ptAgeField);
        p1.add(p12);
        JPanel p13 = createXBoxPanel();
        p13.add(new JLabel("生年月日"));
        ptBirthdayField = createTextField(10);
        p13.add(ptBirthdayField);
        p1.add(p13);
        north.add(p1);
        
        // insurance
        JPanel p2 = createYBoxPanel();
        JPanel p21 = createXBoxPanel();
        p21.add(new JLabel("公費１"));
        pubIns1Field = createTextField(5);
        p21.add(pubIns1Field);
        p2.add(p21);
        JPanel p22 = createXBoxPanel();
        p22.add(new JLabel("公費２"));
        pubIns2Field = createTextField(5);
        p22.add(pubIns2Field);
        p2.add(p22);
        JPanel p23 = createXBoxPanel();
        p23.add(new JLabel("公費３"));
        pubIns3Field = createTextField(5);
        p23.add(pubIns3Field);
        p2.add(p23);
        north.add(p2);
        
        JPanel p3 = createYBoxPanel();
        JPanel p31 = createXBoxPanel();
        p31.add(new JLabel("公受１"));
        pubIns1NumField = createTextField(5);
        p31.add(pubIns1NumField);
        p3.add(p31);
        JPanel p32 = createXBoxPanel();
        p32.add(new JLabel("公受２"));
        pubIns2NumField = createTextField(5);
        p32.add(pubIns2NumField);
        p3.add(p32);
        JPanel p33 = createXBoxPanel();
        p33.add(new JLabel("公受３"));
        pubIns3NumField = createTextField(5);
        p33.add(pubIns3NumField);
        p3.add(p33);
        north.add(p3);
        
        JPanel p4 = createYBoxPanel();
        insTypeField= createTextField(30);
        p4.add(insTypeField);
        JPanel p42 = createXBoxPanel();
        p42.add(new JLabel("保険"));
        insField = createTextField(20);
        p42.add(insField);
        p4.add(p42);
        JPanel p43 = createXBoxPanel();
        insSymbolField = createTextField(10);
        p43.add(insSymbolField);
        insNumberField = createTextField(12);
        p43.add(insNumberField);
        p4.add(p43);
        north.add(p4);
        rightPanel.add(north, BorderLayout.NORTH);
        
        // center
        final int maxWidth = 320;
        JPanel center = createXBoxPanel();
        JPanel centerLeft = createYBoxPanel();
        diagTable = new JTable();
        diagTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane diagScrl = new JScrollPane(diagTable);
        diagScrl.setBorder(BorderFactory.createTitledBorder("傷病名"));
        centerLeft.add(diagScrl);
        commentArea = createTextArea();
        JScrollPane commentScrl = new JScrollPane(commentArea);
        commentScrl.setBorder(BorderFactory.createTitledBorder("コメント"));
        centerLeft.add(commentScrl);
        commentScrl.setPreferredSize(new Dimension(maxWidth, 200));
        JPanel countPanel = createXBoxPanel();
        countPanel.add(new JLabel("病名数"));
        diagCountField = new JTextField(3);
        countPanel.add(diagCountField);
        countPanel.add(new JLabel("診療実日数"));
        numDayField = createTextField(2);
        countPanel.add(numDayField);
        countPanel.add(new JLabel("点数"));
        tenField = createTextField(5);
        countPanel.add(tenField);
        int height = countPanel.getPreferredSize().height;
        countPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        centerLeft.add(countPanel);
        Dimension d = new Dimension(maxWidth, Integer.MAX_VALUE);
        centerLeft.setPreferredSize(d);
        centerLeft.setMaximumSize(d);
        center.add(centerLeft);
        
        itemTable = new JTable();
        itemTable.getTableHeader().setReorderingAllowed(false);
        JScrollPane itemScrl = new JScrollPane(itemTable);
        itemScrl.setBorder(BorderFactory.createTitledBorder("診療行為"));
        center.add(itemScrl);
        rightPanel.add(center, BorderLayout.CENTER);
        
        // south
        JPanel south = createYBoxPanel();
        infoArea = createTextArea();
        JScrollPane infoScrl = new JScrollPane(infoArea);
        infoScrl.setBorder(BorderFactory.createTitledBorder("インフォ"));
        infoScrl.setPreferredSize(new Dimension(400, 200));
        south.add(infoScrl);
        rightPanel.add(south, BorderLayout.SOUTH);
        
        add(rightPanel);
    }

    private JPanel createYBoxPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }
    private JPanel createXBoxPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        return panel;
    }
    private JPanel createXFlowPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        return panel;
    }
    
    private JTextField createTextField(int len) {
        JTextField jf = new JTextField(len);
        jf.setEditable(false);
        return jf;
    }
    
    private JTextArea createTextArea() {
        JTextArea ja = new JTextArea();
        ja.setEditable(false);
        ja.setLineWrap(true);
        return ja;
    }
    
    public JTabbedPane getTabbedPane() {
        return tabbedPane;
    }
    public JButton getImportBtn() {
        return importBtn;
    }
    public JButton getCheckBtn() {
        return checkBtn;
    }
    public JButton getPrevBtn() {
        return prevBtn;
    }
    public JButton getNextBtn() {
        return nextBtn;
    }
    public JTextField getInsTypeField() {
        return insTypeField;
    }
    public JTextField getPubIns1Field() {
        return pubIns1Field;
    }
    public JTextField getPubIns1NumField() {
        return pubIns1NumField;
    }
    public JTextField getPubIns2Field() {
        return pubIns2Field;
    }
    public JTextField getPubIns2NumField() {
        return pubIns2NumField;
    }
    public JTextField getPubIns3Field() {
        return pubIns3Field;
    }
    public JTextField getPubIns3NumField() {
        return pubIns3NumField;
    }
    public JTextField getInsField() {
        return insField;
    }
    public JTextField getInsSymbolField() {
        return insSymbolField;
    }
    public JTextField getInsNumberField() {
        return insNumberField;
    }
    public JTextField getPtNameField() {
        return ptNameField;
    }
    public JTextField getPtBirthdayField() {
        return ptBirthdayField;
    }
    public JTextField getPtSexField() {
        return ptSexField;
    }
    public JTextField getPtAgeField() {
        return ptAgeField;
    }
    public JTable getDiagTable() {
        return diagTable;
    }
    public JTable getItemTable() {
        return itemTable;
    }
    public JTextArea getCommentArea() {
        return commentArea;
    }
    public JTextArea getInfoArea() {
        return infoArea;
    }
    public JTextField getNumDayField() {
        return numDayField;
    }
    public JTextField getTenField() {
        return tenField;
    }
    public JTextField getDiagCountField() {
        return diagCountField;
    }
}
