package org.edu.mail.usage;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.edu.mail.usage.util.UMailUtils;

import javax.mail.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 账户类，包含发件和收件服务器地址信息
 */
public class UMailAccount {

    private String account;
    private String password;

    private String recProtocol;
    private String recHost;
    private int recPort;
    private boolean recIsSSL;

    private String sendProtocol;
    private String sendHost;
    private int sendPort;
    private boolean sendIsSSL;

    private static final String WINDOW_CONF_FILE_NAME = "d://umail//conf//ServerConf.xml";
    private static final String UNIX_CONF_FILE_NAME = "/usr/myprogram/umail/conf/ServerConf.xml";

    private Logger logger0 = Logger.getLogger(UMailAccount.class.getName());
    public UMailAccount(String account, String password){
        this.account = account;
        this.password = password;
        sendProtocol = "smtp";
        optimizeAccount(account);
    }

    public String getRecProtocol() {
        return recProtocol;
    }

    public String getSendProtocol() {
        return sendProtocol;
    }

    public UMailAccount(String recType, String account, String password,
                        String recHost, int recPort, boolean recIsSSL,
                        String sendHost, int sendPort, boolean sendIsSSL){
        this.account = account;
        this.password = password;
        // 如果是ssl要转换
        UMailProtocol recProtocol = UMailProtocol.parseSSL(recType, recIsSSL);
        this.recProtocol = recProtocol.getName();
        this.recHost = recHost;
        this.recPort = recPort;
        this.recIsSSL = recIsSSL;
        // 如果是ssl要转换
        UMailProtocol sendProtocol = UMailProtocol.parseSSL("smtp", sendIsSSL);
        this.sendProtocol = sendProtocol.getName();
        this.sendHost = sendHost;
        this.sendPort = sendPort;
        this.sendIsSSL = sendIsSSL;
    }

    public String getAccount() {
        return account;
    }

    public String getPassword() {
        return password;
    }


    public String getRecHost() {
        return recHost;
    }

    public int getRecPort() {
        return recPort;
    }

    public boolean isRecIsSSL() {
        return recIsSSL;
    }

    public String getSendHost() {
        return sendHost;
    }

    public int getSendPort() {
        return sendPort;
    }

    public boolean isSendIsSSL() {
        return sendIsSSL;
    }

    private void optimizeAccount(String account){
        // 使用正则表达式提取user, server-name
        String reg = "([A-Za-z0-9_\\-\\.]+)@([A-Za-z0-9_\\-\\.]+)";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(account);
        // 邮件服务器基本信息
        String username = null;
        String domainname = null;
        if(matcher.find()){
            username = matcher.group(0);
            domainname = matcher.group(2);
        }else{
            throw new IllegalArgumentException("邮箱账号不合法");
        }
        // 从XML文件读取mail-server信息
        boolean existInConf = false;// 是否存在配置中
        SAXReader reader = new SAXReader();
        Document document = null;
        try {
            InputStream is = ClassLoader.getSystemResourceAsStream("conf/ServerConf.xml");
            if(UMailUtils.isWindowEnv()){
                if(is == null) {
                    is = new FileInputStream(new File(WINDOW_CONF_FILE_NAME));
                }
            }else{
                if(is == null) {
                    is = new FileInputStream(new File(UNIX_CONF_FILE_NAME));
                }
            }
            document = reader.read(is);
        }catch (DocumentException e){
            e.printStackTrace();
        }catch (FileNotFoundException e2){
            e2.printStackTrace();
        }
        List<Node> list = document.selectNodes("//mail-servers/mail-server");
        for(Node node: list){
            // 找到服务器信息
            if(node.selectSingleNode("server-name").getStringValue().equals(domainname)){
                existInConf = true;
                // 收件服务器
                String _recType = node.selectSingleNode("default-receiver").getStringValue();
                UMailProtocol protocol = null;
                boolean isSSL = node.selectSingleNode(_recType + "-ssl").getStringValue().equals("0")? false : true;
                protocol = UMailProtocol.parseSSL(_recType, isSSL);
                recProtocol = protocol.getName();
                recHost = node.selectSingleNode(_recType + "-name").getStringValue();
                recPort = Integer.valueOf(node.selectSingleNode(_recType + "-port").getStringValue());
                recIsSSL = isSSL;
                // 发件服务器
                isSSL = node.selectSingleNode("smtp-ssl").getStringValue().equals("0")? false : true;
                protocol = UMailProtocol.parseSSL("smtp", isSSL);
                sendProtocol = protocol.getName();
                sendHost = node.selectSingleNode("smtp-name").getStringValue();
                sendPort = Integer.valueOf(node.selectSingleNode("smtp-port").getStringValue());
                sendIsSSL = isSSL;
            }
        }
        // 配置文件中不含服务器信息
        if(!existInConf){
            recProtocol = UMailProtocol.POP3.getName();
            recHost = UMailProtocol.POP3.getPrefix() + domainname;
            recPort = UMailProtocol.POP3.getPort();
            recIsSSL = false;
            // 发件服务器
            sendHost = UMailProtocol.SMTP.getPrefix() + domainname;
            sendPort = UMailProtocol.SMTP.getPort();
            sendIsSSL = false;
        }
    }

    /**
     * 邮箱账号是否合法
     * @return true邮箱账号合法，false邮箱账号不合法
     */
    public boolean isValid(){
        boolean valid = true;
        Session session = Session.getInstance(System.getProperties(), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(account, password);
            }
        });
        session.setDebug(true);
        try {
            logger0.info("("+recHost+","+recPort+","+account+")");
            // 收件服务器是否合法
            Store store = session.getStore(recProtocol);
            store.connect(recHost, recPort, account, password);
            store.close();
            // 发件服务器是否合法
            logger0.info("("+sendHost+","+sendPort+","+account+")");
            if(UMailUtils.isWindowEnv()){
                Transport transport = session.getTransport(sendProtocol);
                transport.connect(sendHost, sendPort, account, password);
                transport.close();
            }else{
                Transport transport = session.getTransport(UMailProtocol.SMTPSSL.getName());
                transport.connect(sendHost, UMailProtocol.SMTPSSL.getPort(), account, password);
                transport.close();
            }
        }catch (MessagingException e){
            logger0.info(e.toString());
            valid = false;
        }finally {
            return valid;
        }

    }
}
