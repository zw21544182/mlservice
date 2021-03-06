package cn.ml_tech.mx.mlservice.Service;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import org.litepal.LitePal;
import org.litepal.crud.DataSupport;
import org.litepal.tablemanager.Connector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.ml_tech.mx.mlservice.DAO.AuditTrail;
import cn.ml_tech.mx.mlservice.DAO.AuditTrailEventType;
import cn.ml_tech.mx.mlservice.DAO.AuditTrailInfoType;
import cn.ml_tech.mx.mlservice.DAO.BottlePara;
import cn.ml_tech.mx.mlservice.DAO.CameraParams;
import cn.ml_tech.mx.mlservice.DAO.DetectionDetail;
import cn.ml_tech.mx.mlservice.DAO.DetectionReport;
import cn.ml_tech.mx.mlservice.DAO.DevDynamicParams;
import cn.ml_tech.mx.mlservice.DAO.DevParam;
import cn.ml_tech.mx.mlservice.DAO.DevUuid;
import cn.ml_tech.mx.mlservice.DAO.DrugContainer;
import cn.ml_tech.mx.mlservice.DAO.DrugControls;
import cn.ml_tech.mx.mlservice.DAO.DrugInfo;
import cn.ml_tech.mx.mlservice.DAO.DrugParam;
import cn.ml_tech.mx.mlservice.DAO.Factory;
import cn.ml_tech.mx.mlservice.DAO.FactoryControls;
import cn.ml_tech.mx.mlservice.DAO.LoginLog;
import cn.ml_tech.mx.mlservice.DAO.Modern;
import cn.ml_tech.mx.mlservice.DAO.MotorControl;
import cn.ml_tech.mx.mlservice.DAO.P_Module;
import cn.ml_tech.mx.mlservice.DAO.P_Operator;
import cn.ml_tech.mx.mlservice.DAO.P_Source;
import cn.ml_tech.mx.mlservice.DAO.P_SourceOperator;
import cn.ml_tech.mx.mlservice.DAO.P_UserTypePermission;
import cn.ml_tech.mx.mlservice.DAO.PermissionHelper;
import cn.ml_tech.mx.mlservice.DAO.SpecificationType;
import cn.ml_tech.mx.mlservice.DAO.SystemConfig;
import cn.ml_tech.mx.mlservice.DAO.Tray;
import cn.ml_tech.mx.mlservice.DAO.User;
import cn.ml_tech.mx.mlservice.DAO.UserType;
import cn.ml_tech.mx.mlservice.IMlService;
import cn.ml_tech.mx.mlservice.Util.AlertDialog;
import cn.ml_tech.mx.mlservice.Util.CommonUtil;
import cn.ml_tech.mx.mlservice.Util.LogUtil;
import cn.ml_tech.mx.mlservice.Util.MlMotorUtil;
import cn.ml_tech.mx.mlservice.Util.PdfUtil;
import cn.ml_tech.mx.mlservice.Util.PermissionUtil;

import static android.content.ContentValues.TAG;
import static java.lang.Long.parseLong;
import static org.litepal.crud.DataSupport.find;
import static org.litepal.crud.DataSupport.findAll;
import static org.litepal.crud.DataSupport.findBySQL;
import static org.litepal.crud.DataSupport.where;

public class MotorServices extends Service {
    private List<DevParam> devParamList;
    private AlertDialog alertDialog;
    private MlMotorUtil mlMotorUtil;
    private PermissionUtil permissionUtil;
    private Intent intent;
    private String user_id = "";
    private long userid;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat audittraformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long typeId;
    private Handler handler;
    private PdfUtil pdfUtil;
    private List<P_SourceOperator> p_sourceOperators;
    private Cursor cursor;

    public MotorServices() {
        initMemberData();
        getDevParams();
        Log.d(TAG, "MotorServices: " + String.valueOf(devParamList.size()));
    }

    private void initMemberData() {
        devParamList = new ArrayList<DevParam>();
    }

    public List<DevParam> getDevParams() {
        devParamList.clear();
        devParamList = DataSupport.select("id", "paramName", "paramValue", "type")
                // .where("paramName=?",paramName)
                // .where("type=?",String.valueOf(type))
                .order(" id asc")
                .find(DevParam.class);
        return devParamList;
    }

    private void log(String message) {
        Log.v("zw", message);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("Received start command.");
        return START_STICKY;
    }

    public List<DrugControls> mDrugControls = new ArrayList<>();

    private final IMlService.Stub mBinder = new IMlService.Stub() {


        @Override
        public void addMotorControl(MotorControl mControl) throws RemoteException {
            log("Received addMotorControl.");
        }

        @Override
        public void saveBottlePara(BottlePara bottlepara) throws RemoteException {
            //之后的代码有待完成
        }

        @Override
        public boolean checkAuthority(String name, String password) throws RemoteException {

            List<cn.ml_tech.mx.mlservice.DAO.User> users = where("userName = ? and userPassword = ?", name, password).find(cn.ml_tech.mx.mlservice.DAO.User.class);
            log(users.size() + "userssize");
            if (users.size() != 0) {
                user_id = users.get(0).getUserId();
                userid = users.get(0).getId();
                typeId = users.get(0).getUsertype_id();
            }
            LoginLog loginLog = new LoginLog();
            loginLog.setUser_id(userid);
            loginLog.setLoginDateTime(new Date());
            loginLog.save();
            return users.size() == 0 ? false : true;
        }

        @Override
        public boolean addDrugInfo(String name, String enName, String pinYin, int containterId, int factoryId, String id) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 8, typeId)) {
                alertDialog.callback("无权执行", "");
                return false;
            }
            if (!permissionUtil.checkPermission(3, 8, typeId)) {
                alertDialog.callback("暂无增加权限", "");
                return false;
            }
            DrugInfo drugInfo = new DrugInfo();
            log(name + " " + enName + " " + pinYin + " containerid" + containterId + " factoryId" + factoryId);
            drugInfo.setName(name.trim());
            drugInfo.setEnname(enName.trim());
            drugInfo.setPinyin(pinYin.trim());
            drugInfo.setDrugcontainer_id(containterId);
            drugInfo.setFactory_id(factoryId);
            drugInfo.setCreatedate(new Date());
            if (Integer.parseInt(id) == 0) {
                drugInfo.save();
            } else {
                drugInfo.setId(parseLong(id));
                drugInfo.saveOrUpdate("id = ?", String.valueOf(drugInfo.getId()));
            }
            alertDialog.callback("保存成功", "");
            return true;
        }

        @Override
        public boolean addFactory(String name, String address, String phone, String fax, String mail, String contactName, String contactPhone, String webSite, String province_code, String city_code, String area_code) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 12, typeId)) {
                alertDialog.callback("无权执行", "");
                return false;
            }
            if (!permissionUtil.checkPermission(3, 12, typeId)) {
                alertDialog.callback("暂无增加权限", "");
                return false;
            }

            Factory factory = new Factory();
            factory.setName(name);
            factory.setAddress(address);
            factory.setPhone(phone);
            factory.setFax(fax);
            factory.setMail(mail);
            factory.setContactName(contactName);
            factory.setContactPhone(contactPhone);
            factory.setProvince_code(province_code);
            factory.setCity_code(city_code);
            factory.setArea_code(area_code);
            factory.save();
            return true;
        }

        @Override
        public List<FactoryControls> queryFactoryControl() throws RemoteException {
            List<FactoryControls> factoryControlses = new ArrayList<>();
            List<Factory> factories = findAll(Factory.class);
            for (Factory factory : factories) {
                FactoryControls factoryControls = new FactoryControls();
                factoryControls.setId(factory.getId());
                factoryControls.setName(factory.getName());
                factoryControls.setAddress(factory.getAddress());
                factoryControls.setPhone(factory.getPhone());
                factoryControls.setFax(factory.getFax());
                factoryControls.setMail(factory.getMail());
                factoryControls.setContactName(factory.getContactName());
                factoryControls.setContactPhone(factory.getContactPhone());
                factoryControls.setProvince_code(factory.getProvince_code());
                factoryControls.setCity_code(factory.getCity_code());
                factoryControls.setArea_code(factory.getArea_code());
                factoryControlses.add(factoryControls);
            }
            return factoryControlses;
        }


        @Override
        public List<DrugControls> queryDrugControl() throws RemoteException {
            mDrugControls.clear();
            List<DrugInfo> mDrugInfo = DataSupport.order("id desc").find(DrugInfo.class);

            for (int i = 0; i < mDrugInfo.size(); i++) {
                log(mDrugInfo.get(i).toString());
                List<DrugContainer> list = DataSupport.select(new String[]{"id", "name"}).where("id=?", String.valueOf(mDrugInfo.get(i).getDrugcontainer_id())).find(DrugContainer.class);
                String drugBottleType = list.get(0).getName();
                List<Factory> lists = DataSupport.select(new String[]{"*"}).where("id=?", String.valueOf(mDrugInfo.get(i).getFactory_id())).find(Factory.class);
                String factory_name = lists.get(0).getName();
                DrugControls drugControls = new DrugControls(mDrugInfo.get(i).getName(), drugBottleType, factory_name, mDrugInfo.get(i).getPinyin()
                        , mDrugInfo.get(i).getEnname(), mDrugInfo.get(i).getId());
                mDrugControls.add(drugControls);
            }
            return mDrugControls;
        }

        @Override
        public List<User> getUserList() throws RemoteException {
            List<User> listDao = new ArrayList<>();
            if (!permissionUtil.checkPermission(2, 20, typeId)) {
                alertDialog.callback("暂无权限查询", "");
                return listDao;
            }
            if (permissionUtil.checkPermission(9, 20, typeId)) {
                listDao = DataSupport.where("id = ?", userid + "").find(User.class);
                return listDao;
            }
            listDao = DataSupport.where("usertype_id >= ?", typeId + "").find(User.class);
            return listDao;
        }

        @Override
        public List<SpecificationType> getSpecificationTypeList() throws RemoteException {
            List<SpecificationType> typeList = new ArrayList<>();
            Connector.getDatabase();
            List<SpecificationType> types = findAll(SpecificationType.class);
            for (SpecificationType type : types) {
                SpecificationType specificationType = new SpecificationType();
                specificationType.setId(type.getId());
                specificationType.setName(type.getName());
                typeList.add(specificationType);
            }
            return typeList;
        }

        @Override
        public List<DevParam> getDeviceParamList(int type) throws RemoteException {
            List<DevParam> list = new ArrayList<DevParam>();
            for (DevParam param : devParamList) {
                if (type == param.getType()) list.add(param);
            }
            return list;

        }

        @Override
        public void setDeviceParamList(List<DevParam> list) throws RemoteException {
            for (DevParam param : list
                    ) {
                param.saveOrUpdate("paramName=?", param.getParamName());
            }
            getDevParams();
        }

        @Override
        public void setDrugParamList(List<DrugParam> list) throws RemoteException {
            log("add drugParas");
            for (DrugParam drugParam :
                    list
                    ) {
                log(drugParam.toString());
            }
            for (DrugParam drugParam :
                    list) {
                drugParam.saveOrUpdate("druginfo_id = ? and paramname = ?", String.valueOf(drugParam.getDruginfo_id()), drugParam.getParamname());
            }
        }

        @Override
        public double getDeviceParams(String paramName, int type) throws RemoteException {
            for (DevParam param : devParamList) {
                if (paramName.equals(param.getParamName()) && type == param.getType())
                    return param.getParamValue();
            }
            return 0;
        }

        @Override
        public DevUuid getDeviceManagerInfo() throws RemoteException {
            DevUuid devUuid = new DevUuid();
            devUuid = DataSupport.findFirst(DevUuid.class);
            return devUuid;
        }

        @Override
        public boolean setDeviceManagerInfo(DevUuid info) throws RemoteException {
            boolean flag = true;
            info.saveOrUpdate("id=?", String.valueOf(info.getId()));
            return flag;
        }

        @Override
        public void getTrayIcId(int type) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 21, typeId)) {
                alertDialog.callback("无权执行", "");
                return;
            }
            if (!permissionUtil.checkPermission(3, 21, typeId)) {
                alertDialog.callback("暂无权限增加", "");
                return;
            }

            if (intent == null)
                intent = new Intent();
            mlMotorUtil.getTrayId(alertDialog, intent, type);

        }

        @Override
        public List<Tray> getTrayList() throws RemoteException {
            List<Tray> trayList = findAll(Tray.class);
            return trayList;
        }

        @Override
        public Tray getTray(int id) throws RemoteException {
            Tray tray = find(Tray.class, id);
            return tray;
        }

        @Override
        public boolean setTray(Tray tray) throws RemoteException {

            if (!permissionUtil.checkPermission(8, 21, typeId) && tray.getId() == 0) {
                alertDialog.callback("暂无权限执行", "");
                return false;
            }

            if (!permissionUtil.checkPermission(3, 21, typeId) && tray.getId() == 0) {
                alertDialog.callback("暂无权限增加", "");
                return false;
            }
            if (!permissionUtil.checkPermission(4, 21, typeId) && tray.getId() != 0) {
                alertDialog.callback("暂无权限修改", "");
                return false;
            }

            tray.saveOrUpdate("displayId=?", String.valueOf(tray.getDisplayId()));
            return tray.isSaved();
        }

        @Override
        public boolean delTray(Tray tray) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 21, typeId)) {
                alertDialog.callback("无权执行", "");
                return false;
            }
            if (!permissionUtil.checkPermission(5, 21, typeId)) {
                alertDialog.callback("暂无权限删除", "");
                return false;
            }
            int r = 0;
            r = DataSupport.deleteAll(Tray.class, "displayId=? and icId=?", String.valueOf(tray.getDisplayId()), tray.getIcId());
            if (r > 0) return true;
            else return false;
        }

        @Override
        public int setSystemConfig(List<SystemConfig> list) throws RemoteException {
            int count = 0;
            for (SystemConfig config : list
                    ) {
                if (config.saveOrUpdate("paramName=?", config.getParamName())) count++;
            }
            return count;
        }

        @Override
        public int setCameraParam(CameraParams config) throws RemoteException {
            int count = 0;
            log(config.getParamName() + config.getParamValue());
            if (config.saveOrUpdate("paramName=?", config.getParamName())) count++;
            return count;
        }

        @Override
        public List<SystemConfig> getSystemConfig() throws RemoteException {
            List<SystemConfig> listConfig = findAll(SystemConfig.class);
            return listConfig;
        }

        @Override
        public List<DetectionReport> getDetectionReportList(int reportId) throws RemoteException {

            return null;
        }

        @Override
        public List<CameraParams> getCameraParams() throws RemoteException {
            List<CameraParams> listConfig = findAll(CameraParams.class);

            return listConfig;
        }

        @Override
        public List<AuditTrailInfoType> getAuditTrailInfoType() throws RemoteException {
            List<AuditTrailInfoType> eventTypes = new ArrayList<>();
            eventTypes = findAll(AuditTrailInfoType.class);
            return eventTypes;
        }

        @Override
        public List<AuditTrailEventType> getAuditTrailEventType() throws RemoteException {
            List<AuditTrailEventType> eventTypes = new ArrayList<>();
            eventTypes = findAll(AuditTrailEventType.class);
            return eventTypes;
        }

        @Override
        public List<AuditTrail> getAuditTrail(String starttime, String stoptime, String user, int event_id, int info_id) throws RemoteException {
            List<AuditTrail> auditTrails = new ArrayList<>();
            auditTrails = DataSupport.where("event_id = ? and info_id = ? and username = ?", event_id + "", info_id + "", user).find(AuditTrail.class);

            try {
                long start = Long.parseLong(dateFormat.format(format.parse(starttime)));
                long end = Long.parseLong(dateFormat.format(format.parse(stoptime)));
                Log.d("zw", "start " + start + " end " + end);
                for (int i = 0; i < auditTrails.size(); i++) {
                    AuditTrail auditTrail = auditTrails.get(i);
                    long current = Long.parseLong(dateFormat.format(format.parse(auditTrail.getTime())));
                    Log.d("zw", "current " + current);
                    if (current < start || current > end) {
                        auditTrails.remove(i);
                        i--;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            return auditTrails;
        }

        @Override
        public List<DrugControls> queryDrugControlByInfo(String drugname, String pinyin, String enname, int page) throws RemoteException {
            mDrugControls.clear();
            List<DrugInfo> mDrugInfo = new ArrayList<>();
            log("page" + page);
            mDrugInfo = DataSupport.where("name like ? and enname like ? and pinyin like ?", drugname + "%", enname + "%", pinyin + "%").order("id desc").find(DrugInfo.class);
            if (page != -1) {
                mDrugInfo = DataSupport.where("name like ? and enname like ? and pinyin like ?", drugname + "%", enname + "%", pinyin + "%").limit(20).offset((page - 1) * 20).order("id desc").find(DrugInfo.class);
            }
            for (int i = 0; i < mDrugInfo.size(); i++) {
                log(mDrugInfo.get(i).toString());
                List<DrugContainer> list = DataSupport.select(new String[]{"id", "name"}).where("id=?", String.valueOf(mDrugInfo.get(i).getDrugcontainer_id())).find(DrugContainer.class);
                String drugBottleType = list.get(0).getName();
                List<Factory> lists = DataSupport.select(new String[]{"*"}).where("id=?", String.valueOf(mDrugInfo.get(i).getFactory_id())).find(Factory.class);
                String factory_name = lists.get(0).getName();
                DrugControls drugControls = new DrugControls(mDrugInfo.get(i).getName(), drugBottleType, factory_name, mDrugInfo.get(i).getPinyin()
                        , mDrugInfo.get(i).getEnname(), mDrugInfo.get(i).getId());
                mDrugControls.add(drugControls);
            }
            return mDrugControls;
        }

        @Override
        public void deleteDrugInfoById(int id) throws RemoteException {
            log(id + "id");
            DataSupport.delete(DrugInfo.class, id);
        }

        @Override
        public List<DrugContainer> getDrugContainer() throws RemoteException {
            List<DrugContainer> drugContainers = new ArrayList<>();
            drugContainers = DataSupport.findAll(DrugContainer.class);
            return drugContainers;
        }


        @Override
        public List<DrugParam> getDrugParamById(int id) throws RemoteException {
            List<DrugParam> drugParams = new ArrayList<>();
            drugParams = DataSupport.where("druginfo_id = ?", String.valueOf(id)).find(DrugParam.class);
            for (DrugParam drugParam :
                    drugParams
                    ) {
                log(drugParam.toString());
            }
            return drugParams;
        }

        /**
         * 遮光验证
         * @param drug_id 药品id 为零表示新建药品
         * @param location 遮光位置
         * @throws RemoteException
         */
        @Override
        public void Validate(int drug_id, int location) throws RemoteException {
            log("Validate location" + location + " drug_id" + drug_id);
            //调用底层jni 以下代码为模拟数据
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    try {
                        Thread.sleep(500);
                        //模拟操作随机数被二整除为遮光验证成功
                        log("sucess");
                        intent = new Intent();
                        intent.setAction("com.enterbottle");
                        intent.putExtra("state", "Validate");
                        intent.putExtra("paratype", 2);//参数类型
                        intent.putExtra("colornum", 20);//色差系数
                        sendBroadcast(intent);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }.start();
        }

        @Override
        public List<DetectionReport> queryDetectionReport(String detectionSn, String drugInfo, String factoryName, String detectionNumber, String detectionBatch, String startTime, String stopTime, int page) throws RemoteException {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            long start = 0, end = 0, current = 0;
//            Log.d("zw", "detectionSn " + detectionSn + " drugInfo " + drugInfo + " factoryName " + factoryName + " detectionNumber " + detectionNumber + " detectionBatch " + detectionBatch + " startTime " + startTime + " stopTime " + stopTime);
            List<DetectionReport> detectionReports = new ArrayList<>();
            detectionReports = DataSupport.where("drugName like ? and factoryName like ? and detectionSn like ? and drugBottleType = ? and detectionBatch like ?", drugInfo + "%", factoryName + "%", detectionSn + "%", detectionNumber.trim(), detectionBatch + "%").find(DetectionReport.class);
            if (page != -1) {
                detectionReports = DataSupport.where("drugName like ? and factoryName like ? and detectionSn like ? and drugBottleType = ? and detectionBatch like ?", drugInfo + "%", factoryName + "%", detectionSn + "%", detectionNumber.trim(), detectionBatch + "%").limit(20).offset(((page - 1) * 20)).find(DetectionReport.class);

            }
//            Log.d("zw", "detectionReports size " + detectionReports.size());
            try {
                for (int i = 0; i < detectionReports.size(); i++) {
                    DetectionReport detectionReport = detectionReports.get(i);
                    start = Long.parseLong(dateFormat.format(simpleDateFormat.parse(startTime)));
                    end = Long.parseLong(dateFormat.format(simpleDateFormat.parse(stopTime)));
                    current = Long.parseLong(dateFormat.format(detectionReport.getDate()));
                    if (current < start || current > end) {
                        detectionReports.remove(i);
                        i--;
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }


            return detectionReports;
        }

        @Override
        public void enterBottle() throws RemoteException {
            //调用jni进瓶，以下代码为模拟操作
            log("enterBottle");
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                        //模拟操作随机数被二整除为进瓶成功
                        log("sucess");
                        intent = new Intent();
                        intent.setAction("com.enterbottle");
                        intent.putExtra("state", "sucess");
                        sendBroadcast(intent);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }

        /**旋瓶测试
         * @param num 每分钟转数
         * @throws RemoteException
         */
        @Override
        public void bottleTest(int num) throws RemoteException {
            //jni层调用
            log("每分钟转数" + num);
            alertDialog.callback("托环不匹配", "");
        }

        @Override
        public void leaveBottle() throws RemoteException {
            //jni层 出瓶
            log("出瓶");
            new Thread() {
                @Override
                public void run() {
                    super.run();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    //模拟操作随机数被二整除为进瓶成功
                    log("sucess");
                    intent = new Intent();
                    intent.setAction("com.enterbottle");
                    intent.putExtra("state", "leavebottlesucess");
                    sendBroadcast(intent);

                }
            }.start();
        }

        @Override
        public void deteleDetectionInfoById(long id) throws RemoteException {
            DataSupport.delete(DetectionReport.class, id);
            DataSupport.deleteAll(DetectionDetail.class, "detectionreport_id = ?", id + "");
        }

        @Override
        public void deleteDrugParamById(int id) throws RemoteException {
            if (!permissionUtil.checkPermission(5, 9, typeId)) {
                alertDialog.callback("暂无权限删除", "");
                return;
            }
            DataSupport.deleteAllAsync(DrugParam.class, "druginfo_id = ? ", String.valueOf(id));
        }


        @Override
        public void startCheck(int drug_id, final int checkNum, int rotateNum, final String detectionNumber, String detectionBatch, final boolean isFirst, final String detectionSn) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 10, typeId)) {
                alertDialog.callback("无权执行", "check");
                return;
            }
            mlMotorUtil.checkDrug(drug_id, checkNum, rotateNum, detectionNumber,
                    detectionBatch, isFirst, detectionSn, userid
                    , user_id, CommonUtil.AUTOEBUG_CHECK);
        }


        @Override
        public String getDetectionSn() throws RemoteException {
            String result = "";
            String uuid = getDevUuid();
            String date = getDetectionSnDate();
            result = uuid + date;
            log(result);
            return result;
        }

        @Override
        public DetectionDetail getLastDetail() throws RemoteException {
            return DataSupport.findLast(DetectionDetail.class);
        }


        @Override
        public List<DetectionDetail> queryDetectionDetailByReportId(long id) throws RemoteException {
            List<DetectionDetail> detectionDetails = new ArrayList<>();
            detectionDetails = DataSupport.where("detectionreport_id = ?", id + "").order("id asc").find(DetectionDetail.class);
            Log.d("zw", detectionDetails.size() + "size");
            return detectionDetails;
        }

        @Override
        public DetectionReport getLastReport() throws RemoteException {
            DetectionReport detectionReport = null;
            detectionReport = DataSupport.findLast(DetectionReport.class);
            return detectionReport;
        }

        @Override
        public DrugControls queryDrugControlsById(long id) throws RemoteException {
            DrugInfo drugInfo = DataSupport.find(DrugInfo.class, id);
            List<DrugContainer> list = DataSupport.select(new String[]{"id", "name"}).where("id=?", String.valueOf(drugInfo.getDrugcontainer_id())).find(DrugContainer.class);
            String drugBottleType = list.get(0).getName();
            List<Factory> lists = DataSupport.select(new String[]{"*"}).where("id=?", String.valueOf(drugInfo.getFactory_id())).find(Factory.class);
            String factory_name = lists.get(0).getName();
            DrugControls drugControls = new DrugControls(drugInfo.getName(), drugBottleType, factory_name, drugInfo.getPinyin()
                    , drugInfo.getEnname(), drugInfo.getId());
            return drugControls;
        }

        @Override
        public DevUuid getDevUuidInfo() throws RemoteException {

            return DataSupport.find(DevUuid.class, 1);
        }

        @Override
        public List<DetectionReport> getAllDetectionReports() throws RemoteException {
            return DataSupport.findAll(DetectionReport.class);
        }

        @Override
        public List<UserType> getAllUserType() throws RemoteException {
            return DataSupport.findAll(UserType.class);
        }

        @Override
        public void updateUser(User user) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 20, typeId)) {
                alertDialog.callback("无权操作", "");
                return;
            }
            if (user.getId() != userid) {
                if (permissionUtil.checkPermission(9, 20, typeId)) {
                    alertDialog.callback("仅能操作自身账号", "");
                    return;
                }
            }
            if (user.getId() == 0 && !permissionUtil.checkPermission(3, 20, typeId)) {
                alertDialog.callback("暂无权限增加", "");
                return;
            }
            if (user.getId() != 0 && !permissionUtil.checkPermission(4, 20, typeId)) {
                alertDialog.callback("暂无权限修改", "");
                return;
            }
            if (user.getId() != 0) {
                user.saveOrUpdate("id = ?", user.getId() + "");
            } else {
                String createdate = format.format(new Date());
                user.setCreateDate(createdate);
                user.save();
            }
            if (DataSupport.find(User.class, userid) != null)
                typeId = DataSupport.find(User.class, userid).getUsertype_id();
        }

        @Override
        public UserType getUserTypeById(long id) throws RemoteException {
            return DataSupport.find(UserType.class, id);
        }

        @Override
        public void deleteUserById(long id) throws RemoteException {
            if (!permissionUtil.checkPermission(8, 20, typeId)) {
                alertDialog.callback("无权操作", "");
                return;
            }
            if (id != userid) {
                if (permissionUtil.checkPermission(9, 20, typeId)) {
                    alertDialog.callback("仅能操作自身账号", "");
                    return;
                }
            }
            if (!permissionUtil.checkPermission(5, 20, typeId)) {
                alertDialog.callback("暂无删除权限", "");
                return;
            }
            if (id == userid) {
                alertDialog.callback("无法删除自身账号", "");
                return;
            }
            DataSupport.delete(User.class, id);
        }

        @Override
        public void addAudittrail(int event_id, int info_id, String value, String mark) throws RemoteException {
            AuditTrail auditTrail = new AuditTrail();
            auditTrail.setTime(audittraformat.format(new Date()));
            auditTrail.setUsername(user_id);
            auditTrail.setEvent_id(event_id);
            auditTrail.setInfo_id(info_id);
            auditTrail.setValue(value);
            auditTrail.setMark(mark);
            auditTrail.setUserauto_id(0);
            auditTrail.save();
        }

        @Override
        public List<String> getAllTableName() throws RemoteException {
            List<String> result = new ArrayList<>();
            Cursor cursor = findBySQL("SELECT name FROM sqlite_master WHERE type='table' order by name");
            while (cursor.moveToNext()) {
                if (!cursor.getString(cursor.getColumnIndex("name")).trim().equals("android_metadata"))
                    result.add(cursor.getString(cursor.getColumnIndex("name")));
            }
            return result;
        }

        @Override
        public List<String> getFieldByName(String name) throws RemoteException {
            List<String> result = new ArrayList<>();
            Cursor cursor = findBySQL("pragma table_info(" + name + ")");
            while (cursor.moveToNext()) {
                result.add(cursor.getString(cursor.getColumnIndex("name")));
            }
            return result;
        }

        @Override
        public Modern getDataByTableName(String tableName, int page) throws RemoteException {
            Modern modern = new Modern();
            Map<Integer, List<String>> dataMap = new HashMap<>();
            List<String> field = new ArrayList<>();
            Cursor cursor = DataSupport.findBySQL("pragma table_info(" + tableName + ")");
            while (cursor.moveToNext()) {
                field.add(cursor.getString(cursor.getColumnIndex("name")));
            }
            cursor = DataSupport.findBySQL("select * from " + tableName + " limit 12 offset " + (page - 1) * 12);
            int i = 0;
            while (cursor.moveToNext()) {
                List<String> data = new ArrayList<>();
                for (int itme = 0; itme < field.size(); itme++) {
                    data.add(cursor.getString(cursor.getColumnIndex(field.get(itme))));
                }
                dataMap.put(i, data);
                Log.d("zw", " i = " + i);
                i++;
            }
            modern.setMap(dataMap);
            return modern;
        }

        /**
         * @param tableName
         * @param modern
         * @throws RemoteException
         */
        @Override
        public void updateData(String tableName, Modern modern) throws RemoteException {
            Log.d("zw", "updatedata");
            Map<Integer, List<String>> data = modern.getMap();
            List<String> field = new ArrayList<>();
            Cursor cursor = DataSupport.findBySQL("pragma table_info(" + tableName + ")");
            while (cursor.moveToNext()) {
                field.add(cursor.getString(cursor.getColumnIndex("name")));
            }
            for (int i = 0; i < data.size(); i++) {
                cursor = DataSupport.findBySQL("select * from " + tableName + " where id = " + data.get(i).get(0));
                if (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    for (int s = 0; s < field.size(); s++) {
                        values.put(field.get(s), data.get(i).get(s));
                    }
                    DataSupport.updateAll(tableName, values, field.get(0) + " = ?", data.get(i).get(0) + "");
                } else {
                    Log.d("zw", "insert into ");
                    SQLiteDatabase sqLiteDatabase = LitePal.getDatabase();
                    ContentValues value = new ContentValues();
                    for (int c = 0; c < field.size(); c++) {
                        value.put(field.get(c), data.get(i).get(c));
                    }
                    sqLiteDatabase.insert(tableName, null, value);
                }
            }
        }

        @Override
        public void deleteData(String tableName, List<String> id) throws RemoteException {

            for (int i = 0; i < id.size(); i++) {
                Log.d("zw", " id " + id.get(i));
                DataSupport.deleteAll(tableName, "id = ?", id.get(i));
            }
        }


        @Override
        public boolean canAddType(String typeName) throws RemoteException {
            List<UserType> userTypes = DataSupport.where("typeName = ?", typeName.trim()).find(UserType.class);
            return userTypes.isEmpty();
        }

        @Override
        public void addUserType(String typeName, List<String> sourceoperateId) throws RemoteException {
            UserType userType = new UserType();
            userType.setTypeName(typeName);
            userType.setTypeId(DataSupport.findLast(UserType.class).getTypeId() + 1);
            userType.save();
            long typeId = userType.getTypeId();
            for (String soid :
                    sourceoperateId) {
                long id = Long.parseLong(soid);
                MotorServices.this.addPermission(id, typeId);
            }
        }


        @Override
        public List<DevParam> getDevParamByType(int type) throws RemoteException {
            List<DevParam> devParams = new ArrayList<>();
            devParams = DataSupport.where("type = ?", type + "").find(DevParam.class);
            return devParams;
        }

        @Override
        public void saveDevParam(List<DevParam> devParams) throws RemoteException {
            for (DevParam devParam :
                    devParams) {
                if (devParam.getId() == 0)
                    devParam.save();
                else
                    devParam.update(devParam.getId());
            }
        }

        @Override
        public void saveDetectionReport(DetectionReport detectionReport) throws RemoteException {
            detectionReport.update(detectionReport.getId());
        }

        @Override
        public void deleteDevParamByIds(List<String> ids) throws RemoteException {
            for (String s :
                    ids) {
                DataSupport.delete(DevParam.class, Long.parseLong(s));
            }
        }

        @Override
        public void backUpDevParam() throws RemoteException {
            List<DevParam> devParams = DataSupport.findAll(DevParam.class);
            DataSupport.deleteAll(DevDynamicParams.class, "1=1");
            DevDynamicParams devDynamicParams = new DevDynamicParams();
            for (DevParam devParam :
                    devParams) {
                devDynamicParams.setId(devParam.getId());
                devDynamicParams.setParamName(devParam.getParamName());
                devDynamicParams.setParamValue(devParam.getParamValue());
                devDynamicParams.setType(devParam.getType());
                devDynamicParams.save();
                devDynamicParams.clearSavedState();
            }
        }

        @Override
        public void recoveryParam() throws RemoteException {
            List<DevDynamicParams> devParams = DataSupport.findAll(DevDynamicParams.class);
            DataSupport.deleteAll(DevParam.class, "1=1");
            DevParam devDynamicParams = new DevParam();
            for (DevDynamicParams devParam :
                    devParams) {
                devDynamicParams.setId(devParam.getId());
                devDynamicParams.setParamName(devParam.getParamName());
                devDynamicParams.setParamValue(devParam.getParamValue());
                devDynamicParams.setType(devParam.getType());
                devDynamicParams.save();
                devDynamicParams.clearSavedState();
            }
        }

        @Override
        public long getUserId() throws RemoteException {
            return userid;
        }

        @Override
        public long geTypeId() throws RemoteException {
            return typeId;
        }

        @Override
        public void deleteDetectionReportsById(List<String> ids) throws RemoteException {
            for (String id :
                    ids) {
//                Log.d("zw", "ddetectionReport id " + id);
                DataSupport.delete(DetectionReport.class, Long.parseLong(id.trim()));
                DataSupport.deleteAll(DetectionDetail.class, "detectionreport_id = ?", id);

            }
        }

        @Override
        public void operateMlMotor(int type, int dir, double avgspeed, int distance) throws RemoteException {
//            Log.d("zw", "type " + type + " dir" + dir + " avgspeed " + avgspeed + " distance " + distance);
            mlMotorUtil.operateMlMotor(type, dir, avgspeed, distance);
        }

        @Override
        public void operateLight(boolean isOn) throws RemoteException {
            if (isOn) {
                mlMotorUtil.getMlMotor().motorLightOn();
            } else {
                mlMotorUtil.getMlMotor().motorLightOff();
            }
        }

        @Override
        public void rotaleBottle(int speed) throws RemoteException {
            Log.d("Tagzw", "speed " + speed);
            mlMotorUtil.operateRotale(speed);
        }

        @Override
        public List<DevParam> getAllDevParam() throws RemoteException {
            List<DevParam> devParams = new ArrayList<>();
            devParams = DataSupport.findAll(DevParam.class);
            return devParams;
        }

        @Override
        public void saveAllDevParam(List<DevParam> devParams) throws RemoteException {
            for (DevParam devParam : devParams) {
                devParam.saveOrUpdate("paramname = ?", devParam.getParamName());
                Log.d("ww", "name " + devParam.getParamName() + " value " + devParam.getParamValue());
            }
        }

        @Override
        public void motorReset(int num) throws RemoteException {
            mlMotorUtil.motorReset(num);
        }

        @Override
        public void autoDebug(int type, int num) throws RemoteException {
            Log.d("zw", "server autoDebug");
            if (!permissionUtil.checkPermission(8, 1, typeId)) {
                alertDialog.callback("无权执行", "");
            }
            mlMotorUtil.autoCheck(handler, type, num);

        }

        @Override
        public int getNumByTableName(String name) throws RemoteException {
            return DataSupport.findBySQL("select * from " + name).getCount();
        }

        @Override
        public void OperateReportInfo(List<String> reportIds, String type) throws RemoteException {
            Log.d("zw", "OperateReportInfo " + type);
            if (type.trim().equals(CommonUtil.OPERATEREPORT_DELETE)) {
                Log.d("zw", "delete ids " + reportIds);
                for (String s :
                        reportIds) {

                    if (!DataSupport.where("id = ?", s).find(DetectionReport.class).get(0).ispdfdown()) {
                        alertDialog.callback("数据不完整", "");
                        return;
                    }
                }
                for (String s :
                        reportIds) {
                    DataSupport.delete(DetectionReport.class, Long.parseLong(s.trim()));
                }
                alertDialog.callback("删除成功", "updateui");
            }
            if (type.trim().equals(CommonUtil.OPERATEREPORT_OUTPUT)) {
                if (!permissionUtil.checkPermission(8, 11, typeId)) {
                    alertDialog.callback("无权执行", "");
                }
                if (!permissionUtil.checkPermission(6, 11, typeId)) {
                    alertDialog.callback("暂无权限导出", "");
                    return;
                }

                Log.d("zw", "导出数据");
                Log.d("zw", reportIds.toString());
                pdfUtil.startOutput(reportIds, handler);
            }
        }

        @Override
        public List<PermissionHelper> getPermissionInfoByType(int userTypeId) throws RemoteException {
            List<PermissionHelper> permissionHelpers = new ArrayList<>();
            List<P_SourceOperator> p_sourceOperators = DataSupport.findAll(P_SourceOperator.class);
            if (p_sourceOperators != null && p_sourceOperators.size() != 0) {
                for (P_SourceOperator p_sourceOperator :
                        p_sourceOperators) {
                    PermissionHelper permissionHelper = new PermissionHelper();
                    permissionHelper.setP_sourceOperator(p_sourceOperator);
                    permissionHelper.setCanOperate(permissionUtil.checkPermission(p_sourceOperator.getP_operator_id(),
                            p_sourceOperator.getP_source_id(), userTypeId));
                    permissionHelpers.add(permissionHelper);
                }
            }
            return permissionHelpers;
        }

        @Override
        public void operatePermission(long operateId, long sourcesId, long userTypeId, boolean isAdd) throws RemoteException {
            if (isAdd) {
                permissionUtil.operatePermission(operateId, sourcesId, userTypeId, PermissionUtil.TYPE.ADD);
            } else {
                permissionUtil.operatePermission(operateId, sourcesId, userTypeId, PermissionUtil.TYPE.DELETE);
            }
            String sourceTitle = DataSupport.find(P_Source.class, sourcesId).getTitle();
            String operateTitle = DataSupport.find(P_Operator.class, operateId).getTitle();
            String userTypeName = DataSupport.where("typeid = ?", userTypeId + "").find(UserType.class).get(0).getTypeName();
            if (permissionUtil.checkPermission(operateId, sourcesId, userTypeId)) {
                Log.d("zw", "用户类型: " + userTypeName + " 资源类型: " + sourceTitle + " 操作类型: " + operateTitle + " " +
                        "权限添加成功");
            } else {
                Log.d("zw", "用户类型: " + userTypeName + " 资源类型: " + sourceTitle + " 操作类型: " + operateTitle + " " +
                        "权限删除成功");
            }
        }

        @Override
        public List<PermissionHelper> getPermissionInfo() throws RemoteException {
            List<PermissionHelper> permissionHelpers = new ArrayList<>();
            List<P_SourceOperator> p_sourceOperators = DataSupport.findAll(P_SourceOperator.class);
            if (p_sourceOperators != null && p_sourceOperators.size() != 0) {
                for (P_SourceOperator p_sourceOperator :
                        p_sourceOperators) {
                    PermissionHelper permissionHelper = new PermissionHelper();
                    permissionHelper.setP_sourceOperator(p_sourceOperator);
                    permissionHelper.setCanOperate(false);
                    permissionHelpers.add(permissionHelper);
                }
            }
            return permissionHelpers;
        }

        @Override
        public List<PermissionHelper> getPermissionByType(long parentId) throws RemoteException {
            List<PermissionHelper> permissionHelpers = new ArrayList<>();
            if (p_sourceOperators == null)
                p_sourceOperators = new ArrayList<>();
            p_sourceOperators.clear();
            if (parentId != 0) {
                cursor = DataSupport.findBySQL("select * from p_sourceoperator where " +
                        "p_source_id in (select id from p_source where parentid = " + parentId + ") and " +
                        "p_operator_id = 1 order by p_source_id asc");
            } else {
                cursor = DataSupport.findBySQL("select * from p_sourceoperator where " +
                        "p_source_id in (select id from p_source where url not like '%/%') and " +
                        "p_operator_id = 1 order by id asc");
            }
            while (cursor.moveToNext()) {
                P_SourceOperator p_sourceOperator = new P_SourceOperator();
                p_sourceOperator.setId(cursor.getLong(cursor.getColumnIndex("id")));
                p_sourceOperator.setP_operator_id(cursor.getLong(cursor.getColumnIndex("p_operator_id")));
                p_sourceOperator.setP_source_id(cursor.getLong(cursor.getColumnIndex("p_source_id")));
                p_sourceOperators.add(p_sourceOperator);
            }
            if (p_sourceOperators != null && p_sourceOperators.size() != 0) {
                for (P_SourceOperator p_sourceOperator :
                        p_sourceOperators) {
                    PermissionHelper permissionHelper = new PermissionHelper();
                    permissionHelper.setP_sourceOperator(p_sourceOperator);
                    permissionHelper.setCanOperate(permissionUtil.checkPermission(p_sourceOperator.getP_operator_id(), p_sourceOperator.getP_source_id(), typeId));
                    permissionHelpers.add(permissionHelper);
                }
            }
            return permissionHelpers;
        }

        @Override
        public boolean isRename(String name) throws RemoteException {
            return DataSupport.where("typename = ?", name).find(UserType.class).size() == 0 ? false : true;
        }

        @Override
        public void addNewUserType(String typeName, List<PermissionHelper> permissionHelpers) throws RemoteException {
            UserType userType = new UserType();
            userType.setTypeName(typeName);
            userType.setTypeId(DataSupport.findLast(UserType.class).getTypeId() + 1);
            userType.save();
            long userTypeId = userType.getTypeId();
            for (PermissionHelper permissionHelper : permissionHelpers) {
                if (permissionHelper.isCanOperate()) {
                    permissionUtil.operatePermission(permissionHelper.getP_sourceOperator().getP_operator_id(),
                            permissionHelper.getP_sourceOperator().getP_source_id(), userTypeId, PermissionUtil.TYPE.ADD);
                } else {
                    permissionUtil.operatePermission(permissionHelper.getP_sourceOperator().getP_operator_id(),
                            permissionHelper.getP_sourceOperator().getP_source_id(), userTypeId, PermissionUtil.TYPE.DELETE);
                }
            }
            alertDialog.callback("用户类型添加成功", "initAddType");
        }


    };

    @Override
    public IBinder onBind(Intent intent) {
        initData();
        Connector.getDatabase();
        if (!DataSupport.isExist(UserType.class)) {
            UserType userType = new UserType();
            userType.setTypeId(0);
            userType.setTypeName("超级管理员");
            userType.save();
            userType.clearSavedState();
            userType.setTypeId(1);
            userType.setTypeName("管理员");
            userType.save();
            userType.clearSavedState();
            userType.setTypeId(2);
            userType.setTypeName("操作员");
            userType.save();
        }
        if (!DataSupport.isExist(User.class)) {
            User user = new User();
            user.setUsertype_id(1);
            user.setUserId("Admin");
            user.setIsEnable(1);
            user.setUserName("AdminName");
            user.setUserPassword("Admin");
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            user.setCreateDate(simpleDateFormat.format(new Date()));
            user.save();
            user.clearSavedState();
            user.setUsertype_id(0);
            user.setUserId("zw1025");
            user.setIsEnable(1);
            user.setUserName("zw1025");
            user.setUserPassword("zw1025");
            user.setCreateDate(simpleDateFormat.format(new Date()));
            user.save();
        }
        if (LogUtil.isApkInDebug(this)) {
            if (!DataSupport.isExist(AuditTrailEventType.class)) {
                AuditTrailEventType eventType = new AuditTrailEventType();
                eventType.setName("Add");
                eventType.save();
                eventType.clearSavedState();
                eventType.setName("Delete");
                eventType.save();
                eventType.clearSavedState();
                eventType.setName("Modify");
                eventType.save();
                eventType.clearSavedState();
                eventType.setName("Query");
                eventType.save();
                eventType.clearSavedState();
                eventType.setName("Other");
                eventType.save();
                eventType.clearSavedState();
            }
            if (!DataSupport.isExist(AuditTrail.class)) {
                AuditTrail auditTrail = new AuditTrail();
                auditTrail.setMark("testmark");
                auditTrail.setValue("testvalue");
                auditTrail.setTime("2016-06-29");
                auditTrail.setEvent_id(1);
                auditTrail.setInfo_id(1);
                auditTrail.setUsername("testusername");
                auditTrail.save();
                auditTrail.clearSavedState();
                auditTrail.setMark("admin");
                auditTrail.setValue("admin");
                auditTrail.setTime("2017-07-15");
                auditTrail.setEvent_id(2);
                auditTrail.setInfo_id(1);
                auditTrail.setUsername("zw1025");
                auditTrail.save();
            }
            if (!DataSupport.isExist(AuditTrailInfoType.class)) {
                AuditTrailInfoType infoType = new AuditTrailInfoType();
                infoType.setName("Information");
                infoType.save();
                infoType.clearSavedState();
                infoType.setName("Question");
                infoType.save();
                infoType.clearSavedState();
                infoType.setName("Warning");
                infoType.save();
                infoType.clearSavedState();
                infoType.setName("Critical");
                infoType.save();
                infoType.clearSavedState();
                infoType.setName("Other");
                infoType.save();
                infoType.clearSavedState();
            }
            if (!DataSupport.isExist(DrugContainer.class)) {
                DrugContainer drugContainer = new DrugContainer();
                drugContainer.setName("安瓿瓶1ml");
                drugContainer.setDiameter(0);
                drugContainer.setTray_id(8);
                drugContainer.setSrctime(4.0);
                drugContainer.setStptime(3.0);
                drugContainer.setChannelvalue1(50);
                drugContainer.setChannelvalue2(2.5);
                drugContainer.setChannelvalue3(1.5);
                drugContainer.setChannelvalue4(2.7);
                drugContainer.setDelaytime(0.11);
                drugContainer.setImagetime(0.01);
                drugContainer.setShadeparam(22.0);
                drugContainer.setRotatespeed(4500);
                drugContainer.setSendparam(2.0);
                drugContainer.save();
                drugContainer.clearSavedState();
                drugContainer.setName("安瓿瓶2ml");
                drugContainer.setDiameter(0);
                drugContainer.setTray_id(2);
                drugContainer.setSrctime(2.0);
                drugContainer.setStptime(2.0);
                drugContainer.setChannelvalue1(20);
                drugContainer.setChannelvalue2(2.2);
                drugContainer.setChannelvalue3(1.2);
                drugContainer.setChannelvalue4(2.2);
                drugContainer.setShadeparam(2.0);
                drugContainer.setRotatespeed(4500);
                drugContainer.setSendparam(2.0);
                drugContainer.setDelaytime(0.12);
                drugContainer.setImagetime(0.01);
                drugContainer.save();
            }
            if (!DataSupport.isExist(DrugInfo.class)) {
                DrugInfo drugInfo = new DrugInfo();
                for (int i = 0; i < 73; i++) {
                    drugInfo.setName("weiyi" + i);
                    drugInfo.setPinyin("piniyni" + i);
                    drugInfo.setEnname("enname" + i);
                    drugInfo.setCreatedate(new Date());
                    drugInfo.setFactory_id(1);
                    drugInfo.setUser_id(1);
                    drugInfo.setDrugcontainer_id(1);
                    drugInfo.save();
                    drugInfo.clearSavedState();
                }

            }
            if (!DataSupport.isExist(SystemConfig.class)) {
                SystemConfig config = new SystemConfig();
                config.setParamName("LastDetDate");
                config.setParamValue("20170619");
                config.save();
                config.clearSavedState();
                config.setParamName("LastDetNum");
                config.setParamValue("2");
                config.save();
                config.clearSavedState();
                config.setParamName("IpAddress");
                config.setParamValue("192.168.0.145");
                config.save();
                config.clearSavedState();
                config.setParamName("NetMask");
                config.setParamValue("255.255.255.0");
                config.save();
                config.clearSavedState();
                config.setParamName("DiskUsedMax");
                config.setParamValue("90.0");
                config.save();
                config.clearSavedState();
                config.setParamName("GlassTimeMax");
                config.setParamValue("3.0");
                config.save();
                config.clearSavedState();
                config.setParamName("GlassCountMax");
                config.setParamValue("20.0");
                config.save();
                config.clearSavedState();
                config.setParamName("FiberMax");
                config.setParamValue("2");
                config.save();
                config.clearSavedState();
                config.setParamName("FloatMax");
                config.setParamValue("2");
                config.save();
                config.clearSavedState();
                config.setParamName("StandardDrugRotateCount");
                config.setParamValue("3");
                config.save();
                config.clearSavedState();
                config.setParamName("userRemeber");
                config.setParamValue("0");
                config.save();
                config.clearSavedState();
                config.setParamName("userName");
                config.setParamValue("admin");
                config.save();
                config.clearSavedState();
                config.setParamName("programVersionNum");
                config.setParamValue("1000109");
                config.save();
                config.clearSavedState();
                config.setParamName("programVersionStr");
                config.setParamValue("1.0.1.11");
                config.save();
                config.clearSavedState();
                config.setParamName("databaseVersion");
                config.setParamValue("5");
                config.save();
                config.clearSavedState();
                config.setParamName("databaseVersion");
                config.setParamValue("5");
                config.save();
                config.clearSavedState();
                config.setParamName("userPass");
                config.setParamValue("#&/+,");
                config.save();
                config.clearSavedState();
                config.setParamName("LastStandardId");
                config.setParamValue("4");
                config.save();

            }
        }
        if (!DataSupport.isExist(Factory.class)) {
            Factory factory = new Factory();
            factory.setAddress("asdfgh");
            factory.setName("湖南药厂");
            factory.save();
        }

        if (!DataSupport.isExist(DevUuid.class)) {
            DevUuid devUuid = new DevUuid();
            devUuid.setUserAbbreviation("admin");
            devUuid.setUserName("admin");
            devUuid.setDevID("YA2C1CY00R03002");
            devUuid.setDevModel("ML-AMIXH-2.5");
            devUuid.setDevName("光散射法全自动可见异物检测仪");
            devUuid.setDevFactory("浙江猛凌机电科技有限公司");
            devUuid.setDevDateOfProduction(new Date());
            devUuid.save();
        }
        if (!DataSupport.isExist(CameraParams.class)) {
            CameraParams cameraParams = new CameraParams();
            cameraParams.setParamName("flashGain");
            cameraParams.setParamValue(4.0);
            cameraParams.save();
            cameraParams.clearSavedState();
            cameraParams.setParamName("x_addr_end");
            cameraParams.setParamValue(1280.0);
            cameraParams.save();
            cameraParams.clearSavedState();
            cameraParams.setParamName("y_addr_end");
            cameraParams.setParamValue(768.0);
            cameraParams.save();
            cameraParams.clearSavedState();
            cameraParams.setParamName("Exposure");
            cameraParams.setParamValue(4.0);
            cameraParams.save();
            cameraParams.clearSavedState();
            cameraParams.setParamName("fpgaGain");
            cameraParams.setParamValue(0.0);
            cameraParams.save();
            cameraParams.clearSavedState();
            cameraParams.setParamName("globalGain");
            ;
            cameraParams.clearSavedState();
            cameraParams.setParamName("digitalGain");
            cameraParams.setParamValue(86.0);
            cameraParams.setParamValue(1.0);
            cameraParams.save();
            cameraParams.save();
            cameraParams.clearSavedState();
            cameraParams.setParamName("fpgaFilter");
            cameraParams.setParamValue(0.0);
            cameraParams.save();
        }
        if (!DataSupport.isExist(P_Operator.class)) {
            SQLiteDatabase sqLiteDatabase = LitePal.getDatabase();
            String path = "data.txt";
            executeAssetsSQL(sqLiteDatabase, path);

        }
        if (!DataSupport.isExist(P_Module.class)) {
            P_Module p_module = new P_Module();
            p_module.setTitle("仪器参数标定");
            p_module.setUrl("btnStdDrugDecetion");
            p_module.save();
            p_module.clearSavedState();
            p_module.setTitle("样品检测");
            p_module.setUrl("btnDrugDecetion");
            p_module.save();
            p_module.clearSavedState();
            p_module.setUrl("btnResultDetail");
            p_module.setTitle("检测数据查询");
            p_module.save();
            p_module.clearSavedState();
            p_module.setTitle("系统参数维护");
            p_module.setUrl("btnSystemSetUp");
            p_module.save();
        }
        return mBinder;
    }

    private void initData() {
        mlMotorUtil = MlMotorUtil.getInstance(this);
        alertDialog = new AlertDialog();
        alertDialog.setContext(this);
        pdfUtil = PdfUtil.getInstance(this);
        permissionUtil = PermissionUtil.getInstance(this);
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 1:
                        alertDialog.callback("自动检测完成", "");
                        break;
                    case PdfUtil.SUCESS:
                        alertDialog.callback("数据导出完成", "");
                }

            }
        };

    }

    private void executeAssetsSQL(SQLiteDatabase db, String schemaName) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(getAssets()
                    .open(schemaName)));
            String line;
            String buffer = "";
            while ((line = in.readLine()) != null) {
                buffer += line;
                if (line.trim().endsWith(";")) {
                    db.execSQL(buffer.replace(";", ""));
                    buffer = "";
                }
            }
        } catch (IOException e) {
            Log.e("db-error", e.toString());
        } finally {
            try {
                if (in != null)
                    in.close();
            } catch (IOException e) {
                Log.e("db-error", e.toString());
            }
        }
    }

    public void saveCheckDate() {
        List<SystemConfig> systemConfigs = DataSupport.where("paramName = ?", "LastDetDate").find(SystemConfig.class);
        List<SystemConfig> lastDetNums = DataSupport.where("paramName = ?", "LastDetNum").find(SystemConfig.class);
        SystemConfig lastDetNum = lastDetNums.get(0);
        SystemConfig config = systemConfigs.get(0);
        String currentTime = dateFormat.format(new Date());
        int lastDate = Integer.parseInt(config.getParamValue());
        int currentDate = Integer.parseInt(currentTime);
        Log.d("zw", "lastdate" + lastDate + " currentDate" + currentDate + " ");
        if (currentDate > lastDate) {
            config.setParamValue(currentTime);
            config.saveOrUpdate("paramName = ?", config.getParamName());
            lastDetNum.setParamValue("1");
            lastDetNum.saveOrUpdate("paramName = ?", lastDetNum.getParamName());
        } else if (currentDate == lastDate) {
            String res = lastDetNum.getParamValue();
            int i = Integer.parseInt(res) + 1;
            Log.d("zw", "res " + res + "resl" + i);
            lastDetNum.setParamValue(String.valueOf(i));
            lastDetNum.saveOrUpdate("paramName = ?", lastDetNum.getParamName());

        }
    }

    public String getDevUuid() {
        List<DevUuid> devUuid = DataSupport.findAll(DevUuid.class);
        return devUuid.get(0).getUserAbbreviation();
    }

    public String getDetectionSnDate() {
        String result = "";
        List<SystemConfig> systemConfigs = DataSupport.where("paramName = ?", "LastDetDate").find(SystemConfig.class);
        List<SystemConfig> lastDetNums = DataSupport.where("paramName = ?", "LastDetNum").find(SystemConfig.class);
        SystemConfig lastDetNum = lastDetNums.get(0);
        SystemConfig config = systemConfigs.get(0);
        Log.d("zw", lastDetNum.getParamValue() + config.getParamValue());
        String currentTime = dateFormat.format(new Date());
        int lastDate = Integer.parseInt(config.getParamValue());
        int currentDate = Integer.parseInt(currentTime);
        if (currentDate > lastDate) {
            result = currentTime + "001";
        } else if (currentDate == lastDate) {
            int current = Integer.parseInt(lastDetNum.getParamValue()) + 1;
            if (current < 10) {
                result = currentTime + "00" + current;
            } else if (current > 10 && current < 100) {
                result = currentTime + "0" + current;
            } else {
                result = currentTime + current;
            }
        } else if (currentDate < lastDate) {
            result = (Integer.parseInt(lastDetNum.getParamValue()) + 1) + "";
        }
        return result;
    }


    public void addPermission(long sourceoperateid, long userTypeId) throws RemoteException {
        P_UserTypePermission p_userTypePermission = new P_UserTypePermission();
        p_userTypePermission.setP_sourceoperator_id(sourceoperateid);
        p_userTypePermission.setUsertype(userTypeId);
        p_userTypePermission.setRighttype(1);
        p_userTypePermission.saveOrUpdate("p_sourceoperator_id = ? and usertype = ?", sourceoperateid + "", userTypeId + "");
    }


}
