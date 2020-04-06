package org.edu.mail.usage;

/**
 * 常用协议类型
 */
public final class UMailProtocol {
    final static UMailProtocol IMAP = new UMailProtocol("imap", "imap", 143);
    final static UMailProtocol SMTP = new UMailProtocol("smtp", "smtp", 25);
    final static UMailProtocol POP3 = new UMailProtocol("pop3", "pop", 110);
    final static UMailProtocol IMAPSSL = new UMailProtocol("imaps", "imap", 993);
    final static UMailProtocol SMTPSSL = new UMailProtocol("smtps", "smtp", 465);
    final static UMailProtocol POP3SSL = new UMailProtocol("pop3s", "pop", 995);

    private String name;
    private int port;
    private String prefix;

    private UMailProtocol(String name, String prefix, int port){
        this.name = name;
        this.prefix = prefix;
        this.port = port;
    }

    /**
     * 加工protocol,使用getStore(String protocol)根据ssl是否使用IMAPSSLStore还是IMAPStore
     * @param name 协议名
     * @param isSSL 是否ssl连接
     * @return UMailProtocol
     */
    static UMailProtocol parseSSL(String name, boolean isSSL){
        if(IMAP.name.equals(name)){
            return isSSL ? IMAPSSL : IMAP;
        }
        else if(SMTP.name.equals(name)){
            return isSSL ? SMTPSSL : SMTP;
        }
        else if(POP3.name.equals(name)){
            return isSSL ? POP3SSL : POP3;
        }
        else{
            throw new IllegalArgumentException("不合法的邮件传输协议");
        }
    }

    public String getName(){
        return name;
    }
    public int getPort(){
        return port;
    }
    public String getPrefix(){
        return prefix;
    }
}