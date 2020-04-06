package org.edu.mail.usage;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 邮件服务类
 */
public abstract class UMailService extends UMailConfigure{
    protected Logger logger = Logger.getLogger("service");

    // 邮件会话
    protected Session session;
    // 服务器地址
    protected UMailAccount uaccount;

    protected UMailService(String account, String password){
        uaccount = new UMailAccount(account, password);
        Properties props = System.getProperties();
        session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(account, password);
                    }
                });
        session.setDebug(Boolean.valueOf(conf.getProperty("javamail.debug")));
        logger.info("邮箱账号验证完成......");
    }

    protected UMailService(UMailAccount acc){
        uaccount = acc;
        session = Session.getInstance(System.getProperties());
        session.setDebug(Boolean.valueOf(conf.getProperty("javamail.debug")));
        logger.info("邮箱账号验证完成......");
    }

}
