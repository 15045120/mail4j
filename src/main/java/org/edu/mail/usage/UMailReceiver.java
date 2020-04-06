package org.edu.mail.usage;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.pop3.POP3Folder;
import org.apache.commons.lang3.StringUtils;
import org.edu.mail.usage.*;
import org.edu.mail.usage.job.ReceiveJob;
import org.edu.mail.usage.search.LuceneIndexer;
import org.edu.mail.usage.search.UMailFileReader;
import org.edu.mail.usage.util.UMailUtils;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.edu.mail.usage.UMailFolder.RECEIVE_BOX;
import static org.edu.mail.usage.UMessageContentType.*;
import static org.edu.mail.usage.search.IndexTerm.*;
/**
 * 邮件接受类
 */
public class UMailReceiver extends UMailService {
    
    private UMailReceiver(String account, String password){
        super(account, password);
    }
    private UMailReceiver(UMailAccount acc){
        super(acc);
    }
    // 获取邮件接收对象
    public static UMailReceiver newInstance(String account, String password) throws UMailException {
        if(StringUtils.isEmpty(account) || StringUtils.isEmpty(password)){
            throw new UMailException("账号和密码不能为空", new NullPointerException());
        }
        return new UMailReceiver(account, password);
    }
    public static UMailReceiver newInstance(UMailAccount acc) throws UMailException{
        return new UMailReceiver(acc);
    }
    // 正文列表
    private List<Map> contentList;
    // 附件列表
    private List<Map> attachmentList;

    private void extractContent(Part m) throws UMailException{
        Map<String, String> map;
        try {
            if(m.isMimeType(TEXT_STAR)){// 最外层邮件内容类型：文本类型
                map = new HashMap<>();
                // 文本
                String contentType = m.getContentType();
                String[] types = contentType.split(";");
                for (int i = 1; i < types.length; i++) {
                    int index = types[i].indexOf("=");
                    String value = types[i].substring(index+1);
                    if(value.startsWith("\"")){
                        value = value.substring(1, value.length()-1);
                    }
                    map.put(types[i].substring(0, index).trim(), MimeUtility.decodeText(value));
                }
                // 文本
                map.put("type",  types[0].trim());
                map.put("text", UMailUtils.replaceDoubleQuotes((String)m.getContent())
                );
                contentList.add(map);
            } else if(m.isMimeType(MULTIPART_STAR)){// 最外层邮件内容类型：多部分类型，alternative/mixed/related
                MimeMultipart mp = (MimeMultipart) m.getContent();
                MimeBodyPart bodyPart;
                int count = mp.getCount();
                for (int i = 0; i < count; i++) {
                    bodyPart = (MimeBodyPart)mp.getBodyPart(i);
                    extractContent(bodyPart);
                }
            }else if(m.isMimeType(MESSAGE_REC822)){// 嵌套邮件
                extractContent((Part) m.getContent());
            }else{// 最外层邮件内容类型：其他类型 image/gif;application/pdf;等
                //这里只处理附件
                String disp = m.getDisposition();
                if (disp == null || disp.equalsIgnoreCase(UMessageDisposition.ATTACHMENT)){
                    InputStream mis = (InputStream) m.getContent();
                    map = new HashMap<>();
                    // content-type
                    String contentType = m.getContentType();
                    contentType = contentType.replaceAll("[\r\t\n\"]", "");
                    String[] types = contentType.split(";");
                    for (int i = 1; i < types.length; i++) {
                        int index = types[i].indexOf("=");
                        String value = types[i].substring(index + 1);
                        map.put(types[i].substring(0, index).trim(), MimeUtility.decodeText(value).trim());
                    }
                    // 输出附件
                    String tempDir = "Temp-" + System.currentTimeMillis() + separator;
                    String savePath = conf.getProperty("attach.directory") + tempDir;
                    if (!Files.exists(Paths.get(savePath))) {
                        Files.createDirectories(Paths.get(savePath));
                    }
                    File attachFile = new File(savePath + map.get("name"));
                    FileOutputStream fos = new FileOutputStream(attachFile);
                    int d;
                    while ((d = mis.read()) != -1) {
                        fos.write(d);
                    }
                    mis.close();
                    fos.close();
                    map.put("size", UMailUtils.sizeFile(attachFile));
                    map.put("len", String.valueOf(attachFile.length()));
                    map.put("path", UMailUtils.encode64((tempDir + map.get("name")).getBytes("UTF-8")));
                    attachmentList.add(map);
                }
            }
        }catch (MessagingException e0){
            throw new UMailException(e0);
        }catch(IOException e1){
            throw new UMailException(e1);
        }
    }

    // path不存在时建立Folder
    void buildFolder(String path) throws IOException{
        logger.info("创建本地存储邮箱目录："+path);
        if(!Files.exists(Paths.get(path))){
            Files.createDirectories(Paths.get(path));
            return;
        }
    }

    UMailFileReader umailFileReader;
    /**
     * 同步服务器上邮件到本地
     * @throws UMailException
     */
    private void syncFileFromServer() throws UMailException{
        Store store = null;
        try{
            store = session.getStore(uaccount.getRecProtocol());
            // 连接
            store.connect(uaccount.getRecHost(), uaccount.getRecPort(), uaccount.getAccount(), uaccount.getPassword());
            // pop协议获取邮箱的目录问题
            if(uaccount.getRecProtocol().startsWith("imap")){
                // 获取默认根目录
                for (Object o: UMailFolder.toValueList()){
                    buildFolder(storagePath+o);
                }
                for (Object o: UMailFolder.toValueList()){
//                    buildFolder(storagePath+o);
                    // 获取邮件Id列表

                    String[] msgIds = umailFileReader.listMessageUids((String)o);
                    List msgIdList = Arrays.asList(msgIds);
                    Folder f = store.getFolder((String)o);
                    f.open(Folder.READ_ONLY);
                    Message[] msgs = f.getMessages();
                    String muid = UUID.randomUUID().toString();
                    for (Message msg: msgs) {
                        if(uaccount.getRecProtocol().indexOf("pop") != -1){
                            muid = ((POP3Folder)f).getUID(msg);
                        }else{
                            muid = Long.toString(((IMAPFolder)f).getUID(msg));
                        }
                        // 对于已下载的邮件跳过
                        if(!msgIdList.contains(muid)){
                            dumpEnvelopeJSON(msg, f);
                        }else{
                            logger.info(muid + "====已经同步====");
                        }
                    }
                    f.close();
                }

            }else{
                // pop协议时直接获取INBOX目录
                for (Object o: UMailFolder.toValueList()){
                    buildFolder(storagePath+o);
                }
                for (Object o: UMailFolder.toValueList()){
//                    buildFolder(storagePath+o);
                    if(o.equals(UMailFolder.RECEIVE_BOX.getValue())){
                        // 获取邮件Id列表
                        String[] msgIds = umailFileReader.listMessageUids(UMailFolder.RECEIVE_BOX.getValue());
                        List msgIdList = Arrays.asList(msgIds);
                        Folder f = store.getFolder((String)o);
                        f.open(Folder.READ_ONLY);
                        Message[] msgs = f.getMessages();
                        String muid = UUID.randomUUID().toString();
                        for (Message msg: msgs) {
                            if(uaccount.getRecProtocol().indexOf("pop") != -1){
                                muid = ((POP3Folder)f).getUID(msg);
                            }else{
                                muid = Long.toString(((IMAPFolder)f).getUID(msg));
                            }
                            // 对于已下载的邮件跳过
                            if(!msgIdList.contains(muid)){
                                dumpEnvelopeJSON(msg, f);
                            }else{
                                logger.info(muid + "====已经同步====");
                            }
                        }
                        f.close();
                    }
                }
            }
        }catch(NoSuchProviderException e0){
            throw new UMailException(e0);
        }catch(MessagingException e1){
            throw new UMailException(e1);
        }catch (IOException e2){
            throw new UMailException(e2);
        }
        finally{
            try {
                store.close();
            }catch(MessagingException e){
                throw new UMailException(e);
            }
        }
    }

    private String storagePath;
    private String attachPath;

    /**
     * 定时收取邮件
     * @param uid 用户id
     * @param period 周期
     * @throws UMailException 收取邮件异常
     */
    public void syncWithPeriod(String uid, long period) throws UMailException {
        // 唯一标识
        String identity = UUID.randomUUID().toString();
        SchedulerFactory sf = new StdSchedulerFactory();
        Scheduler sched = null;
        try {
            sched = sf.getScheduler();
            // 使用JobDetail中的JobDataMap携带数据
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("receiver", this);
            dataMap.put("uid", uid);
            JobDataMap jobDataMap = new JobDataMap(dataMap);
            // define the job and tie it to our HelloJob class
            JobDetail job = JobBuilder.newJob(ReceiveJob.class)
                    .withIdentity("receiver-job", identity)
                    .usingJobData(jobDataMap)
                    .build();
            // Define a Trigger that will fire "date", and not repeat
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("receiver-trigger", identity)
                    .startAt(new Date())
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(period)
                            .repeatForever())
                    .build();

            // Tell quartz to schedule the job using our trigger
            sched.scheduleJob(job, trigger);
            sched.start();
//            sched.shutdown(true);
        }catch(SchedulerException e){
            throw new UMailException("定时收取错误", e);
        }
    }
    /**
     * 每隔15分钟收取邮件
     * 同步并建立索引
     * @param uid 用户id
     * @throws UMailException 收取邮件异常
     */
    public void sync(String uid) throws UMailException {
        syncWithPeriod(uid, 15 * 60 * 1000);
        // todo 使用下面方法没用
//        syncWithPeriod(uid, Long.valueOf(conf.getProperty("receiver.period")).longValue());
    }
    public void doSync(String uid) throws UMailException {
        storagePath = conf.getProperty("storage.directory") + uid + separator + uaccount.getAccount() + separator;
        // 同步邮件至本地
        try {
            buildFolder(storagePath);
        }catch (IOException e){
            throw new UMailException("创建本地邮件存储目录失败", e);
        }
        umailFileReader = UMailFileReader.newInstance(uid, uaccount.getAccount());
        syncFileFromServer();
        logger.info("同步邮件完成，开始建立索引");

        LuceneIndexer indexer = LuceneIndexer.newInstance(uid, uaccount.getAccount());
        indexer.index();
    }

    private void dumpEnvelopeJSON(Message m, Folder folder) throws UMailException {
        String directory = storagePath + folder.getName() + separator;
        MimeMessage mm = (MimeMessage)m;
        Address[] as = null;
        Map<String, Object> map = new HashMap<>();
        // 创建JSON文档
        try {
            logger.info("收取<"+uaccount.getAccount()+">邮件：" + m.getSubject());
            // messageID为空的直接返回
            if(mm.getMessageID() == null){
                return;
            }
            // MESSAGE_ID
            map.put(MESSAGE_ID, mm.getMessageID());
            // DATE
            Date d = mm.getSentDate();
            // 格式化方面后面lucene索引使用Range Search查询
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            map.put(DATE, d != null ? format.format(d) : "UNKNOWN");
            // FROM
            List<String> list = new ArrayList<>();
            if ((as = mm.getFrom()) != null) {
                for (Address a : as) {
                    String personal = (((InternetAddress)a).getPersonal()== null) ?
                            ((InternetAddress)a).getAddress():
                            ((InternetAddress)a).getPersonal();
                    list.add(personal + "<"+((InternetAddress)a).getAddress()+">");
                }
            }
            map.put(FROM, JSONObject.toJSON(list));
            // TO
            list = new ArrayList<>();
            if ((as = mm.getRecipients(Message.RecipientType.TO)) != null) {
                for (Address a : as) {
                    String personal = (((InternetAddress)a).getPersonal()== null) ?
                            ((InternetAddress)a).getAddress():
                            ((InternetAddress)a).getPersonal();
                    list.add(personal + "<"+((InternetAddress)a).getAddress()+">");
                }
            }
            map.put(TO, JSONObject.toJSON(list));
            // CC
            list = new ArrayList<>();
            if ((as = mm.getRecipients(Message.RecipientType.CC)) != null) {
                for (Address a : as) {
                    String personal = (((InternetAddress)a).getPersonal()== null) ?
                            ((InternetAddress)a).getAddress():
                            ((InternetAddress)a).getPersonal();
                    list.add(personal + "<"+((InternetAddress)a).getAddress()+">");
                }
            }
            map.put(CC, JSONObject.toJSON(list));
            // BCC
            list = new ArrayList<>();
            if ((as = mm.getRecipients(Message.RecipientType.BCC)) != null) {
                for (Address a : as) {
                    String personal = (((InternetAddress)a).getPersonal()== null) ?
                            ((InternetAddress)a).getAddress():
                            ((InternetAddress)a).getPersonal();
                    list.add(personal + "<"+((InternetAddress)a).getAddress()+">");
                }
            }
            map.put(BCC, JSONObject.toJSON(list));
            // SUBJECT 会遇见无主题情况
            map.put(SUBJECT, StringUtils.isEmpty(mm.getSubject()) ?"":mm.getSubject());
            // CONTENT
            contentList = new ArrayList<Map>();
            attachmentList = new ArrayList<Map>();
            extractContent(mm);
            List<Map> singleContentList = new ArrayList<Map>();
            // 这里只保留一个content
            if(contentList.size() > 1){
                for (Map con: contentList) {
                    if(con.get("type").equals("text/html")){
                        singleContentList.add(con);
                        break;
                    }
                }
            }else{
                singleContentList.addAll(contentList);
            }

            map.put(CONTENT, JSONObject.toJSON(singleContentList));
            // ATTACHMENT
            map.put(ATTACHMENTS, JSONObject.toJSON(attachmentList));
            // FLAGS
            Flags flags = m.getFlags();
            map.put(FLAGS, flags.toString());
            // BOX
            map.put(BOX, folder.getName());
            // NEW
            map.put(NEW, true);
            // MESSAGE_UID
            String muid = UUID.randomUUID().toString();
            if(uaccount.getRecProtocol().indexOf("pop") != -1){
                muid = ((POP3Folder)folder).getUID(m);
            }else{
                muid = Long.toString(((IMAPFolder)folder).getUID(m));
            }
            map.put(MESSAGE_UID, muid);
            // 将JSON文档写入文件,uid作为文件名
            FileOutputStream fos = new FileOutputStream(new File(directory + muid + ".json"));
            fos.write(JSON.toJSONBytes(map));
            fos.close();
        }catch(MessagingException e0){
            throw new UMailException(e0);
        }catch(UnsupportedEncodingException e1){
            throw new UMailException(e1);
        }catch(IOException e2){
            throw new UMailException(e2);
        }
    }
}
