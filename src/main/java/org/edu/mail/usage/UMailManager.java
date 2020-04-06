package org.edu.mail.usage;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.pop3.POP3Folder;
import org.apache.commons.lang3.StringUtils;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 邮件管理类，邮件删除，复制到其他目录，邮件查找
 * 目前只支持IMAP协议
 */
public class UMailManager extends UMailService{
    private UMailReceiver receiver;

    private UMailManager(String account, String password){
        super(account, password);
    }
    private UMailManager(UMailAccount acc){
        super(acc);
    }
    // 获取邮件接收对象
    public static UMailManager newInstance(String account, String password) throws UMailException{
        if(StringUtils.isEmpty(account) || StringUtils.isEmpty(password)){
            throw new UMailException("账号和密码不能为空", new NullPointerException());
        }
        return new UMailManager(account, password);
    }
    public static UMailManager newInstance(UMailAccount acc) throws UMailException{
        return new UMailManager(acc);
    }

    private Store store;

    private void connect() throws MessagingException {
        if(store == null) {
            store = session.getStore(uaccount.getRecProtocol());
            store.connect(uaccount.getRecHost(), uaccount.getRecPort(), uaccount.getAccount(), uaccount.getPassword());
        }
        if(!store.isConnected()){
            store.connect(uaccount.getRecHost(), uaccount.getRecPort(), uaccount.getAccount(), uaccount.getPassword());
        }
    }

    /**
     * 获取邮件目录
     * @return 邮箱目录数组
     */
    public String[] listFolder(){
        //todo 这里pop只支持INBOX
        if(uaccount.getAccount().startsWith("pop")){
            return new String[]{"INBOX","草稿箱","已发送","垃圾箱","已删除"};
        }
        List<String> list = new ArrayList<>();
        try{
            connect();
            Folder root = store.getDefaultFolder();
            Folder[] folders = root.list("%");
            for(Folder folder: folders){
                list.add(folder.getName());
            }
            store.close();
        }catch (MessagingException e){
            e.printStackTrace();
        }finally {
            return list.toArray(new String[list.size()]);
        }
    }

    /**
     * 对目录增加一封邮件
     * @param msg 邮件对象
     * @param folder 邮箱目录
     * @return true移动成功，false移动失败
     */
    public boolean move(Message msg, String folder){
        boolean res = false;
        try{
            connect();
            Folder to = store.getFolder(folder);
            // append message to new folder, 这里使用copyMessages不能复制，不知道怎么回事
            to.appendMessages(new Message[]{msg});
            res = true;
        }catch (MessagingException e){
            e.printStackTrace();
        }finally {
            return res;
        }
    }

    /**
     * 移动邮件至另一文件夹下
     * @param fromFolder 源目录
     * @param toFolder 目的地目录
     * @param msgUids 邮件Uid数组
     * @return true移动成功，false移动失败
     */
    public boolean move(String fromFolder, String toFolder, String[] msgUids){
        boolean res = false;
        try{
            connect();
            Folder from = store.getFolder(fromFolder);
            from.open(Folder.READ_WRITE);
            // The destination Folder does not have to be opened.
            Folder to = store.getFolder(toFolder);
            Message[] allMsgs = from.getMessages();
            List<Message> list = new ArrayList();
            String muid = "";
            for (Message msg: allMsgs) {
                if(uaccount.getRecProtocol().indexOf("pop") != -1){
                    muid = ((POP3Folder)from).getUID(msg);
                }else{
                    muid = Long.toString(((IMAPFolder)from).getUID(msg));
                }
                for(String msgUid: msgUids){
                    if(muid.equals(msgUid)){
                        list.add(msg);
                    }
                }
            }
            int count = list.size();
            // 待移动的邮件存在
            if(count != 0){
                Message[] results = list.toArray(new Message[count]);
                // append message to new folder, 这里使用copyMessages不能复制，不知道怎么回事
                to.appendMessages(results);
                // set deleted flag to old messages in the from folder
                from.setFlags(results, new Flags(Flags.Flag.DELETED), true);
                // do delete
                from.close(true);
                res = true;
            }
            store.close();
        }catch (MessagingException e){
            e.printStackTrace();
        }finally {
            return res;
        }
    }

    /**
     * 删除一个文件夹下的多封邮件
     * @param folder 邮箱目录
     * @param msgUids 邮件Uid数组
     * @return true删除成功，false删除失败
     */
    public boolean delete(String folder, String[] msgUids){
        boolean res = false;
        try{
            connect();
            Folder from = store.getFolder(folder);
            from.open(Folder.READ_WRITE);
            Message[] allMsgs = from.getMessages();
            List<Message> list = new ArrayList();
            String muid = "";
            for (Message msg: allMsgs) {
                if(uaccount.getRecProtocol().indexOf("pop") != -1){
                    muid = ((POP3Folder)from).getUID(msg);
                }else{
                    muid = Long.toString(((IMAPFolder)from).getUID(msg));
                }
                for(String msgUid: msgUids){
                    if(muid.equals(msgUid)){
                        list.add(msg);
                    }
                }
            }
            int count = list.size();
            // 待删除的邮件存在
            if(count != 0){
                Message[] results = list.toArray(new Message[count]);
                // set deleted flag to messages
                from.setFlags(results, new Flags(Flags.Flag.DELETED), true);
                // do delete
                from.close(true);
                res = true;
            }
            store.close();
        }catch (MessagingException e){
            e.printStackTrace();
        }finally {
            return res;
        }
    }

    /**
     * 获取一封邮件
     * @param folder 邮箱目录
     * @param msgUid 邮件Uid
     * @return 邮件Message对象
     */
    public Message fetchOne(String folder, String msgUid){
        try{
            connect();
            Folder from = store.getFolder(folder);
            from.open(Folder.READ_ONLY);
            Message[] msgs = from.getMessages();
            String muid = "";
            for (Message msg: msgs ) {
                if(uaccount.getRecProtocol().indexOf("pop") != -1){
                    muid = ((POP3Folder)from).getUID(msg);
                }else{
                    muid = Long.toString(((IMAPFolder)from).getUID(msg));
                }
                if(muid.equals(msgUid)){
                    return msg;
                }
            }
            store.close();
            return null;
        }catch (MessagingException e){
            return null;
        }
    }

    /**
     * 获取多封邮件
     * @param folder 邮箱目录
     * @param msgUids 邮件Uid数组
     * @return 邮件Message对象数组
     */
    public Message[] fetchMore(String folder, String[] msgUids){
        List<Message> list = new ArrayList();
        try{
            connect();
            Folder from = store.getFolder(folder);
            from.open(Folder.READ_ONLY);
            Message[] msgs = from.getMessages();
            String muid = "";
            for (Message msg: msgs ) {
                if(uaccount.getRecProtocol().indexOf("pop") != -1){
                    muid = ((POP3Folder)from).getUID(msg);
                }else{
                    muid = Long.toString(((IMAPFolder)from).getUID(msg));
                }
                for(String msgUid: msgUids){
                    if(muid.equals(msgUid)){
                        list.add(msg);
                    }
                }
            }
            store.close();
            return list.toArray(new Message[list.size()]);
        }catch (MessagingException e){
            return list.toArray(new Message[0]);
        }
    }
}
