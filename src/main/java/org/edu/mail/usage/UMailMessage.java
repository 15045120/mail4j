package org.edu.mail.usage;

import org.edu.mail.usage.util.UMailUtils;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.edu.mail.usage.UMessageType.*;
/**
 * 未加工邮件类
 */
public class UMailMessage {

    private String from;
    private String[] to;
    private String[] cc;// optional
    private String[] bcc;// optional
    private String[] replayTo;// optional
    private Date date;
    private String subject;
    private String content;
    private File[] attachments;

    private String type;
    private boolean hasAttachments;

    public UMailMessage(String type, String from, String[] to,  String[] cc, String[] bcc,
                        String subject, String content, File[] attachments){
        this(type, from, to, subject, content, attachments);
        this.cc = cc;
        this.bcc = bcc;
        this.type = type;
    }

    public UMailMessage(String type, String from, String[] to, String subject, String content, File[] attachments){
        this.type = type;
        this.from = from;
        this.to = to;
        this.replayTo = new String[]{from};
        this.subject = subject;
        this.content = content;
        this.attachments = attachments;
        this.hasAttachments = (null!=attachments && 0!=attachments.length);
    }

    public UMailMessage(){
    }

    // 字符串转换成Internet地址，如test<test.gmail.com>
    private String internetAddress2Str(InternetAddress address) throws UMailException{
        return address.getPersonal()+"<"+address.getAddress()+">";
    }
    // 检查是否含附件
    private void checkHasAttachment(){
        hasAttachments = (null!=attachments && 0!=attachments.length);
    }
    // 返回Message格式邮件
    Message toMimeMessage(Session session) throws UMailException{
        checkHasAttachment();
        Message msg = new MimeMessage(session);
        try {
            // From
            msg.setFrom(str2InternetAddress(from));
            List<Address> addressList = new ArrayList<>();
            // To
            for (String sourceAddress : to) {
                addressList.add(str2InternetAddress(sourceAddress));
            }
            msg.setRecipients(Message.RecipientType.TO, addressList.toArray(new InternetAddress[addressList.size()]));
            // Cc
            if (null != cc && 0 != cc.length) {
                addressList.clear();
                for (String sourceAddress : cc) {
                    addressList.add(str2InternetAddress(sourceAddress));
                }
                msg.setRecipients(Message.RecipientType.CC, addressList.toArray(new Address[addressList.size()]));
            }
            // Bcc
            if (null != bcc && 0 != bcc.length) {
                addressList.clear();
                for (String sourceAddress : bcc) {
                    addressList.add(str2InternetAddress(sourceAddress));
                }
                msg.setRecipients(Message.RecipientType.BCC, addressList.toArray(new Address[addressList.size()]));
            }
            // Reply-To
            addressList.clear();
            if (null != replayTo && 0 != replayTo.length) {
                for (String sourceAddress : replayTo) {
                    addressList.add(str2InternetAddress(sourceAddress));
                }
                msg.setReplyTo(addressList.toArray(new Address[addressList.size()]));
            }

            // Date
            msg.setSentDate(new Date());
            // Subject
            msg.setSubject(subject);
            // Content
            switch (type) {
                case HTML_TXT: {
                    // alternative
                    MimeMultipart contentMultipart = new MimeMultipart("alternative");
                    // plain
                    MimeBodyPart plainBodyPart = new MimeBodyPart();
                    DataHandler dh1 = new DataHandler(UMailUtils.html2Str(content), "text/plain; charset=utf8");
                    plainBodyPart.setDataHandler(dh1);
                    // html
                    MimeBodyPart htmlBodyPart = new MimeBodyPart();
                    DataHandler dh2 = new DataHandler(content, "text/html; charset=utf8");
                    htmlBodyPart.setDataHandler(dh2);

                    contentMultipart.addBodyPart(plainBodyPart);
                    contentMultipart.addBodyPart(htmlBodyPart);

                    if (!hasAttachments) {
                        msg.setContent(contentMultipart);
                    } else {
                        MimeMultipart multipart = new MimeMultipart("mixed");
                        MimeBodyPart bodyPart = new MimeBodyPart();
                        bodyPart.setContent(contentMultipart);
                        multipart.addBodyPart(bodyPart);
                        for (File attachment : attachments) {
                            MimeBodyPart mimeBodyPart = new MimeBodyPart();
                            mimeBodyPart.attachFile(attachment);
                            multipart.addBodyPart(mimeBodyPart);
                        }
                        msg.setContent(multipart);
                    }
                }
                break;
                case PLAIN_TXT: {
                    if (!hasAttachments) {
                        ((MimeMessage) msg).setText(content, "UTF-8");
                    } else {
                        MimeMultipart multipart = new MimeMultipart("mixed");
                        // 正文
                        MimeBodyPart bodyPart = new MimeBodyPart();
                        bodyPart.setText(content, "UTF-8");
                        multipart.addBodyPart(bodyPart);
                        // 附件
                        for (File attachment : attachments) {
                            MimeBodyPart mimeBodyPart = new MimeBodyPart();
                            mimeBodyPart.attachFile(attachment);
                            multipart.addBodyPart(mimeBodyPart);
                        }
                        msg.setContent(multipart);
                    }
                }
                break;
                default:
                    break;
            }
        }catch(MessagingException e0){
            throw new UMailException("加工邮件异常", e0);
        }catch(IOException e1){
            throw new UMailException("加工邮件异常", e1);
        }
      return msg;
    }

    // 字符串转换成Internet地址，如test<test.gmail.com>
    private Address str2InternetAddress(String sourceAddress) throws UMailException{
        Address address;
        try{
            address = new InternetAddress(sourceAddress, sourceAddress);
            Pattern pattern = Pattern.compile("^(.+?)<(.+?)>$");
            Matcher matcher = pattern.matcher(sourceAddress);
            if(matcher.find()) {
                address = new InternetAddress(matcher.group(2), matcher.group(1));
            }
        } catch (UnsupportedEncodingException e) {
            throw new UMailException("字符串" + sourceAddress + "转换成Internet异常", e);
        }
        return address;
    }
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String[] getTo() {
        return to;
    }

    public void setTo(String[] to) {
        this.to = to;
    }

    public String[] getCc() {
        return cc;
    }

    public void setCc(String[] cc) {
        this.cc = cc;
    }

    public String[] getBcc() {
        return bcc;
    }

    public void setBcc(String[] bcc) {
        this.bcc = bcc;
    }

    public String[] getReplayTo() {
        return replayTo;
    }

    public void setReplayTo(String[] replayTo) {
        this.replayTo = replayTo;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public File[] getAttachments() {
        return attachments;
    }

    public void setAttachments(File[] attachments) {
        this.attachments = attachments;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
