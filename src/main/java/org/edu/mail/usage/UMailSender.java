package org.edu.mail.usage;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.lang3.StringUtils;
import org.edu.mail.usage.job.SendJob;
import org.edu.mail.usage.search.IndexTerm;
import org.edu.mail.usage.util.UMailUtils;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.edu.mail.usage.UMessageSendWay.*;
/**
 * 邮件发送类
 */
public class UMailSender extends UMailService{

    private UMailSender(String account, String password){
        super(account, password);
    }

    private UMailSender(UMailAccount acc){
        super(acc);
    }

    // 获取邮件接收对象
    public static UMailSender newInstance(String account, String password) throws UMailException{
        if(StringUtils.isEmpty(account) || StringUtils.isEmpty(password)){
            throw new UMailException("账号和密码不能为空", new NullPointerException());
        }
        return new UMailSender(account, password);
    }
    public static UMailSender newInstance(UMailAccount acc){
        return new UMailSender(acc);
    }

    public void doSend(UMailMessage sourceMsg, int sendWay, Map args) throws UMailException{
        Message msg = sourceMsg.toMimeMessage(session);
        try{
            // 加密发送
            if((sendWay & ENCRYPT) != 0){
                // zip：由提示词和文件部分构成
                MimeMultipart multipart = new MimeMultipart();
                // zip提示词部分
                MimeBodyPart bodyPart = new MimeBodyPart();
                bodyPart.setText("这是一封加密邮件，请使用标准zip压缩工具解压", "GB2312");
                multipart.addBodyPart(bodyPart);
                // 压缩源邮件成zip并加密
                Message srcMessage = sourceMsg.toMimeMessage(session);
                String tempDir = conf.getProperty("temp.directory") + separator+"Temp-" + System.currentTimeMillis() +separator;
                if(!Files.exists(Paths.get(tempDir))){
                    Files.createDirectories(Paths.get(tempDir));
                }
                File srcFile = new File(tempDir + srcMessage.getSubject() + ".eml");
                srcMessage.writeTo(new FileOutputStream(srcFile));
                File zipFile = UMailUtils.zipAndEncrypt(srcFile, (String) args.get("secret"));
                // zip文件部分
                MimeBodyPart zipBodyPart = new MimeBodyPart();
                zipBodyPart.attachFile(zipFile);
                multipart.addBodyPart(zipBodyPart);

                msg.setContent(multipart);
            }
            // 开始发送
            Transport transport;
            if(UMailUtils.isWindowEnv()){
                transport = session.getTransport(uaccount.getSendProtocol());
                transport.connect(uaccount.getSendHost(), uaccount.getSendPort(), uaccount.getAccount(), uaccount.getPassword());
            }else{
                transport = session.getTransport(UMailProtocol.SMTPSSL.getName());
                transport.connect(uaccount.getSendHost(), UMailProtocol.SMTPSSL.getPort(), uaccount.getAccount(), uaccount.getPassword());
            }
            // 群发单显
            if((sendWay & GROUP_NOT_SEE) != 0){
                Address[] addresses = msg.getRecipients(Message.RecipientType.TO);
                for (Address address : addresses) {
                    msg.setRecipients(Message.RecipientType.TO, new InternetAddress[]{(InternetAddress)address});
                    transport.sendMessage(msg, new InternetAddress[]{(InternetAddress)address});
                }
                transport.close();
            }else{// 正常发送
                transport.sendMessage(msg, msg.getRecipients(Message.RecipientType.TO));
                transport.close();
                // 保存到已发送文件夹
                if (uaccount.getRecProtocol().startsWith("imap")) {
                    UMailManager manager = UMailManager.newInstance(uaccount.getAccount(), uaccount.getPassword());
                    manager.move(msg, "已发送");
                }
            }
        }catch (MessagingException e){
            throw new UMailException(e);
        }catch (IOException e1){
            throw new UMailException(e1);
        }catch (ZipException e3){
            throw new UMailException("邮件加密错误", e3);
        }
    }
    public void send(UMailMessage sourceMsg, int sendWay, Map args) throws UMailException{
        try{
            // 定时发送
            if((sendWay & SCHEDULE) != 0){
                SchedulerFactory sf = new StdSchedulerFactory();
                Scheduler sched;
                    sched = sf.getScheduler();
                    // 使用JobDetail中的JobDataMap携带数据
                    Map<String, Object> dataMap = new HashMap<>();
                    dataMap.put("sender", this);
                    dataMap.put("sourceMsg", sourceMsg);
                    dataMap.put("sendWay", sendWay ^ SCHEDULE);
                    dataMap.put("args", args);
                    JobDataMap jobDataMap = new JobDataMap(dataMap);
                    // define the job and tie it to our HelloJob class
                    JobDetail job = JobBuilder.newJob(SendJob.class)
                            .withIdentity("sender-job", sourceMsg.getFrom())
                            .usingJobData(jobDataMap)
                            .build();
                    // 获取参数
                    Date date = (Date)args.get("date");
                    // Define a Trigger that will fire "date", and not repeat
                    Trigger trigger = TriggerBuilder.newTrigger()
                            .withIdentity("sender-trigger", sourceMsg.getFrom())
                            .startAt(date)
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                                    .withIntervalInMinutes(1)
                                    .withRepeatCount(0))
                            .build();
                    // Tell quartz to schedule the job using our trigger
                    sched.scheduleJob(job, trigger);
                    sched.start();
                    Thread.sleep(date.getTime()- System.currentTimeMillis());// 睡眠保证任务及时安排
                    sched.shutdown(true);
            }
            else{
                doSend(sourceMsg, sendWay ^ SCHEDULE, args);
            }
        }catch (SchedulerException e2) {
            throw new UMailException("邮件定时发送错误", e2);
        }catch (InterruptedException e3){
            throw new UMailException("邮件定时发送错误", e3);
        }
    }

    /**
     * json调用方法
     * <code>
     * {
     *   "msg": {"content":"...","from":"1337078409@qq.com","replayTo":["1337078409@qq.com"],"subject":"JavaMail测试定时发送邮件","to":["17370842180@189.cn"],"type":"html"},
     *   "way":"1",
     *   "date":"Sun Mar 03 2019 16:48:22 GMT+0800",
     *   "secret":"1234"
     * }
     * </code>
     * @param raw 原始json字符串
     * @throws UMailException 发送邮件异常
     */
    public void send(String raw) throws UMailException{
        JSONObject obj = JSONObject.parseObject(raw);
        String data = ((JSONObject) obj.get("msg")).toJSONString();
        // 原始邮件信息
        UMailMessage sourceMsg = JSONObject.parseObject(data, UMailMessage.class);
        // 特别处理attachment
        JSONObject msg = JSONObject.parseObject(data);
        JSONArray attachments = msg.getJSONArray(IndexTerm.ATTACHMENTS);
        List<File> fileList = new ArrayList<>();
        for (Object attach:attachments){
            JSONObject attachment = (JSONObject)attach;
            String realPath = conf.getProperty("attach.directory")+UMailUtils.decode64((String)attachment.get("path"));
            fileList.add(new File(realPath));
        }
        sourceMsg.setAttachments(fileList.toArray(new File[fileList.size()]));
        // 发送方式
        Map<String, Object> args = new HashMap<>();
        int way = (Integer) obj.get("way");
        if((way & ENCRYPT)!=0){
            args.put("secret", obj.get("secret"));
        }
        if((way & SCHEDULE)!=0){
            try {
                Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse((String) obj.get("date"));
                args.put("date", date);
            }catch (ParseException e){
                throw new UMailException("定时发送时间转换错误");
            }
        }
        send(sourceMsg, way, args);
    }
}
