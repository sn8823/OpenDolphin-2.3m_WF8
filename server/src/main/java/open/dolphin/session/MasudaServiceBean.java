package open.dolphin.session;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;
import open.dolphin.common.util.ModuleBeanDecoder;
import open.dolphin.infomodel.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;

/**
 * MasudaServiceBean
 * @author masuda, Masuda Naika
 */
@Stateless
public class MasudaServiceBean {
    
    private static final String QUERY_INSURANCE_BY_PATIENT_PK 
            = "from HealthInsuranceModel h where h.patient.id=:pk";
    
    private static final String PK = "pk";
    private static final String FINISHED = "finished";
    
    @PersistenceContext
    private EntityManager em;
    
    
    // 定期処方
    public List<RoutineMedModel> getRoutineMedModels(long karteId, int firstResult, int maxResults) {
        
        // 橋本医院　加藤さま
        // 【2. 薬歴を新しい順に表示するように修正】からインスパイヤ
        final String sql = "from RoutineMedModel r where r.karteId = :kId and r.bookmark = :bookmark"
                + " order by r.registDate desc";
        
        // bookmarkなしのものは指定した数だけ取得する
        List<RoutineMedModel> list1 =
                em.createQuery(sql)
                .setParameter("kId", karteId)
                .setParameter("bookmark", false)
                .setFirstResult(firstResult)
                .setMaxResults(maxResults)
                .getResultList();
        
        // bookmarkありのものはすべて取得する
        List<RoutineMedModel> list2 =
                em.createQuery(sql)
                .setParameter("kId", karteId)
                .setParameter("bookmark", true)
                .getResultList();
        
        // マージする
        for (RoutineMedModel model : list2) {
            if (!list1.contains(model)) {
                list1.add(model);
            }
        }
        
        // PostgresでFetchType.EAGERにすると
        // org.hibernate.HibernateException: cannot simultaneously fetch multiple bags
        // それゆえFetchType.LAZYにしたので… トホホ MySQLだとOKなんだよぅ
        // トホホ２　OneToManyだと重複不可なんだよぅ
        for (RoutineMedModel model : list1) {
            fetchModuleModelList(model);
        }

        Collections.sort(list1, Collections.reverseOrder());
        
        return list1;
    }
    
    public RoutineMedModel getRoutineMedModel(long id) {
        RoutineMedModel model = em.find(RoutineMedModel.class, id);
        fetchModuleModelList(model);
        return model;
    }
    
/*
    private void fetchModuleModelList(RoutineMedModel model) {

        final String sql = "from ModuleModel m where m.id in (:ids)";
        
        List<ModuleModel> mmListTemp = model.getModuleList();
        List<Long> idList = new ArrayList<Long>();
        for (ModuleModel mm : mmListTemp) {
            idList.add(mm.getId());
        }
        
        List<ModuleModel> mmList =
                em.createQuery(sql)
                .setParameter("ids", idList)
                .getResultList();
        model.setModuleList(mmList);
    }
*/
    private void fetchModuleModelList(RoutineMedModel model) {

        final String sql = "from ModuleModel m where m.id in (:ids)";

        String moduleIds = model.getModuleIds();
        if (moduleIds == null || moduleIds.isEmpty()) {
            return;
        }
        String[] ids = moduleIds.split(",");

        List<Long> idList = new ArrayList<>();
        for (String id : ids) {
            idList.add(Long.valueOf(id));
        }

        List<ModuleModel> mmList =
                em.createQuery(sql)
                .setParameter("ids", idList)
                .getResultList();
        model.setModuleList(mmList);
    }
    
    public long removeRoutineMedModel(long id) {
        RoutineMedModel model = em.find(RoutineMedModel.class, id);
        if (model != null) {
            em.remove(model);
            return model.getId();
        }
        return -1;
    }
    
    public long addRoutineMedModel(RoutineMedModel model) {
        em.persist(model);
        return model.getId();
    }
    
    public long updateRoutineMedModel(RoutineMedModel model) {
        em.merge(model);
        return model.getId();
    }
    
    // 採用薬
    public List<UsingDrugModel> getUsingDrugModels(String fid) {

        final String sql1 = "from UsingDrugModel u where u.facilityId = :fid";

        List<UsingDrugModel> list =
                em.createQuery(sql1)
                .setParameter("fid", fid)
                .getResultList();

        return list;
    }

    public long addUsingDrugModel(UsingDrugModel model) {
        model.setCreated(new Date());
        em.persist(model);
        return model.getId();
    }

    public long removeUsingDrugModel(long id) {
        // 分離オブジェクトは remove に渡せないので対象を検索する
        UsingDrugModel target = em.find(UsingDrugModel.class, id);
        if (target != null) {
            em.remove(target);
            return target.getId();
        }
        return -1;
    }

    public long updateUsingDrugModel(UsingDrugModel model) {
        model.setCreated(new Date());
        em.merge(model);
        return model.getId();
    }

    // 中止項目
    public List<DisconItemModel> getDisconItems(String fid) {

        final String sql1 = "from DisconItemModel d where d.facilityId = :fid";

        List<DisconItemModel> list =
                em.createQuery(sql1)
                .setParameter("fid", fid)
                .getResultList();

        return list;
    }

    public long addDisconItem(DisconItemModel model) {
        em.persist(model);
        return model.getId();
    }

    public long removeDisconItem(long id) {
        // 分離オブジェクトは remove に渡せないので対象を検索する
        DisconItemModel target = em.find(DisconItemModel.class, id);
        if (target != null) {
            em.remove(target);
            return target.getId();
        }
        return -1;
    }

    public long updateDisconItem(DisconItemModel model) {
        em.merge(model);
        return model.getId();
    }


    // 指定したEntityのModuleModleを一括取得
    public List<ModuleModel> getModulesEntitySearch(String fid, long karteId, Date fromDate, Date toDate, List<String> entities) {
        
        // 指定したentityのModuleModelを返す
        List<ModuleModel> ret;
        
        if (karteId != 0){
            final String sql = "from ModuleModel m where m.karte.id = :karteId " +
                    "and m.started between :fromDate and :toDate and m.status='F' " +
                    "and m.moduleInfo.entity in (:entities)";

            ret = em.createQuery(sql)
                    .setParameter("karteId", karteId)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .setParameter("entities", entities)
                    .getResultList();
        } else {
            // karteIdが指定されていなかったら、施設の指定期間のすべて患者のModuleModelを返す
            long fPk = getFacilityPk(fid);
            final String sql = "from ModuleModel m " +
                    "where m.started between :fromDate and :toDate " +
                    "and m.status='F' " +
                    "and m.moduleInfo.entity in (:entities)" +
                    "and m.creator.facility.id = :fPk";

            ret = em.createQuery(sql)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .setParameter("entities",entities)
                    .setParameter("fPk", fPk)
                    .getResultList();
        }

        return ret;
    }

    // FEV-70関連
    public PatientVisitModel getLastPvtInThisMonth(String fid, String ptId) {

        final SimpleDateFormat frmt = new SimpleDateFormat(IInfoModel.DATE_WITHOUT_TIME);
        GregorianCalendar gc = new GregorianCalendar();
        int year = gc.get(GregorianCalendar.YEAR);
        int month = gc.get(GregorianCalendar.MONTH);
        gc.clear();
        gc.set(year, month, 1);
        String fromDate = frmt.format(gc.getTime());
        String toDate = frmt.format(new Date());
        final String sql = "from PatientVisitModel p " +
                "where p.facilityId = :fid and p.patient.patientId = :ptId " +
                "and p.pvtDate >= :fromDate and p.pvtDate < :toDate order by p.pvtDate desc";
        PatientVisitModel result = null;
        try {
            result = (PatientVisitModel)
                    em.createQuery(sql)
                    .setParameter("fid", fid)
                    .setParameter("ptId", ptId)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .setMaxResults(1)
                    .getSingleResult();
            // ダミーの保険情報を設定する
            setHealthInsurances(result.getPatientModel());
        } catch (NoResultException e) {
        }

        return result;
    }

    // 指定されたdocPkのDocInfoModelを返す
    public List<DocInfoModel> getDocumentList(List<Long> docPkList) {

        if (docPkList == null || docPkList.isEmpty()) {
            return null;
        }

        List<DocumentModel> documents =
                em.createQuery("from DocumentModel d where d.id in (:docPkList)")
                .setParameter("docPkList", docPkList)
                .getResultList();

        List<DocInfoModel> result = new ArrayList<>();
        for (DocumentModel docBean : documents) {
            // モデルからDocInfo へ必要なデータを移す
            // クライアントが DocInfo だけを利用するケースがあるため
            docBean.toDetuch();
            result.add(docBean.getDocInfoModel());
        }
        return result;
    }

    // DocumentModelのHiberbate search用インデックスを作成する
    public String makeDocumentModelIndex(String fid, long fromDocPk, int maxResults, long modelCount) {

        long fPk = getFacilityPk(fid);
        if (fPk == 0) {
            System.out.println("Hibernate Search: Illegal facility id.");
            return FINISHED;
        }

        final String fromSql = "from DocumentModel m where m.status = 'F' "
                + "and m.creator.facility.id = :fPk and m.id > :fromPk";
        final FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(em);

        // fromPk == 0の場合、まずはインデックスをクリアする
        if (fromDocPk == 0) {
            // これはサーバーに複数施設が同居してるとよくない
            //fullTextEntityManager.purgeAll(DocumentModel.class);
            purgeIndex(fPk);
        }

        // 総DocumentModel数を取得。進捗表示に使用
        if (modelCount == 0) {
//            modelCount = (Long)
//                    em.createQuery("select count(m) " + fromSql)
//                    .setParameter("fPk", fPk)
//                    .setParameter("fromPk", 0L)
//                    .getSingleResult();
            // use native query
            final String countSql = "select count(d.id) from d_document d, d_users u"
                    + " where d.creator_id = u.id and d.status = 'F' and u.facility_id = ?";
            BigInteger value = (BigInteger) em.createNativeQuery(countSql)
                    .setParameter(1, fPk)
                    .getSingleResult();
            modelCount = value.longValue();
        }
        if (modelCount == 0) {
            return FINISHED;
        }
        // idがfromPkより大きいDocumentModelをmaxResultsずつ取得
        List<DocumentModel> models =
                em.createQuery(fromSql + " order by m.id")
                .setParameter("fPk", fPk)
                .setParameter("fromPk", fromDocPk)
                .setMaxResults(maxResults)
                .getResultList();

        // 該当なしならnullを返して終了
        if (models == null || models.isEmpty()) {
            System.out.println("Hibernate Search: indexing task done.");
            return FINISHED;
        }
        // サーバーでの進捗状況表示
        //long fromId = models.get(0).getId();
        long toId = models.get(models.size() - 1).getId();
        //System.out.println("Hibernate Search: indexing from " + fromId + " to " + toId);

        // DocumentModelのインデックスを作成
        for (DocumentModel dm : models) {
            fullTextEntityManager.index(dm);
        }

        // 返り値は、最後のDocPk:総DocumentModel数
        return String.format("%d,%d", toId, modelCount);
    }

    // 施設のHibernage Searchインデックスを消去する
    private void purgeIndex(long fPk) {
        //System.out.println("Hibernate Search: purging indexes.");
        final String sql = "select m.id from DocumentModel m where m.creator.facility.id = :fPk";
        final FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(em);
        
        List<Long> pkList =
                em.createQuery(sql).setParameter("fPk", fPk).getResultList();
        for (long pk : pkList) {
            fullTextEntityManager.purge(DocumentModel.class, pk);
        }
    }

    // Hibernate searchを利用して全文検索する
    public List<PatientModel> getKarteFullTextSearch(String fid, long karteId, String text) {

        long fPk = getFacilityPk(fid);
        if (fPk == 0) {
            return null;
        }

        HashSet<PatientModel> patientModelSet = new HashSet<>();
        HashSet<Long> karteIdSet = new HashSet<>();

        // karteId == 0なら全患者から検索。PatientMemoModelも検索する
        if (karteId == 0) {
            List<Long> memoResult = getKarteOfMemo(fid, text);
            if (memoResult != null) {
                karteIdSet.addAll(memoResult);
            }
        }

        // DocumentModelを検索
        final FullTextEntityManager fullTextEntityManager = Search.getFullTextEntityManager(em);
        
        final Analyzer analyzer = fullTextEntityManager.getSearchFactory().getAnalyzer(DocumentModel.class);
        final org.apache.lucene.util.Version ver = org.apache.lucene.util.Version.LUCENE_36;
        QueryParser parser =
                new QueryParser(ver, "modules.beanBytes", analyzer);
        // http://lucene.jugem.jp/?eid=403
        parser.setAutoGeneratePhraseQueries(true);
        
        try {
            org.apache.lucene.search.Query luceneQuery = parser.parse(text);
        FullTextQuery fullTextQuery =
                fullTextEntityManager.createFullTextQuery(luceneQuery, DocumentModel.class);

        if (karteId != 0) {
            // karteIdでフィルタリング
            fullTextQuery.enableFullTextFilter("karteId").setParameter("karteId", karteId);
        } else {
            // facilityIdでフィルタリング
            fullTextQuery.enableFullTextFilter("facilityPk").setParameter("facilityPk", fPk);
        }

        // DocumentModelを取得
        List<DocumentModel> result = fullTextQuery.getResultList();
        // karteIdとDocIdの対応マップを作成
        HashMap<Long, List<Long>> karteIdDocIdMap = new HashMap<>();

        for (DocumentModel dm : result) {
            long kid = dm.getKarteBean().getId();
            List<Long> docIdList = karteIdDocIdMap.get(kid);
            if (docIdList == null) {
                docIdList = new ArrayList<>();
            }
            docIdList.add(dm.getId());
            karteIdDocIdMap.put(kid, docIdList);
        }

        // karteIdに対応するPatientModelを取得する
        karteIdSet.addAll(karteIdDocIdMap.keySet());

        for (long kid : karteIdSet) {
            KarteBean karte = em.find(KarteBean.class, kid);
            long patientId = karte.getPatient().getId();
            PatientModel pm = em.find(PatientModel.class, patientId);
            // PatientModelに検索語とDocIdを設定する。
            pm.setSearchText(text);
            List<Long> docIdList = karteIdDocIdMap.get(kid);
            if (docIdList != null) {
                HashSet<Long> pkSet = new HashSet<>();
                pkSet.addAll(docIdList);
                if (pm.getDocPkList() != null) {
                    pkSet.addAll(pm.getDocPkList());
                }
                pm.setDocPkList(new ArrayList(pkSet));
            }
            patientModelSet.add(pm);
        }
        } catch (ParseException ex) {
        }

        // 保険情報をとPvtDateを設定する
        List<PatientModel> ret = new ArrayList<>(patientModelSet);
        setInsuranceAndPvtDate(fid, ret);

        return ret;
    }

    private long getFacilityPk(String fid) {

        try {
            long facilityPk = (Long)
                    em.createQuery("select f.id from FacilityModel f where f.facilityId = :fid")
                    .setParameter("fid", fid)
                    .getSingleResult();
            return facilityPk;
        } catch (NoResultException e) {
        }
        return 0;
    }

    // Memo検索
    private List<Long> getKarteOfMemo(String fid, String text) {

        final String sql = "select p.karte.id from PatientMemoModel p " +
                "where p.memo like :memo " +
                "and p.creator.facility.facilityId = :fid";
        
        List<Long> karteIdList =
                em.createQuery(sql)
                .setParameter("memo", "%" + text + "%")
                .setParameter("fid", fid)
                .getResultList();
        return karteIdList;
    }

    // 保険情報とPvtDateを設定する
    // thx to Dr. pns
    private void setInsuranceAndPvtDate(String fid, List<PatientModel> pmList) {
        
        if (pmList == null || pmList.isEmpty()) {
            return;
        }

        final int CANCEL_PVT = 1 << 6;  // BIT_CANCEL = 6;
        final String sqlPvt = "from PatientVisitModel p where p.facilityId = :fid " +
                "and p.patient in (:pts) and p.status != :status order by p.pvtDate desc";
        

        List<PatientVisitModel> pvtList = (List<PatientVisitModel>) 
                em.createQuery(sqlPvt)
                .setParameter("fid", fid)
                .setParameter("pts", pmList)
                .setParameter("status", CANCEL_PVT)
                .getResultList();
        
        for (PatientModel pm : pmList) {
            // 患者の健康保険を設定する、ダミーだがｗ
            setHealthInsurances(pm);
            for (PatientVisitModel pvt : pvtList) {
                long id = pvt.getPatientModel().getId();
                if (pm.getId() == id) {
                    // PvtDateを設定する
                    pm.setPvtDate(pvt.getPvtDate());
                    break;
                }
            }
        }
    }

    // grep方式の全文検索
    public SearchResultModel getSearchResult(String fid, String searchText, 
            long fromModuleId, int maxResult, long totalCount, boolean progressCourseOnly) {

        final String fromSql = "from ModuleModel m where m.status = 'F' and m.creator.facility.facilityId = :fid";
        final String progressCourse = " and m.moduleInfo.entity = '" + IInfoModel.MODULE_PROGRESS_COURSE + "'";

        StringBuilder sb = new StringBuilder();
        sb.append(fromSql).append(" and m.id > :fromId");
        if (progressCourseOnly) {
            sb.append(progressCourse);
        }
        sb.append(" order by m.id");
        final String sql1 = sb.toString();
        
        sb= new StringBuilder();
        sb.append("select count(m) ").append(fromSql);
        if (progressCourseOnly) {
            sb.append(progressCourse);
        }
        final String sql2 = sb.toString();
        
        // 総モジュール数を取得、進捗具合に利用する
        if (totalCount == 0) {
            totalCount = (Long)
                    em.createQuery(sql2)
                    .setParameter("fid", fid)
                    .getSingleResult();
        }
        if (totalCount == 0) {
            return null;
        }
        
        HashSet<PatientModel> patientModelSet = new HashSet<>();
        HashSet<Long> karteIdSet = new HashSet<>();

        // ModuleModelを取得
        List<ModuleModel> modules =
                em.createQuery(sql1)
                .setParameter("fromId", fromModuleId)
                .setParameter("fid", fid)
                .setMaxResults(maxResult)
                .getResultList();

        if (modules.isEmpty()) {
            // 該当なしならnullを返す
            return null;
        }
        long toId = modules.get(modules.size() - 1).getId();

        // 検索語を含むkarteIdとDocIdの対応マップを作成
        HashMap<Long, List<Long>> karteIdDocIdMap = new HashMap<>();
        for (ModuleModel mm : modules) {
            // テキスト抽出
            //IModuleModel im = (IModuleModel) BeanUtils.xmlDecode(mm.getBeanBytes());
            IModuleModel im = ModuleBeanDecoder.getInstance().decode(mm.getBeanBytes());
            mm.setModel(im);
            String text;
            if (im instanceof ProgressCourse) {
                String xml = ((ProgressCourse) im).getFreeText();
                text = ModelUtils.extractText(xml);
            } else {
                text = im.toString();
            }
            // 検索語を含むかどうか
            if (text.contains(searchText)) {
                long docId = mm.getDocumentModel().getId();
                long karteId = mm.getKarteBean().getId();
                List<Long> docIdList = karteIdDocIdMap.get(karteId);
                if (docIdList == null) {
                    docIdList = new ArrayList<>();
                }
                docIdList.add(docId);
                karteIdDocIdMap.put(karteId, docIdList);
            }
        }

        // karteIdに対応するPatientModelを取得する
        karteIdSet.addAll(karteIdDocIdMap.keySet());
        for (long kid : karteIdSet) {
            KarteBean karte = em.find(KarteBean.class, kid);
            long patientId = karte.getPatient().getId();
            PatientModel pm = em.find(PatientModel.class, patientId);
            pm.setSearchText(searchText);
            List<Long> docIdList = karteIdDocIdMap.get(kid);

            if (docIdList != null) {
                HashSet<Long> pkSet = new HashSet<>();
                pkSet.addAll(docIdList);
                if (pm.getDocPkList() != null) {
                    pkSet.addAll(pm.getDocPkList());
                }
                pm.setDocPkList(new ArrayList(pkSet));
            }

            patientModelSet.add(pm);
        }

        // 保険情報をとPvtDateを設定する
        List<PatientModel> list = new ArrayList<>(patientModelSet);
        setInsuranceAndPvtDate(fid, list);

        // 結果を返す
        SearchResultModel ret = new SearchResultModel(toId, totalCount, list);
        return ret;
    }
    
    // 通信量を減らすためサーバー側で検査履歴を調べる
    public List<ExamHistoryModel> getExamHistory(String fid, long karteId, Date fromDate, Date toDate) {

        final List<String> entities = new ArrayList<>();
        entities.add(IInfoModel.ENTITY_RADIOLOGY_ORDER);
        entities.add(IInfoModel.ENTITY_PHYSIOLOGY_ORDER);
        entities.add(IInfoModel.ENTITY_LABO_TEST);

        List<ModuleModel> models = getModulesEntitySearch(fid, karteId, fromDate, toDate, entities);
        if (models == null) {
            return null;
        }
        // HashMapに登録しておく
        HashMap<Long, ExamHistoryModel> examMap = new HashMap<>();

        for (ModuleModel mm : models) {
            //mm.setModel((IModuleModel) BeanUtils.xmlDecode(mm.getBeanBytes()));
            mm.setModel(ModuleBeanDecoder.getInstance().decode(mm.getBeanBytes()));
            long docPk = mm.getDocumentModel().getId();
            ExamHistoryModel eh = examMap.get(docPk);
            if (eh == null) {
                eh = new ExamHistoryModel();
            }
            // 検査があるもののみ登録していく
            boolean hasExam = eh.putModuleModel(mm);
            if (hasExam) {
                examMap.put(docPk, eh);
            }
        }
        
        return new ArrayList<>(examMap.values());
    }
    
    // 通信量を減らすためにサーバー側で処方切れを調べる
    public List<PatientModel> getOutOfMedStockPatient(String fid, Date fromDate, Date toDate, int yoyuu) {
        
        final long karteId = 0;
        final List<String> entities = Collections.singletonList(IInfoModel.ENTITY_MED_ORDER);
        List<ModuleModel> mmList = getModulesEntitySearch(fid, karteId, fromDate, toDate, entities);
        if (mmList == null) {
            return null;
        }

        // ModuleModelを患者毎に分類
        HashMap<PatientModel, List<ModuleModel>> pmmmMap = new HashMap<>();
        for (ModuleModel mm : mmList){
            // いつもデコード忘れるｗ
            //mm.setModel((IModuleModel) BeanUtils.xmlDecode(mm.getBeanBytes()));
            mm.setModel(ModuleBeanDecoder.getInstance().decode(mm.getBeanBytes()));
            PatientModel pModel = mm.getKarteBean().getPatient();
            List<ModuleModel> list = pmmmMap.get(pModel);
            if (list == null){
                list = new ArrayList<>();
            }
            list.add(mm);
            pmmmMap.put(pModel, list);
        }
        // mmListは用なし。メモリ食いそうなのでnullにしてみるが、効果は？
        mmList.clear();
        //mmList = null;
        // 患者毎に処方切れかどうか調べる
        List<PatientModel> ret = new ArrayList<>();
        for (Map.Entry<PatientModel, List<ModuleModel>> entry : pmmmMap.entrySet()) {
            PatientModel model = entry.getKey();
            List<ModuleModel> list = entry.getValue();
            // 処方日と処方日数を列挙
            HashMap<Date, Integer> dateNumberMap = new HashMap<>();
            for (ModuleModel mm : list){
                // 処方日を取得
                Date date = mm.getDocumentModel().getStarted();
                // 処方日数を更新、外用や頓用の判断は省略ｗ
                int oldNumber = dateNumberMap.get(date) == null ? 0 : dateNumberMap.get(date);
                int newNumber = Integer.valueOf(((BundleMed) mm.getModel()).getBundleNumber());
                if (oldNumber < newNumber) {
                    dateNumberMap.put(date, newNumber);
                }
            }
            // 処方切れかどうかを判断
            Date oldestDate = new Date();
            Date lastDate = ModelUtils.AD1800;
            int totalBundleNumber = 0;
            for (Map.Entry<Date, Integer> entry1 : dateNumberMap.entrySet()) {
                Date date = entry1.getKey();
                int bundleNumber = entry1.getValue();
                if (date.before(oldestDate)) {
                    oldestDate = date;
                }
                if (date.after(lastDate)) {
                    lastDate = date;
                }
                totalBundleNumber = totalBundleNumber + bundleNumber;
            }
            GregorianCalendar now = new GregorianCalendar();
            GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(oldestDate);
            gc.add(GregorianCalendar.DATE, totalBundleNumber + yoyuu);
            // 処方切れの可能性があればPatientModelを追加
            if (now.after(gc)) {
                String pvtDate = ModelUtils.getDateTimeAsString(lastDate);
                model.setPvtDate(pvtDate);
                ret.add(model);
                // 患者の健康保険を取得する
                setHealthInsurances(model);
            }
        }

        return ret;
    }
    
    public List<InFacilityLaboItem> getInFacilityLaboItemList(String fid) {
        final String sql = "from InFacilityLaboItem i where i.laboCode = :fid";
        
        List<InFacilityLaboItem> list = 
                em.createQuery(sql).setParameter("fid", fid).getResultList();
        return list;
    }
    
    public long updateInFacilityLaboItem(String fid, List<InFacilityLaboItem> newList) {
        
        List<InFacilityLaboItem> oldList = getInFacilityLaboItemList(fid);
        // 削除されたものを探すして削除する
        for(InFacilityLaboItem oldItem : oldList) {
            boolean found = false;
            for (InFacilityLaboItem newItem : newList) {
                if (oldItem.getId() == newItem.getId()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                long pk = oldItem.getId();
                InFacilityLaboItem toDelete = em.find(InFacilityLaboItem.class, pk);
                em.remove(toDelete);
            }
        }
        // 変更されたものはmerge, 追加されたものはpersistする
        long added = 0;
        for (InFacilityLaboItem newItem : newList) {
            //newItem.setItemValue(null); // 検査値は消す
            long pk = newItem.getId();
            if (pk == 0) {
                em.persist(newItem);
                added++;
            } else {
                em.merge(newItem);
            }
        }
        return added;
    }
    
    
    // 電子点数表　未使用
    public String updateETensu1Table(List<ETensuModel1> list) {
        
        final String sql1 = "from ETensuModel1 e where e.srycd = :srycd and e.yukostymd = :yukostymd";
        int added = 0;
        int updated = 0;
        
        for (ETensuModel1 model : list) {
            try {
                ETensuModel1 exist = (ETensuModel1) 
                        em.createQuery(sql1)
                        .setParameter("srycd", model.getSrycd())
                        .setParameter("yukostymd", model.getYukostymd())
                        .getSingleResult();
                // 既存の上書き
                model.setId(exist.getId());
                em.merge(model);
                updated++;
            } catch (NoResultException e) {
                // 新規
                em.persist(model);
                added++;
            }
        }
        return String.format("%d,%d", added, updated);
    }
    
    private List<String> getETenRelatedSrycdList(Collection<String> srycds) {

        final String sql1 = "select distinct e.srycd from ETensuModel1 e where e.srycd in (:srycds)";

        if (srycds == null || srycds.isEmpty()) {
            return null;
        }
        
        List<String> list =
                em.createQuery(sql1)
                .setParameter("srycds", srycds)
                .getResultList();
        
        return list;
    }

    public String initSanteiHistory(String fid, long fromId, int maxResults, long totalCount) {
        
        final String sql1 = "from ModuleModel m "
                + "where m.moduleInfo.entity <> '" + IInfoModel.MODULE_PROGRESS_COURSE + "' "
                + "and m.status = 'F' and m.creator.facility.id = :fPk";
        //final String sql2 = "select count(m) " + sql1;
        final String sql3 = sql1 + " and m.id > :fromId order by m.id";
        final String sql4 = "from SanteiHistoryModel s "
                + "where s.moduleModel.id = :mid and s.srycd = :srycd "
                + "and s.itemIndex = :index";
        
        long fPk = getFacilityPk(fid);
        if (fPk == 0) {
            System.out.println("InitSanteiHistory: Illegal facility id.");
            return FINISHED;
        }
        
        // 総数を取得する
        if (totalCount == 0) {
//            totalCount = (Long) em.createQuery(sql2)
//                    .setParameter("fid", fid)
//                    .getSingleResult();
            // use native query
            final String countSql = "select count(m.id) from d_module m, d_users u"
                    + " where m.creator_id = u.id and m.entity <> 'progressCourse'"
                    + " and m.status = 'F' and u.facility_id = ?";
            BigInteger value = (BigInteger) em.createNativeQuery(countSql)
                    .setParameter(1, fPk)
                    .getSingleResult();
            totalCount = value.longValue();
        }
        // 0件ならFINESHEDを返して終了
        if (totalCount == 0) {
            return FINISHED;
        }
        
        // fromIdからModuleModelを取得する
        List<ModuleModel> mmList =
                em.createQuery(sql3)
                .setParameter("fPk", fPk)
                .setParameter("fromId", fromId)
                .setMaxResults(maxResults)
                .getResultList();
        
        // 該当なしならFINESHEDを返して終了
        if (mmList == null || mmList.isEmpty()) {
            return FINISHED;
        }
        
        long addedCount = 0;
        long updatedCount = 0;
        long toId = mmList.get(mmList.size() - 1).getId();
        
        // まずはsrycdをリストアップ
        Set<String> srycds = new HashSet<>();
        for (ModuleModel mm : mmList) {
            //mm.setModel((IModuleModel) BeanUtils.xmlDecode(mm.getBeanBytes()));
            mm.setModel(ModuleBeanDecoder.getInstance().decode(mm.getBeanBytes()));
            ClaimBundle cb = (ClaimBundle) mm.getModel();
            if (cb == null) {
                continue;
            }
            for (ClaimItem ci : cb.getClaimItem()) {
                String srycd = ci.getCode();
                if (srycd != null) {
                    srycds.add(srycd);
                }
            }
        }

        // syrcdsのうち電子点数表に関連するものを取得
        List<String> srycdList = getETenRelatedSrycdList(srycds);
        // 該当なしならリターン
        if (srycdList == null || srycdList.isEmpty()) {
            return String.format("%d,%d,%d,%d", toId, totalCount, addedCount, updatedCount);
        }
        
        // 各々のModuleModelを調べる
        for (ModuleModel mm : mmList) {
            ClaimBundle cb = (ClaimBundle) mm.getModel();
            if (cb == null) {
                continue;
            }
            int bundleNumber = parseInt(cb.getBundleNumber());
            for (int i = 0; i < cb.getClaimItem().length; ++i) {
                ClaimItem ci = cb.getClaimItem()[i];
                if (!srycdList.contains(ci.getCode())) {
                    continue;
                }
                int claimNumber = parseInt(ci.getNumber());
                int count = bundleNumber * claimNumber;
                try {
                    SanteiHistoryModel exist = (SanteiHistoryModel)
                            em.createQuery(sql4)
                            .setParameter("mid", mm.getId())
                            .setParameter("srycd", ci.getCode())
                            .setParameter("index", i)
                            .getSingleResult();
                    exist.setItemCount(count);
                    em.merge(exist);
                    updatedCount++;
                } catch (NoResultException e) {
                    SanteiHistoryModel history = new SanteiHistoryModel();
                    history.setSrycd(ci.getCode());
                    history.setItemCount(count);
                    history.setItemIndex(i);
                    history.setModuleModel(mm);
                    em.persist(history);
                    addedCount++;
                }
            }
        }
        
        return String.format("%d,%d,%d,%d", toId, totalCount, addedCount, updatedCount);
    }
    
    private int parseInt(String str) {

        int num = 1;
        try {
            num = Integer.valueOf(str);
        } catch (NumberFormatException e) {
        }
        return num;
    }
    
    public List<SanteiHistoryModel> getSanteiHistory(long karteId, Date fromDate, Date toDate, List<String> srycds) {
        
        List<SanteiHistoryModel> list;
        
        if (srycds == null) {
            final String sql = "from SanteiHistoryModel s where s.moduleModel.karte.id = :kId "
                    + "and :fromDate <= s.moduleModel.started and s.moduleModel.started < :toDate";

            list = (List<SanteiHistoryModel>) 
                    em.createQuery(sql)
                    .setParameter("kId", karteId)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .getResultList();
        } else {
            final String sql = "from SanteiHistoryModel s where s.moduleModel.karte.id = :kId "
                    + "and :fromDate <= s.moduleModel.started and s.moduleModel.started < :toDate "
                    + "and s.srycd in (:srycds)";
            
            list = (List<SanteiHistoryModel>) 
                    em.createQuery(sql)
                    .setParameter("kId", karteId)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .setParameter("srycds", srycds)
                    .getResultList();
        }
        
        // SanteiHistoryModelに算定日と名前を設定する
        for (SanteiHistoryModel shm : list) {
            ModuleModel mm = shm.getModuleModel();
            //mm.setModel((IModuleModel) BeanUtils.xmlDecode(mm.getBeanBytes()));
            mm.setModel((IModuleModel) ModuleBeanDecoder.getInstance().decode(mm.getBeanBytes()));
            ClaimBundle cb = (ClaimBundle) mm.getModel();
            shm.setItemName(cb.getClaimItem()[shm.getItemIndex()].getName());
            shm.setSanteiDate(mm.getStarted());
        }
        return list;
    }
    
    public String getSanteiCount(long karteId, Date fromDate, Date toDate, List<String> srycds) {

        Map<String, Integer> map = new HashMap<>();
        for (String srycd : srycds) {
            map.put(srycd, 0);
        }
        
        List<SanteiHistoryModel> list =getSanteiHistory(karteId, fromDate, toDate, srycds);
        for (SanteiHistoryModel shm : list) {
            String srycd = shm.getSrycd();
            int count = map.get(srycd) + 1;
            map.put(srycd, count);
        }
        
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String srycd = entry.getKey();
            String num = String.valueOf(entry.getValue());
            if (!first) {
                sb.append(",");
            } else {
                first = false;
            }
            sb.append(srycd).append(",").append(num);
        }
        return sb.toString();
    }
    
    public List<List<RpModel>> getRpModelList(long karteId, Date fromDate, Date toDate, boolean lastOnly) {
        
        final String yakuzaiClassCode = "2";    // 薬剤のclaim class code
        
        final String sql1 = 
                "from DocumentModel d where d.karte.id=:karteId "
                + "and d.started >= :fromDate and d.started < :toDate "
                + "and d.docInfo.hasRp = true and d.status='F' "
                + "order by d.started desc";
        final String sql2 =
                "from ModuleModel m where m.document.id = :docPk "
                + "and m.moduleInfo.entity = '" + IInfoModel.ENTITY_MED_ORDER + "'";
        
        List<List<RpModel>> ret = new ArrayList<>();
        
        List<DocumentModel> docList;
        
        if (lastOnly) {
            docList = em.createQuery(sql1)
                    .setParameter("karteId", karteId)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .setMaxResults(1)
                    .getResultList();
        } else {
            docList = em.createQuery(sql1)
                    .setParameter("karteId", karteId)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .getResultList();
        }

        for (DocumentModel doc : docList) {

            List<ModuleModel> mmList =
                    em.createQuery(sql2).setParameter("docPk", doc.getId()).getResultList();
            if (mmList.isEmpty()) {
                continue;
            }
            
            List<RpModel> rpModelList = new ArrayList<>();
            for (ModuleModel mm : mmList) {
                //mm.setModel((IModuleModel) BeanUtils.xmlDecode(mm.getBeanBytes()));
                mm.setModel(ModuleBeanDecoder.getInstance().decode(mm.getBeanBytes()));
                BundleMed bm = (BundleMed) mm.getModel();
                String rpDay = bm.getBundleNumber();
                String adminSrycd = bm.getAdminCode();
                Date rpDate = mm.getDocumentModel().getStarted();
                for (ClaimItem ci : bm.getClaimItem()) {
                    if (!yakuzaiClassCode.equals(ci.getClassCode())){
                        continue;
                    }
                    // 薬剤なら
                    String drugSrycd = ci.getCode();
                    String drugName = ci.getName();
                    String rpNumber = ci.getNumber();
                    RpModel rpModel = new RpModel(drugSrycd, drugName, adminSrycd, rpNumber, rpDay, rpDate);
                    rpModelList.add(rpModel);
                }
            }
            ret.add(rpModelList);
        }

        return ret;
    }
    
    // UserPropertyを保存する
    public int postUserProperties(List<UserPropertyModel> list) {
    
        final String sql1 = "from UserPropertyModel u where u.key = :key and u.facilityId = :fid and u.userId = :uid";
        final String sql2 = "delete " + sql1;
        
        for (UserPropertyModel model : list) {
            try {
                // 既存のpropertyを取得する
                UserPropertyModel exist = (UserPropertyModel) 
                        em.createQuery(sql1)
                        .setParameter("key", model.getKey())
                        .setParameter("fid", model.getFacilityId())
                        .setParameter("uid", model.getUserId())
                        .getSingleResult();
                // あれば更新
                model.setId(exist.getId());
                em.merge(model);
            } catch (NoResultException ex) {
                // なければ追加
                em.persist(model);
            } catch (NonUniqueResultException ex) {
                // ダブってたら一旦削除してから追加
                int num = em.createQuery(sql2)
                        .setParameter("key", model.getKey())
                        .setParameter("fid", model.getFacilityId())
                        .setParameter("uid", model.getUserId())
                        .executeUpdate();
                System.out.println("delete :" + num);
                em.persist(model);
            }
        }
        return list.size();
    }
    
    // UserPropertyを取得する
    public List<UserPropertyModel> getUserProperties(String userId) {
        
        int pos = userId.indexOf(":");
        String fid = userId.substring(0, pos);
        String userIdAsLocal = userId.substring(pos + 1);
        
        final String sql = "from UserPropertyModel u where u.facilityId = :fid and (u.userId = :fid or u.userId = :uid)";
        
        List<UserPropertyModel> list = (List<UserPropertyModel>)
                em.createQuery(sql)
                .setParameter("fid", fid)
                .setParameter("uid", userIdAsLocal)
                .getResultList();

        return list;
    }
    
    // サーバーで利用する施設共通プロパティー(userId = facilityId)を取得
    public Map<String, String> getUserPropertyMap(String fid) {
        
        final String sql = "from UserPropertyModel u where u.userId = :fid";
        List<UserPropertyModel> list = (List<UserPropertyModel>)
                em.createQuery(sql)
                .setParameter("fid", fid)
                .getResultList();
        
        Map<String, String> propMap = new HashMap<>();
        for (UserPropertyModel model : list) {
            propMap.put(model.getKey(), model.getValue());
        }
        return propMap;
    }
    
    // JMARI番号からfidを探す
    public String getFidFromJmari(String jmariCode) {

        String fid = IInfoModel.DEFAULT_FACILITY_OID;
        try {
            UserPropertyModel model = (UserPropertyModel) 
                    em.createQuery("from UserPropertyModel u where u.key = :key and u.value = :value")
                    .setParameter("key", "jmariCode")
                    .setParameter("value", jmariCode)
                    .getSingleResult();
            fid = model.getFacilityId();
        } catch (NoResultException | NonUniqueResultException ex) {
        }
        return fid;
    }
    
    // サーバーでPVT server socketを開くかどうか
    public boolean usePvtServletServer() {
        
        long c = (Long) 
                em.createQuery("select count(*) from UserPropertyModel u where u.key = :key and u.value = :value")
                .setParameter("key", "pvtOnServer")
                .setParameter("value", String.valueOf(true))
                .getSingleResult();
        return c > 0;
    }

    // 入院モデルを取得する
    public List<AdmissionModel> getAdmissionList(String fid, String patientId) {

        final String sql =
                "from AdmissionModel a where a.patient.patientId = :ptId and a.patient.facilityId = :fid "
                + "order by a.id desc";

        // 既存の入院モデルを取得する
        List<AdmissionModel> list = (List<AdmissionModel>) 
                em.createQuery(sql)
                .setParameter("ptId", patientId)
                .setParameter("fid", fid)
                .getResultList();
        
        return list;
    }
    
    // 入院モデルを更新する
    public int updateAdmissionModels(List<AdmissionModel> list) {
        
        int cnt = 0;
        
        for (AdmissionModel model : list) {
            try {
                em.merge(model);
                cnt++;
            } catch (Exception ex) {
            }
        }
        return cnt;
    }
    
    // 入院モデルを削除する。使うな危険？
    public int deleteAdmissionModels(List<Long> ids) {
        
        int cnt = 0;
        
        for (long id : ids) {
            try {
                // 関連するDocumentModelを取得
                List<DocumentModel> docList = (List<DocumentModel>)
                        em.createQuery("from DocumentModel d where d.admission.id = :id")
                        .setParameter("id", id)
                        .getResultList();
                // それぞれのDocumentModelのAdmissionModelを設定解除する
                for (DocumentModel docModel : docList) {
                    docModel.getDocInfoModel().setAdmissionModel(null);
                }
                // AdmissionModelを削除する
                AdmissionModel exist = em.find(AdmissionModel.class, id);
                em.remove(exist);
                cnt++;
            } catch (Exception ex) {
            }
        }
        
        return cnt;
    }

    // 現時点で過去日になった仮保存カルテを取得する
    public List<PatientModel> getTempDocumentPatients(Date fromDate, long userPk) {

        final String sql = "from DocumentModel d where d.status='T' "
                + "and d.started <= :fromDate and d.creator.id = :id";
        
        List<DocumentModel> documents = (List<DocumentModel>)
                em.createQuery(sql)
                .setParameter("fromDate", fromDate)
                .setParameter("id", userPk)
                .getResultList();

        Set<PatientModel> set = new HashSet<>();

        for (DocumentModel doc : documents) {
            PatientModel pm = doc.getKarteBean().getPatientModel();
            set.add(pm);
        }
        
        // 患者の健康保険を取得する。忘れがちｗ
        setHealthInsurances(set);

        return new ArrayList<>(set);
    }
    
    // 保険情報は後でクライアントから取りに行く
    // http://mdc.blog.ocn.ne.jp/blog/2013/02/post_f69f.html
    // ダミーの保険情報を設定する。LAZY_FETCHを回避する
    // com.fasterxml.jackson.databind.JsonMappingException: could not initialize proxy - no Session
    private void setHealthInsurances(Collection<PatientModel> list) {
        if (list != null && !list.isEmpty()) {
            for (PatientModel pm : list) {
                setHealthInsurances(pm);
            }
        }
    }
    
    private void setHealthInsurances(PatientModel pm) {
        if (pm != null) {
            pm.setHealthInsurances(null);
        }
    }
    
    public List<IndicationModel> getIndicationList(String fid, List<String> srycds) {
        
        List<IndicationModel> list;
        
        if (!srycds.isEmpty()) {
            final String sql = "from IndicationModel i where i.fid = :fid and i.srycd in (:srycds)";
            list = (List<IndicationModel>) em.createQuery(sql)
                    .setParameter("fid", fid)
                    .setParameter("srycds", srycds)
                    .getResultList();
        } else {
            final String sql = "from IndicationModel i where i.fid = :fid";
            list = (List<IndicationModel>) em.createQuery(sql)
                    .setParameter("fid", fid)
                    .getResultList();
        }
        // lazy fetch
        for (IndicationModel model : list) {
            model.getIndicationItems().size();
        }

        return list;
    }
    
    public int importIndicationModels(String fid, List<IndicationModel> list) {
        
        final String sql = "select i.id from IndicationModel i where i.fid = :fid";
 
        // 既存のものを消す
        List<Long> ids = em.createQuery(sql)
                .setParameter("fid", fid)
                .getResultList();
        for (long id : ids) {
            IndicationModel exist = em.find(IndicationModel.class, id);
            em.remove(exist);
        }
        em.flush();

        // persistする
        for (IndicationModel model : list) {
            em.persist(model);
        }

        return list.size();
    }
    
    public IndicationModel getIndicationModel(String fid, String srycd) {

        final String sql = "from IndicationModel i where i.fid = :fid and i.srycd = :srycd";

        try {
            IndicationModel model = (IndicationModel) em.createQuery(sql)
                    .setParameter("fid", fid)
                    .setParameter("srycd", srycd)
                    .getSingleResult();

            // lazy fetch
            model.getIndicationItems().size();

            return model;
        } catch (NoResultException ex) {
        }
        return null;
    }
    
    public int addIndicationModels(List<IndicationModel> list) {
        
        int cnt = 0;
        for (IndicationModel model : list) {
            try {
                model.setLock(false);
                em.persist(model);
                cnt++;
            } catch (Exception ex) {
            }
        }
        return cnt;
    }
    
    public long updateIndicationModel(IndicationModel model) {
        try {
            model.setLock(false);
            em.merge(model);
            return model.getId();
        } catch (Exception ex) {
        }
        return -1;
    }
    
    public long removeIndicationModel(long id) {
        // 分離オブジェクトは remove に渡せないので対象を検索する
        IndicationModel target = em.find(IndicationModel.class, id);
        if (target != null && !target.isLock()) {
            em.remove(target);
            return target.getId();
        }
        return -1;
    }
    
    // 指定月の医師と担当患者のリストを取得する
    public List<DrPatientIdModel> getDrPatientIdModelList(String ym) {
        
        final String sql = "from DocumentModel d"
                + " where d.started >= :from and d.started < :to"
                + " and d.status='F'";
        
        try {
            SimpleDateFormat frmt = new SimpleDateFormat("yyyyMMdd");
            GregorianCalendar gc = new GregorianCalendar();
            gc.setTime(frmt.parse(ym + "01"));
            Date from = gc.getTime();
            gc.add(GregorianCalendar.MONTH, 1);
            Date to = gc.getTime();
            
            // 文書を取得する
            List<DocumentModel> docList = em.createQuery(sql)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .getResultList();
            
            // 医師ごとに分類する
            Map<UserModel, Set<String>> map = new HashMap<>();
            for (DocumentModel docModel : docList) {
                UserModel user = docModel.getCreator();
                Set<String> set = map.get(user);
                if (set == null) {
                    set = new HashSet<>();
                    map.put(user, set);
                }
                String ptId = docModel.getKarteBean().getPatientModel().getPatientId();
                set.add(ptId);
            }
            
            // 返却モデルを作成する
            List<DrPatientIdModel> ret = new ArrayList<>();
            for (Map.Entry<UserModel, Set<String>> entry : map.entrySet()) {
                DrPatientIdModel model = new DrPatientIdModel();
                model.setUserModel(entry.getKey());
                model.setPatientIdList(new ArrayList<>(entry.getValue()));
                ret.add(model);
            }
            map.clear();
            
            return ret;
            
        } catch (java.text.ParseException ex) {
        }
        
        return null;
    }
}
