package open.dolphin.impl.rezept.model;

/**
 * 診療行為レコード
 * 
 * @author masuda, Masuda Naika
 */
public class SI_Model implements IRezeItem {
    
    public static final String ID = "SI";
    private static final int daysStart = 13;
    
    private String classCode;       // 診療識別　21等
    private String srycd;           // 診療行為コード
    private Float number;           // 数量データ
    private Float ten;              // 点数
    private int count;              // 回数
    private String dayData;         // 算定日情報
    
    private String description;

    @Override
    public String getClassCode() {
        return classCode;
    }
    @Override
    public String getSrycd() {
        return srycd;
    }
    @Override
    public Float getNumber() {
        return number;
    }
    @Override
    public Float getTen() {
        return ten;
    }
    @Override
    public Integer getCount() {
        return count;
    }
    public String getDayData() {
        return dayData;
    }
    
    @Override
    public void parseLine(String csv) {
        String[] tokens = csv.split(CAMMA);
        classCode = tokens[1].trim();
        srycd = tokens[3].trim();
        number = tokens[4].isEmpty() ? 1 : Float.parseFloat(tokens[4]);
        ten = tokens[5].isEmpty() ? null : Float.parseFloat(tokens[5]);
        count = Integer.parseInt(tokens[6]);
        
        int len = tokens.length;
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        for (int i = daysStart; i < len; ++i) {
            if (!tokens[i].isEmpty()) {
                if (!first) {
                    sb.append(",");
                } else {
                    first = false;
                }
                int day = i - daysStart + 1;
                sb.append(day).append("(").append(tokens[i]).append(")");
            }
        }
    }
    
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String desc) {
        description = desc;
    }
    
}
