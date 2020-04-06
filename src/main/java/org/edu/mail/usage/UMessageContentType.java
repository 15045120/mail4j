package org.edu.mail.usage;

/**
 * 常见的content-type
 */
public interface UMessageContentType {
    String TEXT_STAR = "text/*";
    String TEXT_HTML = "text/html";
    String TEXT_PLAIN = "text/plain";
    String MULTIPART_STAR = "multipart/*";
    String MULTIPART_ALTERNATIVE = "multipart/alternative";
    String MULTIPART_MIXED = "multipart/mixed";
    String MESSAGE_REC822 = "message/rfc822";

}
