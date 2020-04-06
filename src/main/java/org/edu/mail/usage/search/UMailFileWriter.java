package org.edu.mail.usage.search;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.pop3.POP3Folder;
import org.apache.commons.lang3.StringUtils;
import org.edu.mail.usage.UMailException;
import org.edu.mail.usage.UMailMessage;
import org.edu.mail.usage.util.UMailUtils;

import javax.mail.Address;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.edu.mail.usage.UMessageSendWay.ENCRYPT;
import static org.edu.mail.usage.UMessageSendWay.SCHEDULE;
import static org.edu.mail.usage.search.IndexTerm.*;

/**
 * Created by ibu on 2019/3/11.
 */
public class UMailFileWriter extends LuceneService {
    private UMailFileWriter(String uid, String account){
        super(uid, account);
    }
    // 获取邮件接收对象
    public static UMailFileWriter newInstance(String uid, String account) throws UMailException {
        if(StringUtils.isEmpty(account)){
            throw new UMailException("账号不能为空", new NullPointerException());
        }
        return new UMailFileWriter(uid, account);
    }

    /**
     * 删除信件
     * @param folder 邮箱目录
     * @param msgUids 邮件Uid数组
     * @return true删除成功，false删除失败
     */
    public boolean deleteMessages(String folder, String[] msgUids){
        logger.info("UMailFileWriter.deleteMessages("+folder+","+msgUids.toString()+ ")");
        boolean deleted = true;
        for (String msgUid : msgUids) {
            File file = new File(storagePath + separator + folder + separator + msgUid + ".json");
            deleted = (deleted && file.delete());
        }
        return deleted;
    }
    /**
     * 删除账号
     * @param account 账号
     * @return true删除成功，false删除失败
     */
    public boolean deleteAccount(String account){
        logger.info("UMailFileWriter.deleteAccount("+account+")");
        try {
            doClear(storagePath.toString());
            return true;
        }catch (IOException e){
            return false;
        }
    }

    private void doClear(String path) throws IOException{
        File root = new File(path);
        File[] files = root.listFiles();
        if(files == null || files.length == 0){
            Files.delete(Paths.get(path));
        }else{
            for (File file: files) {
                doClear(path +separator + file.getName());
            }
        }
    }

    /**
     * 保存邮件至草稿箱
     * @param data 邮件内容
     * @return true保存成功，false保存失败
     */
    public boolean saveMessage(String data){
        logger.info("UMailFileWriter.saveMessage("+data+")");
        boolean saved = true;
        try {
            String directory = storagePath + separator+"草稿箱" + separator;
            FileOutputStream fos = new FileOutputStream(new File(directory + UUID.randomUUID().toString() + ".json"));
            fos.write(data.getBytes());
            fos.close();
        }catch (IOException e){
            saved = false;
        }
        return saved;
    }
}