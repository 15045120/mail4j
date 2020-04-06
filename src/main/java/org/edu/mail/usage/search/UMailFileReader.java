package org.edu.mail.usage.search;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.edu.mail.usage.UMailException;
import org.edu.mail.usage.UMailManager;
import org.edu.mail.usage.UMailMessage;
import org.edu.mail.usage.util.UMailUtils;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.edu.mail.usage.search.IndexTerm.*;

/**
 * Created by ibu on 2019/3/2.
 */
public class UMailFileReader extends LuceneService {
    private UMailFileReader(String uid, String account){
        super(uid, account);
    }
    // 获取邮件接收对象
    public static UMailFileReader newInstance(String uid, String account) throws UMailException {
        if(StringUtils.isEmpty(account)){
            throw new UMailException("账号不能为空", new NullPointerException());
        }
        return new UMailFileReader(uid, account);
    }

    /**
     * 账号列表
     * @return 账号数组
     */
    public String[] listAccounts(){
        logger.info("UMailFileReader.listAccounts()");
        File root = storagePath.getParent().toFile();
        String[] children = root.list();
        return children;
    }

    /**
     * 邮箱顶级目录列表
     * @return 邮箱目录数组
     */
    public String[] listFolders(){
        logger.info("UMailFileReader.listFolders()");
        File root = storagePath.toFile();
        String[] children = root.list();
        return children;
    }

    /**
     * 邮件列表
     * @param folder 邮箱目录
     * @return 邮件JSON对象数组
     */
    public Object[] listMessages(String folder){
        logger.info("UMailFileReader.listMessages()");
        List<Object> list = new ArrayList<>();
        File root = new File(storagePath + separator + folder);
        File[] children = root.listFiles();
        for (File child : children) {
            if (child.isFile()) {
                list.add(JSONObject.parse(UMailUtils.file2Str(child, "utf-8")));
            }
        }
        return list.toArray();
    }

    /**
     * 邮件UId列表
     * @param folder 邮箱目录
     * @return 邮件UId数组
     */
    public String[] listMessageUids(String folder){
        logger.info("UMailFileReader.listMessageUids()");
        List<String> list = new ArrayList<>();
        File root = new File(storagePath + separator + folder);
        for(String str: root.list()){
            list.add(str.substring(0, str.indexOf(".json")));
        }
        return list.toArray(new String[list.size()]);
    }

    public static final String REPLY = "回复";

    public static final String FORWARD = "转发";

    /**
     * todo 已读邮件
     * @param folder 邮箱目录
     * @param msgUid 邮件Uid
     * @return JSON邮件对象
     */
    public Object readMessage(String folder, String msgUid){
        logger.info("UMailFileReader.readMessage()");
        File file = new File(storagePath + separator + folder + separator + msgUid + ".json");
        JSONObject obj = JSONObject.parseObject(UMailUtils.file2Str(file,"utf-8"));
        try {
            obj.put("new", false);
            FileOutputStream fos = new FileOutputStream(new File(storagePath + separator + folder + separator + msgUid + ".json"),
                    false);
            fos.write(JSON.toJSONBytes(obj));
            fos.close();
        }catch (IOException e){

        }
        return obj;
    }
    /**
     * todo 转发
     * @param folder 邮箱目录
     * @param msgUid 邮件Uid
     * @return JSON邮件对象
     */
    public Object forward(String folder, String msgUid){
        logger.info("UMailFileReader.forward()");
        File file = new File(storagePath + separator + folder + separator + msgUid + ".json");
        JSONObject obj = JSONObject.parseObject(UMailUtils.file2Str(file,"utf-8"));
        // 只回复发件人
        obj.put(FROM, new String[]{account});
        obj.put(TO, new String[0]);
        obj.put(CC, new String[0]);
        obj.put(BCC, new String[0]);
        // 主题
        obj.put(SUBJECT, FORWARD +": "+obj.get(SUBJECT));
        return obj;
    }

    /**
     * todo 回复
     * @param folder 邮箱目录
     * @param msgUid 邮件Uid
     * @param type 邮件类型，html或plain
     * @return JSON邮件对象
     */
    public Object reply(String folder, String msgUid, String type){
        logger.info("UMailFileReader.reply()");
        File file = new File(storagePath + separator + folder + separator + msgUid + ".json");
        JSONObject obj = JSONObject.parseObject(UMailUtils.file2Str(file,"utf-8"));
        // 回复内容
         obj.put(CONTENT, parseContent(obj, type));
        // 只回复发件人
        obj.put(TO, new String[]{obj.getJSONArray(FROM).get(0).toString()});
        obj.put(FROM, new String[]{account});
        obj.put(CC, new String[0]);
        obj.put(BCC, new String[0]);
        // 主题
        obj.put(SUBJECT, REPLY +": "+obj.get(SUBJECT));
        // 附件
        obj.put(ATTACHMENTS, new String[0]);
        return obj;
    }

    /**
     * todo 回复全部
     * @param folder 邮箱目录
     * @param msgUid 邮件Uid
     * @param type 邮件类型，html或plain
     * @return JSON邮件对象
     */
    public Object replyAll(String folder, String msgUid, String type){
        logger.info("UMailFileReader.replyAll()");
        File file = new File(storagePath + separator + folder + separator + msgUid + ".json");
        JSONObject obj = JSONObject.parseObject(UMailUtils.file2Str(file,"utf-8"));
        // 回复内容
        obj.put(CONTENT, parseContent(obj, type));
        // 回复收到这封邮件的所有人
        obj.put(FROM, new String[]{account});
        List<String> toAddressList = new ArrayList<>();
        JSONArray toAddresses = obj.getJSONArray(TO);
        for(Object toAddress: toAddresses){
            String to = (String)toAddress;
            if(to.indexOf("<") != -1){
                to = to.substring(to.indexOf("<"), to.indexOf(">"));
            }
            if(!to.equals(account)){
                toAddressList.add((String)toAddress);
            }
        }
        obj.put(TO, toAddressList.toArray(new String[toAddressList.size()]));
        // 主题
        obj.put(SUBJECT, REPLY +": "+obj.get(SUBJECT));
        // 附件
        obj.put(ATTACHMENTS, new String[0]);
        return obj;
    }

    // 回复内容
    @Deprecated
    private Object[] parseContent0(JSONObject obj){
        StringBuffer replyContent = new StringBuffer();

        replyContent.append("<br><br><br><blockquote style='margin-Top: 0px; margin-Bottom: 0px; margin-Left: 0.5em'>")
                .append("<div>&nbsp;</div>")
                .append("<div style='border:none;border-top:solid #B5C4DF 1.0pt;padding:3.0pt 0cm 0cm 0cm'>")
                .append("<div style='PADDING-RIGHT: 8px; PADDING-LEFT: 8px; FONT-SIZE: 12px;FONT-FAMILY:tahoma;COLOR:#000000; BACKGROUND: #efefef; PADDING-BOTTOM: 8px; PADDING-TOP: 8px'>");
        String address = obj.getJSONArray(FROM).get(0).toString();
        // 将'173@163.com<173@163.com>'转化为173@163.com
        if(address.indexOf('<') != -1){
            address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
        }
        replyContent.append("<div><b>发件人：</b>&nbsp;").append("<a href='mailto:").append(address).append("'>").append(address).append("</a></div>")
                .append("<div><b>发送时间：</b>&nbsp;").append((String)obj.get(DATE)).append("</div>")
                .append("<div><b>收件人：</b>&nbsp;");
        // 收件人
        JSONArray arr = obj.getJSONArray(TO);
        for (int i = 0; i < arr.size(); i++) {
            address = arr.get(i).toString();
            if(address.indexOf('<') != -1){
                address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
            }
            replyContent.append("<a href='mailto:").append(address).append("'>").append(address).append("</a>");
        }
        replyContent.append("</div>");
        // 抄送
        if(!obj.getJSONArray(CC).isEmpty()){
            replyContent.append("<div><b>抄送：</b>&nbsp;");
            arr = obj.getJSONArray(CC);
            for (int i = 0; i < arr.size(); i++) {
                address = arr.get(i).toString();
                if(address.indexOf('<') != -1){
                    address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                }
                replyContent.append("<a href='mailto:").append(address).append("'>").append(address).append("</a>");
            }
            replyContent.append("</div>");
        }
        // 密送
        if(!obj.getJSONArray(BCC).isEmpty()){
            replyContent.append("<div><b>密送：</b>&nbsp;");
            arr = obj.getJSONArray(BCC);
            for (int i = 0; i < arr.size(); i++) {
                address = arr.get(i).toString();
                if(address.indexOf('<') != -1){
                    address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                }
                replyContent.append("<a href='mailto:").append(address).append("'>").append(address).append("</a>");
            }
            replyContent.append("</div>");
        }
        // 主题
        replyContent.append("<div><b>主题：</b>&nbsp;").append(obj.get(SUBJECT)).append("</div>")
                .append("</div>")
                .append("</div>");
        // 正文
        replyContent.append(((JSONObject)obj.getJSONArray(CONTENT).get(0)).get("text").toString())
                    .append("</blockquote>");
        Map<String, String> content = new HashMap();
        content.put("type", "text/html");
        content.put("charset", "utf-8");
        content.put("text", replyContent.toString());
        return new Object[]{content};
    }
    // 回复内容
    private Object[] parseContent(JSONObject obj, String type){
        StringBuffer replyContent = new StringBuffer();
        // 纯文本邮件
        if(type.equalsIgnoreCase("html")){
            replyContent.append("<br><blockquote style='margin-Top: 0px; margin-Bottom: 0px; margin-Left: 0.5em'>")
                    .append("<div>&nbsp;</div>")
                    .append("<div style='border:none;border-top:solid #B5C4DF 1.0pt;padding:3.0pt 0cm 0cm 0cm'>")
                    .append("<div style='PADDING-RIGHT: 8px; PADDING-LEFT: 8px; FONT-SIZE: 12px;FONT-FAMILY:tahoma;COLOR:#000000; BACKGROUND: #efefef; PADDING-BOTTOM: 8px; PADDING-TOP: 8px'>");
            String address = obj.getJSONArray(FROM).get(0).toString();
            // 将'173@163.com<173@163.com>'转化为173@163.com
            if(address.indexOf('<') != -1){
                address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
            }
            replyContent.append("<div><b>发件人：</b>&nbsp;").append("<a href='mailto:").append(address).append("'>").append(address).append("</a></div>")
                    .append("<div><b>发送时间：</b>&nbsp;").append((String)obj.get(DATE)).append("</div>")
                    .append("<div><b>收件人：</b>&nbsp;");
            // 收件人
            JSONArray arr = obj.getJSONArray(TO);
            for (int i = 0; i < arr.size(); i++) {
                address = arr.get(i).toString();
                if(address.indexOf('<') != -1){
                    address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                }
                replyContent.append("<a href='mailto:").append(address).append("'>").append(address).append("</a>");
            }
            replyContent.append("</div>");
            // 抄送
            if(!obj.getJSONArray(CC).isEmpty()){
                replyContent.append("<div><b>抄送：</b>&nbsp;");
                arr = obj.getJSONArray(CC);
                for (int i = 0; i < arr.size(); i++) {
                    address = arr.get(i).toString();
                    if(address.indexOf('<') != -1){
                        address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                    }
                    replyContent.append("<a href='mailto:").append(address).append("'>").append(address).append("</a>");
                }
                replyContent.append("</div>");
            }
            // 密送
            if(!obj.getJSONArray(BCC).isEmpty()){
                replyContent.append("<div><b>密送：</b>&nbsp;");
                arr = obj.getJSONArray(BCC);
                for (int i = 0; i < arr.size(); i++) {
                    address = arr.get(i).toString();
                    if(address.indexOf('<') != -1){
                        address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                    }
                    replyContent.append("<a href='mailto:").append(address).append("'>").append(address).append("</a>");
                }
                replyContent.append("</div>");
            }
            // 主题
            replyContent.append("<div><b>主题：</b>&nbsp;").append(obj.get(SUBJECT)).append("</div>")
                    .append("</div>")
                    .append("</div>");
            // 正文
            replyContent.append(((JSONObject)obj.getJSONArray(CONTENT).get(0)).get("text").toString())
                    .append("</blockquote>");
        }else{
            String address = obj.getJSONArray(FROM).get(0).toString();
            // 将'173@163.com<173@163.com>'转化为173@163.com
            if(address.indexOf('<') != -1){
                address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
            }
            replyContent.append("\n-------------------- 原始邮件 --------------------\n")
                    .append("发件人：").append(address).append("\n")
                    .append("发送时间：").append((String)obj.get(DATE)).append("\n")
                    .append("收件人：");
            // 收件人
            JSONArray arr = obj.getJSONArray(TO);
            for (int i = 0; i < arr.size(); i++) {
                address = arr.get(i).toString();
                if(address.indexOf('<') != -1){
                    address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                }
                replyContent.append(address).append("  ");
            }
            replyContent.append("\n");
            // 抄送
            if(!obj.getJSONArray(CC).isEmpty()){
                replyContent.append("抄送：");
                arr = obj.getJSONArray(CC);
                for (int i = 0; i < arr.size(); i++) {
                    address = arr.get(i).toString();
                    if(address.indexOf('<') != -1){
                        address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                    }
                    replyContent.append(address).append("  ");
                }
                replyContent.append("\n");
            }
            // 密送
            if(!obj.getJSONArray(BCC).isEmpty()){
                replyContent.append("密送：");
                arr = obj.getJSONArray(BCC);
                for (int i = 0; i < arr.size(); i++) {
                    address = arr.get(i).toString();
                    if(address.indexOf('<') != -1){
                        address = address.substring(address.indexOf('<')+1,address.indexOf('>'));
                    }
                    replyContent.append(address).append("  ");
                }
                replyContent.append("\n");
            }
            //主题
            replyContent.append("主题：").append(obj.get(SUBJECT)).append("\n");
            // 正文
            String contentText = ((JSONObject)obj.getJSONArray(CONTENT).get(0)).get("text").toString();
            replyContent.append(UMailUtils.html2Str(contentText));
        }

        Map<String, String> content = new HashMap();
        content.put("type", "text/"+type);
        content.put("charset", "utf-8");
        content.put("text", replyContent.toString());
        return new Object[]{content};
    }
}
